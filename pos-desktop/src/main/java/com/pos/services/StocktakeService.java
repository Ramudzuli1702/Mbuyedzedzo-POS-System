package com.pos.services;

import com.pos.database.DatabaseConnection;
import java.sql.*;
import java.util.*;

/** Inventory snapshots are immutable; counts change live stock only in an approved transaction. */
public final class StocktakeService {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    public StocktakeService() { this(() -> {
        Connection c = DatabaseConnection.getConnection();
        if (c == null) throw new SQLException("Database unavailable");
        return c;
    }); }
    public StocktakeService(Connections connections) { this.connections = connections; }

    public record Account(int id, String name, String role, String passwordHash) {}
    public record Line(int productId, String name, String barcode, String category,
                       int expected, long version, Integer counted, Integer current, Long currentVersion) {
        public boolean conflict() { return current == null || currentVersion == null || version != currentVersion || expected != current; }
    }
    public record Count(int productId, int quantity) {}
    public record CountSheet(String id, int staffId, String staffName, String status, String notes,
                             String reviewNotes, String createdAt, String submittedAt, String reviewer, List<Line> items) {}
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(String code, String message) { super(message); this.code = code; }
    }
    public Account account(String email) throws SQLException {
        try (Connection c = connections.open(); PreparedStatement p = c.prepareStatement(
                "SELECT StaffID,FullNames,UserType,UserPassword FROM Staff WHERE EmailAddress=? AND Status='Active'")) {
            p.setString(1, email);
            try (ResultSet r = p.executeQuery()) { return r.next() ? new Account(r.getInt(1),r.getString(2),r.getString(3),r.getString(4)) : null; }
        }
    }
    public Account requireStaff(int id, boolean reviewer) throws SQLException {
        try (Connection c = connections.open()) { return requireStaff(c,id,reviewer); }
    }
    private Account requireStaff(Connection c, int id, boolean reviewer) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT FullNames,UserType,UserPassword FROM Staff WHERE StaffID=? AND Status='Active'")) {
            p.setInt(1,id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new Rejected("AUTH_REQUIRED","Sign in again with an active staff account.");
                Account a = new Account(id,r.getString(1),r.getString(2),r.getString(3));
                if (reviewer && !Set.of("Admin","Manager").contains(a.role()))
                    throw new Rejected("FORBIDDEN","Only an Admin or Manager may review stocktakes.");
                return a;
            }
        }
    }
    public CountSheet start(String id, int staffId) throws SQLException {
        UUID.fromString(id);
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                requireStaff(c,staffId,false);
                try (PreparedStatement p = c.prepareStatement("INSERT IGNORE INTO Stocktake (StocktakeID,StaffID) VALUES (?,?)")) {
                    p.setString(1,id); p.setInt(2,staffId);
                    if (p.executeUpdate() == 1) {
                        try (PreparedStatement snapshot = c.prepareStatement("""
                            INSERT INTO StocktakeLine (StocktakeID,ProductID,ProductName,BarCode,CategoryName,ExpectedQuantity,ExpectedVersion)
                            SELECT ?,p.ProductID,p.ProductName,p.BarCode,c.CategoryName,p.Quantity,p.StockVersion
                            FROM Product p LEFT JOIN Category c ON c.CategoryID=p.CategoryID ORDER BY p.ProductID
                            """)) { snapshot.setString(1,id); snapshot.executeUpdate(); }
                    }
                }
                CountSheet result = load(c,id);
                if (result.staffId()!=staffId) throw new Rejected("FORBIDDEN","This count belongs to another staff member.");
                c.commit(); return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public CountSheet resume(String id, int staffId) throws SQLException {
        try (Connection c = connections.open()) {
            requireStaff(c,staffId,false);
            CountSheet sheet = load(c,id);
            if (sheet.staffId()!=staffId) throw new Rejected("FORBIDDEN","This count belongs to another staff member.");
            return sheet;
        }
    }
    public CountSheet submit(String id, int staffId, List<Count> counts, String notes) throws SQLException {
        validateCounts(counts);
        if (notes==null || notes.length()>1000) throw new Rejected("INVALID","Notes must be 1,000 characters or fewer.");
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                requireStaff(c,staffId,false);
                String status = lock(c,id,staffId);
                if (!"Draft".equals(status)) { CountSheet sheet=load(c,id); c.commit(); return sheet; } // retry-safe
                try (PreparedStatement p = c.prepareStatement("UPDATE StocktakeLine SET CountedQuantity=? WHERE StocktakeID=? AND ProductID=?")) {
                    for (Count count: counts) {
                        p.setInt(1,count.quantity()); p.setString(2,id); p.setInt(3,count.productId());
                        if (p.executeUpdate()!=1) throw new Rejected("INVALID","A counted product is not in this inventory snapshot.");
                    }
                }
                try (PreparedStatement p=c.prepareStatement("UPDATE Stocktake SET Status='Pending', Notes=?, SubmittedAt=CURRENT_TIMESTAMP WHERE StocktakeID=?")) {
                    p.setString(1,notes); p.setString(2,id); p.executeUpdate();
                }
                CountSheet sheet=load(c,id); c.commit(); return sheet;
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public static void validateCounts(List<Count> counts) {
        if (counts==null || counts.isEmpty() || counts.size()>100000) throw new Rejected("INVALID","Enter at least one physical count.");
        Set<Integer> seen=new HashSet<>();
        for (Count count:counts) if (count==null || count.productId()<=0 || count.quantity()<0 || count.quantity()>1000000 || !seen.add(count.productId()))
            throw new Rejected("INVALID","Counts must contain unique products and whole quantities from 0 to 1,000,000.");
    }
    private String lock(Connection c,String id,Integer staff) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("SELECT StaffID,Status FROM Stocktake WHERE StocktakeID=? FOR UPDATE")) {
            p.setString(1,id);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new Rejected("NOT_FOUND","Stocktake not found. Start a new count.");
                if(staff!=null && r.getInt(1)!=staff) throw new Rejected("FORBIDDEN","This count belongs to another staff member.");
                return r.getString(2);
            }
        }
    }
    public CountSheet review(String id,int reviewer,boolean approve,String notes) throws SQLException {
        if(notes==null || notes.length()>1000 || !approve && notes.isBlank()) throw new Rejected("INVALID","Enter a rejection reason (up to 1,000 characters).");
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try {
                requireStaff(c,reviewer,true);
                if(!"Pending".equals(lock(c,id,null))) throw new Rejected("ALREADY_REVIEWED","This stocktake has already been reviewed.");
                CountSheet sheet=load(c,id);
                if(approve) {
                    // Conditional writes plus the DB stock-version trigger detect every intervening stock movement.
                    try(PreparedStatement p=c.prepareStatement("UPDATE Product SET Quantity=? WHERE ProductID=? AND Quantity=? AND StockVersion=?")) {
                        for(Line line:sheet.items()) if(line.counted()!=null) {
                            p.setInt(1,line.counted()); p.setInt(2,line.productId()); p.setInt(3,line.expected()); p.setLong(4,line.version());
                            if(p.executeUpdate()!=1) throw new Rejected("STOCK_CHANGED","Stock changed after this inventory was loaded. Reject this count and ask for a fresh count; no stock was changed.");
                        }
                    }
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE Stocktake SET Status=?,ReviewedBy=?,ReviewedAt=CURRENT_TIMESTAMP,ReviewNotes=? WHERE StocktakeID=?")) {
                    p.setString(1,approve?"Approved":"Rejected");p.setInt(2,reviewer);p.setString(3,notes);p.setString(4,id);p.executeUpdate();
                }
                CountSheet result=load(c,id); c.commit();return result;
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
    public List<CountSheet> list(int reviewer) throws SQLException {
        try(Connection c=connections.open()) {
            requireStaff(c,reviewer,true);
            List<CountSheet> result=new ArrayList<>();
            try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT StocktakeID FROM Stocktake WHERE Status<>'Draft' ORDER BY (Status='Pending') DESC,CreatedAt DESC LIMIT 100")) {
                while(r.next()) result.add(load(c,r.getString(1)));
            }
            return result;
        }
    }
    private CountSheet load(Connection c,String id) throws SQLException {
        List<Line> lines=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("""
            SELECT l.*,p.Quantity AS CurrentQuantity,p.StockVersion AS CurrentVersion FROM StocktakeLine l
            LEFT JOIN Product p ON p.ProductID=l.ProductID WHERE l.StocktakeID=? ORDER BY l.ProductID
            """)) {
            p.setString(1,id);
            try(ResultSet r=p.executeQuery()) { while(r.next()) lines.add(new Line(r.getInt("ProductID"),r.getString("ProductName"),r.getString("BarCode"),r.getString("CategoryName"),r.getInt("ExpectedQuantity"),r.getLong("ExpectedVersion"),(Integer)r.getObject("CountedQuantity"),(Integer)r.getObject("CurrentQuantity"),(Long)r.getObject("CurrentVersion"))); }
        }
        try(PreparedStatement p=c.prepareStatement("SELECT s.*,st.FullNames,rv.FullNames AS Reviewer FROM Stocktake s LEFT JOIN Staff st ON st.StaffID=s.StaffID LEFT JOIN Staff rv ON rv.StaffID=s.ReviewedBy WHERE s.StocktakeID=?")) {
            p.setString(1,id);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new Rejected("NOT_FOUND","Stocktake not found.");
                return new CountSheet(id,r.getInt("StaffID"),r.getString("FullNames"),r.getString("Status"),r.getString("Notes"),r.getString("ReviewNotes"),r.getString("CreatedAt"),r.getString("SubmittedAt"),r.getString("Reviewer"),lines);
            }
        }
    }
}

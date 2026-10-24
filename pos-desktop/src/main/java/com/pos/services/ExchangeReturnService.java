package com.pos.services;

import com.pos.database.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

public class ExchangeReturnService {

    // ── Model: ExchangeRequest ────────────────────────────────────────────────

    public static class ExchangeRequest {
        private int exchangeID;
        private int originalTransactionID;
        private int newTransactionID;
        private int staffID;
        private Integer supervisorID;
        private LocalDateTime exchangeDate;
        private String status;
        private String reason;
        private String productName;
        private String newProductName;
        private BigDecimal originalPrice;
        private BigDecimal newPrice;
        private int newProductID;

        public int getExchangeID()                      { return exchangeID; }
        public void setExchangeID(int v)                { this.exchangeID = v; }
        public int getOriginalTransactionID()           { return originalTransactionID; }
        public void setOriginalTransactionID(int v)     { this.originalTransactionID = v; }
        public int getNewTransactionID()                { return newTransactionID; }
        public void setNewTransactionID(int v)          { this.newTransactionID = v; }
        public int getStaffID()                         { return staffID; }
        public void setStaffID(int v)                   { this.staffID = v; }
        public Integer getSupervisorID()                { return supervisorID; }
        public void setSupervisorID(Integer v)          { this.supervisorID = v; }
        public LocalDateTime getExchangeDate()          { return exchangeDate; }
        public void setExchangeDate(LocalDateTime v)    { this.exchangeDate = v; }
        public String getStatus()                       { return status; }
        public void setStatus(String v)                 { this.status = v; }
        public String getReason()                       { return reason; }
        public void setReason(String v)                 { this.reason = v; }
        public String getProductName()                  { return productName; }
        public void setProductName(String v)            { this.productName = v; }
        public String getNewProductName()               { return newProductName; }
        public void setNewProductName(String v)         { this.newProductName = v; }
        public BigDecimal getOriginalPrice()            { return originalPrice; }
        public void setOriginalPrice(BigDecimal v)      { this.originalPrice = v; }
        public BigDecimal getNewPrice()                 { return newPrice; }
        public void setNewPrice(BigDecimal v)           { this.newPrice = v; }
        public int getNewProductID()                    { return newProductID; }
        public void setNewProductID(int v)              { this.newProductID = v; }
    }

    // ── Model: ReturnRequest ──────────────────────────────────────────────────

    public static class ReturnRequest {
        private int returnID;
        private int transactionID;
        private int returnQuantity;
        private int staffID;
        private Integer supervisorID;
        private LocalDateTime returnDate;
        private String reason;
        private BigDecimal refundAmount;
        private String status;
        private String productName;

        public int getReturnID()                       { return returnID; }
        public void setReturnID(int v)                 { this.returnID = v; }
        public int getTransactionID()                  { return transactionID; }
        public void setTransactionID(int v)            { this.transactionID = v; }
        public int getReturnQuantity()                 { return returnQuantity; }
        public void setReturnQuantity(int v)           { this.returnQuantity = v; }
        public int getStaffID()                        { return staffID; }
        public void setStaffID(int v)                  { this.staffID = v; }
        public Integer getSupervisorID()               { return supervisorID; }
        public void setSupervisorID(Integer v)         { this.supervisorID = v; }
        public LocalDateTime getReturnDate()           { return returnDate; }
        public void setReturnDate(LocalDateTime v)     { this.returnDate = v; }
        public String getReason()                      { return reason; }
        public void setReason(String v)                { this.reason = v; }
        public BigDecimal getRefundAmount()            { return refundAmount; }
        public void setRefundAmount(BigDecimal v)      { this.refundAmount = v; }
        public String getStatus()                      { return status; }
        public void setStatus(String v)                { this.status = v; }
        public String getProductName()                 { return productName; }
        public void setProductName(String v)           { this.productName = v; }
    }

    // ── Per-transaction lookups (used by CustomerView) ────────────────────────

    /**
     * Returns all exchanges linked to a specific original transaction ID,
     * ordered most recent first. CustomerView takes the first result.
     */
    public ObservableList<ExchangeRequest> getExchangesForTransaction(int transactionID) {
        ObservableList<ExchangeRequest> list = FXCollections.observableArrayList();
        String sql = """
            SELECT e.*,
                   p1.ProductName  AS OriginalProduct,
                   p2.ProductName  AS NewProduct,
                   t1.SalePrice    AS OriginalPrice
            FROM Exchanges e
            JOIN Transactions t1 ON e.OriginalTransactionID = t1.TransactionID
            JOIN Product p1       ON t1.ProductID = p1.ProductID
            LEFT JOIN Product p2  ON e.NewProductID = p2.ProductID
            WHERE e.OriginalTransactionID = ?
            ORDER BY e.ExchangeDate DESC
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ExchangeRequest ex = new ExchangeRequest();
                ex.setExchangeID(rs.getInt("ExchangeID"));
                ex.setOriginalTransactionID(rs.getInt("OriginalTransactionID"));
                ex.setNewTransactionID(rs.getInt("NewTransactionID"));
                ex.setStaffID(rs.getInt("StaffID"));
                int supID = rs.getInt("SupervisorID");
                if (!rs.wasNull()) ex.setSupervisorID(supID);
                ex.setExchangeDate(rs.getTimestamp("ExchangeDate").toLocalDateTime());
                ex.setStatus(rs.getString("Status"));
                ex.setReason(rs.getString("Reason"));
                ex.setProductName(rs.getString("OriginalProduct"));
                ex.setNewProductName(rs.getString("NewProduct"));
                ex.setOriginalPrice(rs.getBigDecimal("OriginalPrice"));
                ex.setNewPrice(rs.getBigDecimal("NewPrice"));
                ex.setNewProductID(rs.getInt("NewProductID"));
                list.add(ex);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Returns all returns linked to a specific transaction ID,
     * ordered most recent first. CustomerView takes the first result.
     */
    public ObservableList<ReturnRequest> getReturnsForTransaction(int transactionID) {
        ObservableList<ReturnRequest> list = FXCollections.observableArrayList();
        String sql = """
            SELECT r.*, p.ProductName
            FROM Returns r
            JOIN Transactions t ON r.TransactionID = t.TransactionID
            JOIN Product p      ON t.ProductID = p.ProductID
            WHERE r.TransactionID = ?
            ORDER BY r.ReturnDate DESC
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transactionID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ReturnRequest ret = new ReturnRequest();
                ret.setReturnID(rs.getInt("ReturnID"));
                ret.setTransactionID(rs.getInt("TransactionID"));
                ret.setReturnQuantity(rs.getInt("ReturnQuantity"));
                ret.setStaffID(rs.getInt("StaffID"));
                int supID = rs.getInt("SupervisorID");
                if (!rs.wasNull()) ret.setSupervisorID(supID);
                ret.setReturnDate(rs.getTimestamp("ReturnDate").toLocalDateTime());
                ret.setReason(rs.getString("Reason"));
                ret.setRefundAmount(rs.getBigDecimal("RefundAmount"));
                ret.setStatus(rs.getString("Status"));
                ret.setProductName(rs.getString("ProductName"));
                list.add(ret);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ── Request exchange ──────────────────────────────────────────────────────

    public int requestExchange(int originalTransactionID, int staffID, String reason,
                               int newProductID, BigDecimal newPrice) {
        String sql = """
            INSERT INTO Exchanges
            (OriginalTransactionID, StaffID, ExchangeDate, Status, Reason, NewProductID, NewPrice)
            VALUES (?, ?, CURRENT_TIMESTAMP, 'Pending', ?, ?, ?)
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalTransactionID);
            ps.setInt(2, staffID);
            ps.setString(3, reason);
            ps.setInt(4, newProductID);
            ps.setBigDecimal(5, newPrice);
            if (ps.executeUpdate() > 0) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    // ── Approve exchange ──────────────────────────────────────────────────────

    public boolean approveExchange(int exchangeID, int supervisorID, String authCode) {
        return FinancialApproval.review(true, true, exchangeID, supervisorID, authCode);
    }

    // ── Reject exchange ───────────────────────────────────────────────────────

    public boolean rejectExchange(int exchangeID, int supervisorID, String authCode) {
        return FinancialApproval.review(true, false, exchangeID, supervisorID, authCode);
    }

    // ── Request return ────────────────────────────────────────────────────────

    public int requestReturn(int transactionID, int returnQuantity, int staffID,
                             String reason, BigDecimal refundAmount) {
        String sql = """
            INSERT INTO Returns
            (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount, Status)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, 'Pending')
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, transactionID);
            ps.setInt(2, returnQuantity);
            ps.setInt(3, staffID);
            ps.setString(4, reason);
            ps.setBigDecimal(5, refundAmount);
            if (ps.executeUpdate() > 0) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    // ── Approve return ────────────────────────────────────────────────────────

    public boolean approveReturn(int returnID, int supervisorID, String authCode) {
        return FinancialApproval.review(false, true, returnID, supervisorID, authCode);
    }

    // ── Reject return ─────────────────────────────────────────────────────────

    public boolean rejectReturn(int returnID, int supervisorID, String authCode) {
        return FinancialApproval.review(false, false, returnID, supervisorID, authCode);
    }

    // ── Pending queues (used by supervisor approval views) ─────────────────────

    public ObservableList<ExchangeRequest> getPendingExchanges() {
        ObservableList<ExchangeRequest> list = FXCollections.observableArrayList();
        String sql = """
            SELECT e.*,
                   p1.ProductName AS OriginalProduct,
                   p2.ProductName AS NewProduct,
                   t1.SalePrice   AS OriginalPrice,
                   e.NewPrice
            FROM Exchanges e
            JOIN Transactions t1 ON e.OriginalTransactionID = t1.TransactionID
            JOIN Product p1       ON t1.ProductID = p1.ProductID
            LEFT JOIN Product p2  ON e.NewProductID = p2.ProductID
            WHERE e.Status = 'Pending'
            ORDER BY e.ExchangeDate DESC
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ExchangeRequest ex = new ExchangeRequest();
                ex.setExchangeID(rs.getInt("ExchangeID"));
                ex.setOriginalTransactionID(rs.getInt("OriginalTransactionID"));
                ex.setStaffID(rs.getInt("StaffID"));
                ex.setExchangeDate(rs.getTimestamp("ExchangeDate").toLocalDateTime());
                ex.setStatus(rs.getString("Status"));
                ex.setReason(rs.getString("Reason"));
                ex.setProductName(rs.getString("OriginalProduct"));
                ex.setNewProductName(rs.getString("NewProduct"));
                ex.setOriginalPrice(rs.getBigDecimal("OriginalPrice"));
                ex.setNewPrice(rs.getBigDecimal("NewPrice"));
                ex.setNewProductID(rs.getInt("NewProductID"));
                list.add(ex);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public ObservableList<ReturnRequest> getPendingReturns() {
        ObservableList<ReturnRequest> list = FXCollections.observableArrayList();
        String sql = """
            SELECT r.*, p.ProductName
            FROM Returns r
            JOIN Transactions t ON r.TransactionID = t.TransactionID
            JOIN Product p      ON t.ProductID = p.ProductID
            WHERE r.Status = 'Pending'
            ORDER BY r.ReturnDate DESC
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ReturnRequest ret = new ReturnRequest();
                ret.setReturnID(rs.getInt("ReturnID"));
                ret.setTransactionID(rs.getInt("TransactionID"));
                ret.setReturnQuantity(rs.getInt("ReturnQuantity"));
                ret.setStaffID(rs.getInt("StaffID"));
                ret.setReturnDate(rs.getTimestamp("ReturnDate").toLocalDateTime());
                ret.setReason(rs.getString("Reason"));
                ret.setRefundAmount(rs.getBigDecimal("RefundAmount"));
                ret.setStatus(rs.getString("Status"));
                ret.setProductName(rs.getString("ProductName"));
                list.add(ret);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}
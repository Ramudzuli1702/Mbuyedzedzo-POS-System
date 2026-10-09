package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.views.SessionView.SessionActivity;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SessionService {

    public static class BusinessSession {
        private int sessionID;
        private int supervisorID;
        private String supervisorName;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String status;
        private BigDecimal totalCashSales;
        private BigDecimal totalCardSales;
        private BigDecimal totalSales;
        private boolean declarationSigned;
        private String notes;

        public int getSessionID() { return sessionID; }
        public void setSessionID(int sessionID) { this.sessionID = sessionID; }
        public int getSupervisorID() { return supervisorID; }
        public void setSupervisorID(int supervisorID) { this.supervisorID = supervisorID; }
        public String getSupervisorName() { return supervisorName; }
        public void setSupervisorName(String supervisorName) { this.supervisorName = supervisorName; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getTotalCashSales() { return totalCashSales; }
        public void setTotalCashSales(BigDecimal totalCashSales) { this.totalCashSales = totalCashSales; }
        public BigDecimal getTotalCardSales() { return totalCardSales; }
        public void setTotalCardSales(BigDecimal totalCardSales) { this.totalCardSales = totalCardSales; }
        public BigDecimal getTotalSales() { return totalSales; }
        public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }
        public boolean isDeclarationSigned() { return declarationSigned; }
        public void setDeclarationSigned(boolean declarationSigned) { this.declarationSigned = declarationSigned; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class SessionActivityDetail {
        private String type;
        private LocalDateTime timestamp;
        private int saleID;
        private int returnID;
        private int exchangeID;
        private String productName;
        private String newProductName;
        private int quantity;
        private double amount;
        private String status;
        private String staffName;
        private String customerName;
        private String paymentMethod;
        private String reason;
        private double topUp;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public int getSaleID() { return saleID; }
        public void setSaleID(int saleID) { this.saleID = saleID; }
        public int getReturnID() { return returnID; }
        public void setReturnID(int returnID) { this.returnID = returnID; }
        public int getExchangeID() { return exchangeID; }
        public void setExchangeID(int exchangeID) { this.exchangeID = exchangeID; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getNewProductName() { return newProductName; }
        public void setNewProductName(String newProductName) { this.newProductName = newProductName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStaffName() { return staffName; }
        public void setStaffName(String staffName) { this.staffName = staffName; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public double getTopUp() { return topUp; }
        public void setTopUp(double topUp) { this.topUp = topUp; }
    }

    public BusinessSession getActiveSession() {
        String query = """
            SELECT s.*, st.FullNames 
            FROM BusinessSessions s
            JOIN Staff st ON s.SupervisorID = st.StaffID
            WHERE s.Status = 'Active'
            ORDER BY s.StartDate DESC
            LIMIT 1
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                return extractSession(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean startSession(int supervisorID) {
        if (getActiveSession() != null) {
            return false;
        }

        String query = """
            INSERT INTO BusinessSessions 
            (SupervisorID, StartDate, Status, TotalCashSales, TotalCardSales, TotalSales)
            VALUES (?, CURRENT_TIMESTAMP, 'Active', 0.00, 0.00, 0.00)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, supervisorID);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean endSession(int sessionID, String authCode, String notes) {
        String query = """
            UPDATE BusinessSessions 
            SET EndDate = CURRENT_TIMESTAMP, 
                Status = 'Closed',
                DeclarationSigned = TRUE,
                AuthorisationCode = ?,
                Notes = ?
            WHERE SessionID = ? AND Status = 'Active'
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, authCode);
            pstmt.setString(2, notes);
            pstmt.setInt(3, sessionID);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Starts a new session initiated by a cashier (no manager or admin present).
     * Recorded with DeclarationSigned = FALSE so managers/admins can filter
     * and review these sessions later.
     *
     * @param cashierID  StaffID of the cashier starting the session
     * @param notes      Optional reason (e.g. "No manager on site")
     * @return true if the session was created successfully
     */
    public boolean startSessionAsCashier(int cashierID, String notes) {
        if (getActiveSession() != null) {
            return false;
        }

        String query = """
            INSERT INTO BusinessSessions 
            (SupervisorID, StartDate, Status, TotalCashSales, TotalCardSales, TotalSales, DeclarationSigned, Notes)
            VALUES (?, CURRENT_TIMESTAMP, 'Active', 0.00, 0.00, 0.00, FALSE, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, cashierID);
            pstmt.setString(2, "[CASHIER START - NO MANAGER/ADMIN] "
                    + (notes != null && !notes.isBlank() ? notes : ""));

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ends a session closed by a cashier (no manager or admin present).
     * Sets DeclarationSigned = FALSE and appends a flag to Notes so the
     * session shows up clearly in both the UI and the generated PDF reports.
     *
     * @param sessionID  ID of the active session to close
     * @param notes      Optional notes from the cashier for the manager/admin
     * @return true if the session was closed successfully
     */
    public boolean endSessionAsCashier(int sessionID, String notes) {
        // Ensure totals are up to date before closing
        updateSessionTotals(sessionID);

        String query = """
            UPDATE BusinessSessions 
            SET EndDate = CURRENT_TIMESTAMP,
                Status = 'Closed',
                DeclarationSigned = FALSE,
                Notes = CONCAT(COALESCE(Notes, ''), ?)
            WHERE SessionID = ? AND Status = 'Active'
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, " | [CASHIER END - AWAITING MANAGER/ADMIN COUNTERSIGNATURE] "
                    + (notes != null && !notes.isBlank() ? notes : ""));
            pstmt.setInt(2, sessionID);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void updateSessionTotals(int sessionID) {
        String query = """
            UPDATE BusinessSessions s
            SET 
                TotalCashSales = (
                    SELECT COALESCE(SUM(t.SalePrice * t.Quantity), 0)
                    FROM SessionTransactions st
                    JOIN Transactions t ON st.SaleID = t.SaleID
                    WHERE st.SessionID = ? AND t.PaymentMethod = 'Cash'
                ),
                TotalCardSales = (
                    SELECT COALESCE(SUM(t.SalePrice * t.Quantity), 0)
                    FROM SessionTransactions st
                    JOIN Transactions t ON st.SaleID = t.SaleID
                    WHERE st.SessionID = ? AND t.PaymentMethod = 'Card'
                ),
                TotalSales = (
                    SELECT COALESCE(SUM(t.SalePrice * t.Quantity), 0)
                    FROM SessionTransactions st
                    JOIN Transactions t ON st.SaleID = t.SaleID
                    WHERE st.SessionID = ?
                )
            WHERE SessionID = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, sessionID);
            pstmt.setInt(2, sessionID);
            pstmt.setInt(3, sessionID);
            pstmt.setInt(4, sessionID);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean linkSaleToSession(int sessionID, int saleID) {
        String query = "INSERT INTO SessionTransactions (SessionID, SaleID) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, sessionID);
            pstmt.setInt(2, saleID);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<BusinessSession> getAllSessions() {
        List<BusinessSession> sessions = new ArrayList<>();
        String query = """
            SELECT s.*, st.FullNames 
            FROM BusinessSessions s
            JOIN Staff st ON s.SupervisorID = st.StaffID
            ORDER BY s.StartDate DESC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                sessions.add(extractSession(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sessions;
    }

    /** One row per completed sale in a session — for a receipts list, not a line-item feed. */
    public record SessionSaleSummary(int saleID, LocalDateTime saleDate, String staffName, double amount) {}

    public ObservableList<SessionSaleSummary> getSessionSales(int sessionID) {
        ObservableList<SessionSaleSummary> sales = FXCollections.observableArrayList();
        String query = """
            SELECT t.SaleID, MIN(t.TransactionDate) AS SaleDate, MIN(s.FullNames) AS StaffName,
                   SUM(t.SalePrice * t.Quantity) AS Amount
            FROM SessionTransactions st
            JOIN Transactions t ON st.SaleID = t.SaleID
            JOIN Staff s ON t.StaffID = s.StaffID
            WHERE st.SessionID = ?
            GROUP BY t.SaleID
            ORDER BY SaleDate DESC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sales.add(new SessionSaleSummary(
                    rs.getInt("SaleID"),
                    rs.getTimestamp("SaleDate").toLocalDateTime(),
                    rs.getString("StaffName"),
                    rs.getDouble("Amount")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sales;
    }

    public ObservableList<SessionActivity> getSessionActivities(int sessionID) {
        ObservableList<SessionActivity> activities = FXCollections.observableArrayList();

        // Get sales
        String salesQuery = """
            SELECT t.TransactionDate, t.SaleID, p.ProductName, t.Quantity, 
                   (t.SalePrice * t.Quantity) as amount, s.FullNames
            FROM SessionTransactions st
            JOIN Transactions t ON st.SaleID = t.SaleID
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff s ON t.StaffID = s.StaffID
            WHERE st.SessionID = ?
            ORDER BY t.TransactionDate DESC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(salesQuery)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String desc = String.format("Sale #%d: %s (%dx)",
                    rs.getInt("SaleID"),
                    rs.getString("ProductName"),
                    rs.getInt("Quantity"));

                activities.add(new SessionActivity(
                    "SALE",
                    desc,
                    rs.getTimestamp("TransactionDate").toLocalDateTime(),
                    null,
                    rs.getDouble("amount")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Get returns
        String returnsQuery = """
            SELECT r.ReturnDate, r.ReturnID, p.ProductName, r.ReturnQuantity, 
                   r.RefundAmount, r.Status, s.FullNames
            FROM Returns r
            JOIN Transactions t ON r.TransactionID = t.TransactionID
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff s ON r.StaffID = s.StaffID
            JOIN SessionTransactions st ON t.SaleID = st.SaleID
            WHERE st.SessionID = ?
            ORDER BY r.ReturnDate DESC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(returnsQuery)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String desc = String.format("Return #%d: %s (%dx)",
                    rs.getInt("ReturnID"),
                    rs.getString("ProductName"),
                    rs.getInt("ReturnQuantity"));

                activities.add(new SessionActivity(
                    "RETURN",
                    desc,
                    rs.getTimestamp("ReturnDate").toLocalDateTime(),
                    rs.getString("Status"),
                    rs.getDouble("RefundAmount")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Get exchanges
        String exchangesQuery = """
            SELECT e.ExchangeDate, e.ExchangeID, p1.ProductName as OrigProduct, 
                   p2.ProductName as NewProduct, e.Status, s.FullNames
            FROM Exchanges e
            JOIN Transactions t1 ON e.OriginalTransactionID = t1.TransactionID
            JOIN Product p1 ON t1.ProductID = p1.ProductID
            LEFT JOIN Transactions t2 ON e.NewTransactionID = t2.TransactionID
            LEFT JOIN Product p2 ON t2.ProductID = p2.ProductID
            JOIN Staff s ON e.StaffID = s.StaffID
            JOIN SessionTransactions st ON t1.SaleID = st.SaleID
            WHERE st.SessionID = ?
            ORDER BY e.ExchangeDate DESC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(exchangesQuery)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String newProd = rs.getString("NewProduct");
                String desc = String.format("Exchange #%d: %s → %s",
                    rs.getInt("ExchangeID"),
                    rs.getString("OrigProduct"),
                    newProd != null ? newProd : "Pending");

                activities.add(new SessionActivity(
                    "EXCHANGE",
                    desc,
                    rs.getTimestamp("ExchangeDate").toLocalDateTime(),
                    rs.getString("Status"),
                    0
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Sort by timestamp descending
        activities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        return activities;
    }

    public List<SessionActivityDetail> getSessionActivityDetails(int sessionID) {
        List<SessionActivityDetail> details = new ArrayList<>();

        // Get sales details
        String salesQuery = """
            SELECT t.TransactionDate, t.SaleID, p.ProductName, t.Quantity, 
                   (t.SalePrice * t.Quantity) as amount, s.FullNames, a.FullNames as customerFullNames, t.PaymentMethod
            FROM SessionTransactions st
            JOIN Transactions t ON st.SaleID = t.SaleID
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff s ON t.StaffID = s.StaffID
            JOIN Account a ON t.AccountID = a.AccountID
            WHERE st.SessionID = ?
            ORDER BY t.TransactionDate
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(salesQuery)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                SessionActivityDetail detail = new SessionActivityDetail();
                detail.setType("SALE");
                detail.setTimestamp(rs.getTimestamp("TransactionDate").toLocalDateTime());
                detail.setSaleID(rs.getInt("SaleID"));
                detail.setProductName(rs.getString("ProductName"));
                detail.setQuantity(rs.getInt("Quantity"));
                detail.setAmount(rs.getDouble("amount"));
                detail.setStaffName(rs.getString("FullNames"));
                detail.setCustomerName(rs.getString("customerFullNames"));
                detail.setPaymentMethod(rs.getString("PaymentMethod"));
                details.add(detail);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Get returns details
        String returnsQuery = """
            SELECT r.ReturnDate, r.ReturnID, p.ProductName, r.ReturnQuantity, 
                   r.RefundAmount, r.Status, s.FullNames, a.FullNames as customerFullNames, r.Reason
            FROM Returns r
            JOIN Transactions t ON r.TransactionID = t.TransactionID
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff s ON r.StaffID = s.StaffID
            JOIN SessionTransactions st ON t.SaleID = st.SaleID
            JOIN Account a ON t.AccountID = a.AccountID
            WHERE st.SessionID = ?
            ORDER BY r.ReturnDate
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(returnsQuery)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                SessionActivityDetail detail = new SessionActivityDetail();
                detail.setType("RETURN");
                detail.setTimestamp(rs.getTimestamp("ReturnDate").toLocalDateTime());
                detail.setReturnID(rs.getInt("ReturnID"));
                detail.setProductName(rs.getString("ProductName"));
                detail.setQuantity(rs.getInt("ReturnQuantity"));
                detail.setAmount(rs.getDouble("RefundAmount"));
                detail.setStatus(rs.getString("Status"));
                detail.setStaffName(rs.getString("FullNames"));
                detail.setCustomerName(rs.getString("customerFullNames"));
                detail.setReason(rs.getString("Reason"));
                details.add(detail);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Get exchanges details
        String exchangesQuery = """
            SELECT e.ExchangeDate, e.ExchangeID, p1.ProductName as OrigProduct, 
                   p2.ProductName as NewProduct, e.Status, s.FullNames, a.FullNames as customerFullNames, e.Reason,
                   COALESCE((t2.SalePrice * COALESCE(t2.Quantity, 1)) - (t1.SalePrice * t1.Quantity), 0) as topUp
            FROM Exchanges e
            JOIN Transactions t1 ON e.OriginalTransactionID = t1.TransactionID
            JOIN Product p1 ON t1.ProductID = p1.ProductID
            LEFT JOIN Transactions t2 ON e.NewTransactionID = t2.TransactionID
            LEFT JOIN Product p2 ON t2.ProductID = p2.ProductID
            JOIN Staff s ON e.StaffID = s.StaffID
            JOIN SessionTransactions st ON t1.SaleID = st.SaleID
            JOIN Account a ON t1.AccountID = a.AccountID
            WHERE st.SessionID = ?
            ORDER BY e.ExchangeDate
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(exchangesQuery)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                SessionActivityDetail detail = new SessionActivityDetail();
                detail.setType("EXCHANGE");
                detail.setTimestamp(rs.getTimestamp("ExchangeDate").toLocalDateTime());
                detail.setExchangeID(rs.getInt("ExchangeID"));
                detail.setProductName(rs.getString("OrigProduct"));
                detail.setNewProductName(rs.getString("NewProduct"));
                detail.setStatus(rs.getString("Status"));
                detail.setStaffName(rs.getString("FullNames"));
                detail.setCustomerName(rs.getString("customerFullNames"));
                detail.setReason(rs.getString("Reason"));
                detail.setTopUp(rs.getDouble("topUp"));
                details.add(detail);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return details;
    }

    public BusinessSession getSessionById(int sessionID) {
        String query = """
            SELECT s.*, st.FullNames 
            FROM BusinessSessions s
            JOIN Staff st ON s.SupervisorID = st.StaffID
            WHERE s.SessionID = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, sessionID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractSession(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private BusinessSession extractSession(ResultSet rs) throws SQLException {
        BusinessSession session = new BusinessSession();
        session.setSessionID(rs.getInt("SessionID"));
        session.setSupervisorID(rs.getInt("SupervisorID"));
        session.setSupervisorName(rs.getString("FullNames"));
        session.setStartDate(rs.getTimestamp("StartDate").toLocalDateTime());

        Timestamp endTime = rs.getTimestamp("EndDate");
        if (endTime != null) {
            session.setEndDate(endTime.toLocalDateTime());
        }

        session.setStatus(rs.getString("Status"));
        session.setTotalCashSales(rs.getBigDecimal("TotalCashSales"));
        session.setTotalCardSales(rs.getBigDecimal("TotalCardSales"));
        session.setTotalSales(rs.getBigDecimal("TotalSales"));
        session.setDeclarationSigned(rs.getBoolean("DeclarationSigned"));
        session.setNotes(rs.getString("Notes"));

        return session;
    }

    public boolean verifySupervisorCode(int staffID, String code) {
        String query = """
            SELECT COUNT(*) 
            FROM SupervisorCodes 
            WHERE StaffID = ? 
            AND AuthCode = ? 
            AND IsActive = TRUE
            AND (ExpiryDate IS NULL OR ExpiryDate > CURRENT_TIMESTAMP)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, staffID);
            pstmt.setString(2, code);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getOrCreateAuthKey(int staffID) {
        String checkQuery = "SELECT AuthCode FROM SupervisorCodes WHERE StaffID = ? AND IsActive = TRUE LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {

            pstmt.setInt(1, staffID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("AuthCode");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Generate new 6-digit code
        String newCode = generateAuthCode();
        createSupervisorCode(staffID, newCode);
        return newCode;
    }

    private String generateAuthCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    public boolean createSupervisorCode(int staffID, String code) {
        String query = """
            INSERT INTO SupervisorCodes (StaffID, AuthCode, IsActive)
            VALUES (?, ?, TRUE)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, staffID);
            pstmt.setString(2, code);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Called by a manager/admin to countersign a cashier-started/ended session.
     * Sets DeclarationSigned = TRUE, records the countersigner's StaffID and
     * auth code in AuthorisationCode, and appends countersign notes.
     *
     * @param sessionID       ID of the closed, unsigned session
     * @param countersignerID StaffID of the manager/admin signing off
     * @param authCode        Their authorization code (already verified by caller)
     * @param notes           Optional comments from the countersigner
     * @return true if the update succeeded
     */
    public boolean countersignSession(int sessionID, int countersignerID, String authCode, String notes) {
        String query = """
            UPDATE BusinessSessions
            SET DeclarationSigned  = TRUE,
                AuthorisationCode  = ?,
                Notes = CONCAT(COALESCE(Notes, ''), ?)
            WHERE SessionID = ?
              AND Status = 'Closed'
              AND DeclarationSigned = FALSE
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, authCode);
            pstmt.setString(2, " | [COUNTERSIGNED BY STAFF #" + countersignerID + "]"
                    + (notes != null && !notes.isBlank() ? " " + notes : ""));
            pstmt.setInt(3, sessionID);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
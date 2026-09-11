package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.models.Customer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CustomerService {

    public boolean addCustomer(Customer customer) {
        String query = "INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, customer.getStaffID());
            pstmt.setString(2, customer.getFullNames());
            pstmt.setString(3, customer.getEmailAddress());
            pstmt.setDate(4, Date.valueOf(customer.getDateOfBirth()));
            pstmt.setString(5, customer.getContactNo());
            int result = pstmt.executeUpdate();
            if (result > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) customer.setAccountID(rs.getInt(1));
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean updateCustomer(Customer customer) {
        String query = "UPDATE Account SET FullNames = ?, EmailAddress = ?, DateOfBirth = ?, ContactNo = ? WHERE AccountID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, customer.getFullNames());
            pstmt.setString(2, customer.getEmailAddress());
            pstmt.setDate(3, Date.valueOf(customer.getDateOfBirth()));
            pstmt.setString(4, customer.getContactNo());
            pstmt.setInt(5, customer.getAccountID());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean deleteCustomer(int accountID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM Account WHERE AccountID = ?")) {
            pstmt.setInt(1, accountID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Sentinel email for the single shared "walk-in" account used in Retail edition. */
    public static final String WALK_IN_EMAIL = "walk-in@pos.local";

    /**
     * Returns the shared "Walk-in Customer" account, creating it on first use.
     * Retail-edition sales are all booked against this account so no schema
     * change (Transactions.AccountID is NOT NULL) is needed.
     *
     * @param staffID any valid staff id, used only to satisfy Account.StaffID
     */
    public Customer getOrCreateWalkInAccount(int staffID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM Account WHERE EmailAddress = ?")) {
            ps.setString(1, WALK_IN_EMAIL);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return extractCustomer(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Customer walkIn = new Customer();
        walkIn.setStaffID(staffID);
        walkIn.setFullNames("Walk-in Customer");
        walkIn.setEmailAddress(WALK_IN_EMAIL);
        walkIn.setDateOfBirth(LocalDate.of(2000, 1, 1));
        walkIn.setContactNo("0000000000");
        if (addCustomer(walkIn)) return walkIn;

        // Someone inserted it concurrently — re-read.
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM Account WHERE EmailAddress = ?")) {
            ps.setString(1, WALK_IN_EMAIL);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return extractCustomer(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Customer getCustomerById(int accountID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM Account WHERE AccountID = ?")) {
            pstmt.setInt(1, accountID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return extractCustomer(rs);
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public ObservableList<Customer> getAllCustomers() {
        ObservableList<Customer> customers = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Account ORDER BY FullNames")) {
            while (rs.next()) customers.add(extractCustomer(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return customers;
    }

    public ObservableList<Customer> searchCustomers(String keyword) {
        ObservableList<Customer> customers = FXCollections.observableArrayList();
        String query = "SELECT * FROM Account WHERE FullNames LIKE ? OR EmailAddress LIKE ? OR ContactNo LIKE ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            String pattern = "%" + keyword + "%";
            pstmt.setString(1, pattern); pstmt.setString(2, pattern); pstmt.setString(3, pattern);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) customers.add(extractCustomer(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return customers;
    }

    // ── Net purchases: COUNT DISTINCT SALES, not item quantities ─────────────
    //
    // A customer who made 1 sale of 14 items and returned 10 still has 1 sale.
    // We count distinct SaleIDs from transactions that have at least 1 remaining
    // item (original qty minus approved return qty > 0).
    //
    // Previously this summed t.Quantity which gave 14, and after approveReturn
    // zeroed Transactions.Quantity it gave 0. Both were wrong.

    public int getCustomerNetPurchaseQuantity(int accountID) {
        String query = """
            SELECT COUNT(DISTINCT t.SaleID) AS net_sales
            FROM Transactions t
            WHERE t.AccountID = ?
              AND (
                t.Quantity - COALESCE((
                    SELECT SUM(r.ReturnQuantity)
                    FROM Returns r
                    WHERE r.TransactionID = t.TransactionID
                      AND r.Status = 'Approved'
                ), 0)
              ) > 0
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, accountID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("net_sales");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public String getStaffName(int staffID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT FullNames FROM Staff WHERE StaffID = ?")) {
            pstmt.setInt(1, staffID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("FullNames");
        } catch (SQLException e) { e.printStackTrace(); }
        return "Unknown Staff";
    }

    // ── Sale / Purchase models ────────────────────────────────────────────────

    public static class Sale {
        private int saleID;
        private LocalDateTime saleDate;
        private String staffName;
        private int totalQuantity;
        private double totalAmount;
        private ObservableList<Purchase> items;

        public Sale() { this.items = FXCollections.observableArrayList(); }

        public void calculateNetTotals(CustomerService service) {
            int netQty = 0;
            double netTotal = 0.0;
            for (Purchase item : items) {
                int remaining = service.getRemainingQuantity(item.getTransactionID());
                netQty   += remaining;
                netTotal += remaining * item.getSalePrice();
            }
            setTotalQuantity(netQty);
            setTotalAmount(netTotal);
        }

        public int getSaleID()                         { return saleID; }
        public void setSaleID(int saleID)              { this.saleID = saleID; }
        public LocalDateTime getSaleDate()             { return saleDate; }
        public void setSaleDate(LocalDateTime v)       { this.saleDate = v; }
        public String getStaffName()                   { return staffName; }
        public void setStaffName(String v)             { this.staffName = v; }
        public int getTotalQuantity()                  { return totalQuantity; }
        public void setTotalQuantity(int v)            { this.totalQuantity = v; }
        public double getTotalAmount()                 { return totalAmount; }
        public void setTotalAmount(double v)           { this.totalAmount = v; }
        public ObservableList<Purchase> getItems()     { return items; }
        public void setItems(ObservableList<Purchase> v) { this.items = v; }
    }

    public static class Purchase {
        private int transactionID;
        private LocalDateTime transactionDate;
        private String productName;
        private int quantity;          // original quantity sold — NEVER mutated
        private double salePrice;
        private double total;
        private String staffName;
        private String promoCode;

        public Purchase() {}

        public int getTransactionID()                    { return transactionID; }
        public void setTransactionID(int v)              { this.transactionID = v; }
        public LocalDateTime getTransactionDate()        { return transactionDate; }
        public void setTransactionDate(LocalDateTime v)  { this.transactionDate = v; }
        public String getProductName()                   { return productName; }
        public void setProductName(String v)             { this.productName = v; }
        public int getQuantity()                         { return quantity; }
        public void setQuantity(int v)                   { this.quantity = v; }
        public double getSalePrice()                     { return salePrice; }
        public void setSalePrice(double v)               { this.salePrice = v; }
        public double getTotal()                         { return total; }
        public void setTotal(double v)                   { this.total = v; }
        public String getStaffName()                     { return staffName; }
        public void setStaffName(String v)               { this.staffName = v; }
        public String getPromoCode()                     { return promoCode; }
        public void setPromoCode(String v)               { this.promoCode = v; }
    }

    // ── getRemainingQuantity ──────────────────────────────────────────────────
    //
    // IMPORTANT: This reads the ORIGINAL Quantity stored at sale time from
    // Transactions, then subtracts only APPROVED return quantities from Returns.
    // It does NOT read Transactions.Quantity after an approveReturn call, because
    // approveReturn must NOT modify Transactions.Quantity (see ExchangeReturnService).
    //
    // Formula: original_qty - sum(approved_return_qty)
    // Example: sold 14, returned 10 approved → remaining = 14 - 10 = 4  ✓

    public int getRemainingQuantity(int transactionID) {
        String query = """
            SELECT t.Quantity - COALESCE(
                (SELECT SUM(r.ReturnQuantity)
                 FROM Returns r
                 WHERE r.TransactionID = t.TransactionID
                   AND r.Status = 'Approved'),
            0) AS remaining
            FROM Transactions t
            WHERE t.TransactionID = ?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, transactionID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return Math.max(0, rs.getInt("remaining"));
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    // ── getCustomerPurchases ──────────────────────────────────────────────────
    //
    // Reads the ORIGINAL Quantity from Transactions (not the mutated value).
    // PromoCode is also selected so it shows on the receipt and UI.

    public ObservableList<Sale> getCustomerPurchases(int accountID) {
        ObservableList<Sale> sales = FXCollections.observableArrayList();
        String query = """
            SELECT t.SaleID, t.TransactionDate, s.FullNames AS StaffName,
                   t.TransactionID, p.ProductName,
                   t.Quantity, t.SalePrice, t.PromoCode
            FROM Transactions t
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff   s ON t.StaffID   = s.StaffID
            WHERE t.AccountID = ?
            ORDER BY t.SaleID DESC, t.TransactionDate DESC
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, accountID);
            ResultSet rs = pstmt.executeQuery();
            Sale currentSale = null;
            while (rs.next()) {
                int saleID = rs.getInt("SaleID");
                if (currentSale == null || currentSale.getSaleID() != saleID) {
                    currentSale = new Sale();
                    currentSale.setSaleID(saleID);
                    currentSale.setSaleDate(rs.getTimestamp("TransactionDate").toLocalDateTime());
                    currentSale.setStaffName(rs.getString("StaffName"));
                    sales.add(0, currentSale); // maintain DESC order
                }
                Purchase item = new Purchase();
                item.setTransactionID(rs.getInt("TransactionID"));
                item.setTransactionDate(rs.getTimestamp("TransactionDate").toLocalDateTime());
                item.setProductName(rs.getString("ProductName"));
                item.setQuantity(rs.getInt("Quantity"));      // original sold qty
                item.setSalePrice(rs.getDouble("SalePrice"));
                item.setTotal(item.getQuantity() * item.getSalePrice());
                item.setStaffName(rs.getString("StaffName"));
                item.setPromoCode(rs.getString("PromoCode")); // may be null — handled in UI
                currentSale.getItems().add(item);
            }
            for (Sale sale : sales) sale.calculateNetTotals(this);
        } catch (SQLException e) { e.printStackTrace(); }
        return sales;
    }

    public ObservableList<Purchase> getSaleItems(int saleID, int accountID) {
        for (Sale sale : getCustomerPurchases(accountID)) {
            if (sale.getSaleID() == saleID) return sale.getItems();
        }
        return FXCollections.emptyObservableList();
    }

    public boolean processReturn(int transactionID, int returnQuantity, int staffID,
                                  String reason, double refundAmount) {
        ExchangeReturnService exchangeService = new ExchangeReturnService();
        int returnID = exchangeService.requestReturn(
                transactionID, returnQuantity, staffID, reason,
                BigDecimal.valueOf(refundAmount));
        return returnID > 0;
    }

    public Purchase getTransactionDetails(int transactionID) {
        String query = """
            SELECT t.TransactionID, t.TransactionDate, p.ProductName,
                   t.Quantity, t.SalePrice, s.FullNames AS StaffName, t.PromoCode
            FROM Transactions t
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff   s ON t.StaffID   = s.StaffID
            WHERE t.TransactionID = ?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, transactionID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Purchase d = new Purchase();
                d.setTransactionID(rs.getInt("TransactionID"));
                d.setTransactionDate(rs.getTimestamp("TransactionDate").toLocalDateTime());
                d.setProductName(rs.getString("ProductName"));
                d.setQuantity(rs.getInt("Quantity"));
                d.setSalePrice(rs.getDouble("SalePrice"));
                d.setTotal(d.getQuantity() * d.getSalePrice());
                d.setStaffName(rs.getString("StaffName"));
                d.setPromoCode(rs.getString("PromoCode"));
                return d;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public int getCustomerPurchaseCount(int accountID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM Transactions WHERE AccountID = ?")) {
            pstmt.setInt(1, accountID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private Customer extractCustomer(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setAccountID(rs.getInt("AccountID"));
        customer.setStaffID(rs.getInt("StaffID"));
        customer.setFullNames(rs.getString("FullNames"));
        customer.setEmailAddress(rs.getString("EmailAddress"));
        customer.setDateOfBirth(rs.getDate("DateOfBirth").toLocalDate());
        customer.setContactNo(rs.getString("ContactNo"));
        customer.setTimeStamp(rs.getTimestamp("TimeStamp").toLocalDateTime());
        return customer;
    }
}
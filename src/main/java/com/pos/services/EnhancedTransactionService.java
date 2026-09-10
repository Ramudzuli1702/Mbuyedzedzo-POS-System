package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.views.SalesView.CartItem;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;

public class EnhancedTransactionService {

    private final ProductService productService;
    private final SessionService sessionService;

    public EnhancedTransactionService() {
        this.productService = new ProductService();
        this.sessionService = new SessionService();
    }

    public static class PaymentInfo {
        private String paymentMethod; // "Cash" or "Card"
        private BigDecimal amountPaid;
        private BigDecimal changeGiven;

        public PaymentInfo(String paymentMethod, BigDecimal amountPaid, BigDecimal changeGiven) {
            this.paymentMethod = paymentMethod;
            this.amountPaid = amountPaid;
            this.changeGiven = changeGiven;
        }

        public String getPaymentMethod() { return paymentMethod; }
        public BigDecimal getAmountPaid() { return amountPaid; }
        public BigDecimal getChangeGiven() { return changeGiven; }
    }

    public boolean processTransaction(int staffID, int accountID, ObservableList<CartItem> cartItems, PaymentInfo paymentInfo) {
        // Check if session is active
        SessionService.BusinessSession activeSession = sessionService.getActiveSession();
        if (activeSession == null) {
            throw new RuntimeException("No active business session. Please start a session first.");
        }

        Connection conn = null;
        int saleID = -1;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Generate unique SaleID
            saleID = getNextSaleID(conn);
            if (saleID == -1) {
                throw new SQLException("Failed to generate SaleID");
            }

            String insertQuery = """
                INSERT INTO Transactions
                (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, SalePrice, 
                 PaymentMethod, AmountPaid, ChangeGiven, TransactionDate)
                VALUES (?, ?, ?, ?, NULL, ?, NULL, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                for (CartItem item : cartItems) {
                    pstmt.setInt(1, saleID);
                    pstmt.setInt(2, staffID);
                    pstmt.setInt(3, accountID);
                    pstmt.setInt(4, item.getProductId());
                    pstmt.setInt(5, item.getQuantity());
                    pstmt.setBigDecimal(6, item.getPrice());
                    pstmt.setString(7, paymentInfo.getPaymentMethod());
                    pstmt.setBigDecimal(8, paymentInfo.getAmountPaid());
                    pstmt.setBigDecimal(9, paymentInfo.getChangeGiven());
                    pstmt.executeUpdate();

                    productService.updateStock(conn, item.getProductId(), item.getQuantity());
                }
            }

            // Link sale to active session
            sessionService.linkSaleToSession(activeSession.getSessionID(), saleID);

            conn.commit();
            
            // Update session totals
            sessionService.updateSessionTotals(activeSession.getSessionID());
            
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }

    private int getNextSaleID(Connection conn) throws SQLException {
        String selectQuery = "SELECT nextID FROM SaleSequence FOR UPDATE";
        String updateQuery = "UPDATE SaleSequence SET nextID = ?";

        int nextID;
        try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery);
             PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
            
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    nextID = rs.getInt(1) + 1;
                } else {
                    nextID = 1;
                }
            }
            
            updateStmt.setInt(1, nextID);
            updateStmt.executeUpdate();
            
            return nextID;
        }
    }
}
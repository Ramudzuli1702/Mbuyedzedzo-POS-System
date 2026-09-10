package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.views.SalesView.CartItem;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;

public class TransactionService {

    private final ProductService productService;
    private final SessionService sessionService;

    public TransactionService() {
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

    public boolean processTransaction(int staffID, int accountID, ObservableList<CartItem> cartItems,
                                       PaymentInfo paymentInfo) {
        return processTransaction(staffID, accountID, cartItems, paymentInfo, null);
    }

    /**
     * Full overload — pass the applied promo code (or null) so it is stored
     * in Transactions.PromoCode for every line item of this sale.
     */
    public boolean processTransaction(int staffID, int accountID, ObservableList<CartItem> cartItems,
                                       PaymentInfo paymentInfo, String promoCode) {
        // Check if session is active
        SessionService.BusinessSession activeSession = sessionService.getActiveSession();
        if (activeSession == null) {
            throw new RuntimeException("No active business session. Please start a session first.");
        }

        Connection conn = null;
        int saleID = -1;
        boolean committed = false;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                throw new SQLException("Failed to obtain database connection");
            }
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
                VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                for (CartItem item : cartItems) {
                    pstmt.setInt(1, saleID);
                    pstmt.setInt(2, staffID);
                    pstmt.setInt(3, accountID);
                    pstmt.setInt(4, item.getProductId());
                    pstmt.setInt(5, item.getQuantity());
                    // PromoCode — store on every line so it's visible per-item in history
                    if (promoCode != null && !promoCode.isBlank()) {
                        pstmt.setString(6, promoCode);
                    } else {
                        pstmt.setNull(6, java.sql.Types.VARCHAR);
                    }
                    pstmt.setBigDecimal(7, item.getPrice());
                    pstmt.setString(8, paymentInfo.getPaymentMethod());
                    pstmt.setBigDecimal(9, paymentInfo.getAmountPaid());
                    pstmt.setBigDecimal(10, paymentInfo.getChangeGiven());
                    pstmt.executeUpdate();

                    // Update stock using the same connection
                    productService.updateStock(conn, item.getProductId(), item.getQuantity());
                }
            }

            conn.commit();
            committed = true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.err.println("Transaction failed: " + e.getMessage());
            e.printStackTrace();
            committed = false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException setAutoCommitEx) {
                    System.err.println("Failed to reset auto-commit: " + setAutoCommitEx.getMessage());
                }
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }

        // Perform session-related operations after transaction is committed/rolled back.
        // Use a new connection for these to avoid issues with the previous connection.
        if (committed && saleID != -1) {
            try (Connection sessionConn = DatabaseConnection.getConnection()) {
                if (sessionConn != null) {
                    sessionService.linkSaleToSession(activeSession.getSessionID(), saleID);
                    sessionService.updateSessionTotals(activeSession.getSessionID());
                }
            } catch (Exception sessionEx) {
                // Log the error but don't fail the transaction since the main transaction succeeded
                System.err.println("Session update failed: " + sessionEx.getMessage());
                sessionEx.printStackTrace();
            }
        }

        return committed;
    }

    /**
     * Atomically increments the SaleSequence counter and returns the new value.
     *
     * Uses UPDATE-first (SET nextID = nextID + 1) which is a single atomic MySQL
     * write — no two concurrent transactions can ever receive the same ID.
     * The subsequent SELECT reads back the value this connection just wrote,
     * which is safe because we are inside an open transaction (autoCommit=false).
     *
     * Both statements are explicitly scoped to the pinned row (id = 1).
     * SaleSequence.id is a fixed identity column (always 1) and is the
     * table's PRIMARY KEY; nextID is a plain counter column and must never
     * be the primary key. Scoping by id = 1 also protects against any
     * stray extra rows that might exist from a previous schema version —
     * the UPDATE/SELECT pair only ever touches the single canonical row.
     *
     * If the pinned row is missing (e.g. after a manual TRUNCATE), a seed
     * row is inserted and 1 is returned so the system self-heals without
     * manual intervention.
     */
    private int getNextSaleID(Connection conn) throws SQLException {
        String updateQuery = "UPDATE SaleSequence SET nextID = nextID + 1 WHERE id = 1";
        String selectQuery = "SELECT nextID FROM SaleSequence WHERE id = 1";

        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {

            int rowsAffected = updateStmt.executeUpdate();

            if (rowsAffected == 0) {
                // Pinned row missing — seed it and return 1
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO SaleSequence (id, nextID) VALUES (1, 1)")) {
                    insertStmt.executeUpdate();
                }
                return 1;
            }

            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new SQLException("Could not generate SaleID: SaleSequence returned no value.");
    }
}
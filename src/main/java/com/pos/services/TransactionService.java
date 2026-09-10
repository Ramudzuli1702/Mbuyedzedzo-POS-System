package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.views.SalesView.CartItem;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        return processTransaction(staffID, accountID, cartItems, paymentInfo, null, BigDecimal.ZERO);
    }

    /**
     * Processes a sale.
     *
     * <p>The promo discount is distributed across the line items so that the
     * stored {@code SalePrice} is the <em>net</em> unit price actually charged.
     * Every report and session total computes revenue as
     * {@code SUM(SalePrice * Quantity)}, so pushing the discount into the line
     * prices keeps all of those figures equal to the amount the customer paid —
     * without a schema change. (Per-unit 2-decimal rounding can leave a sub-cent
     * difference from the exact discounted total on multi-quantity lines; the
     * printed receipt shows the authoritative subtotal / discount / total.)
     *
     * <p>Payment fields ({@code AmountPaid} / {@code ChangeGiven}) are recorded
     * once, on the first line of the sale, and left at 0 on the rest so a
     * per-SaleID sum is correct. {@code PaymentMethod} is stored on every line
     * because reports and the session cash/card split filter on it row-by-row.
     *
     * @param promo          the applied promo (or null) — sets PromoID + PromoCode
     * @param discountAmount total discount to distribute (or ZERO)
     */
    public boolean processTransaction(int staffID, int accountID, ObservableList<CartItem> cartItems,
                                       PaymentInfo paymentInfo,
                                       PromoService.Promo promo, BigDecimal discountAmount) {
        SessionService.BusinessSession activeSession = sessionService.getActiveSession();
        if (activeSession == null) {
            throw new RuntimeException("No active business session. Please start a session first.");
        }
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("Cannot process an empty cart.");
        }

        final int n = cartItems.size();
        final BigDecimal[] netUnitPrice = distributeDiscount(cartItems, discountAmount);
        final Integer promoID   = promo != null ? promo.getPromoID() : null;
        final String  promoCode = promo != null ? promo.getPromoCode() : null;

        Connection conn = null;
        int saleID = -1;
        boolean committed = false;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                throw new SQLException("Failed to obtain database connection");
            }
            conn.setAutoCommit(false);

            saleID = getNextSaleID(conn);
            if (saleID == -1) {
                throw new SQLException("Failed to generate SaleID");
            }

            String insertQuery = """
                INSERT INTO Transactions
                (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, SalePrice,
                 PaymentMethod, AmountPaid, ChangeGiven, TransactionDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                for (int i = 0; i < n; i++) {
                    CartItem item = cartItems.get(i);
                    boolean firstLine = (i == 0);

                    pstmt.setInt(1, saleID);
                    pstmt.setInt(2, staffID);
                    pstmt.setInt(3, accountID);
                    pstmt.setInt(4, item.getProductId());
                    if (promoID != null) pstmt.setInt(5, promoID);
                    else                 pstmt.setNull(5, Types.INTEGER);
                    pstmt.setInt(6, item.getQuantity());
                    if (promoCode != null && !promoCode.isBlank()) pstmt.setString(7, promoCode);
                    else                                           pstmt.setNull(7, Types.VARCHAR);
                    pstmt.setBigDecimal(8, netUnitPrice[i]);
                    pstmt.setString(9, paymentInfo.getPaymentMethod());
                    pstmt.setBigDecimal(10, firstLine ? paymentInfo.getAmountPaid()  : BigDecimal.ZERO);
                    pstmt.setBigDecimal(11, firstLine ? paymentInfo.getChangeGiven() : BigDecimal.ZERO);
                    pstmt.executeUpdate();

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

        // Session bookkeeping runs on a fresh connection after the sale commits.
        if (committed && saleID != -1) {
            try (Connection sessionConn = DatabaseConnection.getConnection()) {
                if (sessionConn != null) {
                    sessionService.linkSaleToSession(activeSession.getSessionID(), saleID);
                    sessionService.updateSessionTotals(activeSession.getSessionID());
                }
            } catch (Exception sessionEx) {
                System.err.println("Session update failed: " + sessionEx.getMessage());
                sessionEx.printStackTrace();
            }
        }

        return committed;
    }

    /**
     * Splits {@code discountAmount} across the cart in proportion to each line's
     * subtotal and returns the resulting net <em>unit</em> price for each line
     * (index-aligned with {@code cartItems}). The discount is clamped to
     * [0, cartTotal]; the final line absorbs any rounding remainder so the line
     * totals sum back to (cartTotal - discount).
     */
    static BigDecimal[] distributeDiscount(ObservableList<CartItem> cartItems, BigDecimal discountAmount) {
        final int n = cartItems.size();
        final BigDecimal[] netUnit = new BigDecimal[n];

        BigDecimal cartTotal = BigDecimal.ZERO;
        for (CartItem it : cartItems) cartTotal = cartTotal.add(it.getSubtotal());

        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (discount.signum() < 0) discount = BigDecimal.ZERO;
        if (discount.compareTo(cartTotal) > 0) discount = cartTotal;

        if (discount.signum() == 0 || cartTotal.signum() == 0) {
            for (int i = 0; i < n; i++) netUnit[i] = cartItems.get(i).getPrice();
            return netUnit;
        }

        BigDecimal target    = cartTotal.subtract(discount); // amount actually charged
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            CartItem it = cartItems.get(i);
            BigDecimal qty = BigDecimal.valueOf(it.getQuantity());
            BigDecimal lineNet = (i < n - 1)
                    ? it.getSubtotal().multiply(target).divide(cartTotal, 2, RoundingMode.HALF_UP)
                    : target.subtract(allocated);
            allocated = allocated.add(lineNet);
            netUnit[i] = lineNet.divide(qty, 2, RoundingMode.HALF_UP);
        }
        return netUnit;
    }

    /**
     * Atomically increments the SaleSequence counter and returns the new value.
     * See the schema notes in DatabaseSetup for why id=1 scoping matters.
     */
    private int getNextSaleID(Connection conn) throws SQLException {
        String updateQuery = "UPDATE SaleSequence SET nextID = nextID + 1 WHERE id = 1";
        String selectQuery = "SELECT nextID FROM SaleSequence WHERE id = 1";

        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {

            int rowsAffected = updateStmt.executeUpdate();

            if (rowsAffected == 0) {
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

package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.views.SalesView.CartItem;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

/**
 * Atomic checkout persistence. The paid ledger allocation, stock, coupon/offer
 * evidence and session movement share one connection and commit boundary.
 * Receipt rendering/delivery runs after this service returns and is not part
 * of that transaction; a delivery error must not trigger another checkout.
 */
public class TransactionService {

    private final ProductService productService;
    private final SessionService sessionService;
    private volatile String lastFailure;
    public String getLastFailure(){return lastFailure;}

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
     * exactly. Multi-quantity lines may be split into two groups whose unit
     * prices differ by one cent, preserving every cent of the charged total.
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
        return processTransaction(staffID, staffID, accountID, cartItems, paymentInfo, promo, discountAmount);
    }

    public boolean processTransaction(int operatorID, int staffID, int accountID, ObservableList<CartItem> cartItems,
                                       PaymentInfo paymentInfo, PromoService.Promo promo, BigDecimal discountAmount) {
        return processTransaction(operatorID,staffID,accountID,cartItems,paymentInfo,promo,discountAmount,null,promo==null?"":promo.getPromoCode());
    }
    /**
     * Revalidates a displayed quote before saving a promoted sale.
     *
     * @param operatorID authenticated desktop operator, distinct from an attributed cashier
     * @param staffID cashier to whom the sale is attributed
     * @param accountID active customer or edition-specific walk-in account
     * @param cart frozen basket snapshot with regular catalog prices
     * @param payment tender/change matching the displayed net total
     * @param code optional entered coupon/retail-offer code
     * @param expected displayed quote; compared with a freshly calculated locked quote
     * @return true only after the sale commits; false on transactional failure
     * @throws IllegalArgumentException for missing quote or invalid payment/basket values
     */
    public boolean processPromotedTransaction(int operatorID,int staffID,int accountID,ObservableList<CartItem> cart,
                                             PaymentInfo payment,String code,PromotionEngine.Quote expected) {
        if(expected==null)throw new IllegalArgumentException("Refresh promotion prices before taking payment.");
        return processTransaction(operatorID,staffID,accountID,cart,payment,null,expected.discount(),expected,code);
    }
    private boolean processTransaction(int operatorID,int staffID,int accountID,ObservableList<CartItem> cartItems,
                                       PaymentInfo paymentInfo,PromoService.Promo promo,BigDecimal discountAmount,
                                       PromotionEngine.Quote expected,String code) {
        lastFailure=null;
        SessionService.BusinessSession activeSession = sessionService.getActiveSession();
        if (activeSession == null) {
            throw new RuntimeException("No active business session. Please start a session first.");
        }
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("Cannot process an empty cart.");
        }

        final List<PricedLine> lines = allocateLines(cartItems, discountAmount);
        BigDecimal subtotal = cartItems.stream().map(CartItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = lines.stream().map(l -> l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paymentInfo == null || !Set.of("Cash", "Card").contains(paymentInfo.getPaymentMethod()))
            throw new IllegalArgumentException("Choose Cash or Card.");
        money(paymentInfo.getAmountPaid()); money(paymentInfo.getChangeGiven());
        if (paymentInfo.getAmountPaid().subtract(paymentInfo.getChangeGiven()).compareTo(total) != 0
                || "Card".equals(paymentInfo.getPaymentMethod()) && paymentInfo.getChangeGiven().signum() != 0)
            throw new IllegalArgumentException("Payment and change must match the sale total.");
        Integer promoID=null;
        String promoCode=code==null?"":code.trim().toUpperCase(Locale.ROOT);

        Connection conn = null;
        int saleID = -1;
        boolean committed = false;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                throw new SQLException("Failed to obtain database connection");
            }
            conn.setAutoCommit(false);

            requireActiveStaff(conn, operatorID); requireActiveStaff(conn, staffID);
            try(PreparedStatement p=conn.prepareStatement("SELECT AccountID FROM Account WHERE AccountID=? AND IsArchived=FALSE FOR UPDATE")) {
                p.setInt(1,accountID);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Customer is no longer active.");}
            }
            try (PreparedStatement p = conn.prepareStatement("SELECT SessionID FROM BusinessSessions WHERE SessionID=? AND Status='Active' FOR UPDATE")) {
                p.setInt(1, activeSession.getSessionID());
                try (ResultSet r=p.executeQuery()) { if(!r.next()) throw new SQLException("The business session has closed. Start a session before checkout."); }
            }
            PromotionService promotions=new PromotionService();
            PromotionEngine.Quote charged=promotions.quote(conn,cartItems,promoCode,true);
            if(expected!=null && !PromotionService.signature(expected).equals(PromotionService.signature(charged))
                    || charged.discount().compareTo(discountAmount==null?BigDecimal.ZERO:discountAmount)!=0)
                throw new SQLException("Promotions changed or expired. Refresh the basket and confirm payment again.");
            for(var offer:charged.offers())if(offer.id().startsWith("COUPON_")) {
                promoID=Integer.parseInt(offer.id().substring(7));new PromoService().reserve(conn,promoID,subtotal,charged.discount());
            }
            Map<Integer,BigDecimal> costs=new HashMap<>();
            for (CartItem item : cartItems.stream().sorted(Comparator.comparingInt(CartItem::getProductId)).toList()) {
                try(PreparedStatement p=conn.prepareStatement("SELECT Price,CostPrice FROM Product WHERE ProductID=? AND IsArchived=FALSE FOR UPDATE")) {
                    p.setInt(1,item.getProductId());
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next() || r.getBigDecimal(1).compareTo(item.getPrice())!=0) throw new SQLException("A product or price changed. Refresh the cart.");
                        costs.put(item.getProductId(),r.getBigDecimal(2));
                    }
                }
                productService.updateStock(conn,item.getProductId(),item.getQuantity());
            }

            saleID = getNextSaleID(conn);
            if (saleID == -1) {
                throw new SQLException("Failed to generate SaleID");
            }

            String insertQuery = """
                INSERT INTO Transactions
                (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, SalePrice,
                 PaymentMethod, AmountPaid, ChangeGiven, OperatorID, UnitCostAtSale,RegularUnitPrice,PromotionSnapshot, TransactionDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                for (int i = 0; i < charged.lines().size(); i++) {
                    PromotionEngine.Charge item = charged.lines().get(i);
                    boolean firstLine = (i == 0);

                    pstmt.setInt(1, saleID);
                    pstmt.setInt(2, staffID);
                    pstmt.setInt(3, accountID);
                    pstmt.setInt(4, item.productId());
                    if (promoID != null) pstmt.setInt(5, promoID);
                    else                 pstmt.setNull(5, Types.INTEGER);
                    pstmt.setInt(6, item.quantity());
                    if (promoID != null && !promoCode.isBlank()) pstmt.setString(7, promoCode);
                    else                                           pstmt.setNull(7, Types.VARCHAR);
                    pstmt.setBigDecimal(8, item.price());
                    pstmt.setString(9, paymentInfo.getPaymentMethod());
                    pstmt.setBigDecimal(10, firstLine ? paymentInfo.getAmountPaid()  : BigDecimal.ZERO);
                    pstmt.setBigDecimal(11, firstLine ? paymentInfo.getChangeGiven() : BigDecimal.ZERO);
                    pstmt.setInt(12, operatorID);
                    pstmt.setBigDecimal(13, costs.get(item.productId()));
                    pstmt.setBigDecimal(14,item.regularPrice());
                    if(item.offerId().isBlank())pstmt.setNull(15,Types.VARCHAR);else pstmt.setString(15,PromotionService.JSON.toJson(item));
                    pstmt.executeUpdate();
                }
            }
            promotions.record(conn,saleID,charged);

            try (PreparedStatement p=conn.prepareStatement("INSERT INTO SessionTransactions (SessionID,SaleID) VALUES (?,?)")) {
                p.setInt(1,activeSession.getSessionID());p.setInt(2,saleID);p.executeUpdate();
            }
            try (PreparedStatement p=conn.prepareStatement("UPDATE BusinessSessions SET TotalSales=COALESCE(TotalSales,0)+?, TotalCashSales=COALESCE(TotalCashSales,0)+?, TotalCardSales=COALESCE(TotalCardSales,0)+? WHERE SessionID=? AND Status='Active'")) {
                p.setBigDecimal(1,total);p.setBigDecimal(2,"Cash".equals(paymentInfo.getPaymentMethod())?total:BigDecimal.ZERO);
                p.setBigDecimal(3,"Card".equals(paymentInfo.getPaymentMethod())?total:BigDecimal.ZERO);p.setInt(4,activeSession.getSessionID());
                if(p.executeUpdate()!=1)throw new SQLException("The business session changed before checkout.");
            }
            conn.commit();
            committed = true;

        } catch (SQLException | RuntimeException e) {
            lastFailure=e.getMessage();
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

        return committed;
    }

    /**
     * One stored line uses a whole quantity and a price expressed in cents.
     */
    record PricedLine(int productId, int quantity, BigDecimal price) {}

    /** Split each line into at most two cent-priced groups so revenue reconciles exactly. */
    static List<PricedLine> allocateLines(ObservableList<CartItem> cart, BigDecimal discount) {
        if(cart==null || cart.isEmpty()) throw new IllegalArgumentException("The cart is empty.");
        BigDecimal subtotal=BigDecimal.ZERO;
        for(CartItem item:cart) {
            if(item==null || item.getProductId()<=0 || item.getQuantity()<=0) throw new IllegalArgumentException("Sale quantities must be positive whole numbers.");
            money(item.getPrice());subtotal=subtotal.add(item.getSubtotal());
        }
        if(discount==null)discount=BigDecimal.ZERO;
        money(discount);
        if(discount.compareTo(subtotal)>0)throw new IllegalArgumentException("Discount exceeds the cart total.");
        BigDecimal target=subtotal.subtract(discount),allocated=BigDecimal.ZERO,cumulative=BigDecimal.ZERO;
        List<PricedLine> result=new ArrayList<>();
        for(CartItem item:cart) {
            cumulative=cumulative.add(item.getSubtotal());
            BigDecimal cumulativeNet=subtotal.signum()==0?BigDecimal.ZERO:cumulative.multiply(target).divide(subtotal,2,RoundingMode.HALF_UP);
            BigDecimal line=cumulativeNet.subtract(allocated);allocated=cumulativeNet;
            BigDecimal unit=line.divide(BigDecimal.valueOf(item.getQuantity()),2,RoundingMode.DOWN);
            int extra=line.subtract(unit.multiply(BigDecimal.valueOf(item.getQuantity()))).movePointRight(2).intValueExact();
            if(item.getQuantity()>extra)result.add(new PricedLine(item.getProductId(),item.getQuantity()-extra,unit));
            if(extra>0)result.add(new PricedLine(item.getProductId(),extra,unit.add(new BigDecimal("0.01"))));
        }
        return List.copyOf(result);
    }

    static void money(BigDecimal value) {
        if(value==null || value.signum()<0 || value.stripTrailingZeros().scale()>2 || value.compareTo(new BigDecimal("99999999.99"))>0)
            throw new IllegalArgumentException("Amounts must be non-negative and have at most two decimal places.");
    }

    static void requireActiveStaff(Connection c,int id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT StaffID FROM Staff WHERE StaffID=? AND Status='Active' FOR UPDATE")) {
            p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Sign in with an active staff account.");}
        }
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

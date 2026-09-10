package com.pos.services;

import com.pos.database.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * PromoService — manages promotional codes.
 *
 * DISCOUNT TYPES:
 *   PERCENT  — e.g. 20% off (DiscountValue = 20.00)
 *   FIXED    — e.g. R50 off  (DiscountValue = 50.00)
 *
 * VALIDATION RULES (applied at POS checkout):
 *   - Code must exist and IsActive = TRUE
 *   - Current time must be between ValidFrom and ValidTill
 *   - If UsageLimit is set, UsageCount must be below it
 *   - If MinimumPurchase is set, cart total must meet it
 */
public class PromoService {

    // ── Model ─────────────────────────────────────────────────────────────────

    public static class Promo {
        private int promoID;
        private String promoCode;
        private String promoName;
        private String discountType;   // "PERCENT" or "FIXED"
        private BigDecimal discountValue;
        private BigDecimal minimumPurchase;
        private LocalDateTime validFrom;
        private LocalDateTime validTill;
        private boolean isActive;
        private Integer usageLimit;
        private int usageCount;

        public int getPromoID()                   { return promoID; }
        public void setPromoID(int v)             { this.promoID = v; }
        public String getPromoCode()              { return promoCode; }
        public void setPromoCode(String v)        { this.promoCode = v; }
        public String getPromoName()              { return promoName; }
        public void setPromoName(String v)        { this.promoName = v; }
        public String getDiscountType()           { return discountType; }
        public void setDiscountType(String v)     { this.discountType = v; }
        public BigDecimal getDiscountValue()      { return discountValue; }
        public void setDiscountValue(BigDecimal v){ this.discountValue = v; }
        public BigDecimal getMinimumPurchase()    { return minimumPurchase; }
        public void setMinimumPurchase(BigDecimal v){ this.minimumPurchase = v; }
        public LocalDateTime getValidFrom()       { return validFrom; }
        public void setValidFrom(LocalDateTime v) { this.validFrom = v; }
        public LocalDateTime getValidTill()       { return validTill; }
        public void setValidTill(LocalDateTime v) { this.validTill = v; }
        public boolean isActive()                 { return isActive; }
        public void setActive(boolean v)          { this.isActive = v; }
        public Integer getUsageLimit()            { return usageLimit; }
        public void setUsageLimit(Integer v)      { this.usageLimit = v; }
        public int getUsageCount()                { return usageCount; }
        public void setUsageCount(int v)          { this.usageCount = v; }

        public String getStatusDisplay() {
            if (!isActive) return "Inactive";
            LocalDateTime now = LocalDateTime.now();
            if (validFrom != null && now.isBefore(validFrom)) return "Scheduled";
            if (validTill != null && now.isAfter(validTill))  return "Expired";
            if (usageLimit != null && usageCount >= usageLimit) return "Used up";
            return "Active";
        }

        public String getDiscountDisplay() {
            if ("PERCENT".equals(discountType)) {
                return discountValue.toPlainString() + "% off";
            }
            return "R" + String.format("%.2f", discountValue) + " off";
        }
    }

    public record PromoValidationResult(
        boolean valid,
        String errorMessage,
        Promo promo,
        BigDecimal discountAmount
    ) {}

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public ObservableList<Promo> getAllPromos() {
        ObservableList<Promo> list = FXCollections.observableArrayList();
        String sql = "SELECT * FROM Promo ORDER BY ValidTill DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(extract(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public ObservableList<Promo> getActivePromos() {
        ObservableList<Promo> list = FXCollections.observableArrayList();
        String sql = """
            SELECT * FROM Promo
            WHERE IsActive = TRUE
              AND (ValidFrom IS NULL OR ValidFrom <= NOW())
              AND (ValidTill IS NULL OR ValidTill >= NOW())
              AND (UsageLimit IS NULL OR UsageCount < UsageLimit)
            ORDER BY ValidTill ASC
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(extract(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean addPromo(Promo promo) {
        String sql = """
            INSERT INTO Promo
            (PromoCode, PromoName, DiscountType, DiscountValue, MinimumPurchase,
             ValidFrom, ValidTill, IsActive, UsageLimit, UsageCount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, promo.getPromoCode().toUpperCase().trim());
            ps.setString(2, promo.getPromoName());
            ps.setString(3, promo.getDiscountType());
            ps.setBigDecimal(4, promo.getDiscountValue());
            ps.setBigDecimal(5, promo.getMinimumPurchase() != null ? promo.getMinimumPurchase() : BigDecimal.ZERO);
            ps.setObject(6, promo.getValidFrom() != null ? Timestamp.valueOf(promo.getValidFrom()) : null);
            ps.setObject(7, promo.getValidTill() != null ? Timestamp.valueOf(promo.getValidTill()) : null);
            ps.setBoolean(8, promo.isActive());
            ps.setObject(9, promo.getUsageLimit());
            if (ps.executeUpdate() > 0) {
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) promo.setPromoID(keys.getInt(1));
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean updatePromo(Promo promo) {
        String sql = """
            UPDATE Promo SET
                PromoName = ?, DiscountType = ?, DiscountValue = ?,
                MinimumPurchase = ?, ValidFrom = ?, ValidTill = ?,
                IsActive = ?, UsageLimit = ?
            WHERE PromoID = ?
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promo.getPromoName());
            ps.setString(2, promo.getDiscountType());
            ps.setBigDecimal(3, promo.getDiscountValue());
            ps.setBigDecimal(4, promo.getMinimumPurchase() != null ? promo.getMinimumPurchase() : BigDecimal.ZERO);
            ps.setObject(5, promo.getValidFrom() != null ? Timestamp.valueOf(promo.getValidFrom()) : null);
            ps.setObject(6, promo.getValidTill() != null ? Timestamp.valueOf(promo.getValidTill()) : null);
            ps.setBoolean(7, promo.isActive());
            ps.setObject(8, promo.getUsageLimit());
            ps.setInt(9, promo.getPromoID());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean deletePromo(int promoID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM Promo WHERE PromoID = ?")) {
            ps.setInt(1, promoID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean toggleActive(int promoID, boolean active) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE Promo SET IsActive = ? WHERE PromoID = ?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, promoID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ── Validation (used at checkout) ──────────────────────────────────────────

    /**
     * Validates a promo code against a cart total.
     * Returns the discount amount if valid, or an error message if not.
     *
     * Call this when the cashier enters a code at checkout.
     * If valid, apply the returned discountAmount to the cart total.
     * Call incrementUsage() after the transaction is committed.
     */
    public PromoValidationResult validate(String code, BigDecimal cartTotal) {
        String sql = "SELECT * FROM Promo WHERE PromoCode = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.toUpperCase().trim());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return new PromoValidationResult(false, "Promo code not found.", null, BigDecimal.ZERO);
            }

            Promo promo = extract(rs);

            if (!promo.isActive()) {
                return new PromoValidationResult(false, "This promo is not active.", promo, BigDecimal.ZERO);
            }

            LocalDateTime now = LocalDateTime.now();
            if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
                return new PromoValidationResult(false,
                    "This promo is not valid yet. Starts: " + promo.getValidFrom().toLocalDate(),
                    promo, BigDecimal.ZERO);
            }
            if (promo.getValidTill() != null && now.isAfter(promo.getValidTill())) {
                return new PromoValidationResult(false, "This promo has expired.", promo, BigDecimal.ZERO);
            }
            if (promo.getUsageLimit() != null && promo.getUsageCount() >= promo.getUsageLimit()) {
                return new PromoValidationResult(false, "This promo has reached its usage limit.", promo, BigDecimal.ZERO);
            }
            if (promo.getMinimumPurchase() != null &&
                promo.getMinimumPurchase().compareTo(BigDecimal.ZERO) > 0 &&
                cartTotal.compareTo(promo.getMinimumPurchase()) < 0) {
                return new PromoValidationResult(false,
                    "Minimum purchase of R" + String.format("%.2f", promo.getMinimumPurchase()) + " required.",
                    promo, BigDecimal.ZERO);
            }

            BigDecimal discount;
            if ("PERCENT".equals(promo.getDiscountType())) {
                discount = cartTotal.multiply(promo.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                discount = promo.getDiscountValue().min(cartTotal); // never discount more than total
            }

            return new PromoValidationResult(true, null, promo, discount);

        } catch (SQLException e) {
            e.printStackTrace();
            return new PromoValidationResult(false, "Database error validating promo.", null, BigDecimal.ZERO);
        }
    }

    /**
     * Increments usage count after a successful transaction.
     * Call this AFTER the transaction is committed — never before.
     */
    public void incrementUsage(int promoID) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE Promo SET UsageCount = UsageCount + 1 WHERE PromoID = ?")) {
            ps.setInt(1, promoID);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── Extract ───────────────────────────────────────────────────────────────

    private Promo extract(ResultSet rs) throws SQLException {
        Promo p = new Promo();
        p.setPromoID(rs.getInt("PromoID"));
        p.setPromoCode(rs.getString("PromoCode"));
        p.setPromoName(rs.getString("PromoName"));
        p.setDiscountType(rs.getString("DiscountType"));
        p.setDiscountValue(rs.getBigDecimal("DiscountValue"));
        p.setMinimumPurchase(rs.getBigDecimal("MinimumPurchase"));
        Timestamp from = rs.getTimestamp("ValidFrom");
        Timestamp till = rs.getTimestamp("ValidTill");
        if (from != null) p.setValidFrom(from.toLocalDateTime());
        if (till != null) p.setValidTill(till.toLocalDateTime());
        p.setActive(rs.getBoolean("IsActive"));
        int limit = rs.getInt("UsageLimit");
        if (!rs.wasNull()) p.setUsageLimit(limit);
        p.setUsageCount(rs.getInt("UsageCount"));
        return p;
    }
}

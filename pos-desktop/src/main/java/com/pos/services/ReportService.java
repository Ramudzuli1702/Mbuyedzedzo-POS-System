package com.pos.services;

import com.pos.database.DatabaseConnection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ReportService — all queries use the correct schema columns:
 *   - Revenue  = SUM(t.SalePrice * t.Quantity)         [SalePrice = actual selling price]
 *   - Cost     = SUM(p.Price     * t.Quantity)         [Price     = cost/purchase price]
 *   - Profit   = SUM((t.SalePrice - p.Price) * t.Quantity)
 *   - Returns  filtered to Status = 'Approved' only
 *   - Payment breakdown uses t.PaymentMethod (Cash / Card)
 */
public class ReportService {

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 1 — REVENUE & PROFIT
    // ─────────────────────────────────────────────────────────────────────

    /** Gross revenue for the period (sum of SalePrice × Quantity). */
    public BigDecimal getTotalRevenue(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COALESCE(SUM(t.SalePrice * t.Quantity), 0) AS total
            FROM Transactions t
            WHERE t.TransactionDate BETWEEN ? AND ?
            """;
        return queryBigDecimal(sql, tsStart(start), tsEnd(end));
    }

    /** Total cost of goods sold for the period (cost price × quantity). */
    public BigDecimal getTotalCOGS(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COALESCE(SUM(p.Price * t.Quantity), 0) AS total
            FROM Transactions t
            JOIN Product p ON t.ProductID = p.ProductID
            WHERE t.TransactionDate BETWEEN ? AND ?
            """;
        return queryBigDecimal(sql, tsStart(start), tsEnd(end));
    }

    /** Gross profit = revenue − COGS. */
    public BigDecimal getGrossProfit(LocalDate start, LocalDate end) {
        return getTotalRevenue(start, end).subtract(getTotalCOGS(start, end));
    }

    /** Gross profit margin as a percentage (0–100). */
    public double getGrossProfitMargin(LocalDate start, LocalDate end) {
        BigDecimal revenue = getTotalRevenue(start, end);
        if (revenue.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return getGrossProfit(start, end)
                .divide(revenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /** Net revenue after deducting approved refunds. */
    public BigDecimal getNetRevenue(LocalDate start, LocalDate end) {
        return getTotalRevenue(start, end).subtract(getTotalApprovedRefunds(start, end));
    }

    /** Total value of approved refunds in the period. */
    public BigDecimal getTotalApprovedRefunds(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COALESCE(SUM(r.RefundAmount), 0) AS total
            FROM Returns r
            WHERE r.ReturnDate BETWEEN ? AND ?
              AND r.Status = 'Approved'
            """;
        return queryBigDecimal(sql, tsStart(start), tsEnd(end));
    }

    /** Daily revenue breakdown ordered by date. */
    public Map<LocalDate, BigDecimal> getDailyRevenue(LocalDate start, LocalDate end) {
        String sql = """
            SELECT DATE(t.TransactionDate) AS day,
                   COALESCE(SUM(t.SalePrice * t.Quantity), 0) AS total
            FROM Transactions t
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY day
            ORDER BY day
            """;
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                result.put(rs.getDate("day").toLocalDate(), rs.getBigDecimal("total"));
        } catch (SQLException e) { e.printStackTrace(); }
        // fill gaps
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1))
            result.putIfAbsent(d, BigDecimal.ZERO);
        return new TreeMap<>(result);
    }

    /** Revenue split by payment method (Cash / Card). */
    public Map<String, BigDecimal> getRevenueByPaymentMethod(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COALESCE(t.PaymentMethod, 'Unknown') AS method,
                   COALESCE(SUM(t.SalePrice * t.Quantity), 0) AS total
            FROM Transactions t
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY method
            ORDER BY total DESC
            """;
        return queryStringBigDecimalMap(sql, tsStart(start), tsEnd(end));
    }

    /** Monthly revenue for a given calendar year. */
    public Map<YearMonth, BigDecimal> getMonthlyRevenue(int year) {
        String sql = """
            SELECT YEAR(t.TransactionDate) AS yr, MONTH(t.TransactionDate) AS mon,
                   COALESCE(SUM(t.SalePrice * t.Quantity), 0) AS total
            FROM Transactions t
            WHERE YEAR(t.TransactionDate) = ?
            GROUP BY yr, mon
            ORDER BY mon
            """;
        Map<YearMonth, BigDecimal> result = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                result.put(YearMonth.of(rs.getInt("yr"), rs.getInt("mon")),
                        rs.getBigDecimal("total"));
        } catch (SQLException e) { e.printStackTrace(); }
        for (int m = 1; m <= 12; m++)
            result.putIfAbsent(YearMonth.of(year, m), BigDecimal.ZERO);
        return result;
    }

    /** Revenue split by hour of the day (0–23). */
    public Map<Integer, BigDecimal> getHourlyRevenue(LocalDate start, LocalDate end) {
        String sql = """
            SELECT HOUR(t.TransactionDate) AS hr,
                   COALESCE(SUM(t.SalePrice * t.Quantity), 0) AS total
            FROM Transactions t
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY hr
            ORDER BY hr
            """;
        Map<Integer, BigDecimal> result = new TreeMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                result.put(rs.getInt("hr"), rs.getBigDecimal("total"));
        } catch (SQLException e) { e.printStackTrace(); }
        for (int i = 0; i < 24; i++) result.putIfAbsent(i, BigDecimal.ZERO);
        return result;
    }

    /** Revenue split by day of week. */
    public Map<String, BigDecimal> getRevenueByDayOfWeek(LocalDate start, LocalDate end) {
        String sql = """
            SELECT DAYNAME(t.TransactionDate) AS day_name,
                   COALESCE(SUM(t.SalePrice * t.Quantity), 0) AS total
            FROM Transactions t
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY day_name, DAYOFWEEK(t.TransactionDate)
            ORDER BY DAYOFWEEK(t.TransactionDate)
            """;
        return queryStringBigDecimalMap(sql, tsStart(start), tsEnd(end));
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 2 — TRANSACTIONS
    // ─────────────────────────────────────────────────────────────────────

    /** Number of unique sales (SaleID groups) in the period. */
    public int getTotalSales(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COUNT(DISTINCT SaleID) AS cnt
            FROM Transactions
            WHERE TransactionDate BETWEEN ? AND ? AND SaleID > 0
            """;
        return queryInt(sql, tsStart(start), tsEnd(end));
    }

    /** Average value per unique sale. */
    public BigDecimal getAverageSaleValue(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COALESCE(AVG(sale_total), 0) AS avg_val
            FROM (
                SELECT SaleID, SUM(SalePrice * Quantity) AS sale_total
                FROM Transactions
                WHERE TransactionDate BETWEEN ? AND ? AND SaleID > 0
                GROUP BY SaleID
            ) s
            """;
        return queryBigDecimal(sql, tsStart(start), tsEnd(end));
    }

    /** Distribution of sale totals into value buckets. */
    public Map<String, Integer> getSaleValueDistribution(LocalDate start, LocalDate end) {
        String sql = """
            SELECT SUM(SalePrice * Quantity) AS sale_total
            FROM Transactions
            WHERE TransactionDate BETWEEN ? AND ? AND SaleID > 0
            GROUP BY SaleID
            """;
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("R 0–50",    0);
        dist.put("R 51–150",  0);
        dist.put("R 151–300", 0);
        dist.put("R 301–500", 0);
        dist.put("R 500+",    0);
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double v = rs.getBigDecimal("sale_total").doubleValue();
                if      (v <=  50) dist.merge("R 0–50",    1, Integer::sum);
                else if (v <= 150) dist.merge("R 51–150",  1, Integer::sum);
                else if (v <= 300) dist.merge("R 151–300", 1, Integer::sum);
                else if (v <= 500) dist.merge("R 301–500", 1, Integer::sum);
                else               dist.merge("R 500+",    1, Integer::sum);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return dist;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 3 — PRODUCTS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Top products by units sold, revenue, and profit.
     * Returns list of maps with keys: name, category, unitsSold, revenue, cost, profit, margin.
     */
    public List<Map<String, Object>> getTopProductsDetailed(LocalDate start, LocalDate end, int limit) {
        String sql = """
            SELECT p.ProductName,
                   c.CategoryName,
                   SUM(t.Quantity)                                     AS units_sold,
                   SUM(t.SalePrice * t.Quantity)                       AS revenue,
                   SUM(p.Price     * t.Quantity)                       AS cost,
                   SUM((t.SalePrice - p.Price) * t.Quantity)           AS profit,
                   CASE WHEN SUM(t.SalePrice * t.Quantity) = 0 THEN 0
                        ELSE ROUND(SUM((t.SalePrice - p.Price) * t.Quantity)
                                 / SUM(t.SalePrice * t.Quantity) * 100, 1)
                   END AS margin_pct
            FROM Transactions t
            JOIN Product  p ON t.ProductID  = p.ProductID
            JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY p.ProductID, p.ProductName, c.CategoryName
            ORDER BY revenue DESC
            LIMIT ?
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",      rs.getString("ProductName"));
                row.put("category",  rs.getString("CategoryName"));
                row.put("unitsSold", rs.getInt("units_sold"));
                row.put("revenue",   rs.getBigDecimal("revenue"));
                row.put("cost",      rs.getBigDecimal("cost"));
                row.put("profit",    rs.getBigDecimal("profit"));
                row.put("margin",    rs.getDouble("margin_pct"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Products with high stock (>20 units) but very few sales — slow movers. */
    public List<Map<String, Object>> getSlowMovers(LocalDate start, LocalDate end) {
        String sql = """
            SELECT p.ProductName,
                   c.CategoryName,
                   p.Quantity                              AS stock,
                   COALESCE(s.units_sold, 0)              AS units_sold,
                   COALESCE(s.revenue, 0)                 AS revenue,
                   p.Price                                AS cost_price
            FROM Product p
            JOIN Category c ON p.CategoryID = c.CategoryID
            LEFT JOIN (
                SELECT ProductID,
                       SUM(Quantity)                     AS units_sold,
                       SUM(SalePrice * Quantity)         AS revenue
                FROM Transactions
                WHERE TransactionDate BETWEEN ? AND ?
                GROUP BY ProductID
            ) s ON p.ProductID = s.ProductID
            WHERE p.Quantity > 20
              AND COALESCE(s.units_sold, 0) < 5
            ORDER BY p.Quantity DESC
            LIMIT 15
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",      rs.getString("ProductName"));
                row.put("category",  rs.getString("CategoryName"));
                row.put("stock",     rs.getInt("stock"));
                row.put("unitsSold", rs.getInt("units_sold"));
                row.put("revenue",   rs.getBigDecimal("revenue"));
                row.put("costPrice", rs.getBigDecimal("cost_price"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 4 — CATEGORIES
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Revenue, profit, units sold per category — with turnover ratio.
     */
    public List<Map<String, Object>> getCategoryAnalysis(LocalDate start, LocalDate end) {
        String sql = """
            SELECT c.CategoryName,
                   SUM(t.Quantity)                               AS units_sold,
                   SUM(t.SalePrice * t.Quantity)                AS revenue,
                   SUM((t.SalePrice - p.Price) * t.Quantity)    AS profit,
                   CASE WHEN AVG(p.Quantity) = 0 THEN 0
                        ELSE ROUND(SUM(t.Quantity) / NULLIF(AVG(p.Quantity), 0), 2)
                   END AS turnover_ratio,
                   COUNT(DISTINCT p.ProductID)                  AS product_count
            FROM Transactions t
            JOIN Product  p ON t.ProductID  = p.ProductID
            JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY c.CategoryID, c.CategoryName
            ORDER BY revenue DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",         rs.getString("CategoryName"));
                row.put("unitsSold",    rs.getInt("units_sold"));
                row.put("revenue",      rs.getBigDecimal("revenue"));
                row.put("profit",       rs.getBigDecimal("profit"));
                row.put("turnover",     rs.getDouble("turnover_ratio"));
                row.put("productCount", rs.getInt("product_count"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 5 — STAFF
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Per-staff: sales count, revenue, profit, avg sale value, avg login hours.
     */
    public List<Map<String, Object>> getStaffPerformance(LocalDate start, LocalDate end) {
        String sql = """
            SELECT s.FullNames,
                   s.UserType,
                   COUNT(DISTINCT t.SaleID)                                AS sales_count,
                   COALESCE(SUM(t.SalePrice * t.Quantity), 0)              AS revenue,
                   COALESCE(SUM((t.SalePrice - p.Price) * t.Quantity), 0)  AS profit,
                   COALESCE(AVG(sale_totals.sale_total), 0)                AS avg_sale_value,
                   COALESCE(AVG(
                       TIMESTAMPDIFF(MINUTE, ci.LoginStamp, ci.LogoutStamp) / 60.0
                   ), 0)                                                    AS avg_hours_per_shift
            FROM Staff s
            LEFT JOIN Transactions t   ON s.StaffID = t.StaffID
                                       AND t.TransactionDate BETWEEN ? AND ?
            LEFT JOIN Product p        ON t.ProductID = p.ProductID
            LEFT JOIN (
                SELECT StaffID, SaleID, SUM(SalePrice * Quantity) AS sale_total
                FROM Transactions
                WHERE TransactionDate BETWEEN ? AND ? AND SaleID > 0
                GROUP BY StaffID, SaleID
            ) sale_totals              ON s.StaffID = sale_totals.StaffID
            LEFT JOIN CheckIn ci       ON s.StaffID = ci.StaffID
                                       AND ci.LoginStamp BETWEEN ? AND ?
                                       AND ci.LogoutStamp IS NOT NULL
            WHERE s.Status = 'Active'
            GROUP BY s.StaffID, s.FullNames, s.UserType
            ORDER BY revenue DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            Timestamp t1 = tsStart(start), t2 = tsEnd(end);
            ps.setTimestamp(1, t1); ps.setTimestamp(2, t2);
            ps.setTimestamp(3, t1); ps.setTimestamp(4, t2);
            ps.setTimestamp(5, t1); ps.setTimestamp(6, t2);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",          rs.getString("FullNames"));
                row.put("role",          rs.getString("UserType"));
                row.put("salesCount",    rs.getInt("sales_count"));
                row.put("revenue",       rs.getBigDecimal("revenue"));
                row.put("profit",        rs.getBigDecimal("profit"));
                row.put("avgSaleValue",  rs.getBigDecimal("avg_sale_value"));
                row.put("avgHoursShift", rs.getDouble("avg_hours_per_shift"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Staff login frequency and average session duration. */
    public List<Map<String, Object>> getStaffAttendance(LocalDate start, LocalDate end) {
        String sql = """
            SELECT s.FullNames,
                   s.UserType,
                   COUNT(ci.LogID)                                              AS login_count,
                   COALESCE(AVG(
                       TIMESTAMPDIFF(MINUTE, ci.LoginStamp, ci.LogoutStamp)
                   ), 0)                                                        AS avg_duration_mins,
                   COALESCE(SUM(
                       TIMESTAMPDIFF(MINUTE, ci.LoginStamp, ci.LogoutStamp)
                   ) / 60.0, 0)                                                 AS total_hours
            FROM Staff s
            JOIN CheckIn ci ON s.StaffID = ci.StaffID
            WHERE ci.LoginStamp BETWEEN ? AND ?
              AND ci.LogoutStamp IS NOT NULL
            GROUP BY s.StaffID, s.FullNames, s.UserType
            ORDER BY total_hours DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",           rs.getString("FullNames"));
                row.put("role",           rs.getString("UserType"));
                row.put("loginCount",     rs.getInt("login_count"));
                row.put("avgDurationMins",rs.getDouble("avg_duration_mins"));
                row.put("totalHours",     rs.getDouble("total_hours"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    public int getTotalStaff() {
        return queryInt("SELECT COUNT(*) AS cnt FROM Staff WHERE Status = 'Active'");
    }

    public String getTopPerformerByRevenue(LocalDate start, LocalDate end) {
        String sql = """
            SELECT s.FullNames
            FROM Transactions t
            JOIN Staff s ON t.StaffID = s.StaffID
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY s.StaffID, s.FullNames
            ORDER BY SUM(t.SalePrice * t.Quantity) DESC
            LIMIT 1
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("FullNames");
        } catch (SQLException e) { e.printStackTrace(); }
        return "—";
    }

    public BigDecimal getAverageRevenuePerStaff(LocalDate start, LocalDate end) {
        int staff = getTotalStaff();
        if (staff == 0) return BigDecimal.ZERO;
        return getTotalRevenue(start, end).divide(BigDecimal.valueOf(staff), 2, RoundingMode.HALF_UP);
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 6 — CUSTOMERS
    // ─────────────────────────────────────────────────────────────────────

    public int getTotalCustomers() {
        return queryInt("SELECT COUNT(*) AS cnt FROM Account");
    }

    public int getNewCustomers(LocalDate start, LocalDate end) {
        String sql = "SELECT COUNT(*) AS cnt FROM Account WHERE TimeStamp BETWEEN ? AND ?";
        return queryInt(sql, tsStart(start), tsEnd(end));
    }

    /** Customers who made more than one purchase in the period. */
    public double getRepeatCustomerRate(LocalDate start, LocalDate end) {
        String sql = """
            SELECT
                COUNT(DISTINCT CASE WHEN pc > 1 THEN AccountID END) AS repeats,
                COUNT(DISTINCT AccountID)                            AS total
            FROM (
                SELECT AccountID, COUNT(DISTINCT SaleID) AS pc
                FROM Transactions
                WHERE TransactionDate BETWEEN ? AND ?
                GROUP BY AccountID
            ) sub
            """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int repeats = rs.getInt("repeats");
                int total   = rs.getInt("total");
                return total > 0 ? (repeats * 100.0 / total) : 0.0;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0;
    }

    /**
     * Customer value segmentation based on purchase count in the period.
     * VIP ≥ 10, Regular ≥ 5, Occasional ≥ 2, One-Time = 1.
     */
    public Map<String, Integer> getCustomerSegmentation(LocalDate start, LocalDate end) {
        String sql = """
            SELECT
                CASE
                    WHEN pc >= 10 THEN 'VIP (10+)'
                    WHEN pc >= 5  THEN 'Regular (5–9)'
                    WHEN pc >= 2  THEN 'Occasional (2–4)'
                    ELSE               'One-Time'
                END AS segment,
                COUNT(*) AS cnt
            FROM (
                SELECT AccountID, COUNT(DISTINCT SaleID) AS pc
                FROM Transactions
                WHERE TransactionDate BETWEEN ? AND ?
                GROUP BY AccountID
            ) sub
            GROUP BY segment
            """;
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("VIP (10+)", 0);
        result.put("Regular (5–9)", 0);
        result.put("Occasional (2–4)", 0);
        result.put("One-Time", 0);
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString("segment"), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Customer age groups derived from DateOfBirth. */
    public Map<String, Integer> getCustomerAgeGroups() {
        String sql = """
            SELECT
                CASE
                    WHEN age < 18             THEN 'Under 18'
                    WHEN age BETWEEN 18 AND 24 THEN '18–24'
                    WHEN age BETWEEN 25 AND 34 THEN '25–34'
                    WHEN age BETWEEN 35 AND 44 THEN '35–44'
                    WHEN age BETWEEN 45 AND 54 THEN '45–54'
                    ELSE                           '55+'
                END AS age_group,
                COUNT(*) AS cnt
            FROM (
                SELECT TIMESTAMPDIFF(YEAR, DateOfBirth, CURDATE()) AS age
                FROM Account
                WHERE DateOfBirth IS NOT NULL
            ) ages
            GROUP BY age_group
            ORDER BY MIN(age)
            """;
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             Statement  stmt = c.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {
            while (rs.next()) result.put(rs.getString("age_group"), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Monthly new customer registrations for a given year. */
    public Map<YearMonth, Integer> getMonthlyNewCustomers(int year) {
        String sql = """
            SELECT YEAR(TimeStamp) AS yr, MONTH(TimeStamp) AS mon, COUNT(*) AS cnt
            FROM Account
            WHERE YEAR(TimeStamp) = ?
            GROUP BY yr, mon
            ORDER BY mon
            """;
        Map<YearMonth, Integer> result = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                result.put(YearMonth.of(rs.getInt("yr"), rs.getInt("mon")), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        for (int m = 1; m <= 12; m++)
            result.putIfAbsent(YearMonth.of(year, m), 0);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 7 — RETURNS & EXCHANGES
    // ─────────────────────────────────────────────────────────────────────

    /** Summary of returns: count, refund total, top reasons, by status. */
    public Map<String, Object> getReturnsSummary(LocalDate start, LocalDate end) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Counts by status
        String statusSql = """
            SELECT COALESCE(Status, 'Pending') AS status, COUNT(*) AS cnt,
                   COALESCE(SUM(RefundAmount), 0) AS total_refund
            FROM Returns
            WHERE ReturnDate BETWEEN ? AND ?
            GROUP BY status
            """;
        Map<String, Integer>   statusCounts  = new LinkedHashMap<>();
        Map<String, BigDecimal> statusRefunds = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(statusSql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                statusCounts.put(rs.getString("status"), rs.getInt("cnt"));
                statusRefunds.put(rs.getString("status"), rs.getBigDecimal("total_refund"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        summary.put("countByStatus",  statusCounts);
        summary.put("refundByStatus", statusRefunds);

        // Return rate
        int totalSales   = getTotalSales(start, end);
        int totalReturns = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
        summary.put("returnRate", totalSales > 0 ? (totalReturns * 100.0 / totalSales) : 0.0);

        // Top return reasons
        String reasonSql = """
            SELECT COALESCE(Reason, 'Not specified') AS reason, COUNT(*) AS cnt
            FROM Returns
            WHERE ReturnDate BETWEEN ? AND ?
            GROUP BY reason
            ORDER BY cnt DESC
            LIMIT 5
            """;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(reasonSql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) reasons.put(rs.getString("reason"), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        summary.put("topReasons", reasons);

        return summary;
    }

    /** Exchange summary: counts by status, top exchange reasons. */
    public Map<String, Object> getExchangesSummary(LocalDate start, LocalDate end) {
        Map<String, Object> summary = new LinkedHashMap<>();

        String sql = """
            SELECT COALESCE(Status, 'Pending') AS status, COUNT(*) AS cnt
            FROM Exchanges
            WHERE ExchangeDate BETWEEN ? AND ?
            GROUP BY status
            """;
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) statusCounts.put(rs.getString("status"), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        summary.put("countByStatus", statusCounts);

        String reasonSql = """
            SELECT COALESCE(Reason, 'Not specified') AS reason, COUNT(*) AS cnt
            FROM Exchanges
            WHERE ExchangeDate BETWEEN ? AND ?
            GROUP BY reason
            ORDER BY cnt DESC
            LIMIT 5
            """;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(reasonSql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) reasons.put(rs.getString("reason"), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        summary.put("topReasons", reasons);

        return summary;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 8 — INVENTORY
    // ─────────────────────────────────────────────────────────────────────

    public int getTotalProducts() {
        return queryInt("SELECT COUNT(*) AS cnt FROM Product");
    }

    public int getTotalStockUnits() {
        return queryInt("SELECT COALESCE(SUM(Quantity), 0) AS cnt FROM Product");
    }

    public BigDecimal getTotalStockValue() {
        return queryBigDecimal("SELECT COALESCE(SUM(Price * Quantity), 0) AS total FROM Product");
    }

    public int getLowStockCount() {
        return queryInt("SELECT COUNT(*) AS cnt FROM Product WHERE Quantity > 0 AND Quantity < 10");
    }

    public int getOutOfStockCount() {
        return queryInt("SELECT COUNT(*) AS cnt FROM Product WHERE Quantity = 0");
    }

    /**
     * Stock coverage (days of stock remaining) = current stock / avg daily sales.
     * Also returns: current stock, cost value, recent units sold, last restock date.
     */
    public List<Map<String, Object>> getInventoryHealthReport(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        String sql = """
            SELECT p.ProductName,
                   c.CategoryName,
                   p.Quantity                                           AS stock,
                   p.Price                                             AS cost_price,
                   p.Price * p.Quantity                                AS stock_value,
                   COALESCE(s.units_sold, 0)                          AS units_sold,
                   COALESCE(s.units_sold, 0) / ?                      AS daily_avg,
                   CASE WHEN COALESCE(s.units_sold, 0) = 0 THEN 9999
                        ELSE ROUND(p.Quantity / (COALESCE(s.units_sold, 0) / ?))
                   END                                                  AS days_cover,
                   r.last_restock
            FROM Product p
            JOIN Category c ON p.CategoryID = c.CategoryID
            LEFT JOIN (
                SELECT ProductID, SUM(Quantity) AS units_sold
                FROM Transactions
                WHERE TransactionDate BETWEEN ? AND ?
                GROUP BY ProductID
            ) s ON p.ProductID = s.ProductID
            LEFT JOIN (
                SELECT ProductID, MAX(InventoryDate) AS last_restock
                FROM AddInventory
                GROUP BY ProductID
            ) r ON p.ProductID = r.ProductID
            ORDER BY days_cover ASC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, days);
            ps.setLong(2, days);
            ps.setTimestamp(3, tsStart(start));
            ps.setTimestamp(4, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",        rs.getString("ProductName"));
                row.put("category",    rs.getString("CategoryName"));
                row.put("stock",       rs.getInt("stock"));
                row.put("costPrice",   rs.getBigDecimal("cost_price"));
                row.put("stockValue",  rs.getBigDecimal("stock_value"));
                row.put("unitsSold",   rs.getInt("units_sold"));
                row.put("dailyAvg",    rs.getDouble("daily_avg"));
                row.put("daysCover",   rs.getInt("days_cover"));
                row.put("lastRestock", rs.getTimestamp("last_restock"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Stock levels grouped by category with stockout counts. */
    public List<Map<String, Object>> getStockByCategory() {
        String sql = """
            SELECT c.CategoryName,
                   COUNT(p.ProductID)           AS product_count,
                   SUM(p.Quantity)              AS total_stock,
                   SUM(p.Price * p.Quantity)    AS stock_value,
                   SUM(CASE WHEN p.Quantity = 0 THEN 1 ELSE 0 END) AS out_of_stock,
                   SUM(CASE WHEN p.Quantity > 0 AND p.Quantity < 10 THEN 1 ELSE 0 END) AS low_stock
            FROM Product p
            JOIN Category c ON p.CategoryID = c.CategoryID
            GROUP BY c.CategoryID, c.CategoryName
            ORDER BY total_stock DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             Statement  stmt = c.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category",     rs.getString("CategoryName"));
                row.put("productCount", rs.getInt("product_count"));
                row.put("totalStock",   rs.getInt("total_stock"));
                row.put("stockValue",   rs.getBigDecimal("stock_value"));
                row.put("outOfStock",   rs.getInt("out_of_stock"));
                row.put("lowStock",     rs.getInt("low_stock"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Inventory restock history — who restocked what and when. */
    public List<Map<String, Object>> getRestockHistory(LocalDate start, LocalDate end) {
        String sql = """
            SELECT ai.InventoryDate,
                   p.ProductName,
                   c.CategoryName,
                   ai.QuantityAdded,
                   ai.QuantityLeft,
                   s.FullNames AS restocked_by
            FROM AddInventory ai
            JOIN Product  p ON ai.ProductID = p.ProductID
            JOIN Category c ON p.CategoryID = c.CategoryID
            JOIN Staff    s ON ai.StaffID   = s.StaffID
            WHERE ai.InventoryDate BETWEEN ? AND ?
            ORDER BY ai.InventoryDate DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date",        rs.getTimestamp("InventoryDate"));
                row.put("product",     rs.getString("ProductName"));
                row.put("category",    rs.getString("CategoryName"));
                row.put("added",       rs.getInt("QuantityAdded"));
                row.put("remaining",   rs.getInt("QuantityLeft"));
                row.put("restockedBy", rs.getString("restocked_by"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 9 — PROMOTIONS & MARKETING
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Promo performance: revenue with/without each promo code,
     * plus discount impact estimation.
     */
    public List<Map<String, Object>> getPromoPerformance(LocalDate start, LocalDate end) {
        String sql = """
            SELECT COALESCE(t.PromoCode, 'No Promo') AS promo_code,
                   COUNT(DISTINCT t.SaleID)           AS sales_count,
                   SUM(t.Quantity)                    AS units_sold,
                   SUM(t.SalePrice * t.Quantity)      AS revenue,
                   AVG(t.SalePrice * t.Quantity)      AS avg_sale_value
            FROM Transactions t
            WHERE t.TransactionDate BETWEEN ? AND ?
            GROUP BY promo_code
            ORDER BY revenue DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("promoCode",   rs.getString("promo_code"));
                row.put("salesCount",  rs.getInt("sales_count"));
                row.put("unitsSold",   rs.getInt("units_sold"));
                row.put("revenue",     rs.getBigDecimal("revenue"));
                row.put("avgSaleValue",rs.getBigDecimal("avg_sale_value"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    /** Email marketing campaign performance from MarketingHistory. */
    public List<Map<String, Object>> getMarketingCampaigns(LocalDate start, LocalDate end) {
        String sql = """
            SELECT mh.SentAt,
                   mh.Subject,
                   s.FullNames       AS sent_by,
                   mh.RecipientsCount,
                   mh.SuccessCount,
                   CASE WHEN mh.RecipientsCount = 0 THEN 0
                        ELSE ROUND(mh.SuccessCount * 100.0 / mh.RecipientsCount, 1)
                   END AS delivery_rate
            FROM MarketingHistory mh
            JOIN Staff s ON mh.SentBy = s.StaffID
            WHERE mh.SentAt BETWEEN ? AND ?
            ORDER BY mh.SentAt DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sentAt",        rs.getTimestamp("SentAt"));
                row.put("subject",       rs.getString("Subject"));
                row.put("sentBy",        rs.getString("sent_by"));
                row.put("recipients",    rs.getInt("RecipientsCount"));
                row.put("successCount",  rs.getInt("SuccessCount"));
                row.put("deliveryRate",  rs.getDouble("delivery_rate"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 10 — SESSIONS
    // ─────────────────────────────────────────────────────────────────────

    /** Business session analytics: revenue per session, avg session length. */
    public List<Map<String, Object>> getSessionAnalytics(LocalDate start, LocalDate end) {
        String sql = """
            SELECT bs.SessionID,
                   bs.StartDate,
                   bs.EndDate,
                   s.FullNames                                    AS started_by,
                   bs.TotalCashSales,
                   bs.TotalCardSales,
                   bs.TotalSales,
                   bs.DeclarationSigned,
                   TIMESTAMPDIFF(MINUTE, bs.StartDate, bs.EndDate) AS duration_mins
            FROM BusinessSessions bs
            JOIN Staff s ON bs.SupervisorID = s.StaffID
            WHERE bs.StartDate BETWEEN ? AND ?
              AND bs.Status = 'Closed'
            ORDER BY bs.StartDate DESC
            """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, tsStart(start));
            ps.setTimestamp(2, tsEnd(end));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sessionId",       rs.getInt("SessionID"));
                row.put("startDate",       rs.getTimestamp("StartDate"));
                row.put("endDate",         rs.getTimestamp("EndDate"));
                row.put("startedBy",       rs.getString("started_by"));
                row.put("cashSales",       rs.getBigDecimal("TotalCashSales"));
                row.put("cardSales",       rs.getBigDecimal("TotalCardSales"));
                row.put("totalSales",      rs.getBigDecimal("TotalSales"));
                row.put("signed",          rs.getBoolean("DeclarationSigned"));
                row.put("durationMins",    rs.getInt("duration_mins"));
                result.add(row);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 11 — GROWTH & KPI COMPARISONS
    // ─────────────────────────────────────────────────────────────────────

    public double calculateGrowth(BigDecimal prev, BigDecimal curr) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return curr.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP)
                   .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public double calculateGrowth(int prev, int curr) {
        return prev == 0 ? 0.0 : ((curr - prev) * 100.0 / prev);
    }

    /** Returns prior-period equivalents for the given date range. */
    public LocalDate[] getPriorPeriod(LocalDate start, LocalDate end) {
        long len = ChronoUnit.DAYS.between(start, end) + 1;
        return new LocalDate[]{ start.minusDays(len), start.minusDays(1) };
    }

    // ─────────────────────────────────────────────────────────────────────
    // SECTION 12 — EXCEL EXPORT
    // ─────────────────────────────────────────────────────────────────────

    public boolean exportToExcel(LocalDate start, LocalDate end, String fileName) {
        try (Workbook wb = new XSSFWorkbook()) {

            buildExcelSummarySheet      (wb.createSheet("Executive Summary"), start, end);
            buildExcelRevenueSheet      (wb.createSheet("Revenue & Profit"),   start, end);
            buildExcelProductSheet      (wb.createSheet("Products"),           start, end);
            buildExcelCategorySheet     (wb.createSheet("Categories"),         start, end);
            buildExcelStaffSheet        (wb.createSheet("Staff Performance"),  start, end);
            buildExcelCustomerSheet     (wb.createSheet("Customers"),          start, end);
            buildExcelInventorySheet    (wb.createSheet("Inventory"),          start, end);
            buildExcelReturnsSheet      (wb.createSheet("Returns & Exchanges"),start, end);
            buildExcelPromoSheet        (wb.createSheet("Promotions"),         start, end);
            buildExcelMarketingSheet    (wb.createSheet("Marketing"),          start, end);

            File dir = new File("reports");
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream("reports/" + fileName + ".xlsx")) {
                wb.write(fos);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Excel sheet builders ──────────────────────────────────────────────

    private void buildExcelSummarySheet(Sheet sheet, LocalDate start, LocalDate end) {
        LocalDate[] prior = getPriorPeriod(start, end);
        ExcelHelper h = new ExcelHelper(sheet);

        h.title("EXECUTIVE SUMMARY — " + start + " to " + end, 5);
        h.blank();

        // Revenue block
        h.sectionHeader("FINANCIAL SUMMARY", 5);
        h.headerRow("Metric", "Current Period", "Prior Period", "Change (R)", "Growth %");

        BigDecimal rev     = getTotalRevenue(start, end);
        BigDecimal prevRev = getTotalRevenue(prior[0], prior[1]);
        BigDecimal cogs    = getTotalCOGS(start, end);
        BigDecimal profit  = getGrossProfit(start, end);
        BigDecimal refunds = getTotalApprovedRefunds(start, end);
        BigDecimal netRev  = getNetRevenue(start, end);

        h.comparisonRow("Gross Revenue",      rev,             prevRev);
        h.comparisonRow("Total COGS",         cogs,            getTotalCOGS(prior[0], prior[1]));
        h.comparisonRow("Gross Profit",       profit,          getGrossProfit(prior[0], prior[1]));
        h.comparisonRow("Approved Refunds",   refunds,         getTotalApprovedRefunds(prior[0], prior[1]));
        h.comparisonRow("Net Revenue",        netRev,          getNetRevenue(prior[0], prior[1]));
        h.blank();

        h.sectionHeader("OPERATIONAL KPIs", 5);
        h.headerRow("Metric", "Current Period", "Prior Period", "Change", "Growth %");

        int sales     = getTotalSales(start, end);
        int prevSales = getTotalSales(prior[0], prior[1]);
        h.comparisonRow("Total Sales (transactions)", BigDecimal.valueOf(sales), BigDecimal.valueOf(prevSales));
        h.comparisonRow("Average Sale Value",
                getAverageSaleValue(start, end), getAverageSaleValue(prior[0], prior[1]));
        h.comparisonRow("New Customers",
                BigDecimal.valueOf(getNewCustomers(start, end)),
                BigDecimal.valueOf(getNewCustomers(prior[0], prior[1])));

        double margin = getGrossProfitMargin(start, end);
        h.blank();
        h.labelValueRow("Gross Margin %",     String.format("%.1f%%", margin));
        h.labelValueRow("Repeat Customer Rate", String.format("%.1f%%", getRepeatCustomerRate(start, end)));
        h.labelValueRow("Top Performer",       getTopPerformerByRevenue(start, end));
        h.labelValueRow("Low Stock Items",     String.valueOf(getLowStockCount()));
        h.labelValueRow("Out-of-Stock Items",  String.valueOf(getOutOfStockCount()));

        h.autosize(5);
    }

    private void buildExcelRevenueSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("REVENUE & PROFIT ANALYSIS — " + start + " to " + end, 4);
        h.blank();

        h.sectionHeader("DAILY REVENUE", 4);
        h.headerRow("Date", "Revenue (R)", "Gross Profit (R)", "Margin %");

        Map<LocalDate, BigDecimal> daily = getDailyRevenue(start, end);
        for (Map.Entry<LocalDate, BigDecimal> e : daily.entrySet()) {
            BigDecimal r = e.getValue();
            // Approx per-day profit using overall margin
            double margin = getGrossProfitMargin(start, end) / 100.0;
            BigDecimal p = r.multiply(BigDecimal.valueOf(margin)).setScale(2, RoundingMode.HALF_UP);
            h.dataRow(e.getKey().toString(), fmt(r), fmt(p), String.format("%.1f%%", margin * 100));
        }
        h.blank();

        h.sectionHeader("REVENUE BY PAYMENT METHOD", 2);
        h.headerRow("Payment Method", "Revenue (R)");
        getRevenueByPaymentMethod(start, end)
                .forEach((m, v) -> h.dataRow(m, fmt(v)));
        h.blank();

        h.sectionHeader("REVENUE BY DAY OF WEEK", 2);
        h.headerRow("Day", "Revenue (R)");
        getRevenueByDayOfWeek(start, end)
                .forEach((d, v) -> h.dataRow(d, fmt(v)));
        h.blank();

        h.sectionHeader("HOURLY REVENUE PATTERN", 2);
        h.headerRow("Hour", "Revenue (R)");
        getHourlyRevenue(start, end)
                .forEach((hr, v) -> h.dataRow(String.format("%02d:00", hr), fmt(v)));

        h.autosize(4);
    }

    private void buildExcelProductSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("PRODUCT PERFORMANCE — " + start + " to " + end, 7);
        h.blank();

        h.sectionHeader("TOP PRODUCTS BY REVENUE", 7);
        h.headerRow("Product", "Category", "Units Sold", "Revenue (R)", "COGS (R)", "Profit (R)", "Margin %");
        for (Map<String, Object> row : getTopProductsDetailed(start, end, 30)) {
            h.dataRow(
                str(row, "name"), str(row, "category"),
                str(row, "unitsSold"), fmt(bd(row, "revenue")),
                fmt(bd(row, "cost")),  fmt(bd(row, "profit")),
                String.format("%.1f%%", dbl(row, "margin"))
            );
        }
        h.blank();

        h.sectionHeader("SLOW-MOVING STOCK", 6);
        h.headerRow("Product", "Category", "Stock", "Sold (Period)", "Revenue (R)", "Cost Price (R)");
        for (Map<String, Object> row : getSlowMovers(start, end)) {
            h.dataRow(
                str(row, "name"), str(row, "category"),
                str(row, "stock"), str(row, "unitsSold"),
                fmt(bd(row, "revenue")), fmt(bd(row, "costPrice"))
            );
        }

        h.autosize(7);
    }

    private void buildExcelCategorySheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("CATEGORY ANALYSIS — " + start + " to " + end, 6);
        h.blank();
        h.headerRow("Category", "Products", "Units Sold", "Revenue (R)", "Profit (R)", "Turnover Ratio");
        for (Map<String, Object> row : getCategoryAnalysis(start, end)) {
            h.dataRow(
                str(row, "name"),     str(row, "productCount"),
                str(row, "unitsSold"), fmt(bd(row, "revenue")),
                fmt(bd(row, "profit")), String.format("%.2f", dbl(row, "turnover"))
            );
        }
        h.autosize(6);
    }

    private void buildExcelStaffSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("STAFF PERFORMANCE — " + start + " to " + end, 7);
        h.blank();

        h.sectionHeader("SALES PERFORMANCE", 7);
        h.headerRow("Staff", "Role", "Sales", "Revenue (R)", "Profit (R)", "Avg Sale (R)", "Avg Hours/Shift");
        for (Map<String, Object> row : getStaffPerformance(start, end)) {
            h.dataRow(
                str(row, "name"),       str(row, "role"),
                str(row, "salesCount"), fmt(bd(row, "revenue")),
                fmt(bd(row, "profit")), fmt(bd(row, "avgSaleValue")),
                String.format("%.1f", dbl(row, "avgHoursShift"))
            );
        }
        h.blank();

        h.sectionHeader("ATTENDANCE", 5);
        h.headerRow("Staff", "Role", "Logins", "Avg Duration (mins)", "Total Hours");
        for (Map<String, Object> row : getStaffAttendance(start, end)) {
            h.dataRow(
                str(row, "name"),       str(row, "role"),
                str(row, "loginCount"), String.format("%.0f", dbl(row, "avgDurationMins")),
                String.format("%.1f", dbl(row, "totalHours"))
            );
        }
        h.autosize(7);
    }

    private void buildExcelCustomerSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("CUSTOMER INSIGHTS — " + start + " to " + end, 2);
        h.blank();

        h.sectionHeader("OVERVIEW", 2);
        h.labelValueRow("Total Customers",   String.valueOf(getTotalCustomers()));
        h.labelValueRow("New This Period",   String.valueOf(getNewCustomers(start, end)));
        h.labelValueRow("Repeat Rate",       String.format("%.1f%%", getRepeatCustomerRate(start, end)));
        h.blank();

        h.sectionHeader("CUSTOMER SEGMENTS", 2);
        h.headerRow("Segment", "Count");
        getCustomerSegmentation(start, end).forEach((k, v) -> h.dataRow(k, String.valueOf(v)));
        h.blank();

        h.sectionHeader("AGE GROUPS", 2);
        h.headerRow("Age Group", "Count");
        getCustomerAgeGroups().forEach((k, v) -> h.dataRow(k, String.valueOf(v)));
        h.blank();

        h.sectionHeader("MONTHLY NEW CUSTOMERS (" + start.getYear() + ")", 2);
        h.headerRow("Month", "New Customers");
        getMonthlyNewCustomers(start.getYear())
                .forEach((ym, v) -> h.dataRow(ym.getMonth().toString(), String.valueOf(v)));

        h.autosize(2);
    }

    private void buildExcelInventorySheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("INVENTORY HEALTH — " + start + " to " + end, 8);
        h.blank();

        h.sectionHeader("SUMMARY BY CATEGORY", 6);
        h.headerRow("Category", "Products", "Total Stock", "Stock Value (R)", "Out of Stock", "Low Stock");
        for (Map<String, Object> row : getStockByCategory()) {
            h.dataRow(
                str(row, "category"),    str(row, "productCount"),
                str(row, "totalStock"),  fmt(bd(row, "stockValue")),
                str(row, "outOfStock"),  str(row, "lowStock")
            );
        }
        h.blank();

        h.sectionHeader("STOCK COVERAGE (days until stockout)", 8);
        h.headerRow("Product", "Category", "Stock", "Cost Price", "Stock Value", "Sold (Period)", "Daily Avg", "Days Cover");
        for (Map<String, Object> row : getInventoryHealthReport(start, end)) {
            int dc = (int) row.get("daysCover");
            h.dataRow(
                str(row, "name"),      str(row, "category"),
                str(row, "stock"),     fmt(bd(row, "costPrice")),
                fmt(bd(row, "stockValue")), str(row, "unitsSold"),
                String.format("%.1f", dbl(row, "dailyAvg")),
                dc >= 9999 ? "No sales" : String.valueOf(dc)
            );
        }
        h.blank();

        h.sectionHeader("RESTOCK HISTORY", 5);
        h.headerRow("Date", "Product", "Category", "Qty Added", "Restocked By");
        for (Map<String, Object> row : getRestockHistory(start, end)) {
            Timestamp ts = (Timestamp) row.get("date");
            h.dataRow(
                ts != null ? ts.toLocalDateTime().toLocalDate().toString() : "",
                str(row, "product"), str(row, "category"),
                str(row, "added"),   str(row, "restockedBy")
            );
        }
        h.autosize(8);
    }

    private void buildExcelReturnsSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("RETURNS & EXCHANGES — " + start + " to " + end, 2);
        h.blank();

        @SuppressWarnings("unchecked")
        Map<String, Object> retSummary  = getReturnsSummary(start, end);
        @SuppressWarnings("unchecked")
        Map<String, Object> exchSummary = getExchangesSummary(start, end);

        h.sectionHeader("RETURNS OVERVIEW", 2);
        h.labelValueRow("Return Rate",    String.format("%.1f%%", (double) retSummary.get("returnRate")));
        h.blank();

        h.headerRow("Status", "Count");
        ((Map<String, Integer>) retSummary.get("countByStatus"))
                .forEach((k, v) -> h.dataRow(k, String.valueOf(v)));
        h.blank();

        h.sectionHeader("TOP RETURN REASONS", 2);
        h.headerRow("Reason", "Count");
        ((Map<String, Integer>) retSummary.get("topReasons"))
                .forEach((k, v) -> h.dataRow(k, String.valueOf(v)));
        h.blank();

        h.sectionHeader("EXCHANGES OVERVIEW", 2);
        h.headerRow("Status", "Count");
        ((Map<String, Integer>) exchSummary.get("countByStatus"))
                .forEach((k, v) -> h.dataRow(k, String.valueOf(v)));
        h.blank();

        h.sectionHeader("TOP EXCHANGE REASONS", 2);
        h.headerRow("Reason", "Count");
        ((Map<String, Integer>) exchSummary.get("topReasons"))
                .forEach((k, v) -> h.dataRow(k, String.valueOf(v)));

        h.autosize(2);
    }

    private void buildExcelPromoSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("PROMOTIONS PERFORMANCE — " + start + " to " + end, 5);
        h.blank();
        h.headerRow("Promo Code", "Sales Count", "Units Sold", "Revenue (R)", "Avg Sale Value (R)");
        for (Map<String, Object> row : getPromoPerformance(start, end)) {
            h.dataRow(
                str(row, "promoCode"),  str(row, "salesCount"),
                str(row, "unitsSold"),  fmt(bd(row, "revenue")),
                fmt(bd(row, "avgSaleValue"))
            );
        }
        h.autosize(5);
    }

    private void buildExcelMarketingSheet(Sheet sheet, LocalDate start, LocalDate end) {
        ExcelHelper h = new ExcelHelper(sheet);
        h.title("MARKETING CAMPAIGNS — " + start + " to " + end, 6);
        h.blank();
        h.headerRow("Date", "Subject", "Sent By", "Recipients", "Delivered", "Delivery Rate %");
        for (Map<String, Object> row : getMarketingCampaigns(start, end)) {
            Timestamp ts = (Timestamp) row.get("sentAt");
            h.dataRow(
                ts != null ? ts.toLocalDateTime().toLocalDate().toString() : "",
                str(row, "subject"),     str(row, "sentBy"),
                str(row, "recipients"),  str(row, "successCount"),
                String.format("%.1f%%", dbl(row, "deliveryRate"))
            );
        }
        h.autosize(6);
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Range-bound helpers for {@code WHERE col BETWEEN ? AND ?}.
     * tsStart is 00:00:00 of the start date; tsEnd is 23:59:59 of the end date,
     * so the whole of both boundary days is always included. (The previous
     * single ts() helper returned start-of-day for every date except today,
     * which silently dropped the last day of every historical range — and made
     * a "Today" filter, where start == end == today, match nothing.)
     */
    private Timestamp tsStart(LocalDate d) {
        return d == null ? null : Timestamp.valueOf(d.atStartOfDay());
    }
    private Timestamp tsEnd(LocalDate d) {
        return d == null ? null : Timestamp.valueOf(d.atTime(23, 59, 59));
    }

    private BigDecimal queryBigDecimal(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                BigDecimal v = rs.getBigDecimal(1);
                return v != null ? v : BigDecimal.ZERO;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return BigDecimal.ZERO;
    }

    private int queryInt(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private Map<String, BigDecimal> queryStringBigDecimalMap(String sql, Object... params) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString(1),
                    rs.getBigDecimal(2) != null ? rs.getBigDecimal(2) : BigDecimal.ZERO);
        } catch (SQLException e) { e.printStackTrace(); }
        return result;
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "0.00" : String.format("%.2f", v.doubleValue());
    }
    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }
    private static BigDecimal bd(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number  n)  return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
    private static double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    // ─────────────────────────────────────────────────────────────────────
    // EXCEL HELPER — inner utility class
    // ─────────────────────────────────────────────────────────────────────

    private static class ExcelHelper {
        private final Sheet    sheet;
        private final Workbook wb;
        private       int      row = 0;

        private final CellStyle titleStyle;
        private final CellStyle sectionStyle;
        private final CellStyle headerStyle;
        private final CellStyle dataStyle;
        private final CellStyle moneyStyle;

        ExcelHelper(Sheet sheet) {
            this.sheet = sheet;
            this.wb    = sheet.getWorkbook();

            // Title
            titleStyle = wb.createCellStyle();
            Font tf = wb.createFont();
            tf.setBold(true); tf.setFontHeightInPoints((short) 14);
            tf.setColor(IndexedColors.WHITE.getIndex());
            titleStyle.setFont(tf);
            titleStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            // Section header
            sectionStyle = wb.createCellStyle();
            Font sf = wb.createFont();
            sf.setBold(true); sf.setFontHeightInPoints((short) 11);
            sf.setColor(IndexedColors.WHITE.getIndex());
            sectionStyle.setFont(sf);
            sectionStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Column header
            headerStyle = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true); hf.setFontHeightInPoints((short) 10);
            headerStyle.setFont(hf);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Data
            dataStyle = wb.createCellStyle();
            Font df = wb.createFont();
            df.setFontHeightInPoints((short) 10);
            dataStyle.setFont(df);

            // Money
            moneyStyle = wb.createCellStyle();
            moneyStyle.setFont(df);
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);
        }

        void title(String text, int cols) {
            Row r = sheet.createRow(row++);
            Cell c = r.createCell(0);
            c.setCellValue(text);
            c.setCellStyle(titleStyle);
            if (cols > 1) sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, cols - 1));
        }

        void sectionHeader(String text, int cols) {
            Row r = sheet.createRow(row++);
            Cell c = r.createCell(0);
            c.setCellValue(text);
            c.setCellStyle(sectionStyle);
            if (cols > 1) sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, cols - 1));
        }

        void headerRow(String... labels) {
            Row r = sheet.createRow(row++);
            for (int i = 0; i < labels.length; i++) {
                Cell c = r.createCell(i);
                c.setCellValue(labels[i]);
                c.setCellStyle(headerStyle);
            }
        }

        void dataRow(String... values) {
            Row r = sheet.createRow(row++);
            for (int i = 0; i < values.length; i++) {
                Cell c = r.createCell(i);
                c.setCellValue(values[i]);
                c.setCellStyle(dataStyle);
            }
        }

        void comparisonRow(String label, BigDecimal current, BigDecimal prior) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(label);
            setMoney(r.createCell(1), current);
            setMoney(r.createCell(2), prior);
            BigDecimal diff = current.subtract(prior);
            setMoney(r.createCell(3), diff);
            double growth = prior.compareTo(BigDecimal.ZERO) == 0 ? 0
                : diff.divide(prior, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            Cell gc = r.createCell(4);
            gc.setCellValue(String.format("%.1f%%", growth));
            gc.setCellStyle(dataStyle);
        }

        void labelValueRow(String label, String value) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(label);
            r.createCell(1).setCellValue(value);
        }

        void blank() { sheet.createRow(row++); }

        void autosize(int cols) {
            for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
        }

        private void setMoney(Cell c, BigDecimal v) {
            c.setCellValue(v == null ? 0 : v.doubleValue());
            c.setCellStyle(moneyStyle);
        }
    }
}
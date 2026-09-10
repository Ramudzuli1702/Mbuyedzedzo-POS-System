package com.pos.views;

import com.pos.models.User;
import com.pos.services.ReportService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReportsView {

    private final User          currentUser;
    private final ReportService svc;

    private DatePicker      startPicker;
    private DatePicker      endPicker;
    private ComboBox<String> reportCombo;
    private VBox            contentArea;

    // Remembered across navigation within a session (a fresh ReportsView is
    // built every time the user returns to the Reports tab).
    private static LocalDate sessionStart  = LocalDate.now().withDayOfMonth(1);
    private static LocalDate sessionEnd    = LocalDate.now();
    private static String    sessionReport = "Executive Dashboard";

    // Palette - Updated to consistent teal theme
    private static final String C_BLUE   = "#0f766e";
    private static final String C_GREEN  = "#16a34a";
    private static final String C_AMBER  = "#d97706";
    private static final String C_RED    = "#dc2626";
    private static final String C_PURPLE = "#7c3aed";
    private static final String C_TEAL   = "#0f766e";
    private static final String C_SLATE  = "#475569";

    public ReportsView(User user) {
        this.currentUser = user;
        this.svc = new ReportService();
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f1f5f9;");

        VBox top = new VBox();
        top.getChildren().addAll(buildHeader(), buildToolbar());
        root.setTop(top);

        contentArea = new VBox(20);
        contentArea.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(contentArea);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f1f5f9; -fx-background-color: #f1f5f9;");
        root.setCenter(scroll);

        render();
        return root;
    }

    private HBox buildHeader() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(20, 28, 16, 28));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        VBox titles = new VBox(3);
        Label h1 = new Label("Business Intelligence");
        h1.setFont(Font.font("System", FontWeight.BOLD, 26));
        h1.setTextFill(Color.web("#0f766e"));
        Label h2 = new Label("Comprehensive analytics across sales, inventory, staff, and customers");
        h2.setFont(Font.font("System", 13));
        h2.setTextFill(Color.web("#64748b"));
        titles.getChildren().addAll(h1, h2);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportBtn = pill("📊 Export Excel", C_GREEN);
        exportBtn.setOnAction(e -> doExport());

        Button refreshBtn = pill("🔄 Refresh", C_BLUE);
        refreshBtn.setOnAction(e -> render());

        HBox actions = new HBox(10, exportBtn, refreshBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        bar.getChildren().addAll(titles, sp, actions);
        return bar;
    }

    private HBox buildToolbar() {
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(14, 28, 14, 28));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        reportCombo = new ComboBox<>();
        reportCombo.getItems().addAll(
            "Executive Dashboard",
            "Sales & Revenue",
            "Product Performance",
            "Customer Insights",
            "Staff Analytics",
            "Inventory Health",
            "Returns & Exchanges",
            "Promotions & Marketing"
        );
        reportCombo.setValue(reportCombo.getItems().contains(sessionReport) ? sessionReport : "Executive Dashboard");
        reportCombo.setPrefWidth(220);
        reportCombo.setOnAction(e -> render());

        startPicker = new DatePicker(sessionStart);
        endPicker   = new DatePicker(sessionEnd);

        Button applyBtn = pill("Apply", C_BLUE);
        applyBtn.setOnAction(e -> render());

        HBox quick = new HBox(6);
        quick.setAlignment(Pos.CENTER_LEFT);
        for (String[] qf : new String[][]{
            {"Today",     "0",  "0"},
            {"This Week", "6",  "0"},
            {"This Month","29", "0"},
            {"90 Days",   "89", "0"}
        }) {
            Button b = chip(qf[0]);
            int back = Integer.parseInt(qf[1]);
            b.setOnAction(e -> {
                startPicker.setValue(LocalDate.now().minusDays(back));
                endPicker.setValue(LocalDate.now());
                render();
            });
            quick.getChildren().add(b);
        }

        bar.getChildren().addAll(
            label("Report:"), reportCombo,
            sep(),
            label("From:"), startPicker,
            label("To:"),   endPicker,
            applyBtn,
            sep(),
            quick
        );
        return bar;
    }

    private void render() {
        contentArea.getChildren().clear();

        sessionStart  = start();
        sessionEnd    = end();
        sessionReport = reportCombo.getValue();

        if (start() == null || end() == null || end().isBefore(start())) {
            contentArea.getChildren().add(
                emptyState("Pick a valid date range — “From” must be on or before “To”."));
            return;
        }

        switch (reportCombo.getValue()) {
            case "Executive Dashboard"      -> buildExecutiveDashboard();
            case "Sales & Revenue"          -> buildSalesRevenue();
            case "Product Performance"      -> buildProductPerformance();
            case "Customer Insights"        -> buildCustomerInsights();
            case "Staff Analytics"          -> buildStaffAnalytics();
            case "Inventory Health"         -> buildInventoryHealth();
            case "Returns & Exchanges"      -> buildReturnsExchanges();
            case "Promotions & Marketing"   -> buildPromotionsMarketing();
        }
    }

    private LocalDate start() { return startPicker.getValue(); }
    private LocalDate end()   { return endPicker.getValue();   }

    /** A drill-through action for a KPI card: switch the report selector. */
    private Runnable goTo(String report) {
        return () -> reportCombo.setValue(report);
    }

    private void buildExecutiveDashboard() {
        LocalDate s = start(), e = end();
        LocalDate[] prior = svc.getPriorPeriod(s, e);

        BigDecimal rev      = svc.getTotalRevenue(s, e);
        BigDecimal prevRev  = svc.getTotalRevenue(prior[0], prior[1]);
        BigDecimal profit   = svc.getGrossProfit(s, e);
        BigDecimal prevProfit = svc.getGrossProfit(prior[0], prior[1]);
        int        sales    = svc.getTotalSales(s, e);
        int        prevSales= svc.getTotalSales(prior[0], prior[1]);
        BigDecimal avgSale  = svc.getAverageSaleValue(s, e);
        BigDecimal prevAvg  = svc.getAverageSaleValue(prior[0], prior[1]);
        double     margin   = svc.getGrossProfitMargin(s, e);
        int        newCust  = svc.getNewCustomers(s, e);
        int        prevCust = svc.getNewCustomers(prior[0], prior[1]);
        double     repRate  = svc.getRepeatCustomerRate(s, e);

        Runnable toSales     = goTo("Sales & Revenue");
        Runnable toCustomers  = goTo("Customer Insights");

        GridPane kpi1 = kpiGrid(3);
        kpi1.add(kpiCard("💰 Gross Revenue",    fmtR(rev),    svc.calculateGrowth(prevRev, rev),    C_GREEN,  "vs prior period", toSales), 0, 0);
        kpi1.add(kpiCard("📈 Gross Profit",     fmtR(profit), svc.calculateGrowth(prevProfit, profit), C_BLUE, fmtPct(margin) + " margin", toSales), 1, 0);
        kpi1.add(kpiCard("🛒 Total Sales",      String.valueOf(sales), svc.calculateGrowth(prevSales, sales), C_PURPLE, "transactions", toSales), 2, 0);
        contentArea.getChildren().add(kpi1);

        GridPane kpi2 = kpiGrid(3);
        kpi2.add(kpiCard("💳 Avg Sale Value", fmtR(avgSale),           svc.calculateGrowth(prevAvg, avgSale), C_TEAL,  "per transaction", toSales), 0, 0);
        kpi2.add(kpiCard("🆕 New Customers", String.valueOf(newCust),  svc.calculateGrowth(prevCust, newCust), C_AMBER, "registered", toCustomers), 1, 0);
        kpi2.add(kpiCard("🔁 Repeat Rate",   fmtPct(repRate),          0,                                     C_SLATE, "loyal customers", toCustomers), 2, 0);
        contentArea.getChildren().add(kpi2);

        Label drillHint = new Label("Tip: click a metric card to open its detailed report.");
        drillHint.setFont(Font.font("System", 11));
        drillHint.setTextFill(Color.web("#94a3b8"));
        contentArea.getChildren().add(drillHint);

        contentArea.getChildren().add(sectionLabel("Revenue Overview"));
        GridPane charts1 = chartGrid(2);
        charts1.add(buildDailyRevenueTrend(s, e), 0, 0);
        charts1.add(buildCategoryRevenueChart(s, e), 1, 0);
        contentArea.getChildren().add(charts1);

        GridPane charts2 = chartGrid(2);
        charts2.add(buildPaymentMethodChart(s, e), 0, 0);
        charts2.add(buildMonthlyRevenueTrend(), 1, 0);
        contentArea.getChildren().add(charts2);

        contentArea.getChildren().add(sectionLabel("Top Products (Revenue)"));
        contentArea.getChildren().add(buildTopProductsTable(s, e, 10));

        contentArea.getChildren().add(sectionLabel("Staff Performance"));
        contentArea.getChildren().add(buildStaffPerformanceChart(s, e));
    }

    private void buildSalesRevenue() {
        LocalDate s = start(), e = end();

        BigDecimal rev     = svc.getTotalRevenue(s, e);
        BigDecimal netRev  = svc.getNetRevenue(s, e);
        BigDecimal profit  = svc.getGrossProfit(s, e);
        BigDecimal refunds = svc.getTotalApprovedRefunds(s, e);
        int        sales   = svc.getTotalSales(s, e);
        BigDecimal avgSale = svc.getAverageSaleValue(s, e);
        double     margin  = svc.getGrossProfitMargin(s, e);

        GridPane kpi = kpiGrid(4);
        kpi.add(kpiCard("Gross Revenue",    fmtR(rev),    0, C_GREEN,  "total income"),       0, 0);
        kpi.add(kpiCard("Net Revenue",      fmtR(netRev), 0, C_BLUE,   "after refunds"),      1, 0);
        kpi.add(kpiCard("Gross Profit",     fmtR(profit), 0, C_PURPLE, fmtPct(margin) + " margin"), 2, 0);
        kpi.add(kpiCard("Approved Refunds", fmtR(refunds),0, C_RED,    "approved only"),      3, 0);
        contentArea.getChildren().add(kpi);

        GridPane kpi2 = kpiGrid(2);
        kpi2.add(kpiCard("Total Sales",    String.valueOf(sales), 0, C_TEAL,  "transactions"), 0, 0);
        kpi2.add(kpiCard("Avg Sale Value", fmtR(avgSale),         0, C_AMBER, "per sale"),     1, 0);
        contentArea.getChildren().add(kpi2);

        contentArea.getChildren().add(sectionLabel("Sales Patterns"));
        GridPane patterns = chartGrid(2);
        patterns.add(buildDailyRevenueTrend(s, e), 0, 0);
        patterns.add(buildHourlySalesChart(s, e),  1, 0);
        contentArea.getChildren().add(patterns);

        GridPane patterns2 = chartGrid(2);
        patterns2.add(buildDayOfWeekChart(s, e),      0, 0);
        patterns2.add(buildSaleValueDistChart(s, e),  1, 0);
        contentArea.getChildren().add(patterns2);

        contentArea.getChildren().add(sectionLabel("Payment Methods"));
        contentArea.getChildren().add(buildPaymentMethodChart(s, e));
    }

    private void buildProductPerformance() {
        LocalDate s = start(), e = end();

        GridPane kpi = kpiGrid(4);
        kpi.add(kpiCard("Total Products",  String.valueOf(svc.getTotalProducts()),    0, C_BLUE,   "in catalog"),   0, 0);
        kpi.add(kpiCard("In Stock",        String.valueOf(svc.getTotalProducts() - svc.getLowStockCount() - svc.getOutOfStockCount()), 0, C_GREEN,  "available"),    1, 0);
        kpi.add(kpiCard("Low Stock",       String.valueOf(svc.getLowStockCount()),    0, C_AMBER,  "< 10 units"),   2, 0);
        kpi.add(kpiCard("Out of Stock",    String.valueOf(svc.getOutOfStockCount()),  0, C_RED,    "zero units"),   3, 0);
        contentArea.getChildren().add(kpi);

        contentArea.getChildren().add(sectionLabel("Top 15 Products by Revenue"));
        contentArea.getChildren().add(buildTopProductsTable(s, e, 15));

        contentArea.getChildren().add(sectionLabel("Slow-Moving Stock (high stock, low sales)"));
        contentArea.getChildren().add(buildSlowMoversTable(s, e));

        contentArea.getChildren().add(sectionLabel("Revenue by Category"));
        contentArea.getChildren().add(buildCategoryRevenueChart(s, e));
    }

    private void buildCustomerInsights() {
        LocalDate s = start(), e = end();
        LocalDate[] prior = svc.getPriorPeriod(s, e);

        int totalCust = svc.getTotalCustomers();
        int newCust   = svc.getNewCustomers(s, e);
        int prevCust  = svc.getNewCustomers(prior[0], prior[1]);
        double repRate= svc.getRepeatCustomerRate(s, e);

        GridPane kpi = kpiGrid(3);
        kpi.add(kpiCard("Total Customers", String.valueOf(totalCust), 0,                                     C_BLUE,   "all accounts"), 0, 0);
        kpi.add(kpiCard("New This Period", String.valueOf(newCust),   svc.calculateGrowth(prevCust, newCust), C_GREEN,  "registered"),   1, 0);
        kpi.add(kpiCard("Repeat Rate",     fmtPct(repRate),           0,                                     C_PURPLE, "2+ purchases"), 2, 0);
        contentArea.getChildren().add(kpi);

        contentArea.getChildren().add(sectionLabel("Customer Behaviour"));
        GridPane charts = chartGrid(2);
        charts.add(buildCustomerSegmentationChart(s, e), 0, 0);
        charts.add(buildCustomerAgeGroupChart(),         1, 0);
        contentArea.getChildren().add(charts);

        contentArea.getChildren().add(sectionLabel("Monthly New Customer Registrations"));
        contentArea.getChildren().add(buildMonthlyNewCustomersChart());
    }

    private void buildStaffAnalytics() {
        LocalDate s = start(), e = end();

        String topStaff = svc.getTopPerformerByRevenue(s, e);
        BigDecimal avgRevPerStaff = svc.getAverageRevenuePerStaff(s, e);

        GridPane kpi = kpiGrid(3);
        kpi.add(kpiCard("Active Staff",     String.valueOf(svc.getTotalStaff()), 0, C_BLUE,   "team members"), 0, 0);
        kpi.add(kpiCard("Avg Rev / Staff",  fmtR(avgRevPerStaff),                0, C_GREEN,  "this period"),  1, 0);
        kpi.add(kpiCard("Top Performer",    topStaff,                            0, C_AMBER,  "by revenue"),   2, 0);
        contentArea.getChildren().add(kpi);

        contentArea.getChildren().add(sectionLabel("Sales Performance"));
        contentArea.getChildren().add(buildStaffSalesTable(s, e));

        contentArea.getChildren().add(sectionLabel("Revenue by Staff Member"));
        contentArea.getChildren().add(buildStaffPerformanceChart(s, e));

        contentArea.getChildren().add(sectionLabel("Attendance & Hours"));
        contentArea.getChildren().add(buildStaffAttendanceTable(s, e));
    }

    private void buildInventoryHealth() {
        LocalDate s = start(), e = end();

        GridPane kpi = kpiGrid(4);
        kpi.add(kpiCard("Total Products",  String.valueOf(svc.getTotalProducts()),    0, C_BLUE,   "SKUs"),          0, 0);
        kpi.add(kpiCard("Total Stock",     String.valueOf(svc.getTotalStockUnits()),  0, C_GREEN,  "units"),         1, 0);
        kpi.add(kpiCard("Stock Value",     fmtR(svc.getTotalStockValue()),            0, C_PURPLE, "at cost price"), 2, 0);
        kpi.add(kpiCard("Out of Stock",    String.valueOf(svc.getOutOfStockCount()),  0, C_RED,    "urgent restock"),3, 0);
        contentArea.getChildren().add(kpi);

        contentArea.getChildren().add(sectionLabel("Stock by Category"));
        contentArea.getChildren().add(buildStockByCategoryTable());

        contentArea.getChildren().add(sectionLabel("Stock Coverage — Days Until Stockout (sorted: urgent first)"));
        contentArea.getChildren().add(buildInventoryHealthTable(s, e));

        contentArea.getChildren().add(sectionLabel("Restock History"));
        contentArea.getChildren().add(buildRestockHistoryTable(s, e));
    }

    private void buildReturnsExchanges() {
        LocalDate s = start(), e = end();

        Map<String, Object> retSum  = svc.getReturnsSummary(s, e);
        Map<String, Object> exchSum = svc.getExchangesSummary(s, e);

        @SuppressWarnings("unchecked")
        Map<String, Integer>   retByStatus  = (Map<String, Integer>)   retSum.get("countByStatus");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> refByStatus = (Map<String, BigDecimal>) retSum.get("refundByStatus");
        double returnRate = (double) retSum.get("returnRate");

        @SuppressWarnings("unchecked")
        Map<String, Integer> exchByStatus = (Map<String, Integer>) exchSum.get("countByStatus");

        int approvedRet   = retByStatus.getOrDefault("Approved", 0);
        int pendingRet    = retByStatus.getOrDefault("Pending",  0);
        int approvedExch  = exchByStatus.getOrDefault("Approved", 0);
        int pendingExch   = exchByStatus.getOrDefault("Pending",  0);

        BigDecimal approvedRefund = refByStatus.getOrDefault("Approved", BigDecimal.ZERO);

        GridPane kpi = kpiGrid(4);
        kpi.add(kpiCard("Return Rate",      fmtPct(returnRate), 0, C_AMBER,  "of all sales"),   0, 0);
        kpi.add(kpiCard("Approved Refunds", fmtR(approvedRefund), 0, C_RED,  "cash out"),       1, 0);
        kpi.add(kpiCard("Pending Returns",  String.valueOf(pendingRet),  0, C_AMBER, "awaiting"), 2, 0);
        kpi.add(kpiCard("Pending Exchanges",String.valueOf(pendingExch), 0, C_BLUE,  "awaiting"), 3, 0);
        contentArea.getChildren().add(kpi);

        contentArea.getChildren().add(sectionLabel("Returns by Status & Top Reasons"));
        GridPane charts = chartGrid(2);
        charts.add(buildReturnsStatusChart(retByStatus),         0, 0);
        @SuppressWarnings("unchecked")
        Map<String, Integer> retReasons = (Map<String, Integer>) retSum.get("topReasons");
        charts.add(buildReasonsChart("Top Return Reasons", retReasons), 1, 0);
        contentArea.getChildren().add(charts);

        contentArea.getChildren().add(sectionLabel("Exchanges by Status & Top Reasons"));
        GridPane charts2 = chartGrid(2);
        charts2.add(buildExchangeStatusChart(exchByStatus),         0, 0);
        @SuppressWarnings("unchecked")
        Map<String, Integer> exchReasons = (Map<String, Integer>) exchSum.get("topReasons");
        charts2.add(buildReasonsChart("Top Exchange Reasons", exchReasons), 1, 0);
        contentArea.getChildren().add(charts2);
    }

    private void buildPromotionsMarketing() {
        LocalDate s = start(), e = end();

        List<Map<String, Object>> promos   = svc.getPromoPerformance(s, e);
        List<Map<String, Object>> campaigns= svc.getMarketingCampaigns(s, e);

        int   promoSalesCount = promos.stream()
                .filter(r -> !"No Promo".equals(r.get("promoCode")))
                .mapToInt(r -> (int) r.get("salesCount")).sum();
        BigDecimal promoRevenue = promos.stream()
                .filter(r -> !"No Promo".equals(r.get("promoCode")))
                .map(r -> (BigDecimal) r.get("revenue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int campaignCount = campaigns.size();
        int totalRecipients = campaigns.stream().mapToInt(r -> (int) r.get("recipients")).sum();

        GridPane kpi = kpiGrid(4);
        kpi.add(kpiCard("Promo Sales",       String.valueOf(promoSalesCount), 0, C_GREEN,  "with promo code"), 0, 0);
        kpi.add(kpiCard("Promo Revenue",     fmtR(promoRevenue),              0, C_BLUE,   "promo-driven"),    1, 0);
        kpi.add(kpiCard("Email Campaigns",   String.valueOf(campaignCount),   0, C_PURPLE, "sent"),            2, 0);
        kpi.add(kpiCard("Total Recipients",  String.valueOf(totalRecipients), 0, C_TEAL,   "emails sent"),     3, 0);
        contentArea.getChildren().add(kpi);

        contentArea.getChildren().add(sectionLabel("Promo Code Performance"));
        contentArea.getChildren().add(buildPromoTable(promos));

        contentArea.getChildren().add(sectionLabel("Revenue: Promo vs Non-Promo"));
        contentArea.getChildren().add(buildPromoVsNoPromoChart(promos));

        contentArea.getChildren().add(sectionLabel("Email Marketing Campaigns"));
        contentArea.getChildren().add(buildMarketingCampaignsTable(campaigns));
    }

    private VBox buildDailyRevenueTrend(LocalDate s, LocalDate e) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        svc.getDailyRevenue(s, e).forEach((day, val) ->
            series.getData().add(new XYChart.Data<>(day.format(fmt), val.doubleValue()))
        );
        chart.getData().add(series);
        return chartCard("📈 Daily Revenue Trend", chart);
    }

    private VBox buildCategoryRevenueChart(LocalDate s, LocalDate e) {
        PieChart chart = new PieChart();
        chart.setLegendSide(javafx.geometry.Side.RIGHT);
        svc.getCategoryAnalysis(s, e).forEach(row -> {
            BigDecimal rev = (BigDecimal) row.get("revenue");
            if (rev != null && rev.compareTo(BigDecimal.ZERO) > 0)
                chart.getData().add(new PieChart.Data(
                    (String) row.get("name") + " (R" + String.format("%.0f", rev.doubleValue()) + ")",
                    rev.doubleValue()
                ));
        });
        return chartCard("🎯 Revenue by Category", chart);
    }

    private VBox buildPaymentMethodChart(LocalDate s, LocalDate e) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getRevenueByPaymentMethod(s, e)
            .forEach((method, val) -> series.getData().add(new XYChart.Data<>(method, val.doubleValue())));
        chart.getData().add(series);
        return chartCard("💳 Revenue by Payment Method", chart);
    }

    private VBox buildMonthlyRevenueTrend() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getMonthlyRevenue(LocalDate.now().getYear()).forEach((ym, val) ->
            series.getData().add(new XYChart.Data<>(
                ym.getMonth().toString().substring(0, 3),
                val.doubleValue()
            ))
        );
        chart.getData().add(series);
        return chartCard("📅 Monthly Revenue (" + LocalDate.now().getYear() + ")", chart);
    }

    private VBox buildHourlySalesChart(LocalDate s, LocalDate e) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Hour");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getHourlyRevenue(s, e).forEach((hr, val) ->
            series.getData().add(new XYChart.Data<>(String.format("%02d:00", hr), val.doubleValue()))
        );
        chart.getData().add(series);
        return chartCard("⏰ Revenue by Hour of Day", chart);
    }

    private VBox buildDayOfWeekChart(LocalDate s, LocalDate e) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getRevenueByDayOfWeek(s, e).forEach((day, val) ->
            series.getData().add(new XYChart.Data<>(day.substring(0, 3), val.doubleValue()))
        );
        chart.getData().add(series);
        return chartCard("📅 Revenue by Day of Week", chart);
    }

    private VBox buildSaleValueDistChart(LocalDate s, LocalDate e) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Sale Value Range");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Count");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getSaleValueDistribution(s, e)
            .forEach((range, cnt) -> series.getData().add(new XYChart.Data<>(range, cnt)));
        chart.getData().add(series);
        return chartCard("📊 Sale Value Distribution", chart);
    }

    private VBox buildStaffPerformanceChart(LocalDate s, LocalDate e) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelRotation(35);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map<String, Object> row : svc.getStaffPerformance(s, e)) {
            String name = (String) row.get("name");
            BigDecimal rev = (BigDecimal) row.get("revenue");
            String label = name.length() > 14 ? name.substring(0, 14) + "…" : name;
            series.getData().add(new XYChart.Data<>(label, rev.doubleValue()));
        }
        chart.getData().add(series);
        return chartCard("👤 Revenue by Staff Member", chart);
    }

    private VBox buildCustomerSegmentationChart(LocalDate s, LocalDate e) {
        PieChart chart = new PieChart();
        chart.setLegendSide(javafx.geometry.Side.RIGHT);
        svc.getCustomerSegmentation(s, e).forEach((seg, cnt) -> {
            if (cnt > 0) chart.getData().add(new PieChart.Data(seg + " (" + cnt + ")", cnt));
        });
        return chartCard("👥 Customer Segments", chart);
    }

    private VBox buildCustomerAgeGroupChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Customers");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getCustomerAgeGroups().forEach((age, cnt) ->
            series.getData().add(new XYChart.Data<>(age, cnt))
        );
        chart.getData().add(series);
        return chartCard("🎂 Customer Age Groups", chart);
    }

    private VBox buildMonthlyNewCustomersChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("New Customers");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        svc.getMonthlyNewCustomers(LocalDate.now().getYear()).forEach((ym, cnt) ->
            series.getData().add(new XYChart.Data<>(ym.getMonth().toString().substring(0, 3), cnt))
        );
        chart.getData().add(series);
        return chartCard("📈 Monthly New Customers (" + LocalDate.now().getYear() + ")", chart);
    }

    private VBox buildReturnsStatusChart(Map<String, Integer> data) {
        PieChart chart = new PieChart();
        data.forEach((k, v) -> { if (v > 0) chart.getData().add(new PieChart.Data(k + " (" + v + ")", v)); });
        return chartCard("↩️ Returns by Status", chart);
    }

    private VBox buildExchangeStatusChart(Map<String, Integer> data) {
        PieChart chart = new PieChart();
        data.forEach((k, v) -> { if (v > 0) chart.getData().add(new PieChart.Data(k + " (" + v + ")", v)); });
        return chartCard("🔄 Exchanges by Status", chart);
    }

    private VBox buildReasonsChart(String title, Map<String, Integer> data) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelRotation(35);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Count");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        data.forEach((reason, cnt) -> {
            String lbl = reason.length() > 20 ? reason.substring(0, 20) + "…" : reason;
            series.getData().add(new XYChart.Data<>(lbl, cnt));
        });
        chart.getData().add(series);
        return chartCard(title, chart);
    }

    private VBox buildPromoVsNoPromoChart(List<Map<String, Object>> promos) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (R)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        promos.forEach(row -> {
            String code = (String) row.get("promoCode");
            BigDecimal rev = (BigDecimal) row.get("revenue");
            String lbl = code.length() > 15 ? code.substring(0, 15) + "…" : code;
            series.getData().add(new XYChart.Data<>(lbl, rev.doubleValue()));
        });
        chart.getData().add(series);
        return chartCard("📣 Promo Revenue Breakdown", chart);
    }

    private VBox buildTopProductsTable(LocalDate s, LocalDate e, int limit) {
        TableView<Map<String, Object>> table = baseTable();

        table.getColumns().addAll(
            strCol("Product",   "name",      250),
            strCol("Category",  "category",  120),
            intCol("Units Sold","unitsSold",  90),
            moneyCol("Revenue", "revenue",   110),
            moneyCol("COGS",    "cost",      110),
            moneyCol("Profit",  "profit",    110),
            pctCol("Margin",    "margin",     80)
        );
        table.getItems().setAll(svc.getTopProductsDetailed(s, e, limit));
        return card(table, 350);
    }

    private VBox buildSlowMoversTable(LocalDate s, LocalDate e) {
        TableView<Map<String, Object>> table = baseTable();
        table.getColumns().addAll(
            strCol("Product",      "name",       250),
            strCol("Category",     "category",   120),
            intCol("Stock",        "stock",        80),
            intCol("Sold (Period)","unitsSold",    90),
            moneyCol("Revenue",    "revenue",     110),
            moneyCol("Cost Price", "costPrice",   110)
        );
        table.getItems().setAll(svc.getSlowMovers(s, e));
        return card(table, 300);
    }

    private VBox buildStaffSalesTable(LocalDate s, LocalDate e) {
        TableView<Map<String, Object>> table = baseTable();
        table.getColumns().addAll(
            strCol("Staff",         "name",        180),
            strCol("Role",          "role",         90),
            intCol("Sales",         "salesCount",   70),
            moneyCol("Revenue",     "revenue",      110),
            moneyCol("Profit",      "profit",       110),
            moneyCol("Avg Sale",    "avgSaleValue", 110),
            dblCol("Avg Hrs/Shift", "avgHoursShift", 110, "%.1f hrs")
        );
        table.getItems().setAll(svc.getStaffPerformance(s, e));
        return card(table, 300);
    }

    private VBox buildStaffAttendanceTable(LocalDate s, LocalDate e) {
        TableView<Map<String, Object>> table = baseTable();
        table.getColumns().addAll(
            strCol("Staff",          "name",          180),
            strCol("Role",           "role",           90),
            intCol("Logins",         "loginCount",     70),
            dblCol("Avg Duration",   "avgDurationMins",100, "%.0f mins"),
            dblCol("Total Hours",    "totalHours",     100, "%.1f hrs")
        );
        table.getItems().setAll(svc.getStaffAttendance(s, e));
        return card(table, 260);
    }

    private VBox buildStockByCategoryTable() {
        TableView<Map<String, Object>> table = baseTable();
        table.getColumns().addAll(
            strCol("Category",    "category",    160),
            intCol("Products",    "productCount", 80),
            intCol("Total Stock", "totalStock",   100),
            moneyCol("Stock Value","stockValue",  120),
            intCol("Out of Stock","outOfStock",    90),
            intCol("Low Stock",   "lowStock",      90)
        );
        table.getItems().setAll(svc.getStockByCategory());
        return card(table, 300);
    }

    private VBox buildInventoryHealthTable(LocalDate s, LocalDate e) {
        TableView<Map<String, Object>> table = baseTable();

        TableColumn<Map<String, Object>, String> coverCol = new TableColumn<>("Days Cover");
        coverCol.setPrefWidth(90);
        coverCol.setCellValueFactory(cd -> {
            int dc = (int) cd.getValue().get("daysCover");
            return new javafx.beans.property.SimpleStringProperty(dc >= 9999 ? "No sales" : String.valueOf(dc));
        });
        coverCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if ("No sales".equals(item)) {
                    setStyle("-fx-text-fill: #64748b;");
                } else {
                    int dc = Integer.parseInt(item);
                    if      (dc <= 7)  setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    else if (dc <= 14) setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                    else               setStyle("-fx-text-fill: #16a34a;");
                }
            }
        });

        table.getColumns().addAll(
            strCol("Product",    "name",      230),
            strCol("Category",   "category",  120),
            intCol("Stock",      "stock",      70),
            moneyCol("Cost Price","costPrice", 100),
            intCol("Sold",       "unitsSold",  70),
            dblCol("Daily Avg",  "dailyAvg",   80, "%.1f"),
            coverCol
        );
        table.getItems().setAll(svc.getInventoryHealthReport(s, e));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                int dc = (int) item.get("daysCover");
                if (dc <= 7 && dc < 9999) setStyle("-fx-background-color: #fff1f2;");
                else if (dc <= 14)        setStyle("-fx-background-color: #fffbeb;");
                else                      setStyle("");
            }
        });
        return card(table, 400);
    }

    private VBox buildRestockHistoryTable(LocalDate s, LocalDate e) {
        TableView<Map<String, Object>> table = baseTable();

        TableColumn<Map<String, Object>, String> dateCol = new TableColumn<>("Date");
        dateCol.setPrefWidth(120);
        dateCol.setCellValueFactory(cd -> {
            Timestamp ts = (Timestamp) cd.getValue().get("date");
            String val = ts != null ? ts.toLocalDateTime().toLocalDate().toString() : "";
            return new javafx.beans.property.SimpleStringProperty(val);
        });

        table.getColumns().addAll(
            dateCol,
            strCol("Product",      "product",     200),
            strCol("Category",     "category",    120),
            intCol("Qty Added",    "added",         80),
            intCol("Stock After",  "remaining",     90),
            strCol("Restocked By", "restockedBy",  150)
        );
        table.getItems().setAll(svc.getRestockHistory(s, e));
        return card(table, 300);
    }

    private VBox buildPromoTable(List<Map<String, Object>> promos) {
        TableView<Map<String, Object>> table = baseTable();
        table.getColumns().addAll(
            strCol("Promo Code",    "promoCode",    120),
            intCol("Sales",         "salesCount",    80),
            intCol("Units Sold",    "unitsSold",     90),
            moneyCol("Revenue",     "revenue",       120),
            moneyCol("Avg Sale",    "avgSaleValue",  120)
        );
        table.getItems().setAll(promos);
        return card(table, 280);
    }

    private VBox buildMarketingCampaignsTable(List<Map<String, Object>> campaigns) {
        TableView<Map<String, Object>> table = baseTable();

        TableColumn<Map<String, Object>, String> dateCol = new TableColumn<>("Sent At");
        dateCol.setPrefWidth(110);
        dateCol.setCellValueFactory(cd -> {
            Timestamp ts = (Timestamp) cd.getValue().get("sentAt");
            String val = ts != null ? ts.toLocalDateTime().toLocalDate().toString() : "";
            return new javafx.beans.property.SimpleStringProperty(val);
        });

        table.getColumns().addAll(
            dateCol,
            strCol("Subject",       "subject",      250),
            strCol("Sent By",       "sentBy",       130),
            intCol("Recipients",    "recipients",    90),
            intCol("Delivered",     "successCount",  90),
            dblCol("Delivery Rate", "deliveryRate",  100, "%.1f%%")
        );
        table.getItems().setAll(campaigns);
        return card(table, 280);
    }

    private <T> TableView<T> baseTable() {
        TableView<T> t = new TableView<>();
        t.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        t.setStyle("-fx-font-size: 12;");
        Label ph = new Label("No data for the selected period.");
        ph.setTextFill(Color.web("#94a3b8"));
        t.setPlaceholder(ph);
        return t;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<Map<String, Object>, String> strCol(String header, String key, double width) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                cd.getValue().get(key) != null ? cd.getValue().get(key).toString() : ""
            )
        );
        return col;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<Map<String, Object>, String> intCol(String header, String key, double width) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        col.setCellValueFactory(cd -> {
            Object v = cd.getValue().get(key);
            return new javafx.beans.property.SimpleStringProperty(v != null ? v.toString() : "0");
        });
        return col;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<Map<String, Object>, String> moneyCol(String header, String key, double width) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        col.setCellValueFactory(cd -> {
            Object v = cd.getValue().get(key);
            BigDecimal bd = (v instanceof BigDecimal b) ? b
                          : (v instanceof Number n)     ? BigDecimal.valueOf(n.doubleValue())
                          : BigDecimal.ZERO;
            return new javafx.beans.property.SimpleStringProperty("R " + String.format("%,.2f", bd.doubleValue()));
        });
        return col;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<Map<String, Object>, String> pctCol(String header, String key, double width) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        col.setCellValueFactory(cd -> {
            Object v = cd.getValue().get(key);
            double d = (v instanceof Number n) ? n.doubleValue() : 0.0;
            return new javafx.beans.property.SimpleStringProperty(String.format("%.1f%%", d));
        });
        return col;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<Map<String, Object>, String> dblCol(String header, String key, double width, String fmt) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        col.setCellValueFactory(cd -> {
            Object v = cd.getValue().get(key);
            double d = (v instanceof Number n) ? n.doubleValue() : 0.0;
            return new javafx.beans.property.SimpleStringProperty(String.format(fmt, d));
        });
        return col;
    }

    private VBox kpiCard(String title, String value, double growth, String color, String sub) {
        return kpiCard(title, value, growth, color, sub, null);
    }

    private VBox kpiCard(String title, String value, double growth, String color, String sub, Runnable drill) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        String base =
            "-fx-background-color: white; -fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 3);";
        card.setStyle(base);

        if (drill != null) {
            card.setStyle(base + "-fx-cursor: hand;");
            card.setOnMouseEntered(e -> card.setStyle(base +
                "-fx-cursor: hand; -fx-border-color: " + color + "; -fx-border-radius: 10; -fx-border-width: 1.5;"));
            card.setOnMouseExited(e -> card.setStyle(base + "-fx-cursor: hand;"));
            card.setOnMouseClicked(e -> drill.run());
        }

        Label t = new Label(title);
        t.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        t.setTextFill(Color.web("#64748b"));

        Label v = new Label(value);
        v.setFont(Font.font("System", FontWeight.BOLD, 28));
        v.setTextFill(Color.web(color));
        v.setWrapText(true);

        HBox bottom = new HBox(6);
        bottom.setAlignment(Pos.CENTER_LEFT);
        if (growth != 0) {
            String arrow = growth > 0 ? "↑" : "↓";
            String gc    = growth > 0 ? C_GREEN : C_RED;
            Label gl = new Label(arrow + " " + String.format("%.1f%%", Math.abs(growth)));
            gl.setFont(Font.font("System", FontWeight.BOLD, 11));
            gl.setTextFill(Color.web(gc));
            gl.setStyle("-fx-background-color: " + gc + "18; -fx-padding: 2 7; -fx-background-radius: 4;");
            bottom.getChildren().add(gl);
        }
        Label sl = new Label(sub);
        sl.setFont(Font.font("System", 11));
        sl.setTextFill(Color.web("#94a3b8"));
        bottom.getChildren().add(sl);

        card.getChildren().addAll(t, v, bottom);
        return card;
    }

    private GridPane kpiGrid(int cols) {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(16);
        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setFillWidth(true);
            g.getColumnConstraints().add(cc);
        }
        return g;
    }

    private GridPane chartGrid(int cols) {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(16);
        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setFillWidth(true);
            g.getColumnConstraints().add(cc);
        }
        return g;
    }

    private VBox chartCard(String title, Chart chart) {
        VBox c = new VBox(12);
        c.setPadding(new Insets(20));
        c.setStyle(
            "-fx-background-color: white; -fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 3);"
        );
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        lbl.setTextFill(Color.web("#1e293b"));

        if (chartIsEmpty(chart)) {
            c.getChildren().addAll(lbl, emptyState("No data in this date range."));
        } else {
            instrumentChart(chart);
            chart.setMinHeight(280);
            VBox.setVgrow(chart, Priority.ALWAYS);
            c.getChildren().addAll(lbl, chart);
        }
        return c;
    }

    private static boolean chartIsEmpty(Chart chart) {
        if (chart instanceof PieChart pie) return pie.getData().isEmpty();
        if (chart instanceof XYChart<?, ?> xy)
            return xy.getData().stream().allMatch(s -> s.getData().isEmpty());
        return false;
    }

    /** Turns off the re-animate-on-every-render churn and adds hover tooltips
     *  showing the exact value behind each bar / point / slice. */
    private void instrumentChart(Chart chart) {
        chart.setAnimated(false);
        if (chart instanceof PieChart pie) {
            double total = pie.getData().stream().mapToDouble(PieChart.Data::getPieValue).sum();
            for (PieChart.Data d : pie.getData()) {
                double pct = total > 0 ? d.getPieValue() / total * 100 : 0;
                tip(d.nodeProperty(), () -> d.getName() + "  ·  " + fmtNum(d.getPieValue())
                        + String.format("  (%.1f%%)", pct));
            }
        } else if (chart instanceof XYChart<?, ?> xy) {
            for (XYChart.Series<?, ?> s : xy.getData()) {
                for (XYChart.Data<?, ?> d : s.getData()) {
                    double y = (d.getYValue() instanceof Number n) ? n.doubleValue() : 0;
                    String x = String.valueOf(d.getXValue());
                    tip(d.nodeProperty(), () -> x + "  ·  " + fmtNum(y));
                }
            }
        }
    }

    private void tip(javafx.beans.value.ObservableValue<? extends javafx.scene.Node> nodeProp,
                     java.util.function.Supplier<String> text) {
        Runnable apply = () -> {
            javafx.scene.Node n = nodeProp.getValue();
            if (n == null) return;
            Tooltip t = new Tooltip(text.get());
            t.setShowDelay(javafx.util.Duration.millis(120));
            Tooltip.install(n, t);
        };
        if (nodeProp.getValue() != null) apply.run();
        else nodeProp.addListener((o, a, b) -> { if (b != null) apply.run(); });
    }

    private static String fmtNum(double v) {
        return (v == Math.rint(v) && Math.abs(v) < 1e15)
            ? String.format("%,d", (long) v)
            : String.format("%,.2f", v);
    }

    private VBox emptyState(String msg) {
        VBox b = new VBox(8);
        b.setAlignment(Pos.CENTER);
        b.setMinHeight(120);
        b.setPadding(new Insets(28));
        Label icon = new Label("📭");
        icon.setFont(Font.font(26));
        Label m = new Label(msg);
        m.setTextFill(Color.web("#94a3b8"));
        m.setFont(Font.font("System", 13));
        m.setWrapText(true);
        b.getChildren().addAll(icon, m);
        return b;
    }

    private VBox card(javafx.scene.Node content, double minHeight) {
        VBox c = new VBox(12);
        c.setPadding(new Insets(20));
        c.setMinHeight(minHeight);
        c.setStyle(
            "-fx-background-color: white; -fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 3);"
        );
        VBox.setVgrow(content, Priority.ALWAYS);
        c.getChildren().add(content);
        return c;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 17));
        l.setTextFill(Color.web("#0f172a"));
        l.setPadding(new Insets(10, 0, 4, 0));
        return l;
    }

    private Button pill(String text, String color) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: " + color + "; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13; -fx-padding: 9 18;" +
            "-fx-background-radius: 8; -fx-cursor: hand;"
        );
        return b;
    }

    private Button chip(String text) {
        Button b = new Button(text);
        b.setStyle(
            "-fx-background-color: #f1f5f9; -fx-text-fill: #475569;" +
            "-fx-font-size: 12; -fx-padding: 5 12; -fx-background-radius: 16;" +
            "-fx-cursor: hand; -fx-border-color: #cbd5e1; -fx-border-radius: 16;"
        );
        b.setOnMouseEntered(ev -> b.setStyle(
            "-fx-background-color: #e2e8f0; -fx-text-fill: #1e293b;" +
            "-fx-font-size: 12; -fx-padding: 5 12; -fx-background-radius: 16;" +
            "-fx-cursor: hand; -fx-border-color: #94a3b8; -fx-border-radius: 16;"
        ));
        b.setOnMouseExited(ev -> b.setStyle(
            "-fx-background-color: #f1f5f9; -fx-text-fill: #475569;" +
            "-fx-font-size: 12; -fx-padding: 5 12; -fx-background-radius: 16;" +
            "-fx-cursor: hand; -fx-border-color: #cbd5e1; -fx-border-radius: 16;"
        ));
        return b;
    }

    private Label label(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        l.setTextFill(Color.web("#374151"));
        return l;
    }

    private Separator sep() {
        return new Separator(javafx.geometry.Orientation.VERTICAL);
    }

    private static String fmtR(BigDecimal v) {
        return "R " + String.format("%,.2f", v == null ? 0.0 : v.doubleValue());
    }

    private static String fmtPct(double v) {
        return String.format("%.1f%%", v);
    }

    private void doExport() {
        LocalDate s = start(), e = end();
        String name = reportCombo.getValue().replaceAll("\\s+", "_").toLowerCase()
                      + "_" + s + "_to_" + e;
        boolean ok = svc.exportToExcel(s, e, name);
        Alert a = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(ok
            ? "Exported to reports/" + name + ".xlsx"
            : "Export failed — check console for details."
        );
        a.showAndWait();
    }
}
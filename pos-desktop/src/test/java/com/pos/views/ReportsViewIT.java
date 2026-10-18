package com.pos.views;

import com.pos.models.User;
import com.pos.services.ReportService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportsViewIT {
    @Test void reportsRenderWithTheSharedPaletteAndValidDateShortcuts() throws Exception {
        Platform.startup(()->{});Platform.setImplicitExit(false);
        CompletableFuture<Void> result=new CompletableFuture<>();
        Platform.runLater(()->{
            try {
                var user=new User(1,"Test Manager","manager@example.test","Manager","Active");
                BorderPane root=new ReportsView(user,new FixtureReports()).getView();
                Scene scene=new Scene(root,1180,900);
                scene.getStylesheets().add(getClass().getResource("/css/app-theme.css").toExternalForm());
                root.applyCss();root.layout();
                var selector=(ComboBox<String>)root.lookup("#reportSelector");
                for(String report:List.copyOf(selector.getItems())) {
                    selector.setValue(report);root.applyCss();root.layout();
                    assertTrue(((Label)root.lookup("#reportRange")).getText().startsWith(report));
                    assertFalse(root.lookupAll(".chart").stream().anyMatch(n->!n.getStyleClass().contains("report-chart")));
                    if(report.equals("Returns & Exchanges")) {
                        for(var node:root.lookupAll(".chart"))if(node instanceof javafx.scene.chart.BarChart<?,?> chart) {
                            for(var series:chart.getData())for(var item:series.getData()) {
                                String expected=switch(String.valueOf(item.getYValue())) {
                                    case "Approved" -> "0x16a34aff";
                                    case "Pending" -> "0xd97706ff";
                                    case "Rejected" -> "0xdc2626ff";
                                    default -> null;
                                };
                                if(expected!=null)assertEquals(expected,((javafx.scene.layout.Region)item.getNode()).getBackground().getFills().get(0).getFill().toString());
                            }
                        }
                    }
                }
                selector.setValue("Overview");root.applyCss();root.layout();
                var bar=root.lookup(".chart-bar");assertNotNull(bar);
                assertEquals("0x0f766eff",((javafx.scene.layout.Region)bar).getBackground().getFills().get(0).getFill().toString());
                for(String name:List.of("This month","This week")) {
                    root.lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().equals(name)).map(n->(Button)n).findFirst().orElseThrow().fire();
                    LocalDate expected=name.equals("This month")?LocalDate.now().withDayOfMonth(1):LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    assertEquals(expected,((DatePicker)root.lookup("#reportFrom")).getValue());
                }
                selector.setValue("Overview");root.applyCss();root.layout();
                javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",java.nio.file.Path.of("target/reports-overview.png").toFile());
                ((DatePicker)root.lookup("#reportFrom")).setValue(LocalDate.now().plusDays(1));
                ((Button)root.lookup("#reportApply")).fire();
                assertTrue(((Label)root.lookup("#reportRange")).getText().startsWith("Choose"));
                assertTrue(root.lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().startsWith("Export all")).findFirst().orElseThrow().isDisable());
                result.complete(null);
            } catch(Throwable ex){result.completeExceptionally(ex);}
        });
        try {result.get(45,TimeUnit.SECONDS);} finally {Platform.exit();}
    }
    private static class FixtureReports extends ReportService {
        @Override public BigDecimal getTotalRevenue(LocalDate start, LocalDate end) { return new BigDecimal("4000.00"); }
        @Override public BigDecimal getTotalCOGS(LocalDate start, LocalDate end) { return new BigDecimal("4000.00"); }
        @Override public BigDecimal getGrossProfit(LocalDate start, LocalDate end) { return new BigDecimal("1000.00"); }
        @Override public double getGrossProfitMargin(LocalDate start, LocalDate end) { return 25.0; }
        @Override public BigDecimal getNetRevenue(LocalDate start, LocalDate end) { return new BigDecimal("4000.00"); }
        @Override public BigDecimal getTotalApprovedRefunds(LocalDate start, LocalDate end) { return new BigDecimal("4000.00"); }
        @Override public Map<LocalDate, BigDecimal> getDailyRevenue(LocalDate start, LocalDate end) { var days=new java.util.TreeMap<LocalDate,BigDecimal>();days.put(start,new BigDecimal("1250"));days.merge(end,new BigDecimal("2750"),BigDecimal::add);return days; }
        @Override public Map<String, BigDecimal> getRevenueByPaymentMethod(LocalDate start, LocalDate end) { return Map.of("Cash",new BigDecimal("1250"),"Card",new BigDecimal("2750")); }
        @Override public Map<YearMonth, BigDecimal> getMonthlyRevenue(int year) { return Map.of(); }
        @Override public Map<Integer, BigDecimal> getHourlyRevenue(LocalDate start, LocalDate end) { return Map.of(9,new BigDecimal("1250"),10,new BigDecimal("2750")); }
        @Override public Map<String, BigDecimal> getRevenueByDayOfWeek(LocalDate start, LocalDate end) { return Map.of("Monday",new BigDecimal("1250")); }
        @Override public int getTotalSales(LocalDate start, LocalDate end) { return 20; }
        @Override public BigDecimal getAverageSaleValue(LocalDate start, LocalDate end) { return new BigDecimal("200.00"); }
        @Override public Map<String, Integer> getSaleValueDistribution(LocalDate start, LocalDate end) { return Map.of(); }
        @Override public List<Map<String, Object>> getTopProductsDetailed(LocalDate start, LocalDate end, int limit) { return List.of(); }
        @Override public List<Map<String, Object>> getSlowMovers(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getCategoryAnalysis(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getStaffPerformance(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getStaffAttendance(LocalDate start, LocalDate end) { return List.of(); }
        @Override public int getTotalStaff() { return 20; }
        @Override public String getTopPerformerByRevenue(LocalDate start, LocalDate end) { return "Jane"; }
        @Override public BigDecimal getAverageRevenuePerStaff(LocalDate start, LocalDate end) { return new BigDecimal("4000.00"); }
        @Override public int getTotalCustomers() { return 20; }
        @Override public int getNewCustomers(LocalDate start, LocalDate end) { return 20; }
        @Override public double getRepeatCustomerRate(LocalDate start, LocalDate end) { return 25.0; }
        @Override public Map<String, Integer> getCustomerSegmentation(LocalDate start, LocalDate end) { return Map.of("New",10,"Returning",5); }
        @Override public Map<String, Integer> getCustomerAgeGroups() { return Map.of(); }
        @Override public Map<YearMonth, Integer> getMonthlyNewCustomers(int year) { return Map.of(); }
        @Override public Map<String, Object> getReturnsSummary(LocalDate start, LocalDate end) { return Map.of("countByStatus", Map.of("Approved",3,"Pending",2,"Rejected",1),"refundByStatus",Map.of("Approved",new BigDecimal("75")),"returnRate",2.5,"topReasons",Map.of("Damaged",3)); }
        @Override public Map<String, Object> getExchangesSummary(LocalDate start, LocalDate end) { return Map.of("countByStatus", Map.of("Approved",3,"Pending",2),"topReasons",Map.of("Wrong size",2)); }
        @Override public int getTotalProducts() { return 12; }
        @Override public int getTotalStockUnits() { return 20; }
        @Override public BigDecimal getTotalStockValue() { return new BigDecimal("4000.00"); }
        @Override public int getLowStockCount() { return 2; }
        @Override public int getOutOfStockCount() { return 1; }
        @Override public List<Map<String, Object>> getInventoryHealthReport(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getStockByCategory() { return List.of(); }
        @Override public List<Map<String, Object>> getRestockHistory(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getPromoPerformance(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getMarketingCampaigns(LocalDate start, LocalDate end) { return List.of(); }
        @Override public List<Map<String, Object>> getSessionAnalytics(LocalDate start, LocalDate end) { return List.of(); }
        @Override public boolean exportToExcel(LocalDate start, LocalDate end, String fileName) { throw new AssertionError("Export must not run with invalid dates"); }
    }
}

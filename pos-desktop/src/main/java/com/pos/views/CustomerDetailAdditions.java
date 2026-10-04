package com.pos.views;

/**
 * CustomerView — ADDITIONS for exchange/return history in customer details.
 *
 * HOW TO INTEGRATE:
 *   In your existing CustomerView.showCustomerDetails() method, after the
 *   purchases TableView section, add the two panels below using:
 *
 *       VBox exchangeHistory = CustomerDetailAdditions.buildExchangeHistory(customer.getAccountID());
 *       VBox returnHistory   = CustomerDetailAdditions.buildReturnHistory(customer.getAccountID());
 *       subRoot.getChildren().addAll(exchangeHistory, returnHistory);
 *
 * Both panels are self-contained — they query the database and render
 * a table of past exchanges/returns for the selected customer.
 */

import com.pos.database.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CustomerDetailAdditions {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // ── Exchange History ───────────────────────────────────────────────────────

    public static VBox buildExchangeHistory(int accountID) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        box.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);"
        );

        Label title = new Label("Exchange History");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setTextFill(Color.web("#0f766e"));

        TableView<ExchangeRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(180);
        table.setPlaceholder(new Label("No exchanges on record."));

        TableColumn<ExchangeRow, String> dateCol = col("Date", "date", 140);
        TableColumn<ExchangeRow, String> origCol = col("Original Product", "originalProduct", 0);
        TableColumn<ExchangeRow, String> newCol  = col("Exchanged For", "newProduct", 0);
        TableColumn<ExchangeRow, Void> diffCol   = new TableColumn<>("Price Diff");
        diffCol.setPrefWidth(100);
        diffCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); setStyle(""); return; }
                ExchangeRow row = getTableView().getItems().get(getIndex());
                BigDecimal diff = row.getDiff();
                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                setText(sign + "R" + String.format("%.2f", diff));
                setStyle(diff.compareTo(BigDecimal.ZERO) > 0
                    ? "-fx-text-fill: #dc2626; -fx-font-weight: bold;"
                    : diff.compareTo(BigDecimal.ZERO) < 0
                        ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;"
                        : "-fx-text-fill: #64748b;");
            }
        });

        TableColumn<ExchangeRow, String> statusCol = col("Status", "status", 90);
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "Approved" -> "-fx-text-fill: #155724; -fx-font-weight: bold;";
                    case "Rejected" -> "-fx-text-fill: #721c24; -fx-font-weight: bold;";
                    default         -> "-fx-text-fill: #856404; -fx-font-weight: bold;";
                });
            }
        });

        TableColumn<ExchangeRow, String> reasonCol = col("Reason", "reason", 0);
        TableColumn<ExchangeRow, String> staffCol  = col("Handled By", "staffName", 120);

        table.getColumns().addAll(dateCol, origCol, newCol, diffCol, statusCol, reasonCol, staffCol);
        table.setItems(loadExchanges(accountID));

        box.getChildren().addAll(title, table);
        return box;
    }

    private static ObservableList<ExchangeRow> loadExchanges(int accountID) {
        ObservableList<ExchangeRow> list = FXCollections.observableArrayList();
        String sql = """
            SELECT e.ExchangeDate, p1.ProductName AS OrigProd, p2.ProductName AS NewProd,
                   t1.SalePrice AS OrigPrice, COALESCE(e.NewPrice, t1.SalePrice) AS NewPrice,
                   e.Status, e.Reason, s.FullNames AS StaffName
            FROM Exchanges e
            JOIN Transactions t1 ON e.OriginalTransactionID = t1.TransactionID
            JOIN Account a ON t1.AccountID = a.AccountID
            JOIN Product p1 ON t1.ProductID = p1.ProductID
            LEFT JOIN Product p2 ON e.NewProductID = p2.ProductID
            JOIN Staff s ON e.StaffID = s.StaffID
            WHERE a.AccountID = ?
            ORDER BY e.ExchangeDate DESC
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal orig = rs.getBigDecimal("OrigPrice");
                BigDecimal nw   = rs.getBigDecimal("NewPrice");
                list.add(new ExchangeRow(
                    rs.getTimestamp("ExchangeDate").toLocalDateTime().format(FMT),
                    rs.getString("OrigProd"),
                    rs.getString("NewProd") != null ? rs.getString("NewProd") : "—",
                    nw.subtract(orig),
                    rs.getString("Status"),
                    rs.getString("Reason") != null ? rs.getString("Reason") : "",
                    rs.getString("StaffName")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ── Return History ────────────────────────────────────────────────────────

    public static VBox buildReturnHistory(int accountID) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        box.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);"
        );

        Label title = new Label("↩️ Return History");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setTextFill(Color.web("#0f766e"));

        TableView<ReturnRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(180);
        table.setPlaceholder(new Label("No returns on record."));

        TableColumn<ReturnRow, String> dateCol    = col("Date", "date", 140);
        TableColumn<ReturnRow, String> productCol = col("Product", "productName", 0);
        TableColumn<ReturnRow, String> qtyCol     = col("Qty", "quantity", 60);
        TableColumn<ReturnRow, String> refundCol  = col("Refund", "refundAmount", 90);
        TableColumn<ReturnRow, String> statusCol  = col("Status", "status", 90);
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "Approved" -> "-fx-text-fill: #155724; -fx-font-weight: bold;";
                    case "Rejected" -> "-fx-text-fill: #721c24; -fx-font-weight: bold;";
                    default         -> "-fx-text-fill: #856404; -fx-font-weight: bold;";
                });
            }
        });
        TableColumn<ReturnRow, String> reasonCol = col("Reason", "reason", 0);
        TableColumn<ReturnRow, String> staffCol  = col("Handled By", "staffName", 120);

        table.getColumns().addAll(dateCol, productCol, qtyCol, refundCol, statusCol, reasonCol, staffCol);
        table.setItems(loadReturns(accountID));

        box.getChildren().addAll(title, table);
        return box;
    }

    private static ObservableList<ReturnRow> loadReturns(int accountID) {
        ObservableList<ReturnRow> list = FXCollections.observableArrayList();
        String sql = """
            SELECT r.ReturnDate, p.ProductName, r.ReturnQuantity,
                   r.RefundAmount, r.Status, r.Reason, s.FullNames AS StaffName
            FROM Returns r
            JOIN Transactions t ON r.TransactionID = t.TransactionID
            JOIN Account a ON t.AccountID = a.AccountID
            JOIN Product p ON t.ProductID = p.ProductID
            JOIN Staff s ON r.StaffID = s.StaffID
            WHERE a.AccountID = ?
            ORDER BY r.ReturnDate DESC
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new ReturnRow(
                    rs.getTimestamp("ReturnDate").toLocalDateTime().format(FMT),
                    rs.getString("ProductName"),
                    String.valueOf(rs.getInt("ReturnQuantity")),
                    "R" + String.format("%.2f", rs.getBigDecimal("RefundAmount")),
                    rs.getString("Status"),
                    rs.getString("Reason") != null ? rs.getString("Reason") : "",
                    rs.getString("StaffName")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ── Row models ────────────────────────────────────────────────────────────

    public static class ExchangeRow {
        private final String date, originalProduct, newProduct, status, reason, staffName;
        private final BigDecimal diff;

        public ExchangeRow(String date, String originalProduct, String newProduct,
                           BigDecimal diff, String status, String reason, String staffName) {
            this.date = date; this.originalProduct = originalProduct;
            this.newProduct = newProduct; this.diff = diff;
            this.status = status; this.reason = reason; this.staffName = staffName;
        }
        public String getDate()            { return date; }
        public String getOriginalProduct() { return originalProduct; }
        public String getNewProduct()      { return newProduct; }
        public BigDecimal getDiff()        { return diff; }
        public String getStatus()          { return status; }
        public String getReason()          { return reason; }
        public String getStaffName()       { return staffName; }
    }

    public static class ReturnRow {
        private final String date, productName, quantity, refundAmount, status, reason, staffName;

        public ReturnRow(String date, String productName, String quantity,
                         String refundAmount, String status, String reason, String staffName) {
            this.date = date; this.productName = productName; this.quantity = quantity;
            this.refundAmount = refundAmount; this.status = status;
            this.reason = reason; this.staffName = staffName;
        }
        public String getDate()         { return date; }
        public String getProductName()  { return productName; }
        public String getQuantity()     { return quantity; }
        public String getRefundAmount() { return refundAmount; }
        public String getStatus()       { return status; }
        public String getReason()       { return reason; }
        public String getStaffName()    { return staffName; }
    }

    // ── Column helper ─────────────────────────────────────────────────────────

    private static <T> TableColumn<T, String> col(String header, String property, double width) {
        TableColumn<T, String> col = new TableColumn<>(header);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        if (width > 0) col.setPrefWidth(width);
        return col;
    }
}

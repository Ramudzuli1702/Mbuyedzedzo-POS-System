package com.pos.views;

import com.pos.models.User;
import com.pos.services.ExchangeReturnService;
import com.pos.services.ExchangeReturnService.ExchangeRequest;
import com.pos.services.ExchangeReturnService.ReturnRequest;
import com.pos.services.SessionService;
import com.pos.utils.ReceiptGenerator;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class SupervisorApprovalView {

    private final User currentUser;
    private final ExchangeReturnService exchangeReturnService;
    private final SessionService sessionService;
    private TableView<ExchangeRequest> exchangeTable;
    private TableView<ReturnRequest> returnTable;

    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public SupervisorApprovalView(User user) {
        this.currentUser         = user;
        this.exchangeReturnService = new ExchangeReturnService();
        this.sessionService      = new SessionService();
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());

        HBox body = new HBox(20);
        body.setPadding(new Insets(20));

        VBox exchangePanel = createExchangePanel();
        HBox.setHgrow(exchangePanel, Priority.ALWAYS);

        VBox returnPanel = createReturnPanel();
        HBox.setHgrow(returnPanel, Priority.ALWAYS);

        body.getChildren().addAll(exchangePanel, returnPanel);
        layout.setCenter(body);

        refreshData();
        return layout;
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("✅ Supervisor Approval Portal");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setStyle(
            "-fx-background-color: #3498db; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;"
        );
        refreshBtn.setOnAction(e -> refreshData());

        topBar.getChildren().addAll(title, spacer, refreshBtn);
        return topBar;
    }

    private VBox createExchangePanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        VBox.setVgrow(panel, Priority.ALWAYS);
        panel.setStyle(
            "-fx-background-color: white; -fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);"
        );

        Label title = new Label("🔄 Pending Exchanges");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        exchangeTable = new TableView<>();
        exchangeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        exchangeTable.setPlaceholder(new Label("No pending exchanges."));
        VBox.setVgrow(exchangeTable, Priority.ALWAYS);

        TableColumn<ExchangeRequest, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("exchangeID"));
        idCol.setPrefWidth(50);

        TableColumn<ExchangeRequest, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("exchangeDate"));
        dateCol.setPrefWidth(135);
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DT_FMT));
            }
        });

        TableColumn<ExchangeRequest, String> originalCol = new TableColumn<>("Original Item");
        originalCol.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<ExchangeRequest, String> newCol = new TableColumn<>("New Item");
        newCol.setCellValueFactory(new PropertyValueFactory<>("newProductName"));

        TableColumn<ExchangeRequest, BigDecimal> diffCol = new TableColumn<>("Diff");
        diffCol.setPrefWidth(80);
        diffCol.setCellValueFactory(new PropertyValueFactory<>("newPrice"));
        diffCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal newPrice, boolean empty) {
                super.updateItem(newPrice, empty);
                if (empty || newPrice == null) { setText(null); setStyle(""); return; }
                ExchangeRequest row = getTableView().getItems().get(getIndex());
                BigDecimal diff = newPrice.subtract(row.getOriginalPrice());
                String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                setText(sign + "R" + String.format("%.2f", diff));
                setStyle(diff.compareTo(BigDecimal.ZERO) > 0
                    ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold;"
                    : "-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            }
        });

        TableColumn<ExchangeRequest, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(new PropertyValueFactory<>("reason"));

        TableColumn<ExchangeRequest, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(155);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button approveBtn = new Button("✓ Approve");
            private final Button rejectBtn  = new Button("✗ Reject");
            {
                approveBtn.setStyle(approveStyle());
                rejectBtn.setStyle(rejectStyle());
                approveBtn.setOnAction(e ->
                    handleExchangeApproval(getTableView().getItems().get(getIndex()), true));
                rejectBtn.setOnAction(e ->
                    handleExchangeApproval(getTableView().getItems().get(getIndex()), false));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(5, approveBtn, rejectBtn);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });

        exchangeTable.getColumns().addAll(
            idCol, dateCol, originalCol, newCol, diffCol, reasonCol, actionCol
        );
        panel.getChildren().addAll(title, exchangeTable);
        return panel;
    }

    private VBox createReturnPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        VBox.setVgrow(panel, Priority.ALWAYS);
        panel.setStyle(
            "-fx-background-color: white; -fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);"
        );

        Label title = new Label("↩️ Pending Returns");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        returnTable = new TableView<>();
        returnTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        returnTable.setPlaceholder(new Label("No pending returns."));
        VBox.setVgrow(returnTable, Priority.ALWAYS);

        TableColumn<ReturnRequest, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("returnID"));
        idCol.setPrefWidth(50);

        TableColumn<ReturnRequest, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("returnDate"));
        dateCol.setPrefWidth(135);
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DT_FMT));
            }
        });

        TableColumn<ReturnRequest, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<ReturnRequest, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("returnQuantity"));
        qtyCol.setPrefWidth(50);
        qtyCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<ReturnRequest, BigDecimal> refundCol = new TableColumn<>("Refund");
        refundCol.setCellValueFactory(new PropertyValueFactory<>("refundAmount"));
        refundCol.setPrefWidth(90);
        refundCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText("R " + String.format("%.2f", item));
                setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        });

        TableColumn<ReturnRequest, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(new PropertyValueFactory<>("reason"));

        TableColumn<ReturnRequest, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(155);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button approveBtn = new Button("✓ Approve");
            private final Button rejectBtn  = new Button("✗ Reject");
            {
                approveBtn.setStyle(approveStyle());
                rejectBtn.setStyle(rejectStyle());
                approveBtn.setOnAction(e ->
                    handleReturnApproval(getTableView().getItems().get(getIndex()), true));
                rejectBtn.setOnAction(e ->
                    handleReturnApproval(getTableView().getItems().get(getIndex()), false));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(5, approveBtn, rejectBtn);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });

        returnTable.getColumns().addAll(
            idCol, dateCol, productCol, qtyCol, refundCol, reasonCol, actionCol
        );
        panel.getChildren().addAll(title, returnTable);
        return panel;
    }

    private void handleExchangeApproval(ExchangeRequest exchange, boolean approve) {
        getAuthCode((approve ? "Approve" : "Reject") + " Exchange #" + exchange.getExchangeID())
            .ifPresent(authCode -> {
                if (!sessionService.verifySupervisorCode(currentUser.getStaffID(), authCode)) {
                    showAlert("Error", "Invalid authorization code.", Alert.AlertType.ERROR);
                    return;
                }

                boolean success = approve
                    ? exchangeReturnService.approveExchange(exchange.getExchangeID(), currentUser.getStaffID(), authCode)
                    : exchangeReturnService.rejectExchange(exchange.getExchangeID(), currentUser.getStaffID(), authCode);

                if (success) {
                    if (approve) {
                        String receipt = generateExchangeReceipt(exchange);
                        if (new com.pos.services.SettingsService().getReceiptAutoPrint())
                            ReceiptGenerator.printReceipt(receipt);
                        showAlert("Receipt", receipt, Alert.AlertType.INFORMATION);
                    }
                    showAlert("Success",
                        "Exchange " + (approve ? "approved" : "rejected") + " successfully.",
                        Alert.AlertType.INFORMATION);
                    refreshData();
                } else {
                    showAlert("Error", "Failed to process exchange.", Alert.AlertType.ERROR);
                }
            });
    }

    private void handleReturnApproval(ReturnRequest returnReq, boolean approve) {
        getAuthCode((approve ? "Approve" : "Reject") + " Return #" + returnReq.getReturnID())
            .ifPresent(authCode -> {
                if (!sessionService.verifySupervisorCode(currentUser.getStaffID(), authCode)) {
                    showAlert("Error", "Invalid authorization code.", Alert.AlertType.ERROR);
                    return;
                }

                boolean success = approve
                    ? exchangeReturnService.approveReturn(returnReq.getReturnID(), currentUser.getStaffID(), authCode)
                    : exchangeReturnService.rejectReturn(returnReq.getReturnID(), currentUser.getStaffID(), authCode);

                if (success) {
                    if (approve) {
                        String receipt = generateReturnReceipt(returnReq);
                        if (new com.pos.services.SettingsService().getReceiptAutoPrint())
                            ReceiptGenerator.printReceipt(receipt);
                        showAlert("Receipt", receipt, Alert.AlertType.INFORMATION);
                    }
                    showAlert("Success",
                        "Return " + (approve ? "approved" : "rejected") + " successfully.",
                        Alert.AlertType.INFORMATION);
                    refreshData();
                } else {
                    showAlert("Error", "Failed to process return.", Alert.AlertType.ERROR);
                }
            });
    }

    private Optional<String> getAuthCode(String header) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Authorization Required");
        dialog.setHeaderText(header);
        dialog.setContentText("Enter your 6-digit authorization code:");
        return dialog.showAndWait();
    }

    private String generateExchangeReceipt(ExchangeRequest exchange) {
        BigDecimal diff = exchange.getNewPrice().subtract(exchange.getOriginalPrice());
        return "========================================\n" +
               "         EXCHANGE RECEIPT\n"               +
               "========================================\n\n" +
               "Exchange ID : " + exchange.getExchangeID() + "\n" +
               "Date        : " + LocalDateTime.now().format(DT_FMT) + "\n" +
               "Supervisor  : " + currentUser.getFullNames() + "\n\n" +
               "----------------------------------------\n" +
               "ORIGINAL ITEM:\n" +
               "  " + exchange.getProductName() + "\n" +
               "  Price : R" + String.format("%.2f", exchange.getOriginalPrice()) + "\n\n" +
               "NEW ITEM:\n" +
               "  " + exchange.getNewProductName() + "\n" +
               "  Price : R" + String.format("%.2f", exchange.getNewPrice()) + "\n\n" +
               (diff.compareTo(BigDecimal.ZERO) > 0
                   ? "Top-up Paid : R" + String.format("%.2f", diff) + "\n"
                   : "Same Price Exchange\n") +
               "----------------------------------------\n" +
               "Reason: " + exchange.getReason() + "\n" +
               "========================================\n" +
               "     Thank you for your business!\n"       +
               "========================================\n";
    }

    private String generateReturnReceipt(ReturnRequest r) {
        return "========================================\n" +
               "          RETURN RECEIPT\n"                +
               "========================================\n\n" +
               "Return ID  : " + r.getReturnID() + "\n" +
               "Date       : " + LocalDateTime.now().format(DT_FMT) + "\n" +
               "Supervisor : " + currentUser.getFullNames() + "\n\n" +
               "----------------------------------------\n" +
               "RETURNED ITEM:\n" +
               "  " + r.getProductName() + "\n" +
               "  Quantity      : " + r.getReturnQuantity() + "\n" +
               "  Refund Amount : R" + String.format("%.2f", r.getRefundAmount()) + "\n\n" +
               "Reason: " + r.getReason() + "\n" +
               "----------------------------------------\n" +
               "========================================\n" +
               "     Thank you for your business!\n"       +
               "========================================\n";
    }

    private void refreshData() {
        ObservableList<ExchangeRequest> exchanges = exchangeReturnService.getPendingExchanges();
        exchangeTable.setItems(exchanges);

        ObservableList<ReturnRequest> returns = exchangeReturnService.getPendingReturns();
        returnTable.setItems(returns);
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String approveStyle() {
        return "-fx-background-color: #27ae60; -fx-text-fill: white;" +
               "-fx-font-size: 10; -fx-padding: 5 10; -fx-background-radius: 4; -fx-cursor: hand;";
    }

    private String rejectStyle() {
        return "-fx-background-color: #e74c3c; -fx-text-fill: white;" +
               "-fx-font-size: 10; -fx-padding: 5 10; -fx-background-radius: 4; -fx-cursor: hand;";
    }
}
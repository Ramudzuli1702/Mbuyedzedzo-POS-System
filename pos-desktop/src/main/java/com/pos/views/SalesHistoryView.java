package com.pos.views;

import com.pos.models.Customer;
import com.pos.models.Product;
import com.pos.models.User;
import com.pos.services.CustomerService;
import com.pos.services.ExchangeReturnService;
import com.pos.services.ProductService;
import com.pos.services.TransactionService.PaymentInfo;
import com.pos.utils.ReceiptGenerator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Retail-edition sales history: every retail sale is booked against one
 * shared "Walk-in Customer" account (see CustomerService.getOrCreateWalkInAccount),
 * since Retail has no per-customer accounts at all. Standard already covers
 * this same return/exchange workflow per real customer via CustomerView — this
 * view is the Retail-only equivalent, scoped to that one shared account, so
 * Retail shops can actually look up a past sale and process a return or
 * exchange, which previously had no entry point at all in this edition.
 */
public class SalesHistoryView {
    private final User currentUser;
    private final CustomerService customerService;
    private final ProductService productService;
    private Customer walkIn;
    private TableView<CustomerService.Sale> salesTable;
    private TextField searchField;
    private ObservableList<CustomerService.Sale> allSales = FXCollections.observableArrayList();

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public SalesHistoryView(User user) {
        this.currentUser = user;
        this.customerService = new CustomerService();
        this.productService = new ProductService();
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());

        walkIn = customerService.getOrCreateWalkInAccount(currentUser.getStaffID());

        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));

        if (walkIn == null) {
            mainContent.getChildren().add(
                new Label("Could not load sales history — check the log for details."));
            layout.setCenter(mainContent);
            return layout;
        }

        allSales = customerService.getCustomerPurchases(walkIn.getAccountID());

        mainContent.getChildren().addAll(createSummaryCards(), createSalesTableCard());
        layout.setCenter(mainContent);
        return layout;
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Sales History");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search by sale ID or staff name...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-font-size: 13; -fx-padding: 10;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchSales(newVal));

        topBar.getChildren().addAll(title, spacer, searchField);
        return topBar;
    }

    private void searchSales(String query) {
        if (salesTable == null) return;
        if (query == null || query.trim().isEmpty()) {
            salesTable.setItems(allSales);
            return;
        }
        String q = query.trim().toLowerCase();
        salesTable.setItems(allSales.stream()
                .filter(s -> String.valueOf(s.getSaleID()).contains(q)
                        || (s.getStaffName() != null && s.getStaffName().toLowerCase().contains(q)))
                .collect(Collectors.toCollection(FXCollections::observableArrayList)));
    }

    // ── Summary cards ─────────────────────────────────────────────────────────

    private HBox createSummaryCards() {
        int totalSales = allSales.size();
        double totalRevenue = allSales.stream().mapToDouble(CustomerService.Sale::getTotalAmount).sum();
        java.time.LocalDate today = java.time.LocalDate.now();
        long todaysSales = allSales.stream()
                .filter(s -> s.getSaleDate() != null && s.getSaleDate().toLocalDate().equals(today))
                .count();
        double avgSale = totalSales == 0 ? 0 : totalRevenue / totalSales;

        return com.pos.components.SummaryCards.row(
            new com.pos.components.SummaryCards.Card("Total Sales", String.format("%,d", totalSales), "#0f766e", "all time"),
            new com.pos.components.SummaryCards.Card("Today's Sales", String.valueOf(todaysSales), "#16a34a", today.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))),
            new com.pos.components.SummaryCards.Card("Net Revenue", "R" + String.format("%,.2f", totalRevenue), "#7c3aed", "all time"),
            new com.pos.components.SummaryCards.Card("Average Sale", "R" + String.format("%,.2f", avgSale), "#d97706", "per transaction")
        );
    }

    // ── Sales table ───────────────────────────────────────────────────────────

    private VBox createSalesTableCard() {
        VBox tableBox = new VBox(15);
        tableBox.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");
        tableBox.setPadding(new Insets(20));

        Label tableTitle = new Label("Recent Sales");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        salesTable = buildSalesTable(walkIn);
        salesTable.setItems(allSales);
        VBox.setVgrow(salesTable, Priority.ALWAYS);

        tableBox.getChildren().addAll(tableTitle, salesTable);
        return tableBox;
    }

    private TableView<CustomerService.Sale> buildSalesTable(Customer customer) {
        TableView<CustomerService.Sale> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No sales yet."));

        TableColumn<CustomerService.Sale, Integer> saleIdCol = new TableColumn<>("Sale ID");
        saleIdCol.setCellValueFactory(new PropertyValueFactory<>("saleID"));
        saleIdCol.setPrefWidth(80);

        TableColumn<CustomerService.Sale, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        dateCol.setPrefWidth(150);

        TableColumn<CustomerService.Sale, Integer> qtyCol = new TableColumn<>("Net Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("totalQuantity"));
        qtyCol.setPrefWidth(80);

        TableColumn<CustomerService.Sale, Double> totalCol = new TableColumn<>("Net Amount");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        totalCol.setPrefWidth(100);

        TableColumn<CustomerService.Sale, String> staffCol = new TableColumn<>("Sold By");
        staffCol.setCellValueFactory(new PropertyValueFactory<>("staffName"));
        staffCol.setPrefWidth(150);

        TableColumn<CustomerService.Sale, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(120);
        actionsCol.setCellFactory(param -> new TableCell<>() {
            private final Button detailsBtn = com.pos.components.Ui.actionButton("Details", "#2563eb", "View this sale's items");
            {
                detailsBtn.setOnAction(e -> {
                    CustomerService.Sale sale = getTableView().getItems().get(getIndex());
                    showSaleDetails(sale, customer, salesTable);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : detailsBtn);
            }
        });

        table.getColumns().addAll(saleIdCol, dateCol, qtyCol, totalCol, staffCol, actionsCol);
        return table;
    }

    // ── Sale details window ───────────────────────────────────────────────────

    private void showSaleDetails(CustomerService.Sale sale, Customer customer,
                                  TableView<CustomerService.Sale> salesTable) {
        Stage subStage = new Stage();
        subStage.setTitle("Sale Details — Sale #" + sale.getSaleID());
        subStage.initModality(Modality.WINDOW_MODAL);
        subStage.setResizable(true);

        VBox subRoot = new VBox(15);
        subRoot.setPadding(new Insets(20));
        subRoot.setStyle("-fx-background-color: #f5f7fa;");

        Label summaryLbl = new Label(
                "Date: " + sale.getSaleDate().format(DT_FMT) +
                "   |   Sold By: " + sale.getStaffName() +
                "   |   Net Total: R" + String.format("%.2f", sale.getTotalAmount()));
        summaryLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        subRoot.getChildren().add(summaryLbl);

        TableView<CustomerService.Purchase> itemsTable = new TableView<>();
        itemsTable.setItems(sale.getItems());
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<CustomerService.Purchase, String> prodCol = new TableColumn<>("Product");
        prodCol.setCellValueFactory(new PropertyValueFactory<>("productName"));
        prodCol.setPrefWidth(180);

        TableColumn<CustomerService.Purchase, Integer> qtyCol = new TableColumn<>("Orig Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setPrefWidth(70);

        TableColumn<CustomerService.Purchase, Void> remainingCol = new TableColumn<>("Remaining");
        remainingCol.setPrefWidth(80);
        remainingCol.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); return; }
                CustomerService.Purchase p = getTableView().getItems().get(getIndex());
                setText(String.valueOf(customerService.getRemainingQuantity(p.getTransactionID())));
            }
        });

        TableColumn<CustomerService.Purchase, Double> priceCol = new TableColumn<>("Unit Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("salePrice"));
        priceCol.setPrefWidth(80);

        TableColumn<CustomerService.Purchase, Void> totalCol = new TableColumn<>("Net Line Total");
        totalCol.setPrefWidth(100);
        totalCol.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); return; }
                CustomerService.Purchase p = getTableView().getItems().get(getIndex());
                int rem = customerService.getRemainingQuantity(p.getTransactionID());
                setText("R" + String.format("%.2f", rem * p.getSalePrice()));
            }
        });

        TableColumn<CustomerService.Purchase, String> promoCol = new TableColumn<>("Promo");
        promoCol.setCellValueFactory(new PropertyValueFactory<>("promoCode"));
        promoCol.setPrefWidth(70);

        TableColumn<CustomerService.Purchase, Void> actionCol = new TableColumn<>("Status / Actions");
        actionCol.setPrefWidth(200);
        actionCol.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }

                CustomerService.Purchase purchase = getTableView().getItems().get(getIndex());
                int txID = purchase.getTransactionID();

                ExchangeReturnStatus status = getExchangeReturnStatus(txID);

                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER_LEFT);

                if (status.hasExchange()) {
                    Button exBtn = com.pos.components.Ui.actionButton("Exchanged", "#2563eb", "View exchange details");
                    exBtn.setOnAction(e -> showExchangeInfo(status.exchange));
                    box.getChildren().add(exBtn);
                }

                if (status.hasReturn()) {
                    Button retBtn = com.pos.components.Ui.actionButton("↩️ Returned", "#d97706", "View return details");
                    retBtn.setOnAction(e -> showReturnInfo(status.ret));
                    box.getChildren().add(retBtn);
                }

                int remaining = customerService.getRemainingQuantity(txID);
                if (!status.hasExchange() && !status.hasReturn()) {
                    Button returnBtn = com.pos.components.Ui.actionButton("↩️ Return", "#d97706", "Process a return for this item");
                    returnBtn.setDisable(remaining <= 0);

                    Button exchangeBtn = com.pos.components.Ui.actionButton("Exchange", "#2563eb", "Process an exchange for this item");
                    exchangeBtn.setDisable(remaining <= 0);

                    returnBtn.setOnAction(e -> {
                        handleReturn(purchase, customer, itemsTable, sale, salesTable);
                        itemsTable.refresh();
                    });
                    exchangeBtn.setOnAction(e -> {
                        handleExchange(purchase, customer, itemsTable, sale, salesTable);
                        itemsTable.refresh();
                    });

                    box.getChildren().addAll(returnBtn, exchangeBtn);
                }

                setGraphic(box);
            }
        });

        itemsTable.getColumns().addAll(prodCol, qtyCol, remainingCol, priceCol, totalCol, promoCol, actionCol);
        VBox.setVgrow(itemsTable, Priority.ALWAYS);
        subRoot.getChildren().add(itemsTable);

        Button printBtn = new Button("Print Receipt");
        printBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13;");
        printBtn.setOnAction(e -> printSaleReceipt(sale, customer));

        HBox printBar = new HBox(printBtn);
        printBar.setAlignment(Pos.CENTER_RIGHT);
        printBar.setPadding(new Insets(8, 0, 0, 0));
        subRoot.getChildren().add(printBar);

        ScrollPane scroll = new ScrollPane(subRoot);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f5f7fa; -fx-background-color: #f5f7fa;");

        subStage.setScene(new Scene(scroll, 900, 560));
        subStage.showAndWait();
    }

    // ── Exchange / return info popups ────────────────────────────────────────

    private void showExchangeInfo(ExchangeReturnService.ExchangeRequest ex) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Exchange Details");
        alert.setHeaderText("Exchange #" + ex.getExchangeID());
        alert.setContentText(
                "Original Product : " + ex.getProductName() + "\n" +
                "Exchanged For    : " + (ex.getNewProductName() != null ? ex.getNewProductName() : "—") + "\n" +
                "Original Price   : R" + String.format("%.2f", ex.getOriginalPrice()) + "\n" +
                "New Price        : R" + String.format("%.2f", ex.getNewPrice()) + "\n" +
                "Status           : " + ex.getStatus() + "\n" +
                "Reason           : " + (ex.getReason() != null ? ex.getReason() : "—") + "\n" +
                "Date             : " + ex.getExchangeDate().format(DT_FMT));
        alert.showAndWait();
    }

    private void showReturnInfo(ExchangeReturnService.ReturnRequest ret) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Return Details");
        alert.setHeaderText("Return #" + ret.getReturnID());
        alert.setContentText(
                "Product          : " + ret.getProductName() + "\n" +
                "Return Quantity  : " + ret.getReturnQuantity() + "\n" +
                "Refund Amount    : R" + String.format("%.2f", ret.getRefundAmount()) + "\n" +
                "Status           : " + ret.getStatus() + "\n" +
                "Reason           : " + (ret.getReason() != null ? ret.getReason() : "—") + "\n" +
                "Date             : " + ret.getReturnDate().format(DT_FMT));
        alert.showAndWait();
    }

    // ── Print receipt ─────────────────────────────────────────────────────────

    private void printSaleReceipt(CustomerService.Sale sale, Customer customer) {
        ExchangeReturnService exchangeService = new ExchangeReturnService();
        String receiptText = ReceiptGenerator.generateSaleReceipt(
                sale, customer, customerService, exchangeService);

        ReceiptGenerator.saveReceipt(receiptText, "sale_" + sale.getSaleID());
        ReceiptGenerator.printReceipt(receiptText);
        showAlert("Receipt — Sale #" + sale.getSaleID(), receiptText, Alert.AlertType.INFORMATION);
    }

    // ── Exchange/return status lookup ────────────────────────────────────────

    private ExchangeReturnStatus getExchangeReturnStatus(int transactionID) {
        ExchangeReturnService svc = new ExchangeReturnService();

        ExchangeReturnService.ExchangeRequest exchange = null;
        for (ExchangeReturnService.ExchangeRequest ex : svc.getExchangesForTransaction(transactionID)) {
            exchange = ex;
            break;
        }

        ExchangeReturnService.ReturnRequest ret = null;
        for (ExchangeReturnService.ReturnRequest r : svc.getReturnsForTransaction(transactionID)) {
            ret = r;
            break;
        }

        return new ExchangeReturnStatus(exchange, ret);
    }

    private static class ExchangeReturnStatus {
        final ExchangeReturnService.ExchangeRequest exchange;
        final ExchangeReturnService.ReturnRequest ret;

        ExchangeReturnStatus(ExchangeReturnService.ExchangeRequest exchange,
                             ExchangeReturnService.ReturnRequest ret) {
            this.exchange = exchange;
            this.ret = ret;
        }

        boolean hasExchange() { return exchange != null; }
        boolean hasReturn()   { return ret != null; }
    }

    // ── Handle return ─────────────────────────────────────────────────────────

    private void handleReturn(CustomerService.Purchase purchase, Customer customer,
            TableView<CustomerService.Purchase> itemsTable,
            CustomerService.Sale sale,
            TableView<CustomerService.Sale> salesTable) {
        int transactionID = purchase.getTransactionID();
        int maxQty = customerService.getRemainingQuantity(transactionID);
        if (maxQty <= 0) {
            showAlert("Cannot Return", "No remaining quantity to return.", Alert.AlertType.WARNING);
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Process Return");
        dialog.setHeaderText("Return items from Transaction #" + transactionID);

        ButtonType returnButtonType = new ButtonType("Submit Return Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(returnButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField qtyField  = new TextField("1");
        qtyField.setMaxWidth(100);
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter reason for return...");
        reasonArea.setPrefRowCount(3);

        grid.add(new Label("Return Quantity (max " + maxQty + "):"), 0, 0);
        grid.add(qtyField, 1, 0);
        grid.add(new Label("Reason:"), 0, 1);
        grid.add(reasonArea, 1, 1);

        Label noteLabel = new Label("Note: This return requires supervisor approval");
        noteLabel.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
        grid.add(noteLabel, 0, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == returnButtonType) {
                try {
                    int returnQty = Integer.parseInt(qtyField.getText());
                    if (returnQty > maxQty || returnQty <= 0) {
                        showAlert("Invalid Quantity", "Quantity must be between 1 and " + maxQty, Alert.AlertType.ERROR);
                        return null;
                    }
                    double refund = returnQty * purchase.getSalePrice();
                    String reason = reasonArea.getText().trim();
                    if (customerService.processReturn(transactionID, returnQty, currentUser.getStaffID(),
                            reason.isEmpty() ? "No reason provided" : reason, refund)) {
                        showAlert("Return Requested",
                                "Return request submitted.\nRefund: R" + String.format("%.2f", refund) +
                                "\nStatus: Awaiting supervisor approval",
                                Alert.AlertType.INFORMATION);
                        sale.setItems(customerService.getSaleItems(sale.getSaleID(), customer.getAccountID()));
                        sale.calculateNetTotals(customerService);
                        itemsTable.setItems(sale.getItems());
                        itemsTable.refresh();
                        allSales = customerService.getCustomerPurchases(customer.getAccountID());
                        salesTable.setItems(allSales);
                        salesTable.refresh();
                    } else {
                        showAlert("Error", "Failed to submit return request", Alert.AlertType.ERROR);
                    }
                } catch (NumberFormatException ex) {
                    showAlert("Invalid Input", "Please enter a valid quantity", Alert.AlertType.ERROR);
                }
            }
            return null;
        });
        dialog.showAndWait();
    }

    // ── Handle exchange ───────────────────────────────────────────────────────

    private void handleExchange(CustomerService.Purchase purchase, Customer customer,
            TableView<CustomerService.Purchase> itemsTable,
            CustomerService.Sale sale,
            TableView<CustomerService.Sale> salesTable) {
        int transactionID = purchase.getTransactionID();
        int maxQty = customerService.getRemainingQuantity(transactionID);
        if (maxQty <= 0) {
            showAlert("Cannot Exchange", "No remaining quantity to exchange.", Alert.AlertType.WARNING);
            return;
        }

        BigDecimal originalPrice = BigDecimal.valueOf(purchase.getSalePrice());
        ObservableList<Product> eligibleProducts = productService.getAllProducts().stream()
                .filter(p -> p.getPrice().compareTo(originalPrice) >= 0 && !p.isOutOfStock())
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        if (eligibleProducts.isEmpty()) {
            showAlert("No Products",
                    "No products available with price >= R" + String.format("%.2f", originalPrice),
                    Alert.AlertType.WARNING);
            return;
        }

        Dialog<ExchangeInfo> dialog = new Dialog<>();
        dialog.setTitle("Process Exchange");
        dialog.setHeaderText("Exchange: " + purchase.getProductName() +
                " @ R" + String.format("%.2f", originalPrice));

        ButtonType exchangeButtonType = new ButtonType("Submit Exchange", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exchangeButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField productSearch = new TextField();
        productSearch.setPromptText("Search products...");
        productSearch.setStyle("-fx-font-size: 13; -fx-padding: 8;");

        ComboBox<Product> productCombo = new ComboBox<>(eligibleProducts);
        productCombo.setPromptText("Choose a product...");
        productCombo.setPrefWidth(400);
        productCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getProductName() + " - R" + String.format("%.2f", p.getPrice()));
            }
        });
        productCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getProductName() + " - R" + String.format("%.2f", p.getPrice()));
            }
        });

        productSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                productCombo.setItems(eligibleProducts);
            } else {
                String s = newVal.toLowerCase();
                productCombo.setItems(eligibleProducts.stream()
                        .filter(p -> p.getProductName().toLowerCase().contains(s))
                        .collect(Collectors.toCollection(FXCollections::observableArrayList)));
            }
        });

        Label topUpLabel = new Label("Top-up Required: R 0.00");
        topUpLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        topUpLabel.setTextFill(Color.web("#16a34a"));

        VBox paymentBox = new VBox(10);
        paymentBox.setVisible(false); paymentBox.setManaged(false);

        ToggleGroup paymentGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton("Cash");
        cashRadio.setToggleGroup(paymentGroup); cashRadio.setSelected(true);
        RadioButton cardRadio = new RadioButton("Card");
        cardRadio.setToggleGroup(paymentGroup);

        VBox cashFields = new VBox(10);
        TextField amountField = new TextField();
        amountField.setPromptText("Amount received");
        Label changeLabel = new Label("Change: R 0.00");
        changeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        cashFields.getChildren().addAll(new Label("Amount Received:"), amountField, changeLabel);
        cashRadio.selectedProperty().addListener((obs, old, sel) -> {
            cashFields.setVisible(sel); cashFields.setManaged(sel);
        });
        paymentBox.getChildren().addAll(new Label("Payment Method:"), cashRadio, cardRadio, cashFields);

        productCombo.valueProperty().addListener((obs, old, newProduct) -> {
            if (newProduct == null) return;
            BigDecimal topUp = newProduct.getPrice().subtract(originalPrice);
            if (topUp.compareTo(BigDecimal.ZERO) > 0) {
                topUpLabel.setText("Top-up Required: R" + String.format("%.2f", topUp));
                topUpLabel.setTextFill(Color.web("#d97706"));
                paymentBox.setVisible(true); paymentBox.setManaged(true);
                amountField.textProperty().addListener((o, oldAmt, newAmt) -> {
                    try {
                        double change = Double.parseDouble(newAmt) - topUp.doubleValue();
                        if (change >= 0) {
                            changeLabel.setText("Change: R" + String.format("%.2f", change));
                            changeLabel.setTextFill(Color.web("#16a34a"));
                        } else {
                            changeLabel.setText("Insufficient: R" + String.format("%.2f", Math.abs(change)));
                            changeLabel.setTextFill(Color.web("#dc2626"));
                        }
                    } catch (NumberFormatException e) { changeLabel.setText("Change: R 0.00"); }
                });
            } else {
                topUpLabel.setText("Top-up Required: R 0.00 (Same Price)");
                topUpLabel.setTextFill(Color.web("#16a34a"));
                paymentBox.setVisible(false); paymentBox.setManaged(false);
            }
        });

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter reason for exchange...");
        reasonArea.setPrefRowCount(3);

        Label noteLabel = new Label("Note: Exchange requires supervisor approval");
        noteLabel.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");

        content.getChildren().addAll(
                new Label("Select New Product:"), productSearch, productCombo,
                topUpLabel, paymentBox, new Label("Reason:"), reasonArea, noteLabel);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefHeight(600);

        dialog.setResultConverter(button -> {
            if (button == exchangeButtonType) {
                Product newProduct = productCombo.getValue();
                if (newProduct == null) { showAlert("Error", "Please select a product", Alert.AlertType.ERROR); return null; }
                String reason = reasonArea.getText().trim();
                if (reason.isEmpty()) reason = "No reason provided";
                PaymentInfo payment = null;
                BigDecimal topUp = newProduct.getPrice().subtract(originalPrice);
                if (topUp.compareTo(BigDecimal.ZERO) > 0) {
                    String method = cashRadio.isSelected() ? "Cash" : "Card";
                    BigDecimal paid = topUp;
                    BigDecimal change = BigDecimal.ZERO;
                    if (cashRadio.isSelected()) {
                        try {
                            paid = new BigDecimal(amountField.getText());
                            change = paid.subtract(topUp);
                            if (change.compareTo(BigDecimal.ZERO) < 0) {
                                showAlert("Error", "Insufficient payment", Alert.AlertType.ERROR); return null;
                            }
                        } catch (NumberFormatException e) {
                            showAlert("Error", "Invalid amount", Alert.AlertType.ERROR); return null;
                        }
                    }
                    payment = new PaymentInfo(method, paid, change);
                }
                return new ExchangeInfo(newProduct, reason, payment);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(info -> {
            ExchangeReturnService exchangeService = new ExchangeReturnService();
            int exchangeID = exchangeService.requestExchange(
                    transactionID, currentUser.getStaffID(),
                    info.reason, info.newProduct.getProductID(), info.newProduct.getPrice());

            if (exchangeID > 0) {
                String msg = "Exchange request submitted.\n" +
                        "Original: " + purchase.getProductName() + " @ R" + String.format("%.2f", originalPrice) + "\n" +
                        "New: " + info.newProduct.getProductName() + " @ R" + String.format("%.2f", info.newProduct.getPrice()) + "\n";
                if (info.payment != null) {
                    msg += "Top-up: R" + String.format("%.2f", info.newProduct.getPrice().subtract(originalPrice)) + "\n" +
                           "Payment: " + info.payment.getPaymentMethod() + "\n";
                }
                msg += "Status: Awaiting supervisor approval";
                showAlert("Exchange Requested", msg, Alert.AlertType.INFORMATION);

                sale.setItems(customerService.getSaleItems(sale.getSaleID(), customer.getAccountID()));
                sale.calculateNetTotals(customerService);
                itemsTable.setItems(sale.getItems());
                itemsTable.refresh();
                allSales = customerService.getCustomerPurchases(customer.getAccountID());
                salesTable.setItems(allSales);
                salesTable.refresh();
            } else {
                showAlert("Error", "Failed to submit exchange request", Alert.AlertType.ERROR);
            }
        });
    }

    private static class ExchangeInfo {
        Product newProduct;
        String reason;
        PaymentInfo payment;
        ExchangeInfo(Product p, String r, PaymentInfo pay) { newProduct = p; reason = r; payment = pay; }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        com.pos.components.Ui.showAlert(salesTable, title, content, type);
    }
}

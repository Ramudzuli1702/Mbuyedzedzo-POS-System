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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Collectors;

public class CustomerView {
    private User currentUser;
    private CustomerService customerService;
    private ProductService productService;
    private TableView<Customer> customerTable;
    private TextField searchField;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");    public CustomerView(User user) {
        this.currentUser = user;
        this.customerService = new CustomerService();
        this.productService = new ProductService();
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());

        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));
        mainContent.getChildren().add(createCustomerTable());
        layout.setCenter(mainContent);

        return layout;
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("🛍️ Customer Management");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("🔍 Search customers...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-font-size: 13; -fx-padding: 10;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchCustomers(newVal));

        Button addBtn = new Button("+ Add Customer");
        addBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        addBtn.setOnAction(e -> showAddCustomerDialog());

        topBar.getChildren().addAll(title, spacer, searchField, addBtn);
        return topBar;
    }

    // ── Customer table ────────────────────────────────────────────────────────

    private VBox createCustomerTable() {
        VBox tableBox = new VBox(15);
        tableBox.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        tableBox.setPadding(new Insets(20));

        Label tableTitle = new Label("Customer List");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        customerTable = new TableView<>();
        customerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Customer, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("accountID"));
        idCol.setPrefWidth(60);

        TableColumn<Customer, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullNames"));
        nameCol.setPrefWidth(200);

        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("emailAddress"));
        emailCol.setPrefWidth(220);

        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("contactNo"));
        phoneCol.setPrefWidth(150);

        TableColumn<Customer, LocalDate> dobCol = new TableColumn<>("Date of Birth");
        dobCol.setCellValueFactory(new PropertyValueFactory<>("dateOfBirth"));
        dobCol.setPrefWidth(120);

        TableColumn<Customer, Void> purchasesCol = new TableColumn<>("Net Purchases");
        purchasesCol.setPrefWidth(100);
        purchasesCol.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); return; }
                Customer c = getTableView().getItems().get(getIndex());
                setText(String.valueOf(customerService.getCustomerNetPurchaseQuantity(c.getAccountID())));
            }
        });

        TableColumn<Customer, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(150);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn   = new Button("✏️");
            private final Button viewBtn   = new Button("👁️");
            private final Button deleteBtn = new Button("🗑️");
            {
                editBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                viewBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                editBtn.setOnAction(e -> showEditCustomerDialog(getTableView().getItems().get(getIndex())));
                viewBtn.setOnAction(e -> { showCustomerDetails(getTableView().getItems().get(getIndex())); loadCustomers(); });
                deleteBtn.setOnAction(e -> deleteCustomer(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox buttons = new HBox(5, editBtn, viewBtn, deleteBtn);
                buttons.setAlignment(Pos.CENTER);
                setGraphic(buttons);
            }
        });

        customerTable.getColumns().addAll(idCol, nameCol, emailCol, phoneCol, dobCol, purchasesCol, actionCol);
        loadCustomers();
        VBox.setVgrow(customerTable, Priority.ALWAYS);
        tableBox.getChildren().addAll(tableTitle, customerTable);
        return tableBox;
    }

    private void loadCustomers() {
        customerTable.setItems(customerService.getAllCustomers());
        customerTable.refresh();
    }

    private void searchCustomers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            loadCustomers();
        } else {
            customerTable.setItems(customerService.searchCustomers(keyword));
            customerTable.refresh();
        }
    }

    // ── Customer details window ───────────────────────────────────────────────

    private void showCustomerDetails(Customer customer) {
        Stage stage = new Stage();
        stage.setTitle("Customer Details - " + customer.getFullNames());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(true);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f7fa;");

        // Info box
        VBox infoBox = new VBox(10);
        infoBox.setPadding(new Insets(20));
        infoBox.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        String signupStaff = customerService.getStaffName(customer.getStaffID());
        infoBox.getChildren().addAll(
                createLabeledField("Customer ID",      String.valueOf(customer.getAccountID())),
                createLabeledField("Full Name",        customer.getFullNames()),
                createLabeledField("Email",            customer.getEmailAddress()),
                createLabeledField("Phone",            customer.getContactNo()),
                createLabeledField("Date of Birth",    customer.getDateOfBirth().toString()),
                createLabeledField("Signed Up By",     signupStaff),
                createLabeledField("Member Since",     customer.getTimeStamp().toLocalDate().toString()),
                createLabeledField("Total Net Purchases",
                        customerService.getCustomerNetPurchaseQuantity(customer.getAccountID()) + " items"));
        root.setTop(infoBox);

        // Purchases table
        VBox tableBox = new VBox(15);
        tableBox.setPadding(new Insets(20));

        Label purchasesTitle = new Label("Previous Purchases (Grouped by Sale)");
        purchasesTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        tableBox.getChildren().add(purchasesTitle);

        TableView<CustomerService.Sale> salesTable = buildSalesTable(customer);
        ObservableList<CustomerService.Sale> sales = customerService.getCustomerPurchases(customer.getAccountID());
        salesTable.setItems(sales);
        VBox.setVgrow(salesTable, Priority.ALWAYS);
        tableBox.getChildren().add(salesTable);

        root.setCenter(tableBox);

        stage.setScene(new Scene(root, 800, 700));
        stage.showAndWait();
    }

    private TableView<CustomerService.Sale> buildSalesTable(Customer customer) {
        TableView<CustomerService.Sale> salesTable = new TableView<>();
        salesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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
            private final Button detailsBtn = new Button("📋 Details");
            {
                detailsBtn.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white;" +
                        "-fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
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

        salesTable.getColumns().addAll(saleIdCol, dateCol, qtyCol, totalCol, staffCol, actionsCol);
        return salesTable;
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

        // Summary row
        Label summaryLbl = new Label(
                "Date: " + sale.getSaleDate().format(DT_FMT) +
                "   |   Sold By: " + sale.getStaffName() +
                "   |   Net Total: R" + String.format("%.2f", sale.getTotalAmount()));
        summaryLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        subRoot.getChildren().add(summaryLbl);

        // Items table
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

        // ── Actions column — context-aware ───────────────────────────────
        TableColumn<CustomerService.Purchase, Void> actionCol = new TableColumn<>("Status / Actions");
        actionCol.setPrefWidth(200);
        actionCol.setCellFactory(param -> new TableCell<>() {

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }

                CustomerService.Purchase purchase = getTableView().getItems().get(getIndex());
                int txID = purchase.getTransactionID();

                // Look up exchange / return status for this transaction
                ExchangeReturnStatus status = getExchangeReturnStatus(txID);

                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER_LEFT);

                if (status.hasExchange()) {
                    Button exBtn = new Button("🔄 Exchanged");
                    exBtn.setStyle(
                            "-fx-background-color: #3498db; -fx-text-fill: white;" +
                            "-fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                    exBtn.setOnAction(e -> showExchangeInfo(status.exchange));
                    box.getChildren().add(exBtn);
                }

                if (status.hasReturn()) {
                    Button retBtn = new Button("↩️ Returned");
                    retBtn.setStyle(
                            "-fx-background-color: #e67e22; -fx-text-fill: white;" +
                            "-fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                    retBtn.setOnAction(e -> showReturnInfo(status.ret));
                    box.getChildren().add(retBtn);
                }

                // Only show action buttons if no exchange/return yet and there's remaining qty
                int remaining = customerService.getRemainingQuantity(txID);
                if (!status.hasExchange() && !status.hasReturn()) {
                    Button returnBtn = new Button("↩️ Return");
                    returnBtn.setStyle(
                            "-fx-background-color: #e67e22; -fx-text-fill: white;" +
                            "-fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                    returnBtn.setDisable(remaining <= 0);

                    Button exchangeBtn = new Button("🔄 Exchange");
                    exchangeBtn.setStyle(
                            "-fx-background-color: #3498db; -fx-text-fill: white;" +
                            "-fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                    exchangeBtn.setDisable(remaining <= 0);

                    returnBtn.setOnAction(e -> {
                        handleReturn(purchase, customer, itemsTable, sale, salesTable);
                        itemsTable.refresh(); // refresh so status buttons update
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

        // ── Print Receipt button ─────────────────────────────────────────
        Button printBtn = new Button("🖨️ Print Receipt");
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
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        subStage.setScene(new Scene(scroll, 900, 560));
        subStage.showAndWait();
    }

    // ── Exchange info popup ───────────────────────────────────────────────────

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

    // ── Return info popup ─────────────────────────────────────────────────────

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

        // Save a copy to the configured receipts folder
        ReceiptGenerator.saveReceipt(receiptText, "sale_" + sale.getSaleID());

        // Print via ReceiptGenerator (handles printer availability + fallback)
        ReceiptGenerator.printReceipt(receiptText);

        // Also show inline so the cashier can review before walking away
        showAlert("Receipt — Sale #" + sale.getSaleID(), receiptText, Alert.AlertType.INFORMATION);
    }

    // ── Exchange/return status lookup ────────────────────────────────────────

    /**
     * Queries the database for the most recent exchange and return for a given
     * transaction ID and wraps them in a lightweight status holder.
     */
    private ExchangeReturnStatus getExchangeReturnStatus(int transactionID) {
        ExchangeReturnService svc = new ExchangeReturnService();

        // Find exchange for this specific transaction
        ExchangeReturnService.ExchangeRequest exchange = null;
        for (ExchangeReturnService.ExchangeRequest ex : svc.getExchangesForTransaction(transactionID)) {
            exchange = ex; // take the most recent (service returns DESC)
            break;
        }

        // Find return for this specific transaction
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

    // ── Add customer ──────────────────────────────────────────────────────────

    private void showAddCustomerDialog() {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Add New Customer");
        dialog.setHeaderText("Enter customer details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField  = new TextField();
        TextField emailField = new TextField();
        TextField phoneField = new TextField();
        DatePicker dobPicker = new DatePicker();

        grid.add(new Label("Full Name:"),    0, 0); grid.add(nameField,  1, 0);
        grid.add(new Label("Email:"),        0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Phone:"),        0, 2); grid.add(phoneField, 1, 2);
        grid.add(new Label("Date of Birth:"),0, 3); grid.add(dobPicker,  1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveButtonType) {
                Customer c = new Customer();
                c.setStaffID(currentUser.getStaffID());
                c.setFullNames(nameField.getText());
                c.setEmailAddress(emailField.getText());
                c.setContactNo(phoneField.getText());
                c.setDateOfBirth(dobPicker.getValue());
                return c;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(c -> {
            if (customerService.addCustomer(c)) {
                showAlert("Success", "Customer added successfully", Alert.AlertType.INFORMATION);
                loadCustomers();
            } else {
                showAlert("Error", "Failed to add customer", Alert.AlertType.ERROR);
            }
        });
    }

    // ── Edit customer ─────────────────────────────────────────────────────────

    private void showEditCustomerDialog(Customer customer) {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Edit Customer");
        dialog.setHeaderText("Modify customer details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField  = new TextField(customer.getFullNames());
        TextField emailField = new TextField(customer.getEmailAddress());
        TextField phoneField = new TextField(customer.getContactNo());
        DatePicker dobPicker = new DatePicker(customer.getDateOfBirth());

        grid.add(new Label("Full Name:"),    0, 0); grid.add(nameField,  1, 0);
        grid.add(new Label("Email:"),        0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Phone:"),        0, 2); grid.add(phoneField, 1, 2);
        grid.add(new Label("Date of Birth:"),0, 3); grid.add(dobPicker,  1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveButtonType) {
                customer.setFullNames(nameField.getText());
                customer.setEmailAddress(emailField.getText());
                customer.setContactNo(phoneField.getText());
                customer.setDateOfBirth(dobPicker.getValue());
                return customer;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(c -> {
            if (customerService.updateCustomer(c)) {
                showAlert("Success", "Customer updated successfully", Alert.AlertType.INFORMATION);
                loadCustomers();
            } else {
                showAlert("Error", "Failed to update customer", Alert.AlertType.ERROR);
            }
        });
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
        noteLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
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
                        salesTable.setItems(customerService.getCustomerPurchases(customer.getAccountID()));
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
        productSearch.setPromptText("🔍 Search products...");
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
        topUpLabel.setTextFill(Color.web("#27ae60"));

        VBox paymentBox = new VBox(10);
        paymentBox.setVisible(false); paymentBox.setManaged(false);

        ToggleGroup paymentGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton("💵 Cash");
        cashRadio.setToggleGroup(paymentGroup); cashRadio.setSelected(true);
        RadioButton cardRadio = new RadioButton("💳 Card");
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
                topUpLabel.setTextFill(Color.web("#e67e22"));
                paymentBox.setVisible(true); paymentBox.setManaged(true);
                amountField.textProperty().addListener((o, oldAmt, newAmt) -> {
                    try {
                        double change = Double.parseDouble(newAmt) - topUp.doubleValue();
                        if (change >= 0) {
                            changeLabel.setText("Change: R" + String.format("%.2f", change));
                            changeLabel.setTextFill(Color.web("#27ae60"));
                        } else {
                            changeLabel.setText("Insufficient: R" + String.format("%.2f", Math.abs(change)));
                            changeLabel.setTextFill(Color.web("#e74c3c"));
                        }
                    } catch (NumberFormatException e) { changeLabel.setText("Change: R 0.00"); }
                });
            } else {
                topUpLabel.setText("Top-up Required: R 0.00 (Same Price)");
                topUpLabel.setTextFill(Color.web("#27ae60"));
                paymentBox.setVisible(false); paymentBox.setManaged(false);
            }
        });

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Enter reason for exchange...");
        reasonArea.setPrefRowCount(3);

        Label noteLabel = new Label("Note: Exchange requires supervisor approval");
        noteLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");

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
                salesTable.setItems(customerService.getCustomerPurchases(customer.getAccountID()));
                salesTable.refresh();
            } else {
                showAlert("Error", "Failed to submit exchange request", Alert.AlertType.ERROR);
            }
        });
    }

    // ── Delete customer ───────────────────────────────────────────────────────

    private void deleteCustomer(Customer customer) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Customer");
        alert.setHeaderText("Are you sure you want to delete this customer?");
        alert.setContentText(customer.getFullNames());
        if (alert.showAndWait().get() == ButtonType.OK) {
            if (customerService.deleteCustomer(customer.getAccountID())) {
                showAlert("Success", "Customer deleted successfully", Alert.AlertType.INFORMATION);
                loadCustomers();
            } else {
                showAlert("Error", "Failed to delete customer", Alert.AlertType.ERROR);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox createLabeledField(String labelText, String valueText) {
        VBox vbox = new VBox(2);
        Label label = new Label(labelText + ":");
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f766e;");
        vbox.getChildren().addAll(label, new Label(valueText));
        return vbox;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max - 1) + "…" : (s != null ? s : "");
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static class ExchangeInfo {
        Product newProduct;
        String reason;
        PaymentInfo payment;
        ExchangeInfo(Product p, String r, PaymentInfo pay) { newProduct = p; reason = r; payment = pay; }
    }
}
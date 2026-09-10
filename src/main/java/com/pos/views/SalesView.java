package com.pos.views;

import com.pos.models.Customer;
import com.pos.models.Product;
import com.pos.models.User;
import com.pos.services.CustomerService;
import com.pos.services.ProductService;
import com.pos.services.PromoService;
import com.pos.services.TransactionService;
import com.pos.services.WiFiHandler;
import com.pos.services.SessionService;
import com.pos.services.CommunicationsService;
import com.pos.services.CommunicationsService.CommPreferences;
import com.pos.services.TransactionService.PaymentInfo;
import com.pos.services.UserService;
import com.pos.dialogs.CommunicationsDialog;
import com.pos.utils.ReceiptGenerator;
import javafx.application.Platform;
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
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SalesView {
    private User currentUser;
    private User activeCashier;
    private ProductService productService;
    private CustomerService customerService;
    private TransactionService transactionService;
    private SessionService sessionService;
    private CommunicationsService commService;
    private UserService userService;
    private PromoService promoService;
    private TableView<CartItem> cartTable;
    private ObservableList<CartItem> cartItems;
    private Label totalLabel;
    private ComboBox<Customer> customerCombo;
    private TextField searchField;
    private Map<Integer, CartItem> cartMap;
    private ObservableList<Product> allProducts;
    private FlowPane quickAddPane;
    private ObservableList<Customer> allCustomers;

    private Label activeCashierLabel;

    // ── Active promo state (valid only for the sale currently being rung up) ──
    private PromoService.Promo activePromo = null;
    private BigDecimal activeDiscount = BigDecimal.ZERO;

    private WiFiHandler wifiHandler;
    private final AtomicBoolean updatingCombo = new AtomicBoolean(false);

    private Label sessionStatusLabel;

    // ── Display zoom (helps cashiers read the terminal without eye strain) ──
    private static final DateTimeFormatter RECEIPT_FILE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");

    private static double zoom = 1.0;
    private static final double ZOOM_MIN = 0.9, ZOOM_MAX = 2.0, ZOOM_STEP = 0.15;
    private Label zoomLabel;
    private Label quickAddLabel;
    private Label searchLabel;

    public SalesView(User user) {
        this.currentUser = user;
        this.activeCashier = user;

        this.productService = new ProductService();
        this.customerService = new CustomerService();
        this.transactionService = new TransactionService();
        this.sessionService = new SessionService();
        this.commService = new CommunicationsService();
        this.userService = new UserService();
        this.promoService = new PromoService();
        this.cartItems = FXCollections.observableArrayList();
        this.cartMap = new HashMap<>();
        this.allProducts = productService.getAllProducts();
        this.wifiHandler = WiFiHandler.getInstance();

        initializeWiFiReceiver();
    }

    private void initializeWiFiReceiver() {
        new Thread(() -> {
            boolean started;
            if (wifiHandler.isServerRunning()) {
                started = true;
            } else {
                started = wifiHandler.startListening(
                        scannedData -> {
                            Platform.runLater(() -> {
                                if (searchField != null) {
                                    String cleanQuery = scannedData;
                                    if (scannedData.startsWith("PRODUCT:")) {
                                        cleanQuery = scannedData.substring(8).trim();
                                    }
                                    searchField.setText(cleanQuery);
                                    addProductToCart(cleanQuery);
                                }
                            });
                        },
                        null);
            }
            Platform.runLater(() -> updateWiFiStatus(started));
        }, "WiFi-Init").start();
    }

    private void addProductToCart(String cleanQuery) {
        if (cleanQuery.isEmpty()) return;

        Optional<Product> productOpt = allProducts.stream()
                .filter(p -> p.getBarCode() != null && p.getBarCode().equals(cleanQuery))
                .findFirst();

        if (productOpt.isPresent()) {
            addProductWithDefaultQuantity(productOpt.get());
            if (searchField != null) {
                searchField.clear();
                handleProductSearch();
            }
        } else {
            showAlert("Product not found", "No product with barcode: " + cleanQuery, Alert.AlertType.WARNING);
            if (searchField != null) {
                searchField.clear();
                handleProductSearch();
            }
        }
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());

        HBox mainContent = new HBox(20);
        mainContent.setPadding(new Insets(20));

        VBox leftPanel = createLeftPanel();
        VBox rightPanel = createRightPanel();

        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        mainContent.getChildren().addAll(leftPanel, rightPanel);
        layout.setCenter(mainContent);

        applyZoom(); // all controls exist now — apply the remembered zoom level
        return layout;
    }

    private String getCustomerDisplay(Customer customer) {
        if (customer == null) return "";
        String name = customer.getFullNames() != null ? customer.getFullNames() : "";
        String email = customer.getEmailAddress() != null ? customer.getEmailAddress() : "";
        return name + " - " + email;
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Sales Terminal");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        sessionStatusLabel = new Label();
        updateSessionStatus();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        activeCashierLabel = new Label("Cashier: " + activeCashier.getFullNames());
        activeCashierLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        activeCashierLabel.setTextFill(Color.web("#0f766e"));
        activeCashierLabel.setStyle(
                "-fx-background-color: #e6f4f2; -fx-padding: 6 12; -fx-background-radius: 20;");

        Button switchCashierBtn = new Button("Switch Cashier");
        switchCashierBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 8 14; -fx-background-radius: 6; -fx-cursor: hand;");
        switchCashierBtn.setOnAction(e -> showCashierSelectDialog());

        Label customerLabel = new Label("Customer:");
        customerLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        customerCombo = new ComboBox<>();
        customerCombo.setEditable(true);
        customerCombo.setPromptText("Search Customer...");
        customerCombo.setPrefWidth(250);

        loadCustomers();

        customerCombo.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer customer) {
                return getCustomerDisplay(customer);
            }

            @Override
            public Customer fromString(String string) {
                if (string == null || string.isEmpty()) return null;
                return allCustomers.stream()
                        .filter(c -> getCustomerDisplay(c).equals(string))
                        .findFirst().orElse(null);
            }
        });

        customerCombo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                if (updatingCombo.get()) return;
                updatingCombo.set(true);
                try {
                    String displayText = customerCombo.getEditor().getText();
                    boolean isFullMatch = allCustomers.stream()
                            .anyMatch(c -> getCustomerDisplay(c).equals(displayText));

                    if (displayText.isEmpty() || isFullMatch) {
                        customerCombo.setItems(allCustomers);
                    } else {
                        String lowerText = displayText.toLowerCase().trim();
                        ObservableList<Customer> filtered = allCustomers.stream()
                                .filter(c -> {
                                    String displayLower = getCustomerDisplay(c).toLowerCase();
                                    String phoneLower = c.getContactNo() != null ? c.getContactNo().toLowerCase() : "";
                                    return displayLower.contains(lowerText) || phoneLower.contains(lowerText);
                                })
                                .collect(Collectors.toCollection(FXCollections::observableArrayList));
                        customerCombo.setItems(filtered);
                        if (!isFullMatch && !displayText.isEmpty()) {
                            customerCombo.getEditor().setText(displayText);
                        }
                    }
                } finally {
                    updatingCombo.set(false);
                }
            }
        });

        customerCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (oldText != null && oldText.equals(newText)) return;
            if (updatingCombo.get()) return;
            updatingCombo.set(true);
            try {
                String text = newText.toLowerCase().trim();
                if (text.isEmpty()) {
                    customerCombo.setItems(allCustomers);
                } else {
                    ObservableList<Customer> filtered = allCustomers.stream()
                            .filter(c -> {
                                String displayLower = getCustomerDisplay(c).toLowerCase();
                                String phoneLower = c.getContactNo() != null ? c.getContactNo().toLowerCase() : "";
                                return displayLower.contains(text) || phoneLower.contains(text);
                            })
                            .collect(Collectors.toCollection(FXCollections::observableArrayList));
                    customerCombo.setItems(filtered);
                    customerCombo.getEditor().setText(newText);
                    if (!filtered.isEmpty()) customerCombo.show();
                    else customerCombo.hide();
                }
            } finally {
                updatingCombo.set(false);
            }
        });

        Button newCustomerBtn = new Button("+ New Customer");
        newCustomerBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        newCustomerBtn.setOnAction(e -> showNewCustomerDialog());

        topBar.getChildren().addAll(
                title, sessionStatusLabel, spacer,
                activeCashierLabel, switchCashierBtn,
                customerLabel, customerCombo, newCustomerBtn);

        return topBar;
    }

    private void showCashierSelectDialog() {
        List<User> activeStaff = userService.getAllActiveUsers();

        if (activeStaff == null || activeStaff.isEmpty()) {
            showAlert("No Staff Found", "No active staff members could be loaded.", Alert.AlertType.WARNING);
            return;
        }

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Select Active Cashier");
        dialog.setHeaderText("Who is making this sale?");

        ButtonType selectBtn = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectBtn, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(420);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label note = new Label(
                "Logged-in account: " + currentUser.getFullNames() + "\n"
                + "Choose the cashier currently at the register.");
        note.setStyle("-fx-text-fill: #666; -fx-font-size: 12;");
        note.setWrapText(true);

        ListView<User> staffList = new ListView<>();
        staffList.setPrefHeight(250);
        staffList.getItems().addAll(activeStaff);

        staffList.getItems().stream()
                .filter(u -> u.getStaffID() == activeCashier.getStaffID())
                .findFirst()
                .ifPresent(u -> {
                    staffList.getSelectionModel().select(u);
                    staffList.scrollTo(u);
                });

        staffList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);

                    Label avatar = new Label(getInitials(user.getFullNames()));
                    avatar.setStyle(
                            "-fx-background-color: #0f766e; -fx-text-fill: white;"
                            + "-fx-font-weight: bold; -fx-padding: 6 10;"
                            + "-fx-background-radius: 20; -fx-font-size: 12;");

                    VBox info = new VBox(2);
                    Label nameLbl = new Label(user.getFullNames());
                    nameLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

                    Label roleLbl = new Label(user.getUserType() != null ? user.getUserType() : "Staff");
                    roleLbl.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

                    info.getChildren().addAll(nameLbl, roleLbl);
                    row.getChildren().addAll(avatar, info);

                    if (user.getStaffID() == activeCashier.getStaffID()) {
                        Label badge = new Label("● Active");
                        badge.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11; -fx-font-weight: bold;");
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);
                        row.getChildren().addAll(spacer, badge);
                    }

                    setGraphic(row);
                    setText(null);
                }
            }
        });

        content.getChildren().addAll(note, new Separator(), staffList);
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(selectBtn).setDisable(
                staffList.getSelectionModel().isEmpty());
        staffList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> dialog.getDialogPane().lookupButton(selectBtn).setDisable(sel == null));

        dialog.setResultConverter(btn -> {
            if (btn == selectBtn) return staffList.getSelectionModel().getSelectedItem();
            return null;
        });

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(selectedUser -> {
            activeCashier = selectedUser;
            activeCashierLabel.setText("Cashier: " + activeCashier.getFullNames());
            showAlert("Cashier Switched",
                    activeCashier.getFullNames() + " is now the active cashier for this sale.",
                    Alert.AlertType.INFORMATION);
        });
    }

    private String getInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private void loadCustomers() {
        allCustomers = customerService.getAllCustomers();
        customerCombo.setItems(allCustomers);

        customerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Customer customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : getCustomerDisplay(customer));
            }
        });

        customerCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Customer customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null ? null : getCustomerDisplay(customer));
            }
        });
    }

    private void updateWiFiStatus(boolean started) {
    }

    private void updateSessionStatus() {
        SessionService.BusinessSession session = sessionService.getActiveSession();
        if (session != null) {
            sessionStatusLabel.setText("🟢 Session Active");
            sessionStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 14;");
        } else {
            sessionStatusLabel.setText("🔴 No Active Session");
            sessionStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14;");
        }
    }

    private void showNewCustomerDialog() {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Add New Customer");
        dialog.setHeaderText("Enter customer details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        TextField emailField = new TextField();
        TextField phoneField = new TextField();
        DatePicker dobPicker = new DatePicker();

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2);
        grid.add(phoneField, 1, 2);
        grid.add(new Label("Date of Birth:"), 0, 3);
        grid.add(dobPicker, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Customer customer = new Customer();
                customer.setStaffID(currentUser.getStaffID());
                customer.setFullNames(nameField.getText().trim());
                customer.setEmailAddress(emailField.getText().trim());
                customer.setContactNo(phoneField.getText().trim());
                customer.setDateOfBirth(dobPicker.getValue());
                return customer;
            }
            return null;
        });

        Optional<Customer> result = dialog.showAndWait();
        result.ifPresent(customer -> {
            LocalDate today = LocalDate.now();
            LocalDate minDob = today.minusYears(18);

            if (customer.getDateOfBirth() == null || customer.getDateOfBirth().isAfter(minDob)) {
                showAlert("Error", "Customer must be at least 18 years old", Alert.AlertType.ERROR);
                return;
            }

            if (customerService.addCustomer(customer)) {
                showAlert("Success", "Customer added successfully", Alert.AlertType.INFORMATION);
                loadCustomers();
                customerCombo.setValue(customer);
            } else {
                showAlert("Error", "Failed to add customer", Alert.AlertType.ERROR);
            }
        });
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");

        searchLabel = new Label("Search Product");

        searchField = new TextField();
        searchField.setPromptText("Enter barcode or product name");
        searchField.textProperty().addListener((obs, oldText, newText) -> handleProductSearch());

        quickAddLabel = new Label("Quick Add Products");

        Region qaSpacer = new Region();
        HBox.setHgrow(qaSpacer, Priority.ALWAYS);
        HBox quickAddHeader = new HBox(10, quickAddLabel, qaSpacer, createZoomControls());
        quickAddHeader.setAlignment(Pos.CENTER_LEFT);

        quickAddPane = new FlowPane(10, 10);
        populateQuickAddButtons(allProducts, quickAddPane);

        ScrollPane quickScroll = new ScrollPane(quickAddPane);
        quickScroll.setFitToWidth(true);
        quickScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        panel.getChildren().addAll(searchLabel, searchField, quickAddHeader, quickScroll);
        VBox.setVgrow(quickScroll, Priority.ALWAYS);

        applyZoom();
        return panel;
    }

    // ── Zoom ────────────────────────────────────────────────────────────────

    private HBox createZoomControls() {
        Button minus = new Button("A−");
        Button plus  = new Button("A+");
        zoomLabel = new Label();
        String s = "-fx-background-color: #e6f4f2; -fx-text-fill: #0f766e; -fx-font-weight: bold;"
                 + "-fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand;";
        minus.setStyle(s); plus.setStyle(s);
        minus.setTooltip(new Tooltip("Smaller text"));
        plus.setTooltip(new Tooltip("Larger text"));
        minus.setOnAction(e -> setZoom(zoom - ZOOM_STEP));
        plus.setOnAction(e -> setZoom(zoom + ZOOM_STEP));
        zoomLabel.setTextFill(Color.web("#0f766e"));
        zoomLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        HBox box = new HBox(6, minus, zoomLabel, plus);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void setZoom(double z) {
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, Math.round(z * 100) / 100.0));
        applyZoom();
        handleProductSearch(); // rebuild product buttons at the new size
    }

    private void applyZoom() {
        if (zoomLabel != null) zoomLabel.setText(Math.round(zoom * 100) + "%");
        if (searchLabel != null)   searchLabel.setFont(Font.font("System", FontWeight.BOLD, 18 * zoom));
        if (quickAddLabel != null) quickAddLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14 * zoom));
        if (searchField != null)   searchField.setStyle("-fx-font-size: " + (14 * zoom) + "px; -fx-padding: " + (12 * zoom) + "px;");
        if (cartTable != null)     cartTable.setStyle("-fx-font-size: " + (13 * zoom) + "px;");
        if (totalLabel != null)    totalLabel.setFont(Font.font("System", FontWeight.BOLD, 24 * zoom));
    }

    private void handleProductSearch() {
        String query = searchField.getText().trim().toLowerCase();

        ObservableList<Product> filtered;
        if (query.isEmpty()) {
            filtered = allProducts;
        } else {
            filtered = allProducts.stream()
                    .filter(p -> {
                        String name = p.getProductName() != null ? p.getProductName().toLowerCase() : "";
                        String barcode = p.getBarCode() != null ? p.getBarCode().toLowerCase() : "";
                        return name.contains(query) || barcode.contains(query);
                    })
                    .collect(Collectors.toCollection(FXCollections::observableArrayList));
        }

        populateQuickAddButtons(filtered, quickAddPane);
    }

    private void populateQuickAddButtons(ObservableList<Product> products, FlowPane pane) {
        pane.getChildren().clear();

        for (Product product : products) {
            if (!product.isOutOfStock()) {
                Button productBtn = new Button(product.getProductName() + "\nR" + product.getPrice());
                productBtn.setWrapText(true);
                productBtn.setStyle(
                        "-fx-background-color: #ecf0f1; -fx-padding: " + (14 * zoom) + "px;"
                        + "-fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: " + (13 * zoom) + "px;");
                productBtn.setPrefWidth(120 * zoom);
                productBtn.setPrefHeight(80 * zoom);
                productBtn.setOnAction(e -> {
                    addProductWithDefaultQuantity(product);
                    searchField.clear();
                    populateQuickAddButtons(allProducts, pane);
                });
                pane.getChildren().add(productBtn);
            }
        }

        pane.requestLayout();
    }

    private void addProductWithDefaultQuantity(Product product) {
        int quantityToAdd = 1;

        if (cartMap.containsKey(product.getProductID())) {
            CartItem item = cartMap.get(product.getProductID());
            if (item.getQuantity() + quantityToAdd > product.getQuantity()) {
                showAlert("Error", "Not enough stock available", Alert.AlertType.ERROR);
                return;
            }
            item.setQuantity(item.getQuantity() + quantityToAdd);
            cartTable.refresh();
        } else {
            CartItem item = new CartItem(product.getProductID(), product.getProductName(), product.getPrice(),
                    quantityToAdd);
            cartItems.add(item);
            cartMap.put(product.getProductID(), item);
        }

        updateTotal();
    }

    private VBox createRightPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");

        Label cartLabel = new Label("Shopping Cart");
        cartLabel.setFont(Font.font("System", FontWeight.BOLD, 18));

        cartTable = new TableView<>();
        cartTable.setItems(cartItems);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<CartItem, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<CartItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<CartItem, Integer> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setCellFactory(tc -> {
            TableCell<CartItem, Integer> cell = new TableCell<>() {
                @Override
                protected void updateItem(Integer qty, boolean empty) {
                    super.updateItem(qty, empty);
                    if (empty) {
                        setText(null);
                    } else {
                        setText(qty.toString());
                        setOnMouseClicked(e -> {
                            CartItem item = getTableView().getItems().get(getIndex());
                            TextInputDialog dialog = new TextInputDialog(String.valueOf(item.getQuantity()));
                            dialog.setTitle("Adjust Quantity");
                            dialog.setHeaderText("Adjust quantity for " + item.getProductName());
                            Optional<String> result = dialog.showAndWait();
                            result.ifPresent(str -> {
                                try {
                                    int newQty = Integer.parseInt(str);
                                    if (newQty > 0 && newQty <= productService.getProductById(item.getProductId())
                                            .getQuantity()) {
                                        item.setQuantity(newQty);
                                        cartTable.refresh();
                                        updateTotal();
                                    } else {
                                        showAlert("Error", "Invalid quantity", Alert.AlertType.ERROR);
                                    }
                                } catch (NumberFormatException ex) {
                                    showAlert("Error", "Invalid number", Alert.AlertType.ERROR);
                                }
                            });
                        });
                    }
                }
            };
            return cell;
        });

        TableColumn<CartItem, BigDecimal> subtotalCol = new TableColumn<>("Subtotal");
        subtotalCol.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        TableColumn<CartItem, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");

            {
                removeBtn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;"
                        + "-fx-font-size: 11; -fx-padding: 4 10; -fx-background-radius: 5; -fx-cursor: hand;");
                removeBtn.setOnAction(e -> removeFromCart(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeBtn);
            }
        });

        cartTable.getColumns().addAll(nameCol, priceCol, qtyCol, subtotalCol, actionCol);
        VBox.setVgrow(cartTable, Priority.ALWAYS);

        Label totalText = new Label("TOTAL:");
        totalText.setFont(Font.font("System", FontWeight.BOLD, 20));

        totalLabel = new Label("R 0.00");
        totalLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        totalLabel.setTextFill(Color.web("#27ae60"));

        HBox totalBox = new HBox(totalText, new Region(), totalLabel);
        HBox.setHgrow(totalBox.getChildren().get(1), Priority.ALWAYS);

        HBox actionBox = new HBox(10);

        Button clearBtn = new Button("Clear Cart");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setStyle(
                "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> clearCart());
        HBox.setHgrow(clearBtn, Priority.ALWAYS);

        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white;"
                + "-fx-font-weight: bold; -fx-padding: 12; -fx-cursor: hand;");
        checkoutBtn.setOnAction(e -> processCheckout());
        HBox.setHgrow(checkoutBtn, Priority.ALWAYS);

        actionBox.getChildren().addAll(clearBtn, checkoutBtn);

        panel.getChildren().addAll(cartLabel, cartTable, totalBox, actionBox);

        return panel;
    }

    private void removeFromCart(CartItem item) {
        cartItems.remove(item);
        cartMap.remove(item.getProductId());
        updateTotal();
    }

    private void clearCart() {
        cartItems.clear();
        cartMap.clear();
        // Also clear any active promo when the cart is cleared
        activePromo = null;
        activeDiscount = BigDecimal.ZERO;
        updateTotal();
    }

    private void updateTotal() {
        BigDecimal total = cartItems.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalLabel.setText("R " + String.format("%.2f", total));
    }

    private void processCheckout() {
        SessionService.BusinessSession activeSession = sessionService.getActiveSession();
        if (activeSession == null) {
            showAlert("No Active Session",
                    "Sales cannot be processed without an active business session.\n"
                    + "Please ask a supervisor to start a session first.",
                    Alert.AlertType.ERROR);
            return;
        }

        if (cartItems.isEmpty()) {
            showAlert("Empty Cart", "Please add items to cart", Alert.AlertType.WARNING);
            return;
        }

        Customer customer = customerCombo.getValue();
        if (customer == null) {
            showAlert("No Customer", "Please select a customer", Alert.AlertType.WARNING);
            return;
        }

        if (!commService.hasAcceptedTerms(customer.getAccountID())) {
            Optional<CommPreferences> prefs = CommunicationsDialog.showDialog(
                    customer.getAccountID(), commService);

            if (prefs.isPresent()) {
                commService.savePreferences(prefs.get());
            } else {
                showAlert("Terms Required",
                        "Customer must accept terms and conditions to continue",
                        Alert.AlertType.WARNING);
                return;
            }
        }

        BigDecimal cartTotal = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            cartTotal = cartTotal.add(item.getSubtotal());
        }

        // Start from a clean promo state — a promo is only valid for the sale it
        // was entered on. showPaymentDialog() sets these if the cashier applies one.
        activePromo = null;
        activeDiscount = BigDecimal.ZERO;

        // ── Promo + payment dialog ────────────────────────────────────────
        Optional<PaymentInfo> paymentResult = showPaymentDialog(cartTotal);
        if (paymentResult.isEmpty()) return;

        final PaymentInfo paymentInfo = paymentResult.get();
        final BigDecimal finalTotal = cartTotal.subtract(activeDiscount);
        final Customer saleCustomer = customer;
        final User saleCashier = activeCashier;
        final PromoService.Promo salePromo = activePromo;
        final BigDecimal saleDiscount = activeDiscount;

        // Run the sale (DB write, receipt build, email) off the FX thread so the
        // register stays responsive, with a clear "please wait" indicator.
        Stage busy = showBusyDialog("Processing sale — please wait…",
                "Recording the transaction, updating stock and preparing the receipt.");

        Thread worker = new Thread(() -> {
            String error = null;
            String receipt = null;
            File savedFile = null;
            boolean success = false;
            try {
                success = transactionService.processTransaction(
                        saleCashier.getStaffID(), saleCustomer.getAccountID(),
                        cartItems, paymentInfo, salePromo, saleDiscount);

                if (success) {
                    receipt = ReceiptGenerator.generateReceipt(
                            saleCustomer, cartItems, saleCashier, paymentInfo,
                            salePromo != null ? salePromo.getPromoCode() : null, saleDiscount);
                    savedFile = ReceiptGenerator.saveReceiptToFile(
                            receipt, "Receipt " + LocalDateTime.now().format(RECEIPT_FILE_FMT));

                    CommPreferences prefs = commService.getPreferences(saleCustomer.getAccountID());
                    if (prefs.isReceiptByEmail() && saleCustomer.getEmailAddress() != null
                            && !saleCustomer.getEmailAddress().isBlank()) {
                        commService.sendReceiptByEmail(saleCustomer.getEmailAddress(), receipt);
                    }
                    if (salePromo != null) promoService.incrementUsage(salePromo.getPromoID());
                }
            } catch (RuntimeException e) {
                error = e.getMessage();
            }

            final boolean fSuccess = success;
            final String fReceipt = receipt;
            final String fError = error;
            final File fSavedFile = savedFile;
            Platform.runLater(() -> {
                busy.close();
                if (fError != null) {
                    showAlert("Sale not completed", fError, Alert.AlertType.ERROR);
                    return;
                }
                if (!fSuccess) {
                    showAlert("Sale not completed",
                            "The transaction could not be saved. Nothing was charged. Please try again.",
                            Alert.AlertType.ERROR);
                    return;
                }

                ReceiptGenerator.printReceipt(fReceipt);
                if (wifiHandler != null && wifiHandler.isConnected()) {
                    new Thread(() -> wifiHandler.sendReceipt(fReceipt), "Receipt-Sender").start();
                }

                showSaleCompleteDialog(fReceipt, finalTotal, paymentInfo, fSavedFile);

                clearCart();
                customerCombo.setValue(null);
                activeCashier = currentUser;
                activeCashierLabel.setText("Cashier: " + activeCashier.getFullNames());
                updateSessionStatus();
            });
        }, "Checkout-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** A small non-closable modal shown while a slow operation runs. */
    private Stage showBusyDialog(String heading, String detail) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        if (cartTable != null && cartTable.getScene() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(46, 46);

        Label h = new Label(heading);
        h.setFont(Font.font("System", FontWeight.BOLD, 15));
        h.setTextFill(Color.web("#0f766e"));

        Label d = new Label(detail);
        d.setWrapText(true);
        d.setTextFill(Color.web("#475569"));
        d.setMaxWidth(300);

        VBox text = new VBox(4, h, d);
        HBox box = new HBox(18, spinner, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(26, 30, 26, 26));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-border-color: #cbd5e1; -fx-border-radius: 12;");

        dialog.setScene(new Scene(box));
        dialog.show();
        return dialog;
    }

    /** Post-sale confirmation with the receipt in a readable monospace pane. */
    private void showSaleCompleteDialog(String receipt, BigDecimal total, PaymentInfo payment, File savedFile) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Sale Complete");
        dialog.initOwner(cartTable.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(430);

        Label ok = new Label("Sale completed — R " + String.format("%.2f", total)
                + " (" + payment.getPaymentMethod() + ")");
        ok.setFont(Font.font("System", FontWeight.BOLD, 15));
        ok.setTextFill(Color.web("#16a34a"));

        String printed = wifiHandler != null && wifiHandler.isConnected()
                ? "Receipt printed and sent to the scanner device."
                : "Receipt sent to the printer.";
        Label note = new Label(printed);
        note.setTextFill(Color.web("#475569"));

        Label saved = new Label(savedFile != null
                ? "Saved automatically to:  " + savedFile.getParent()
                : "Note: the receipt could not be saved to disk.");
        saved.setTextFill(Color.web(savedFile != null ? "#475569" : "#e74c3c"));
        saved.setWrapText(true);

        TextArea receiptArea = new TextArea(receipt);
        receiptArea.setEditable(false);
        receiptArea.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12;");
        receiptArea.setPrefRowCount(16);

        VBox content = new VBox(10, ok, note, saved, new Separator(), receiptArea);
        content.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    // ── Payment dialog — now includes promo code field ────────────────────
    private Optional<PaymentInfo> showPaymentDialog(BigDecimal cartTotal) {
        Dialog<PaymentInfo> dialog = new Dialog<>();
        dialog.setTitle("Payment Method");
        dialog.setHeaderText("Complete Payment — Cashier: " + activeCashier.getFullNames());

        ButtonType payButtonType = new ButtonType("Process Payment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(payButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // ── Running total display (updates when promo applied) ────────────
        Label totalDisplay = new Label("Total Amount: R " + String.format("%.2f", cartTotal));
        totalDisplay.setFont(Font.font("System", FontWeight.BOLD, 18));
        totalDisplay.setTextFill(Color.web("#27ae60"));

        content.getChildren().add(totalDisplay);
        content.getChildren().add(new Separator());

        // ── Promo code section ────────────────────────────────────────────
        Label promoSectionLabel = new Label("Promo Code (optional):");
        promoSectionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox promoRow = new HBox(10);
        promoRow.setAlignment(Pos.CENTER_LEFT);

        TextField promoCodeField = new TextField();
        promoCodeField.setPromptText("Enter promo code...");
        promoCodeField.setPrefWidth(200);
        promoCodeField.setStyle("-fx-font-size: 13; -fx-padding: 8;");
        HBox.setHgrow(promoCodeField, Priority.ALWAYS);

        Button applyPromoBtn = new Button("Apply");
        applyPromoBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");

        Button clearPromoBtn = new Button("Clear");
        clearPromoBtn.setStyle(
                "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        clearPromoBtn.setVisible(false);

        promoRow.getChildren().addAll(promoCodeField, applyPromoBtn, clearPromoBtn);

        Label promoStatusLabel = new Label();
        promoStatusLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        promoStatusLabel.setVisible(false);

        Label discountLabel = new Label();
        discountLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        discountLabel.setTextFill(Color.web("#27ae60"));
        discountLabel.setVisible(false);

        // Local mutable reference for the effective total within this dialog
        final BigDecimal[] effectiveTotal = { cartTotal };

        applyPromoBtn.setOnAction(e -> {
            String code = promoCodeField.getText().trim();
            if (code.isEmpty()) {
                promoStatusLabel.setText("⚠️ Please enter a promo code.");
                promoStatusLabel.setTextFill(Color.web("#e67e22"));
                promoStatusLabel.setVisible(true);
                return;
            }

            PromoService.PromoValidationResult result = promoService.validate(code, cartTotal);
            if (result.valid()) {
                activePromo = result.promo();
                activeDiscount = result.discountAmount();
                effectiveTotal[0] = cartTotal.subtract(activeDiscount);

                promoStatusLabel.setText("✅ Promo applied: " + code);
                promoStatusLabel.setTextFill(Color.web("#27ae60"));
                promoStatusLabel.setVisible(true);

                discountLabel.setText("Discount: -R" + String.format("%.2f", activeDiscount));
                discountLabel.setVisible(true);

                totalDisplay.setText("Total Amount: R " + String.format("%.2f", effectiveTotal[0]));

                promoCodeField.setDisable(true);
                applyPromoBtn.setDisable(true);
                clearPromoBtn.setVisible(true);
            } else {
                activePromo = null;
                activeDiscount = BigDecimal.ZERO;
                effectiveTotal[0] = cartTotal;

                promoStatusLabel.setText("❌ " + result.errorMessage());
                promoStatusLabel.setTextFill(Color.web("#e74c3c"));
                promoStatusLabel.setVisible(true);

                discountLabel.setVisible(false);
                totalDisplay.setText("Total Amount: R " + String.format("%.2f", cartTotal));
            }
        });

        clearPromoBtn.setOnAction(e -> {
            activePromo = null;
            activeDiscount = BigDecimal.ZERO;
            effectiveTotal[0] = cartTotal;

            promoCodeField.setText("");
            promoCodeField.setDisable(false);
            applyPromoBtn.setDisable(false);
            clearPromoBtn.setVisible(false);
            promoStatusLabel.setVisible(false);
            discountLabel.setVisible(false);
            totalDisplay.setText("Total Amount: R " + String.format("%.2f", cartTotal));
        });

        content.getChildren().addAll(promoSectionLabel, promoRow, promoStatusLabel, discountLabel);
        content.getChildren().add(new Separator());

        // ── Payment method ────────────────────────────────────────────────
        Label methodLabel = new Label("Select Payment Method:");
        methodLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        ToggleGroup paymentGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton("💵 Cash");
        cashRadio.setToggleGroup(paymentGroup);
        cashRadio.setSelected(true);
        cashRadio.setFont(Font.font("System", 13));

        RadioButton cardRadio = new RadioButton("💳 Card");
        cardRadio.setToggleGroup(paymentGroup);
        cardRadio.setFont(Font.font("System", 13));

        content.getChildren().addAll(methodLabel, cashRadio, cardRadio);
        content.getChildren().add(new Separator());

        VBox cashFields = new VBox(10);
        Label amountLabel = new Label("Amount Received:");
        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount received");
        amountField.setStyle("-fx-font-size: 14; -fx-padding: 10;");

        Label changeLabel = new Label("Change: R 0.00");
        changeLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        changeLabel.setTextFill(Color.web("#3498db"));

        cashFields.getChildren().addAll(amountLabel, amountField, changeLabel);
        content.getChildren().add(cashFields);

        amountField.textProperty().addListener((obs, old, newVal) -> {
            try {
                double amount = Double.parseDouble(newVal);
                double change = amount - effectiveTotal[0].doubleValue();
                if (change >= 0) {
                    changeLabel.setText("Change: R " + String.format("%.2f", change));
                    changeLabel.setTextFill(Color.web("#27ae60"));
                } else {
                    changeLabel.setText("Insufficient: R " + String.format("%.2f", Math.abs(change)));
                    changeLabel.setTextFill(Color.web("#e74c3c"));
                }
            } catch (NumberFormatException e) {
                changeLabel.setText("Change: R 0.00");
                changeLabel.setTextFill(Color.web("#3498db"));
            }
        });

        cashFields.setVisible(true);
        cashRadio.selectedProperty().addListener((obs, old, newVal) -> {
            cashFields.setVisible(newVal);
            if (!newVal) {
                changeLabel.setText("Change: R 0.00");
                amountField.clear();
            }
        });

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == payButtonType) {
                String method = cashRadio.isSelected() ? "Cash" : "Card";
                BigDecimal paid = effectiveTotal[0];
                BigDecimal change = BigDecimal.ZERO;

                if (cashRadio.isSelected()) {
                    try {
                        paid = new BigDecimal(amountField.getText());
                        change = paid.subtract(effectiveTotal[0]);
                        if (change.compareTo(BigDecimal.ZERO) < 0) {
                            showAlert("Error", "Insufficient payment amount", Alert.AlertType.ERROR);
                            return null;
                        }
                    } catch (NumberFormatException e) {
                        showAlert("Error", "Please enter a valid amount", Alert.AlertType.ERROR);
                        return null;
                    }
                }

                return new PaymentInfo(method, paid, change);
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void cleanup() {
        if (wifiHandler != null) wifiHandler.cleanup();
    }

    public static class CartItem {
        private final int productId;
        private final String productName;
        private final BigDecimal price;
        private int quantity;

        public CartItem(int productId, String productName, BigDecimal price, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.price = price;
            this.quantity = quantity;
        }

        public int getProductId()        { return productId; }
        public String getProductName()   { return productName; }
        public BigDecimal getPrice()     { return price; }
        public int getQuantity()         { return quantity; }
        public void setQuantity(int q)   { this.quantity = q; }
        public BigDecimal getSubtotal()  { return price.multiply(BigDecimal.valueOf(quantity)); }
    }
}
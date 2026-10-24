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
import com.pos.utils.BarcodeScannerSupport;
import com.pos.utils.ReceiptGenerator;
import com.pos.utils.Theme;
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
    private final com.pos.services.SettingsService settingsService = new com.pos.services.SettingsService();
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
    private final com.pos.services.PromotionService promotionService=new com.pos.services.PromotionService();
    private com.pos.services.PromotionEngine.Quote activeQuote;
    private String appliedPromotionCode="";
    private long priceGeneration;
    private Label promotionSummary;
    private boolean checkingOut;

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
            // Registered unconditionally, via the dedicated setter rather than
            // startListening(...) — startListening() overwrites BOTH the scan
            // and product callbacks every time it's called, so whichever view
            // happened to start the server first would silently wipe out the
            // other view's callback the moment this ran. Inventory already
            // registers its product callback this same way; previously this
            // view only registered its scan callback inside the "not yet
            // running" branch, so if Inventory (or anything else) had started
            // the server first, Sales's scan callback was simply never set —
            // every barcode scanned from the phone while on this screen did
            // nothing at all, as if the product didn't exist.
            // Must boot the socket (if it isn't already) BEFORE setting the
            // callback below — startListening() unconditionally overwrites
            // both callback fields itself, so calling it afterwards would
            // immediately wipe out the setScanCallback() call that follows.
            boolean started = wifiHandler.isServerRunning() || wifiHandler.startListening(null, null);

            wifiHandler.setScanCallback(scannedData -> Platform.runLater(() -> {
                if (searchField != null) {
                    String cleanQuery = scannedData;
                    if (scannedData.startsWith("PRODUCT:")) {
                        cleanQuery = scannedData.substring(8).trim();
                    }
                    searchField.setText(cleanQuery);
                    addProductToCart(cleanQuery);
                }
            }));

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
                searchField.requestFocus();
            }
        } else {
            showAlert("Product not found", "No product with barcode: " + cleanQuery, Alert.AlertType.WARNING);
            if (searchField != null) {
                searchField.clear();
                handleProductSearch();
                searchField.requestFocus();
            }
        }
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle(Theme.page());
        layout.setTop(createTopBar());

        HBox mainContent = new HBox(24);
        mainContent.setPadding(new Insets(24));

        VBox leftPanel = createLeftPanel();
        VBox rightPanel = createRightPanel();

        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        mainContent.getChildren().addAll(leftPanel, rightPanel);
        layout.setCenter(mainContent);

        applyZoom(); // all controls exist now — apply the remembered zoom level

        // Hardware USB barcode scanners act as keyboard-wedge devices: once the
        // scene exists, a scan is recognised anywhere, not just while searchField
        // happens to have focus. Reuses the same lookup/add-to-cart path as the
        // mobile WiFi scanner bridge.
        layout.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                BarcodeScannerSupport.attach(newScene, this::addProductToCart);
            }
        });
        if (searchField != null) {
            Platform.runLater(() -> searchField.requestFocus());
        }

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
        topBar.setPadding(new Insets(18, 24, 18, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(Theme.topBar());

        Label title = Theme.pageTitleLabel("Sales Terminal");

        sessionStatusLabel = new Label();
        updateSessionStatus();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        activeCashierLabel = new Label("Cashier: " + activeCashier.getFullNames());
        activeCashierLabel.setFont(Theme.label());
        activeCashierLabel.setStyle(Theme.chip(Theme.BRAND_TINT, Theme.BRAND_DARK));

        Button switchCashierBtn = new Button("Switch Cashier");
        Theme.hover(switchCashierBtn, Theme.secondaryButton(), Theme.secondaryHover());
        switchCashierBtn.setOnAction(e -> showCashierSelectDialog());

        topBar.getChildren().addAll(
                title, sessionStatusLabel, spacer,
                activeCashierLabel, switchCashierBtn);

        // Retail edition: no customer picker — sales go to the walk-in account.
        if (com.pos.Edition.current().isRetail()) {
            return topBar;
        }

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
        Theme.hover(newCustomerBtn, Theme.primaryButton(), Theme.primaryHover());
        newCustomerBtn.setOnAction(e -> showNewCustomerDialog());

        topBar.getChildren().addAll(customerLabel, customerCombo, newCustomerBtn);

        return topBar;
    }

    private void showCashierSelectDialog() {
        var activeStaff=FXCollections.observableArrayList(userService.getAllActiveUsers());
        if(activeStaff==null || activeStaff.isEmpty()) {
            showAlert("No staff found","No active staff members could be loaded.",Alert.AlertType.WARNING);return;
        }
        ComboBox<User> cashier=new ComboBox<>(activeStaff);cashier.setPromptText("Select the cashier at this register");
        cashier.setConverter(new javafx.util.StringConverter<User>() {
            public String toString(User user){return user==null?"":user.getFullNames()+" ("+user.getUserType()+")";}
            public User fromString(String text){return null;}
        });
        activeStaff.stream().filter(user->user.getStaffID()==activeCashier.getStaffID()).findFirst().ifPresent(cashier::setValue);
        PasswordField password=new PasswordField();password.setPromptText("Selected cashier's login password");
        cashier.valueProperty().addListener((o,a,b)->password.clear());
        var grid=com.pos.components.Ui.formGrid();com.pos.components.Ui.formField(grid,0,"Cashier",cashier);com.pos.components.Ui.formField(grid,1,"Confirm password",password);
        Label note=new Label("The selected cashier must enter their password. New sales will be recorded under their account.");note.setWrapText(true);
        com.pos.components.Ui.createForm("Switch cashier","Who's at the register?",new VBox(16,
                com.pos.components.Ui.section("Current register",com.pos.components.Ui.detailRow("Logged-in account",currentUser.getFullNames()),com.pos.components.Ui.detailRow("Active cashier",activeCashier.getFullNames())),
                com.pos.components.Ui.section("Cashier sign-in",grid,note)),"Switch cashier",()->{
            User selected=cashier.getValue();if(selected==null)throw new IllegalArgumentException("Select a cashier.");
            User verified=userService.login(selected.getEmailAddress(),password.getText());password.clear();
            if(verified==null || verified.getStaffID()!=selected.getStaffID())throw new IllegalArgumentException("The password is incorrect or this account is inactive.");
            activeCashier=verified;activeCashierLabel.setText("Cashier: "+verified.getFullNames());return true;
        }).showAndWait();
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
            sessionStatusLabel.setText("Session Active");
            sessionStatusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 14;");
        } else {
            sessionStatusLabel.setText("No Active Session");
            sessionStatusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 14;");
        }
    }

    private void showNewCustomerDialog() {
        com.pos.components.CustomerForm.create(currentUser.getStaffID(),customerService,customer->{
            loadCustomers();customerCombo.setValue(customer);
        }).showAndWait();
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(24));
        panel.setStyle(Theme.card());

        searchLabel = new Label("Search Product");

        searchField = new TextField();
        searchField.setPromptText("Scan a barcode, or type a product name");
        searchField.setStyle(Theme.input());
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
        // Opaque background: a transparent ScrollPane background makes label
        // text inside it render blank on some Windows GPUs.
        quickScroll.setStyle("-fx-background: white; -fx-background-color: white;");

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
        String s = Theme.chip(Theme.BRAND_TINT, Theme.BRAND_DARK) + "-fx-cursor: hand;";
        minus.setStyle(s); plus.setStyle(s);
        minus.setTooltip(new Tooltip("Smaller text"));
        plus.setTooltip(new Tooltip("Larger text"));
        minus.setOnAction(e -> setZoom(zoom - ZOOM_STEP));
        plus.setOnAction(e -> setZoom(zoom + ZOOM_STEP));
        zoomLabel.setTextFill(Color.web(Theme.BRAND_DARK));
        zoomLabel.setFont(Theme.label());

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
        if (searchField != null)   searchField.setStyle(Theme.input()
                + "-fx-font-size: " + (14 * zoom) + "px; -fx-padding: " + (12 * zoom) + "px;");
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
                String base = "-fx-background-color: " + Theme.SURFACE + "; -fx-text-fill: " + Theme.TEXT + ";"
                        + "-fx-border-color: " + Theme.BORDER + "; -fx-border-width: 1.5px;"
                        + "-fx-border-radius: " + Theme.RADIUS_SM + "px; -fx-background-radius: " + Theme.RADIUS_SM + "px;"
                        + "-fx-padding: " + (14 * zoom) + "px; -fx-cursor: hand; -fx-font-size: " + (13 * zoom) + "px;";
                String hovered = base.replace(Theme.SURFACE, Theme.BRAND_TINT).replace(Theme.BORDER, Theme.BRAND);
                Theme.hover(productBtn, base, hovered);
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
        if(checkingOut){showAlert("Payment in progress","Finish or cancel payment before scanning more products.",Alert.AlertType.WARNING);return;}
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
        panel.setPadding(new Insets(24));
        panel.setStyle(Theme.card());

        Label cartLabel = Theme.sectionTitleLabel("Shopping Cart");

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
                        "-fx-background-color: " + Theme.DANGER_TINT + "; -fx-text-fill: " + Theme.DANGER + ";"
                        + "-fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 4 10;"
                        + "-fx-background-radius: 5; -fx-cursor: hand;");
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
        totalText.setTextFill(Color.web(Theme.TEXT));

        totalLabel = new Label("R 0.00");
        totalLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        totalLabel.setTextFill(Color.web(Theme.SUCCESS));

        HBox totalBox = new HBox(totalText, new Region(), totalLabel);
        HBox.setHgrow(totalBox.getChildren().get(1), Priority.ALWAYS);

        HBox actionBox = new HBox(10);

        String clearBase = Theme.ghostButton() + "-fx-border-color: " + Theme.BORDER_STRONG + "; -fx-border-width: 1.5px;"
                + "-fx-border-radius: " + Theme.RADIUS_SM + "px; -fx-padding: 11 16;";
        String clearHover = Theme.ghostHover() + "-fx-border-color: " + Theme.BORDER_STRONG + "; -fx-border-width: 1.5px;"
                + "-fx-border-radius: " + Theme.RADIUS_SM + "px; -fx-padding: 11 16;";
        Button clearBtn = new Button("Clear Cart");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        Theme.hover(clearBtn, clearBase, clearHover);
        clearBtn.setOnAction(e -> clearCart());
        HBox.setHgrow(clearBtn, Priority.ALWAYS);

        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        Theme.hover(checkoutBtn, Theme.primaryButton() + "-fx-padding: 12 20;", Theme.primaryHover() + "-fx-padding: 12 20;");
        checkoutBtn.setOnAction(e -> processCheckout());
        HBox.setHgrow(checkoutBtn, Priority.ALWAYS);

        actionBox.getChildren().addAll(clearBtn, checkoutBtn);

        promotionSummary=new Label();promotionSummary.setWrapText(true);promotionSummary.setStyle("-fx-text-fill: "+Theme.TEXT_MUTED+";");
        panel.getChildren().addAll(cartLabel, cartTable, totalBox,promotionSummary, actionBox);

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
        long generation=++priceGeneration;
        if(cartItems.isEmpty()){totalLabel.setText("R 0.00");if(promotionSummary!=null)promotionSummary.setText("");return;}
        var snapshot=cartItems.stream().map(i->new CartItem(i.getProductId(),i.getProductName(),i.getPrice(),i.getQuantity())).toList();
        totalLabel.setText("Calculating...");
        java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return promotionService.quote(snapshot,"");}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((quote,error)->Platform.runLater(()->{
            if(generation!=priceGeneration)return;
            if(error!=null){totalLabel.setText("Unavailable");promotionSummary.setText("Promotion prices could not be loaded. Retry checkout to refresh.");return;}
            totalLabel.setText("R "+quote.total().toPlainString());promotionSummary.setText(quote.description());
        }));
    }

    private void processCheckout() {
        if(checkingOut)return;
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

        Customer customer;
        if (com.pos.Edition.current().isRetail()) {
            // No customer picker — book the sale against the shared walk-in account.
            customer = customerService.getOrCreateWalkInAccount(activeCashier.getStaffID());
            if (customer == null) {
                showAlert("Cannot Process Sale",
                        "The walk-in account could not be created. Check the log for details.",
                        Alert.AlertType.ERROR);
                return;
            }
        } else {
            customer = customerCombo.getValue();
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
        checkingOut=true;
        Optional<PaymentInfo> paymentResult;
        try {paymentResult=showPaymentDialog(cartTotal);}catch(RuntimeException ex){checkingOut=false;throw ex;}
        if (paymentResult.isEmpty()){checkingOut=false;return;}

        final PaymentInfo paymentInfo = paymentResult.get();
        final BigDecimal finalTotal = cartTotal.subtract(activeDiscount);
        final Customer saleCustomer = customer;
        final User saleCashier = activeCashier;
        final PromoService.Promo salePromo = activePromo;
        final BigDecimal saleDiscount = activeDiscount;
        final var saleQuote=activeQuote;
        final String saleCode=appliedPromotionCode;
        final ObservableList<CartItem> saleCart=FXCollections.observableArrayList(
                cartItems.stream().map(item->new CartItem(item.getProductId(),item.getProductName(),item.getPrice(),item.getQuantity())).toList());

        // Run the sale (DB write, receipt build, email) off the FX thread so the
        // register stays responsive, with a clear "please wait" indicator.
        Stage busy = showBusyDialog("Processing sale — please wait…",
                "Recording the transaction, updating stock and preparing the receipt.");

        Thread worker = new Thread(() -> {
            RuntimeException error = null;
            String receipt = null;
            File savedFile = null;
            boolean success = false;
            try {
                success = transactionService.processPromotedTransaction(
                        currentUser.getStaffID(), saleCashier.getStaffID(), saleCustomer.getAccountID(),
                        saleCart, paymentInfo, saleCode, saleQuote);

                if (success) {
                    receipt = ReceiptGenerator.generateReceipt(
                            saleCustomer, saleCart, saleCashier, paymentInfo,
                            saleQuote);
                    savedFile = ReceiptGenerator.saveReceiptToFile(
                            receipt, null);

                    if (com.pos.Edition.current().hasCustomers()) {
                        CommPreferences prefs = commService.getPreferences(saleCustomer.getAccountID());
                        if (prefs.isReceiptByEmail() && saleCustomer.getEmailAddress() != null
                                && !saleCustomer.getEmailAddress().isBlank()) {
                            commService.sendReceiptByEmail(saleCustomer.getEmailAddress(), receipt);
                        }
                    }
                }
            } catch (RuntimeException e) {
                error = e;
            }

            final boolean fSuccess = success;
            final String fReceipt = receipt;
            final RuntimeException fError = error;
            final File fSavedFile = savedFile;
            Platform.runLater(() -> {
                checkingOut=false;
                busy.close();
                if (fError != null && !fSuccess) {
                    com.pos.utils.Dialogs.error(
                            cartTable.getScene() != null ? cartTable.getScene().getWindow() : null,
                            "Sale not completed",
                            "Nothing was charged. " + (fError.getMessage() != null ? fError.getMessage() : ""),
                            fError);
                    return;
                }
                if (!fSuccess) {
                    showAlert("Sale not completed",
                            "Nothing was charged. "+(transactionService.getLastFailure()==null?"Please retry checkout.":transactionService.getLastFailure()),
                            Alert.AlertType.ERROR);
                    return;
                }

                // Retire the committed cart before any optional printer/delivery/dialog can fail.
                clearCart();
                if (customerCombo != null) customerCombo.setValue(null);
                activeCashier = currentUser;
                activeCashierLabel.setText("Cashier: " + activeCashier.getFullNames());
                updateSessionStatus();
                if (fError != null) showAlert("Sale saved", "The sale was saved, but receipt delivery failed. Do not repeat the sale. Use Sales history to retrieve it.", Alert.AlertType.WARNING);
                if (fReceipt != null && settingsService.getReceiptAutoPrint()) ReceiptGenerator.printReceipt(fReceipt);
                if (fReceipt != null && wifiHandler != null) {
                    new Thread(() -> wifiHandler.sendReceipt(fReceipt), "Receipt-Sender").start();
                }

                if(fReceipt!=null)showSaleCompleteDialog(fReceipt, finalTotal, paymentInfo, fSavedFile);

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

        String savedMsg = savedFile != null
                ? "Saved automatically to:  " + savedFile.getParent()
                : "Note: the receipt could not be saved to disk.";
        if (wifiHandler != null && wifiHandler.isConnected())
            savedMsg += "\nAlso sent to the connected scanner device.";
        Label saved = new Label(savedMsg);
        saved.setTextFill(Color.web(savedFile != null ? "#475569" : "#dc2626"));
        saved.setWrapText(true);

        TextArea receiptArea = new TextArea(receipt);
        receiptArea.setEditable(false);
        receiptArea.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12;");
        receiptArea.setPrefRowCount(16);

        Button printBtn = new Button("Print receipt");
        printBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        printBtn.setOnAction(e -> ReceiptGenerator.printReceipt(receipt));
        HBox actions = new HBox(printBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, ok, saved, new Separator(), receiptArea, actions);
        var logo = com.pos.utils.BrandAssets.logoMark(28);
        if (logo != null) {
            HBox logoRow = new HBox(logo);
            logoRow.setAlignment(Pos.CENTER);
            content.getChildren().add(logoRow);
        }
        content.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    // ── Payment dialog — now includes promo code field ────────────────────
    private Optional<PaymentInfo> showPaymentDialog(BigDecimal cartTotal) {
        appliedPromotionCode="";
        try {activeQuote=promotionService.quote(List.copyOf(cartItems),"");}
        catch(Exception ex){showAlert("Pricing unavailable",ex.getMessage(),Alert.AlertType.ERROR);return Optional.empty();}
        activeDiscount=activeQuote.discount();
        Label total=new Label("R "+activeQuote.total().toPlainString());total.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: "+Theme.BRAND+";");
        Label deals=new Label(activeQuote.description());deals.setWrapText(true);
        Label feedback=new Label();feedback.setWrapText(true);
        TextField code=new TextField();code.setPromptText("Optional coupon code");code.setStyle(Theme.input());
        Button apply=new Button("Apply code"),clear=new Button("Clear code");apply.setStyle(Theme.secondaryButton());clear.setStyle(Theme.secondaryButton());
        RadioButton cash=new RadioButton("Cash"),card=new RadioButton("Card");ToggleGroup methods=new ToggleGroup();cash.setToggleGroup(methods);card.setToggleGroup(methods);cash.setSelected(true);
        TextField amount=new TextField();amount.setPromptText("Amount received");Label change=new Label("Change: R0.00");
        Runnable refresh=()->{activeDiscount=activeQuote.discount();total.setText("R "+activeQuote.total().toPlainString());deals.setText(activeQuote.description());amount.clear();change.setText("Change: R0.00");};
        apply.setOnAction(e->{try {
            if(code.getText().isBlank())throw new IllegalArgumentException("Enter a coupon code.");
            var quote=promotionService.quote(List.copyOf(cartItems),code.getText());activeQuote=quote;appliedPromotionCode=code.getText().trim().toUpperCase(java.util.Locale.ROOT);
            refresh.run();code.setDisable(true);feedback.setText(quote.offers().stream().noneMatch(o->o.id().startsWith("COUPON_"))?"The best qualifying offers are shown below; a basket coupon may lose to better automatic offers.":"Coupon applied.");
        }catch(Exception ex){feedback.setText(ex.getMessage());}});
        clear.setOnAction(e->{try {activeQuote=promotionService.quote(List.copyOf(cartItems),"");appliedPromotionCode="";code.clear();code.setDisable(false);feedback.setText("");refresh.run();}catch(Exception ex){feedback.setText(ex.getMessage());}});
        amount.textProperty().addListener((o,a,b)->{try {BigDecimal difference=new BigDecimal(b).subtract(activeQuote.total());change.setText((difference.signum()<0?"Still due: R":"Change: R")+difference.abs().toPlainString());}catch(NumberFormatException ex){change.setText("Enter the amount received.");}});
        amount.disableProperty().bind(card.selectedProperty());
        var payment=com.pos.components.Ui.formGrid();com.pos.components.Ui.formField(payment,0,"Payment method",new HBox(16,cash,card));com.pos.components.Ui.formField(payment,1,"Amount received (R)",amount);
        PaymentInfo[] result={null};
        com.pos.components.Ui.createForm("Complete payment","Cashier: "+activeCashier.getFullNames(),new VBox(16,
                com.pos.components.Ui.section("Amount due",total,deals),
                com.pos.components.Ui.section("Optional coupon",new HBox(12,code,apply,clear),feedback),
                com.pos.components.Ui.section("Payment",payment,change)),"Process payment",()->{
            BigDecimal received=activeQuote.total(),changeGiven=BigDecimal.ZERO;
            if(cash.isSelected()) {
                try {received=new BigDecimal(amount.getText().trim());}
                catch(NumberFormatException ex){throw new IllegalArgumentException("Enter a valid amount received.");}
                if(received.signum()<0 || received.stripTrailingZeros().scale()>2)throw new IllegalArgumentException("Use a non-negative amount with at most two decimal places.");
                changeGiven=received.subtract(activeQuote.total());if(changeGiven.signum()<0)throw new IllegalArgumentException("The amount received is less than the total.");
            }
            result[0]=new PaymentInfo(cash.isSelected()?"Cash":"Card",received,changeGiven);return true;
        }).showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        com.pos.components.Ui.showAlert(cartTable, title, content, type);
    }

    public void cleanup() {
        ++priceGeneration;
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

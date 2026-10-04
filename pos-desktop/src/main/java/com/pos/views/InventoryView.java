package com.pos.views;

import com.pos.components.SummaryCards;
import com.pos.models.Product;
import com.pos.models.User;
import com.pos.services.CategoryService;
import com.pos.services.ProductService;
import com.pos.services.ReportService;
import com.pos.services.WiFiHandler;
import com.pos.utils.Icons;
import com.pos.utils.QRCodeUtil;
import com.pos.utils.Theme;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.util.Optional;

public class InventoryView {
    private User currentUser;
    private ProductService productService;
    private CategoryService categoryService;
    private TableView<Product> productTable;
    private ObservableList<Product> products;
    private TextField searchField;

    private WiFiHandler wifiHandler;
    private Label wifiStatusLabel;

    private final ReportService reportService = new ReportService();

    public InventoryView(User user) {
        this.currentUser = user;
        this.productService = new ProductService();
        this.categoryService = new CategoryService();
        this.wifiHandler = WiFiHandler.getInstance();

        initializeWiFiReceiver();
    }

    private HBox createSummaryCards() {
        int products = reportService.getTotalProducts();
        int units    = reportService.getTotalStockUnits();
        int low      = reportService.getLowStockCount();
        int out      = reportService.getOutOfStockCount();
        java.math.BigDecimal value = reportService.getTotalStockValue();
        return SummaryCards.row(
            new SummaryCards.Card("Products",     String.valueOf(products), "#0f766e", "in catalogue"),
            new SummaryCards.Card("Stock on Hand", String.format("%,d", units), "#16a34a", "units"),
            new SummaryCards.Card("Stock Value",  "R " + String.format("%,.2f", value == null ? 0.0 : value.doubleValue()), "#7c3aed", "at cost price"),
            new SummaryCards.Card("Needs Restock", low + " low · " + out + " out", low + out > 0 ? "#dc2626" : "#64748b", "low / out of stock")
        );
    }

    private void initializeWiFiReceiver() {
        new Thread(() -> {
            wifiHandler.setProductCallback(product -> Platform.runLater(() -> handleProductFromAndroid(product)));

            boolean started;
            if (wifiHandler.isServerRunning()) {
                started = true;
                System.out.println("WiFi already running - product callback registered on existing server");
            } else {
                started = wifiHandler.startListening(
                        null,
                        product -> Platform.runLater(() -> handleProductFromAndroid(product)));
            }

            Platform.runLater(() -> updateWiFiStatus(started));
        }, "WiFi-Inventory-Init").start();
    }

    private void handleProductFromAndroid(Product product) {
        System.out.println("➕ InventoryView: Product received from Android: " + product.getProductName());

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Add Product from Android Scanner");
        alert.setHeaderText("New product scanned from mobile device");
        alert.setContentText(
                "Product: " + product.getProductName() + "\n" +
                        "Category: " + product.getCategoryName() + "\n" +
                        "Barcode: " + product.getBarCode() + "\n" +
                        "Quantity: " + product.getQuantity() + "\n" +
                        "Price: R " + String.format("%.2f", product.getPrice()) + "\n\n" +
                        "Add this product to inventory?");

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            addProductToDatabase(product);
        } else {
            System.out.println("Product addition cancelled by user");
            wifiHandler.sendData("{\"type\":\"product_rejected\",\"message\":\"Product addition cancelled by staff\"}");
        }
    }

    private void addProductToDatabase(Product product) {
        int categoryId = categoryService.getCategoryId(product.getCategoryName());

        if (categoryId == -1) {
            Alert categoryAlert = new Alert(Alert.AlertType.CONFIRMATION);
            categoryAlert.setTitle("Create New Category");
            categoryAlert.setHeaderText("Category '" + product.getCategoryName() + "' does not exist");
            categoryAlert.setContentText("Would you like to create this category?");

            Optional<ButtonType> categoryResult = categoryAlert.showAndWait();

            if (categoryResult.isPresent() && categoryResult.get() == ButtonType.OK) {
                System.out.println("📁 Creating new category: " + product.getCategoryName());

                boolean categoryCreated = categoryService.addCategory(product.getCategoryName());

                if (categoryCreated) {
                    categoryId = categoryService.getCategoryId(product.getCategoryName());
                    System.out.println("Category created with ID: " + categoryId);
                } else {
                    System.err.println("Failed to create category");
                    showAlert("Error", "Failed to create category: " + product.getCategoryName(),
                            Alert.AlertType.ERROR);
                    wifiHandler.sendData("{\"type\":\"error\",\"message\":\"Failed to create category\"}");
                    return;
                }
            } else {
                System.out.println("Category creation cancelled");
                wifiHandler.sendData("{\"type\":\"product_rejected\",\"message\":\"Category creation cancelled\"}");
                return;
            }
        }

        product.setCategoryID(categoryId);
        product.setStaffID(currentUser.getStaffID());

        boolean success = productService.addProduct(product);

        if (success) {
            System.out.println("Product added to database: " + product.getProductName());
            System.out.println("   Product ID: " + product.getProductID());
            System.out.println("   QR Code: " + product.getQrCode());

            showAlert("Success",
                    "Product added successfully from mobile scanner!\n\n" +
                            "Product: " + product.getProductName() + "\n" +
                            "Product ID: " + product.getProductID() + "\n" +
                            "Barcode: " + product.getBarCode() + "\n" +
                            "Category: " + product.getCategoryName(),
                    Alert.AlertType.INFORMATION);

            wifiHandler.sendData(
                    String.format(
                            "{\"type\":\"product_added\"," +
                                    "\"message\":\"Product added successfully\"," +
                                    "\"product_id\":%d," +
                                    "\"qr_code\":\"%s\"}",
                            product.getProductID(),
                            product.getQrCode()));

            loadProducts();

        } else {
            System.err.println("Failed to add product to database");
            showAlert("Error", "Failed to add product to database", Alert.AlertType.ERROR);
            wifiHandler.sendData("{\"type\":\"error\",\"message\":\"Database error - failed to add product\"}");
        }
    }

    private void updateWiFiStatus(boolean started) {
        if (wifiStatusLabel != null) {
            Platform.runLater(() -> {
                if (started) {
                    wifiStatusLabel.setText("WiFi Ready — Listening for product additions from Android");
                    wifiStatusLabel.setTextFill(Color.web(Theme.SUCCESS));
                } else {
                    wifiStatusLabel.setText("WiFi Error — Cannot receive products from Android");
                    wifiStatusLabel.setTextFill(Color.web(Theme.DANGER));
                }
            });
        }
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle(Theme.page());
        layout.setTop(createTopBar());

        // ── Products tab ────────────────────────────────────────────────
        VBox productsContent = new VBox(20);
        productsContent.setPadding(new Insets(24));
        productsContent.getChildren().addAll(
            createSummaryCards(), createWiFiStatusBox(), createLowStockAlert(), createProductTable());

        ScrollPane productsScroll = new ScrollPane(productsContent);
        productsScroll.setFitToWidth(true);
        productsScroll.setStyle("-fx-background: " + Theme.BG + "; -fx-background-color: " + Theme.BG + ";");

        // ── Promo Codes tab ────────────────────────────────────────────
        ScrollPane promoScroll = new ScrollPane(new PromoView(currentUser).getView());
        promoScroll.setFitToWidth(true);
        promoScroll.setStyle("-fx-background: " + Theme.BG + "; -fx-background-color: " + Theme.BG + ";");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Products", productsScroll),
            new Tab("Promo Codes", promoScroll));

        layout.setCenter(tabs);
        return layout;
    }

    private HBox createWiFiStatusBox() {
        HBox statusBox = new HBox(10);
        statusBox.setPadding(new Insets(12));
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setStyle(
                "-fx-background-color: " + Theme.SUCCESS_TINT + "; -fx-background-radius: " + Theme.RADIUS_SM + "px;" +
                "-fx-border-color: " + Theme.SUCCESS + "; -fx-border-width: 1; -fx-border-radius: " + Theme.RADIUS_SM + "px;");

        Node icon = Icons.tinted(Icons.wifi(18), Theme.SUCCESS);

        wifiStatusLabel = new Label("WiFi: Checking...");
        wifiStatusLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        wifiStatusLabel.setTextFill(Color.web(Theme.SUCCESS));

        Label info = new Label("Android app can now add products directly to inventory");
        info.setFont(Font.font("System", 11));
        info.setTextFill(Color.web(Theme.SUCCESS));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBox.getChildren().addAll(icon, wifiStatusLabel, new Label("|"), info, spacer);
        return statusBox;
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(18, 24, 18, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(Theme.topBar());

        Label title = Theme.pageTitleLabel("Inventory Management");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search products...");
        searchField.setPrefWidth(300);
        searchField.setStyle(Theme.input());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchProducts(newVal));

        Button addBtn = new Button("+ Add Product");
        Theme.hover(addBtn, Theme.primaryButton(), Theme.primaryHover());
        addBtn.setOnAction(e -> showAddProductDialog());

        Button categoriesBtn = new Button("Categories");
        Theme.hover(categoriesBtn, Theme.secondaryButton(), Theme.secondaryHover());
        categoriesBtn.setOnAction(e -> showCategoriesDialog());

        topBar.getChildren().addAll(title, spacer, searchField, categoriesBtn, addBtn);
        return topBar;
    }

    private HBox createLowStockAlert() {
        HBox alertBox = new HBox(15);
        alertBox.setPadding(new Insets(15));
        alertBox.setAlignment(Pos.CENTER_LEFT);
        alertBox.setStyle(
                "-fx-background-color: " + Theme.WARNING_TINT + "; -fx-background-radius: " + Theme.RADIUS_SM + "px;" +
                "-fx-border-color: " + Theme.WARNING + "; -fx-border-width: 1; -fx-border-radius: " + Theme.RADIUS_SM + "px;");

        Node icon = Icons.tinted(Icons.warning(20), Theme.WARNING);

        ObservableList<Product> lowStock = productService.getLowStockProducts();

        Label message = new Label("Low Stock Alert: " + lowStock.size() + " product(s) need restocking");
        message.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        message.setTextFill(Color.web(Theme.WARNING));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button viewBtn = new Button("View Low Stock");
        viewBtn.setStyle(
                "-fx-background-color: " + Theme.WARNING + "; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-padding: 8 16; -fx-background-radius: " + Theme.RADIUS_SM + "px; -fx-cursor: hand;");
        viewBtn.setOnAction(e -> showLowStockProducts());

        alertBox.getChildren().addAll(icon, message, spacer, viewBtn);

        if (lowStock.isEmpty()) {
            alertBox.setVisible(false);
            alertBox.setManaged(false);
        }

        return alertBox;
    }

    private VBox createProductTable() {
        VBox tableBox = new VBox(15);
        tableBox.setStyle(Theme.card());
        tableBox.setPadding(new Insets(24));

        Label tableTitle = Theme.sectionTitleLabel("Product List");

        productTable = new TableView<>();
        productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        productTable.setPrefHeight(420);

        TableColumn<Product, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("productID"));
        idCol.setPrefWidth(60);

        TableColumn<Product, String> nameCol = new TableColumn<>("Product Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("productName"));
        nameCol.setPrefWidth(200);

        TableColumn<Product, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        categoryCol.setPrefWidth(120);

        TableColumn<Product, String> barcodeCol = new TableColumn<>("Barcode");
        barcodeCol.setCellValueFactory(new PropertyValueFactory<>("barCode"));
        barcodeCol.setPrefWidth(120);

        TableColumn<Product, Integer> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        quantityCol.setPrefWidth(100);
        quantityCol.setCellFactory(column -> new TableCell<Product, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    if (item < 10) {
                        setStyle("-fx-background-color: " + Theme.DANGER_TINT + "; -fx-text-fill: " + Theme.DANGER + ";");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        TableColumn<Product, Integer> soldCol = new TableColumn<>("Sold");
        soldCol.setCellValueFactory(new PropertyValueFactory<>("noSold"));
        soldCol.setPrefWidth(80);

        TableColumn<Product, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(100);
        priceCol.setCellFactory(column -> new TableCell<Product, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("R " + String.format("%.2f", item));
                }
            }
        });

        TableColumn<Product, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = com.pos.components.Ui.viewButton();

            {
                viewBtn.setOnAction(e -> {
                    Product product = getTableView().getItems().get(getIndex());
                    showProductDetailsDialog(product);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        productTable.getColumns().addAll(
            idCol, nameCol, categoryCol, barcodeCol,
            quantityCol, soldCol, priceCol, actionCol
        );

        loadProducts();

        tableBox.getChildren().addAll(tableTitle, productTable);
        return tableBox;
    }

    private void loadProducts() {
        products = productService.getAllProducts();
        productTable.setItems(products);
    }

    private void searchProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            loadProducts();
        } else {
            ObservableList<Product> results = productService.searchProducts(keyword);
            productTable.setItems(results);
        }
    }

    private void showLowStockProducts() {
        ObservableList<Product> lowStock = productService.getLowStockProducts();
        productTable.setItems(lowStock);
    }

    private void showProductDetailsDialog(Product product) {
        VBox summary = new VBox(10);
        summary.getChildren().addAll(
            com.pos.components.Ui.detailRow("Category:", product.getCategoryName()),
            com.pos.components.Ui.detailRow("Barcode:", product.getBarCode()),
            com.pos.components.Ui.detailRow("Quantity in stock:", String.valueOf(product.getQuantity())
                + (product.getQuantity() < 10 ? "  (low stock)" : "")),
            com.pos.components.Ui.detailRow("Units sold:", String.valueOf(product.getNoSold())),
            com.pos.components.Ui.detailRow("Price:", "R " + String.format("%.2f", product.getPrice()))
        );

        Button viewQRBtn  = com.pos.components.Ui.actionButton("Show QR", Theme.INFO, "Show / print the product QR code");
        Button editBtn    = com.pos.components.Ui.actionButton("Edit", Theme.WARNING, "Edit product details");
        Button restockBtn = com.pos.components.Ui.actionButton("Restock", Theme.SUCCESS, "Add stock for this product");
        Button deleteBtn  = com.pos.components.Ui.actionButton("Delete", Theme.DANGER, "Delete this product");

        viewQRBtn.setOnAction(e -> showQRCode(product));
        editBtn.setOnAction(e -> showEditProductDialog(product));
        restockBtn.setOnAction(e -> showRestockDialog(product));
        deleteBtn.setOnAction(e -> deleteProduct(product));

        com.pos.components.Ui.showDetailDialog("Product Details", product.getProductName(), summary,
            viewQRBtn, editBtn, restockBtn, deleteBtn);
    }

    private void showQRCode(Product product) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Product QR Code");
        dialog.setHeaderText(product.getProductName());

        VBox content = new VBox(15);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));

        ImageView qrImageView = new ImageView(QRCodeUtil.getQRCodeImage(product.getQrCode()));
        qrImageView.setFitWidth(300);
        qrImageView.setFitHeight(300);

        Label barcodeLabel = new Label("Barcode: " + product.getBarCode());
        barcodeLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        content.getChildren().addAll(qrImageView, barcodeLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showAddProductDialog() {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Add New Product");
        dialog.setHeaderText("Enter product details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField     = new TextField();
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.setItems(categoryService.getAllCategories());
        TextField barcodeField  = new TextField();
        TextField quantityField = new TextField();
        TextField priceField    = new TextField();

        grid.add(new Label("Product Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Barcode:"), 0, 2);
        grid.add(barcodeField, 1, 2);
        grid.add(new Label("Quantity:"), 0, 3);
        grid.add(quantityField, 1, 3);
        grid.add(new Label("Price (R):"), 0, 4);
        grid.add(priceField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    Product product = new Product();
                    product.setStaffID(currentUser.getStaffID());
                    product.setProductName(nameField.getText());
                    product.setCategoryID(categoryService.getCategoryId(categoryCombo.getValue()));
                    product.setBarCode(barcodeField.getText());
                    product.setQuantity(Integer.parseInt(quantityField.getText()));
                    product.setPrice(new BigDecimal(priceField.getText()));
                    return product;
                } catch (NumberFormatException e) {
                    showAlert("Error", "Invalid number format", Alert.AlertType.ERROR);
                    return null;
                }
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(product -> {
            if (productService.addProduct(product)) {
                showAlert("Success", "Product added successfully", Alert.AlertType.INFORMATION);
                loadProducts();
            } else {
                showAlert("Error", "Failed to add product", Alert.AlertType.ERROR);
            }
        });
    }

    private void showEditProductDialog(Product product) {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Edit Product");
        dialog.setHeaderText("Modify product details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField     = new TextField(product.getProductName());
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.setItems(categoryService.getAllCategories());
        categoryCombo.setValue(product.getCategoryName());
        TextField barcodeField  = new TextField(product.getBarCode());
        TextField quantityField = new TextField(String.valueOf(product.getQuantity()));
        TextField priceField    = new TextField(product.getPrice().toString());

        grid.add(new Label("Product Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Barcode:"), 0, 2);
        grid.add(barcodeField, 1, 2);
        grid.add(new Label("Quantity:"), 0, 3);
        grid.add(quantityField, 1, 3);
        grid.add(new Label("Price (R):"), 0, 4);
        grid.add(priceField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    product.setProductName(nameField.getText());
                    product.setCategoryID(categoryService.getCategoryId(categoryCombo.getValue()));
                    product.setBarCode(barcodeField.getText());
                    product.setQuantity(Integer.parseInt(quantityField.getText()));
                    product.setPrice(new BigDecimal(priceField.getText()));
                    return product;
                } catch (NumberFormatException e) {
                    showAlert("Error", "Invalid number format", Alert.AlertType.ERROR);
                    return null;
                }
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(p -> {
            if (productService.updateProduct(p)) {
                showAlert("Success", "Product updated successfully", Alert.AlertType.INFORMATION);
                loadProducts();
            } else {
                showAlert("Error", "Failed to update product", Alert.AlertType.ERROR);
            }
        });
    }

    private void showRestockDialog(Product product) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Restock Product");
        dialog.setHeaderText("Restock: " + product.getProductName());
        dialog.setContentText("Current Stock: " + product.getQuantity() + "\nEnter quantity to add:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(quantity -> {
            try {
                int qty = Integer.parseInt(quantity);
                if (qty > 0) {
                    product.setQuantity(product.getQuantity() + qty);
                    if (productService.updateProduct(product)) {
                        showAlert("Success", "Product restocked successfully", Alert.AlertType.INFORMATION);
                        loadProducts();
                    } else {
                        showAlert("Error", "Failed to restock product", Alert.AlertType.ERROR);
                    }
                } else {
                    showAlert("Error", "Quantity must be positive", Alert.AlertType.ERROR);
                }
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid quantity", Alert.AlertType.ERROR);
            }
        });
    }

    private void deleteProduct(Product product) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Product");
        alert.setHeaderText("Are you sure you want to delete this product?");
        alert.setContentText(product.getProductName());

        if (alert.showAndWait().get() == ButtonType.OK) {
            if (productService.deleteProduct(product.getProductID())) {
                showAlert("Success", "Product deleted successfully", Alert.AlertType.INFORMATION);
                loadProducts();
            } else {
                showAlert("Error", "Failed to delete product", Alert.AlertType.ERROR);
            }
        }
    }

    private void showCategoriesDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Category Management");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(500);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label title = Theme.sectionTitleLabel("Manage Categories");

        HBox addRow = new HBox(10);
        addRow.setAlignment(Pos.CENTER_LEFT);

        TextField newCategoryField = new TextField();
        newCategoryField.setPromptText("New category name...");
        newCategoryField.setPrefWidth(280);
        newCategoryField.setStyle(Theme.input());
        HBox.setHgrow(newCategoryField, Priority.ALWAYS);

        Button addCategoryBtn = new Button("+ Add");
        Theme.hover(addCategoryBtn, Theme.primaryButton(), Theme.primaryHover());

        addRow.getChildren().addAll(newCategoryField, addCategoryBtn);

        ListView<javafx.util.Pair<Integer, String>> categoryListView = new ListView<>();
        categoryListView.setPrefHeight(300);
        categoryListView.setStyle("-fx-background-radius: " + Theme.RADIUS_SM + "px; -fx-border-color: " + Theme.BORDER + "; -fx-border-radius: " + Theme.RADIUS_SM + "px;");

        Runnable refreshList = () -> categoryListView.setItems(categoryService.getAllCategoriesWithId());
        refreshList.run();

        categoryListView.setCellFactory(lv -> new ListCell<>() {
            private final Label nameLabel = new Label();
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox row = new HBox(10, nameLabel, new Region(), editBtn, deleteBtn);

            {
                HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 8, 4, 8));

                editBtn.setStyle(
                        "-fx-background-color: " + Theme.WARNING + "; -fx-text-fill: white;" +
                        "-fx-font-size: 11; -fx-padding: 5 10; -fx-background-radius: 4; -fx-cursor: hand;");
                deleteBtn.setStyle(
                        "-fx-background-color: " + Theme.DANGER + "; -fx-text-fill: white;" +
                        "-fx-font-size: 11; -fx-padding: 5 10; -fx-background-radius: 4; -fx-cursor: hand;");
                nameLabel.setFont(Font.font("System", 13));

                editBtn.setOnAction(e -> {
                    javafx.util.Pair<Integer, String> item = getItem();
                    if (item == null) return;

                    TextInputDialog editDialog = new TextInputDialog(item.getValue());
                    editDialog.setTitle("Edit Category");
                    editDialog.setHeaderText("Rename category");
                    editDialog.setContentText("New name:");

                    editDialog.showAndWait().ifPresent(newName -> {
                        String trimmed = newName.trim();
                        if (trimmed.isEmpty()) {
                            showAlert("Error", "Category name cannot be empty.", Alert.AlertType.ERROR);
                            return;
                        }
                        if (categoryService.updateCategory(item.getKey(), trimmed)) {
                            showAlert("Success", "Category renamed to \"" + trimmed + "\".",
                                    Alert.AlertType.INFORMATION);
                            refreshList.run();
                            loadProducts();
                        } else {
                            showAlert("Error", "Failed to rename category.", Alert.AlertType.ERROR);
                        }
                    });
                });

                deleteBtn.setOnAction(e -> {
                    javafx.util.Pair<Integer, String> item = getItem();
                    if (item == null) return;

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete Category");
                    confirm.setHeaderText("Delete \"" + item.getValue() + "\"?");
                    confirm.setContentText(
                            "This will fail if any products are assigned to this category.\n" +
                            "Reassign or delete those products first.");

                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            boolean deleted = categoryService.deleteCategory(item.getKey());
                            if (deleted) {
                                showAlert("Success", "Category deleted.", Alert.AlertType.INFORMATION);
                                refreshList.run();
                            } else {
                                showAlert("Cannot Delete",
                                        "\"" + item.getValue() + "\" has products assigned to it.\n" +
                                        "Please reassign or delete those products first.",
                                        Alert.AlertType.WARNING);
                            }
                        }
                    });
                });
            }

            @Override
            protected void updateItem(javafx.util.Pair<Integer, String> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getValue());
                    setGraphic(row);
                }
            }
        });

        addCategoryBtn.setOnAction(e -> {
            String name = newCategoryField.getText().trim();
            if (name.isEmpty()) {
                showAlert("Error", "Please enter a category name.", Alert.AlertType.ERROR);
                return;
            }
            if (categoryService.addCategory(name)) {
                newCategoryField.clear();
                refreshList.run();
                showAlert("Success", "Category \"" + name + "\" added.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Error", "Failed to add category. It may already exist.", Alert.AlertType.ERROR);
            }
        });

        newCategoryField.setOnAction(e -> addCategoryBtn.fire());

        Label countLabel = new Label();
        countLabel.setFont(Font.font("System", 11));
        countLabel.setTextFill(Color.web("#64748b"));

        categoryListView.itemsProperty().addListener((obs, o, n) -> {
            int count = n == null ? 0 : n.size();
            countLabel.setText(count + " categor" + (count == 1 ? "y" : "ies") + " total");
        });
        countLabel.setText(categoryListView.getItems().size() + " categor" +
                (categoryListView.getItems().size() == 1 ? "y" : "ies") + " total");

        content.getChildren().addAll(title, addRow, categoryListView, countLabel);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    public void cleanup() {
        wifiHandler.setProductCallback(null);
        System.out.println("🧹 InventoryView: product callback cleared");
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        com.pos.components.Ui.showAlert(productTable, title, content, type);
    }
}
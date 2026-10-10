package com.pos.views;

import com.pos.components.SummaryCards;
import com.pos.models.Product;
import com.pos.models.User;
import com.pos.services.CategoryService;
import com.pos.services.InventoryExcelService;
import com.pos.services.ProductService;
import com.pos.services.ReportService;
import com.pos.services.WiFiHandler;
import com.pos.utils.Icons;
import com.pos.utils.Theme;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InventoryView {
    private User currentUser;
    private ProductService productService;
    private CategoryService categoryService;
    private TableView<Product> productTable;
    private Button importBtnRef;
    private ComboBox<String> categoryFilterCombo;
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
            // Must boot the socket (if it isn't already) BEFORE setting the
            // callback below — startListening() unconditionally overwrites
            // both the scan and product callbacks itself, so calling it
            // afterwards (or from the "not yet running" branch only, as this
            // used to) would wipe out whichever callback the OTHER screen
            // (e.g. Sales's barcode-scan handler) had already registered.
            boolean started = wifiHandler.isServerRunning() || wifiHandler.startListening(null, null);

            wifiHandler.setProductCallback(product -> Platform.runLater(() -> handleProductFromAndroid(product)));

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
                        "Category: " + (product.getCategoryName() != null ? product.getCategoryName() : "(none selected)") + "\n" +
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
        // The phone picks from the real category list now (fed to it over
        // WiFi), so use whatever name it sent — falling back to the default
        // only if it's missing or doesn't match anything (e.g. an older
        // phone build still mid-upgrade).
        String categoryName = product.getCategoryName();
        int categoryId = (categoryName != null && !categoryName.isBlank())
                ? categoryService.getCategoryId(categoryName) : 0;
        product.setCategoryID(categoryId > 0 ? categoryId : categoryService.getOrCreateDefaultCategoryId());
        product.setStaffID(currentUser.getStaffID());
        // The phone only ever sends a selling price, never a cost price —
        // CostPrice is NOT NULL, so leaving this unset crashed the insert for
        // every product added from the Android scanner. Defaults to 0 until
        // someone edits it in Inventory with the real purchase price.
        if (product.getCostPrice() == null) product.setCostPrice(BigDecimal.ZERO);

        boolean success = productService.addProduct(product);

        if (success) {
            System.out.println("Product added to database: " + product.getProductName());
            System.out.println("   Product ID: " + product.getProductID());
            System.out.println("   QR Code: " + product.getQrCode());

            showAlert("Success",
                    "Product added successfully from mobile scanner!\n\n" +
                            "Product: " + product.getProductName() + "\n" +
                            "Product ID: " + product.getProductID() + "\n" +
                            "Barcode: " + product.getBarCode(),
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

        // The search box is the flexible item: it shrinks first when the window
        // is narrow, so the buttons beside it always keep their full text.
        searchField = new TextField();
        searchField.setPromptText("Search products...");
        searchField.setPrefWidth(260);
        searchField.setMinWidth(140);
        searchField.setStyle(Theme.input());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchProducts(newVal));
        HBox.setHgrow(searchField, Priority.SOMETIMES);

        categoryFilterCombo = new ComboBox<>();
        categoryFilterCombo.setPromptText("All Categories");
        categoryFilterCombo.setStyle(Theme.input());
        categoryFilterCombo.setMinWidth(Region.USE_PREF_SIZE);
        categoryFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        Button categoriesBtn = new Button("Manage Categories");
        Theme.hover(categoriesBtn, Theme.secondaryButton(), Theme.secondaryHover());
        categoriesBtn.setMinWidth(Region.USE_PREF_SIZE);
        categoriesBtn.setOnAction(e -> showCategoriesDialog());

        Button addBtn = new Button("+ Add Product");
        Theme.hover(addBtn, Theme.primaryButton(), Theme.primaryHover());
        addBtn.setMinWidth(Region.USE_PREF_SIZE);
        addBtn.setOnAction(e -> showAddProductDialog());

        // Import / Export now live in the Product List header (see createProductTable).
        topBar.getChildren().addAll(title, spacer, searchField, categoryFilterCombo, categoriesBtn, addBtn);
        refreshCategoryFilterItems();
        return topBar;
    }

    private void refreshCategoryFilterItems() {
        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("All Categories");
        items.addAll(categoryService.getAllCategories());
        String previous = categoryFilterCombo.getValue();
        categoryFilterCombo.setItems(items);
        categoryFilterCombo.setValue(items.contains(previous) ? previous : "All Categories");
    }

    // ── Excel export / import ────────────────────────────────────────────────

    private void exportInventory() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export inventory to Excel");
        chooser.setInitialFileName("Inventory_" +
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_DATE) + ".xlsx");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        java.io.File target = chooser.showSaveDialog(productTable.getScene().getWindow());
        if (target == null) return;

        ObservableList<Product> toExport = productTable.getItems();

        new Thread(() -> {
            try {
                new InventoryExcelService().exportToExcel(toExport, target);
                Platform.runLater(() -> com.pos.components.Ui.showAlert(productTable,
                        "Exported", "Saved " + toExport.size() + " products to " + target.getName(),
                        Alert.AlertType.INFORMATION));
            } catch (Exception ex) {
                com.pos.utils.Dialogs.error("Export failed",
                        "Could not export inventory to Excel: " + ex.getMessage(), ex);
            }
        }, "Inventory-Export").start();
    }

    /**
     * Import is two-phase: the file is analysed read-only first, the user is
     * asked about any new categories and shown a full review of what will be
     * added, and only after they confirm is anything written to the database.
     */
    private void importInventory() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Import inventory from Excel");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx", "*.xls"));
        java.io.File source = chooser.showOpenDialog(productTable.getScene().getWindow());
        if (source == null) return;

        importBtnRef.setDisable(true);
        importBtnRef.setText("Reading file…");

        new Thread(() -> {
            try {
                InventoryExcelService service = new InventoryExcelService();
                InventoryExcelService.ImportPlan plan = service.analyze(source); // read-only
                Platform.runLater(() -> {
                    resetImportButton();
                    reviewAndCommitImport(service, plan);
                });
            } catch (Exception ex) {
                Platform.runLater(this::resetImportButton);
                com.pos.utils.Dialogs.error("Import failed",
                        "Could not read that file as an Excel inventory sheet: " + ex.getMessage(), ex);
            }
        }, "Inventory-Import-Analyze").start();
    }

    private void resetImportButton() {
        importBtnRef.setDisable(false);
        importBtnRef.setText("Import from Excel");
    }

    private void reviewAndCommitImport(InventoryExcelService service, InventoryExcelService.ImportPlan plan) {
        // Nothing importable — just report why, don't ask anything.
        if (plan.toAdd().isEmpty()) {
            showImportResult(new InventoryExcelService.ImportResult(
                    List.of(), plan.duplicates(), plan.invalidRows()));
            return;
        }

        // Step 1: new categories — ask before anything is created.
        Set<String> approved = new LinkedHashSet<>();
        if (!plan.newCategoryNames().isEmpty()) {
            Optional<Set<String>> choice = askAboutNewCategories(plan);
            if (choice.isEmpty()) return; // user cancelled the whole import
            approved = choice.get();
        }
        final Set<String> approvedFinal = approved;

        // Step 2: full review of what will be added — nothing is written until OK.
        if (!confirmImportReview(plan, approvedFinal)) return;

        importBtnRef.setDisable(true);
        importBtnRef.setText("Importing…");

        new Thread(() -> {
            try {
                var result = service.commit(plan, approvedFinal, currentUser.getStaffID());
                Platform.runLater(() -> {
                    resetImportButton();
                    loadProducts();
                    showImportResult(result);
                });
            } catch (Exception ex) {
                Platform.runLater(this::resetImportButton);
                com.pos.utils.Dialogs.error("Import failed",
                        "Import stopped part-way: " + ex.getMessage(), ex);
            }
        }, "Inventory-Import-Commit").start();
    }

    /** One checkbox per distinct new category (case-insensitive), with a
     *  "did you mean…?" hint when it looks like a typo of an existing one. */
    private Optional<Set<String>> askAboutNewCategories(InventoryExcelService.ImportPlan plan) {
        // Collapse "Dairy" / "dairy" into one entry, remembering how many rows use it.
        Map<String, String> displayByLower = new LinkedHashMap<>();
        Map<String, Integer> rowCount = new LinkedHashMap<>();
        for (String name : plan.newCategoryNames()) {
            displayByLower.putIfAbsent(name.toLowerCase(), name);
        }
        for (var row : plan.toAdd()) {
            String c = row.categoryName();
            if (c != null && displayByLower.containsKey(c.trim().toLowerCase())) {
                rowCount.merge(c.trim().toLowerCase(), 1, Integer::sum);
            }
        }

        ObservableList<String> existing = categoryService.getAllCategories();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Categories Found");
        dialog.setHeaderText("This sheet uses categories that don't exist yet");

        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        Label intro = new Label(
                "Tick the ones you want created. If one is a typo, leave it unticked — "
              + "those products will go into the default category instead, and you can "
              + "fix the sheet and re-import if you prefer.");
        intro.setWrapText(true);
        intro.setPrefWidth(460);
        box.getChildren().add(intro);

        Map<CheckBox, String> boxes = new LinkedHashMap<>();
        for (var e : displayByLower.entrySet()) {
            String display = e.getValue();
            int count = rowCount.getOrDefault(e.getKey(), 0);
            String label = "\"" + display + "\"  —  " + count + " product" + (count == 1 ? "" : "s");

            String similar = null;
            for (String ex : existing) {
                if (levenshtein(ex.toLowerCase(), e.getKey()) <= 2) { similar = ex; break; }
            }
            if (similar != null) label += "   (did you mean existing \"" + similar + "\"?)";

            CheckBox cb = new CheckBox(label);
            cb.setSelected(similar == null); // likely typos start unticked
            boxes.put(cb, display);
            box.getChildren().add(cb);
        }

        ButtonType continueType = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(continueType, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != continueType) return Optional.empty();

        Set<String> approved = new LinkedHashSet<>();
        boxes.forEach((cb, name) -> { if (cb.isSelected()) approved.add(name); });
        return Optional.of(approved);
    }

    /** Shows every row that will be inserted (with the category each will land
     *  in), plus duplicates/invalid rows that will be skipped. True = go ahead. */
    private boolean confirmImportReview(InventoryExcelService.ImportPlan plan, Set<String> approvedNew) {
        Set<String> approvedLower = new LinkedHashSet<>();
        for (String s : approvedNew) approvedLower.add(s.toLowerCase());
        Set<String> existingLower = new LinkedHashSet<>();
        for (String s : categoryService.getAllCategories()) existingLower.add(s.trim().toLowerCase());

        TableView<InventoryExcelService.PendingRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefSize(720, 300);
        table.setItems(FXCollections.observableArrayList(plan.toAdd()));

        TableColumn<InventoryExcelService.PendingRow, String> rowCol = new TableColumn<>("Row");
        rowCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().rowNumber())));
        rowCol.setMaxWidth(60);

        TableColumn<InventoryExcelService.PendingRow, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().productName()));

        TableColumn<InventoryExcelService.PendingRow, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> {
            String cat = c.getValue().categoryName();
            String key = cat == null ? "" : cat.trim().toLowerCase();
            String shown;
            if (key.isEmpty())                    shown = "(default)";
            else if (existingLower.contains(key)) shown = cat;
            else if (approvedLower.contains(key)) shown = cat + "  (new)";
            else                                  shown = "(default)  — \"" + cat + "\" not added";
            return new SimpleStringProperty(shown);
        });

        TableColumn<InventoryExcelService.PendingRow, String> barcodeCol = new TableColumn<>("Barcode");
        barcodeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().barcode()));

        TableColumn<InventoryExcelService.PendingRow, String> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().quantity())));
        qtyCol.setMaxWidth(70);

        TableColumn<InventoryExcelService.PendingRow, String> costCol = new TableColumn<>("Cost");
        costCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("R %.2f", c.getValue().costPrice())));

        TableColumn<InventoryExcelService.PendingRow, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("R %.2f", c.getValue().price())));

        table.getColumns().addAll(rowCol, nameCol, catCol, barcodeCol, qtyCol, costCol, priceCol);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.getChildren().add(new Label(plan.toAdd().size() + " product(s) will be added:"));
        content.getChildren().add(table);

        if (!approvedNew.isEmpty()) {
            content.getChildren().add(new Label("New categories to be created: " + String.join(", ", approvedNew)));
        }

        if (!plan.duplicates().isEmpty() || !plan.invalidRows().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (!plan.duplicates().isEmpty()) {
                sb.append("DUPLICATE BARCODES (skipped)\n");
                for (var d : plan.duplicates())
                    sb.append("  • Row ").append(d.rowNumber()).append(": ")
                      .append(d.productName()).append(" (").append(d.barcode()).append(")\n");
                sb.append('\n');
            }
            if (!plan.invalidRows().isEmpty()) {
                sb.append("INVALID ROWS (skipped)\n");
                for (String line : plan.invalidRows()) sb.append("  • ").append(line).append('\n');
            }
            TextArea skipped = new TextArea(sb.toString());
            skipped.setEditable(false);
            skipped.setPrefHeight(130);
            TitledPane pane = new TitledPane(
                    (plan.duplicates().size() + plan.invalidRows().size()) + " row(s) will be skipped", skipped);
            pane.setExpanded(true);
            content.getChildren().add(pane);
        }

        ButtonType importType = new ButtonType("Import " + plan.toAdd().size() + " product(s)",
                ButtonBar.ButtonData.OK_DONE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Review Import");
        dialog.setHeaderText("Check this is correct — nothing has been added yet");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(importType, ButtonType.CANCEL);
        dialog.setResizable(true);

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == importType;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    private void showImportResult(InventoryExcelService.ImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Added: ").append(result.added().size()).append('\n');
        sb.append("Duplicates skipped (already in inventory): ").append(result.duplicates().size()).append('\n');
        sb.append("Invalid rows skipped: ").append(result.invalidRows().size()).append("\n\n");

        if (!result.added().isEmpty()) {
            sb.append("ADDED\n");
            for (var r : result.added()) sb.append("  • ").append(r.productName()).append(" (").append(r.barcode()).append(")\n");
            sb.append('\n');
        }
        if (!result.duplicates().isEmpty()) {
            sb.append("DUPLICATES (skipped — already in inventory)\n");
            for (var r : result.duplicates()) sb.append("  • ").append(r.productName()).append(" (").append(r.barcode()).append(")\n");
            sb.append('\n');
        }
        if (!result.invalidRows().isEmpty()) {
            sb.append("SKIPPED (invalid row)\n");
            for (String line : result.invalidRows()) sb.append("  • ").append(line).append('\n');
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import Complete");
        alert.setHeaderText(result.added().size() + " product(s) added, "
                + result.duplicates().size() + " duplicate(s) skipped, "
                + result.invalidRows().size() + " invalid row(s) skipped.");

        TextArea details = new TextArea(sb.toString());
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefSize(480, 320);
        alert.getDialogPane().setContent(details);
        alert.showAndWait();
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

        // Excel import/export sit at the top-right of the table they act on.
        Button exportBtn = new Button("Export to Excel");
        Theme.hover(exportBtn, Theme.secondaryButton(), Theme.secondaryHover());
        exportBtn.setMinWidth(Region.USE_PREF_SIZE);
        exportBtn.setOnAction(e -> exportInventory());

        Button importBtn = new Button("Import from Excel");
        Theme.hover(importBtn, Theme.secondaryButton(), Theme.secondaryHover());
        importBtn.setMinWidth(Region.USE_PREF_SIZE);
        importBtn.setOnAction(e -> importInventory());
        importBtnRef = importBtn;

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox tableHeader = new HBox(10, tableTitle, headerSpacer, exportBtn, importBtn);
        tableHeader.setAlignment(Pos.CENTER_LEFT);

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
        categoryCol.setPrefWidth(130);

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

        tableBox.getChildren().addAll(tableHeader, productTable);
        return tableBox;
    }

    private void loadProducts() {
        products = productService.getAllProducts();
        if (categoryFilterCombo != null) refreshCategoryFilterItems();
        applyFilters();
    }

    private void searchProducts(String keyword) {
        applyFilters();
    }

    /** Combines the free-text search box with the category filter — both
     *  narrow the same in-memory product list rather than re-querying the
     *  database for each. */
    private void applyFilters() {
        // createTopBar() runs before createProductTable(), and setting the
        // filter combo's value fires this listener — so bail out until the
        // table and product list actually exist.
        if (productTable == null || products == null) return;

        String keyword = searchField == null ? null : searchField.getText();
        String category = categoryFilterCombo == null ? null : categoryFilterCombo.getValue();

        ObservableList<Product> base = (keyword == null || keyword.trim().isEmpty())
                ? products
                : productService.searchProducts(keyword);

        if (category == null || "All Categories".equals(category)) {
            productTable.setItems(base);
        } else {
            productTable.setItems(base.filtered(p -> category.equals(p.getCategoryName())));
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
            com.pos.components.Ui.detailRow("Selling price:", "R " + String.format("%.2f", product.getPrice())),
            com.pos.components.Ui.detailRow("Purchase price:", "R " + String.format("%.2f", product.getCostPrice()))
        );

        Button viewBarcodeBtn = com.pos.components.Ui.actionButton("Show Barcode", Theme.INFO, "Show / print the product barcode");
        Button editBtn    = com.pos.components.Ui.actionButton("Edit", Theme.WARNING, "Edit product details");
        Button restockBtn = com.pos.components.Ui.actionButton("Restock", Theme.SUCCESS, "Add stock for this product");
        Button deleteBtn  = com.pos.components.Ui.actionButton("Delete", Theme.DANGER, "Delete this product");

        viewBarcodeBtn.setOnAction(e -> showBarcode(product));
        editBtn.setOnAction(e -> showEditProductDialog(product));
        restockBtn.setOnAction(e -> showRestockDialog(product));
        deleteBtn.setOnAction(e -> deleteProduct(product));

        com.pos.components.Ui.showDetailDialog("Product Details", product.getProductName(), summary,
            viewBarcodeBtn, editBtn, restockBtn, deleteBtn);
    }

    private void showBarcode(Product product) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Product Barcode");
        dialog.setHeaderText(product.getProductName());

        VBox content = new VBox(15);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));

        ImageView barcodeImageView = new ImageView(com.pos.utils.BarcodeUtil.getBarcodeImage(product.getQrCode()));
        barcodeImageView.setFitWidth(280);
        barcodeImageView.setFitHeight(105);
        barcodeImageView.setPreserveRatio(true);

        Label barcodeLabel = new Label(product.getBarCode());
        barcodeLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 16));

        content.getChildren().addAll(barcodeImageView, barcodeLabel);

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
        categoryCombo.setPromptText("Select a category...");
        TextField barcodeField  = new TextField();
        TextField quantityField = new TextField();
        TextField costPriceField = new TextField();
        TextField priceField    = new TextField();

        grid.add(new Label("Product Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Barcode:"), 0, 2);
        grid.add(barcodeField, 1, 2);
        grid.add(new Label("Quantity:"), 0, 3);
        grid.add(quantityField, 1, 3);
        grid.add(new Label("Purchase Price (R):"), 0, 4);
        grid.add(costPriceField, 1, 4);
        grid.add(new Label("Selling Price (R):"), 0, 5);
        grid.add(priceField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                if (categoryCombo.getValue() == null) {
                    showAlert("Error", "Please select a category.", Alert.AlertType.ERROR);
                    return null;
                }
                try {
                    Product product = new Product();
                    product.setStaffID(currentUser.getStaffID());
                    product.setProductName(nameField.getText());
                    product.setCategoryID(categoryService.getCategoryId(categoryCombo.getValue()));
                    product.setBarCode(barcodeField.getText());
                    product.setQuantity(Integer.parseInt(quantityField.getText()));
                    product.setCostPrice(new BigDecimal(costPriceField.getText()));
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
        TextField costPriceField = new TextField(product.getCostPrice() == null ? "0.00" : product.getCostPrice().toString());
        TextField priceField    = new TextField(product.getPrice().toString());

        grid.add(new Label("Product Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Barcode:"), 0, 2);
        grid.add(barcodeField, 1, 2);
        grid.add(new Label("Quantity:"), 0, 3);
        grid.add(quantityField, 1, 3);
        grid.add(new Label("Purchase Price (R):"), 0, 4);
        grid.add(costPriceField, 1, 4);
        grid.add(new Label("Selling Price (R):"), 0, 5);
        grid.add(priceField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                if (categoryCombo.getValue() == null) {
                    showAlert("Error", "Please select a category.", Alert.AlertType.ERROR);
                    return null;
                }
                try {
                    product.setProductName(nameField.getText());
                    product.setCategoryID(categoryService.getCategoryId(categoryCombo.getValue()));
                    product.setBarCode(barcodeField.getText());
                    product.setQuantity(Integer.parseInt(quantityField.getText()));
                    product.setCostPrice(new BigDecimal(costPriceField.getText()));
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

    public void cleanup() {
        // Deliberately does NOT clear the WiFi product callback — a product
        // scanned/added from the Android app must still go through even when
        // Inventory isn't the active screen (the whole point of a phone as a
        // second scanner). The callback is only ever torn down at actual app
        // shutdown, via MainDashboard's handleCloseRequest -> wifiHandler.cleanup().
    }

    // ── Category management ──────────────────────────────────────────────────

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
                            refreshCategoryFilterItems();
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
                                refreshCategoryFilterItems();
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
                refreshCategoryFilterItems();
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

    private void showAlert(String title, String content, Alert.AlertType type) {
        com.pos.components.Ui.showAlert(productTable, title, content, type);
    }
}
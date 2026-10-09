package com.pos.views;

import com.pos.models.User;
import com.pos.services.UserService;
import com.pos.services.WiFiHandler;
import com.pos.components.WiFiStatusButton;
import com.pos.database.DatabaseConnection;
import com.pos.utils.Theme;
import com.pos.utils.Icons;
import javafx.scene.Node;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class MainDashboard {
    private Stage stage;
    private User currentUser;
    private UserService userService;
    private BorderPane mainLayout;
    private VBox sidebar;
    private WiFiHandler wifiHandler;
    private Object currentView;
    private String currentSection = "sales";
    private Button infoBtn;
    private BorderPane contentArea;
    private VBox sidebarExtraInfo;
    private boolean sidebarCollapsed = false;
    private final java.util.Map<Button, String> menuButtonLabels = new java.util.HashMap<>();
    private static final double SIDEBAR_EXPANDED_WIDTH  = 250;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 72;

    private Button salesBtn;
    private Button salesHistoryBtn;
    private Button inventoryBtn;
    private Button userMgmtBtn;
    private Button customerBtn;
    private Button reportsBtn;
    private Button sessionsBtn;
    private Button approvalsBtn;
    private Button marketingBtn;
    private Button subscriptionBtn;
    private Button settingsBtn;
    private VBox menuBox;

    public MainDashboard(Stage stage, User user) {
        this.stage = stage;
        this.currentUser = user;
        this.userService = new UserService();
        this.wifiHandler = WiFiHandler.getInstance();
    }

    public void show() {
        mainLayout = new BorderPane();

        sidebar = createSidebar();
        mainLayout.setLeft(sidebar);

        // The info bar belongs to the content area only — it must not sit in
        // mainLayout's own top region, which spans the full window width and
        // would otherwise run straight over the sidebar.
        contentArea = new BorderPane();
        contentArea.setTop(createTopBar());
        mainLayout.setCenter(contentArea);

        navigateTo("sales");

        // No explicit width/height — see LoginView for why.
        Scene scene = new Scene(mainLayout);

        // Make the BorderPane fill the entire scene as the window resizes
        mainLayout.prefWidthProperty().bind(scene.widthProperty());
        mainLayout.prefHeightProperty().bind(scene.heightProperty());

        stage.setScene(scene);
        stage.setTitle(com.pos.Branding.APP_NAME + " — " + currentUser.getFullNames());
        stage.setOnCloseRequest(this::handleCloseRequest);
        stage.setMinWidth(1100);
        stage.setMinHeight(600);
        stage.show();

        if (!com.pos.setup.TutorialState.hasSeenTutorial()) {
            com.pos.setup.TutorialState.markSeen();
            Platform.runLater(() ->
                com.pos.components.TutorialOverlay.showWalkthrough(stage, visibleSections()));
        }
    }

    /** Sections this user/edition actually has in the menu, in menu order. */
    private java.util.List<String> visibleSections() {
        java.util.List<String> all = com.pos.utils.HelpContent.sectionsInOrder();
        java.util.List<String> visible = new java.util.ArrayList<>();
        boolean full = currentUser.hasFullAccess();
        boolean hasCustomers = com.pos.Edition.current().hasCustomers();
        for (String s : all) {
            if (("users" .equals(s) || "customers".equals(s) || "reports".equals(s)
                    || "approvals".equals(s) || "marketing".equals(s) || "settings".equals(s)) && !full) continue;
            if (("customers".equals(s) || "marketing".equals(s)) && !hasCustomers) continue;
            if ("salesHistory".equals(s) && hasCustomers) continue;
            visible.add(s);
        }
        return visible;
    }

    private HBox createTopBar() {
        Button collapseBtn = new Button("☰");
        collapseBtn.setTooltip(new Tooltip("Collapse / expand the menu"));
        collapseBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: " + Theme.BRAND + ";" +
            "-fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 15px;"
        );
        collapseBtn.setOnAction(e -> toggleSidebar());

        infoBtn = new Button("ⓘ About this screen");
        infoBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: " + Theme.BRAND + ";" +
            "-fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 13px;"
        );
        infoBtn.setOnAction(e ->
            com.pos.components.TutorialOverlay.showSingle(stage, currentSection));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(collapseBtn, spacer, infoBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 16, 8, 16));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e5e7eb transparent; -fx-border-width: 0 0 1 0;");
        return bar;
    }

    private void navigateTo(String section) {
        try {
            navigateToInternal(section);
        } catch (RuntimeException ex) {
            com.pos.utils.Dialogs.error(stage, "Could not open " + section,
                    "That screen failed to load. You can keep using the rest of the app.", ex);
        }
    }

    private void navigateToInternal(String section) {
        // Retail edition has no customer/marketing screens
        if (com.pos.Edition.current().isRetail() && ("customers".equals(section) || "marketing".equals(section))) {
            return;
        }
        // Stop the outgoing SessionView's background poll timer before swapping
        // it out (otherwise every visit leaks another INDEFINITE timeline).
        // SalesView/InventoryView keep their resources across navigation and are
        // only torn down on logout / window close via cleanupCurrentView().
        if (currentView instanceof SessionView v) v.cleanup();
        resetButtonStyles();
        currentSection = section;

        switch (section) {
            case "sales" -> {
                salesBtn.setStyle(getActiveButtonStyle());
                SalesView view = new SalesView(currentUser);
                BorderPane.setMargin(view.getView(), Insets.EMPTY);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "inventory" -> {
                inventoryBtn.setStyle(getActiveButtonStyle());
                InventoryView view = new InventoryView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "salesHistory" -> {
                salesHistoryBtn.setStyle(getActiveButtonStyle());
                SalesHistoryView view = new SalesHistoryView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "users" -> {
                userMgmtBtn.setStyle(getActiveButtonStyle());
                UserManagementView view = new UserManagementView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "customers" -> {
                customerBtn.setStyle(getActiveButtonStyle());
                CustomerView view = new CustomerView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "reports" -> {
                reportsBtn.setStyle(getActiveButtonStyle());
                ReportsView view = new ReportsView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "sessions" -> {
                sessionsBtn.setStyle(getActiveButtonStyle());
                SessionView view = new SessionView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "approvals" -> {
                approvalsBtn.setStyle(getActiveButtonStyle());
                SupervisorApprovalView view = new SupervisorApprovalView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "marketing" -> {
                marketingBtn.setStyle(getActiveButtonStyle());
                MarketingView view = new MarketingView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "subscription" -> {
                subscriptionBtn.setStyle(getActiveButtonStyle());
                SubscriptionView view = new SubscriptionView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
            case "settings" -> {
                settingsBtn.setStyle(getActiveButtonStyle());
                SettingsView view = new SettingsView(currentUser);
                contentArea.setCenter(view.getView());
                currentView = view;
            }
        }
    }

    private void handleCloseRequest(WindowEvent event) {
        System.out.println("🛑 Shutting down POS system...");

        cleanupCurrentView();
        wifiHandler.stopListening();
        wifiHandler.cleanup();
        DatabaseConnection.closeAll();
        userService.logCheckOut(currentUser.getStaffID());

        Platform.runLater(() -> {
            Platform.exit();
            System.exit(0);
        });

        event.consume();
    }

    private void cleanupCurrentView() {
        if (currentView instanceof SalesView v)     v.cleanup();
        if (currentView instanceof InventoryView v) v.cleanup();
        if (currentView instanceof SessionView v)   v.cleanup();
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private VBox createSidebar() {
        VBox sb = new VBox(0);
        sb.setPrefWidth(SIDEBAR_EXPANDED_WIDTH);
        sb.setMinWidth(SIDEBAR_EXPANDED_WIDTH);
        sb.setMaxWidth(SIDEBAR_EXPANDED_WIDTH);
        // Flat colour, not a gradient: the ScrollPane below can't reliably paint a
        // matching gradient without the "transparent ScrollPane renders labels
        // blank on some Windows GPUs" bug seen elsewhere in this codebase, so an
        // opaque solid fill shared by sidebar + scroll pane is the safe choice.
        sb.setStyle("-fx-background-color: " + Theme.BRAND + ";"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 4, 0);");

        // Header — fixed at top
        VBox header = createSidebarHeader();

        // Menu — scrollable if it ever overflows
        VBox menu = createMenuBox();
        ScrollPane menuScroll = new ScrollPane(menu);
        menuScroll.setFitToWidth(true);
        menuScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        menuScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        menuScroll.setStyle(
            "-fx-background: " + Theme.BRAND + "; -fx-background-color: " + Theme.BRAND + ";" +
            "-fx-border-color: transparent;"
        );
        VBox.setVgrow(menuScroll, Priority.ALWAYS);

        // Footer — fixed at bottom
        VBox footer = createFooter();

        sb.getChildren().addAll(header, menuScroll, footer);
        return sb;
    }

    private VBox createSidebarHeader() {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(30, 20, 20, 20));
        header.setStyle("-fx-background-color: rgba(0,0,0,0.2);");

        Label systemTitle = new Label(com.pos.Branding.APP_SHORT);
        systemTitle.setFont(Font.font("System", FontWeight.BOLD, 22));
        systemTitle.setTextFill(Color.WHITE);

        Label userName = new Label(currentUser.getFullNames());
        userName.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        userName.setTextFill(Color.WHITE);

        Label userRole = new Label(currentUser.getUserType());
        userRole.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
        userRole.setTextFill(Color.web("rgba(255,255,255,0.85)"));
        userRole.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-background-radius: 12; -fx-padding: 4 12;"
        );

        WiFiStatusButton wifiBtn = new WiFiStatusButton();
        wifiBtn.setMaxWidth(Double.MAX_VALUE);

        com.pos.components.ScannerStatusButton scannerBtn = new com.pos.components.ScannerStatusButton();
        scannerBtn.setMaxWidth(Double.MAX_VALUE);

        // Collapsed into its own box so toggleSidebar() can hide it as one unit,
        // leaving just the short branding title visible in the narrow rail.
        sidebarExtraInfo = new VBox(10, userName, userRole, wifiBtn, scannerBtn);
        sidebarExtraInfo.setAlignment(Pos.CENTER);

        header.getChildren().addAll(systemTitle, sidebarExtraInfo);
        return header;
    }

    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        double width = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH;
        sidebar.setPrefWidth(width);
        sidebar.setMinWidth(width);
        sidebar.setMaxWidth(width);

        sidebarExtraInfo.setVisible(!sidebarCollapsed);
        sidebarExtraInfo.setManaged(!sidebarCollapsed);

        for (var entry : menuButtonLabels.entrySet()) {
            entry.getKey().setText(sidebarCollapsed ? null : entry.getValue());
        }
    }

    private VBox createMenuBox() {
        menuBox = new VBox(5);
        menuBox.setPadding(new Insets(20, 10, 20, 10));

        boolean full = currentUser.hasFullAccess();

        salesBtn     = createMenuButton(Icons.sales(18),        "Sales",           true);
        salesHistoryBtn = createMenuButton(Icons.reports(18),   "Sales History",   true);
        inventoryBtn = createMenuButton(Icons.inventory(18),     "Inventory",       full);
        userMgmtBtn  = createMenuButton(Icons.users(18),         "User Management", full);
        customerBtn  = createMenuButton(Icons.customers(18),     "Customers",       full);
        reportsBtn   = createMenuButton(Icons.reports(18),       "Reports",         full);
        sessionsBtn  = createMenuButton(Icons.sessions(18),      "Sessions",        true);
        approvalsBtn = createMenuButton(Icons.approvals(18),     "Approvals",       full);
        marketingBtn = createMenuButton(Icons.marketing(18),     "Marketing",       full);
        subscriptionBtn = createMenuButton(Icons.subscription(18), "Subscription",  true);
        settingsBtn  = createMenuButton(Icons.settings(18),      "Settings",        full);

        salesBtn.setOnAction(e     -> navigateTo("sales"));
        salesHistoryBtn.setOnAction(e -> navigateTo("salesHistory"));
        inventoryBtn.setOnAction(e -> navigateTo("inventory"));
        userMgmtBtn.setOnAction(e  -> navigateTo("users"));
        customerBtn.setOnAction(e  -> navigateTo("customers"));
        reportsBtn.setOnAction(e   -> navigateTo("reports"));
        sessionsBtn.setOnAction(e  -> navigateTo("sessions"));
        approvalsBtn.setOnAction(e -> navigateTo("approvals"));
        marketingBtn.setOnAction(e -> navigateTo("marketing"));
        subscriptionBtn.setOnAction(e -> navigateTo("subscription"));
        settingsBtn.setOnAction(e  -> navigateTo("settings"));

        menuBox.getChildren().add(salesBtn);
        // Retail only: Standard already covers returns/exchanges per real
        // customer via the Customers screen, which Retail doesn't have —
        // every Retail sale books against one shared walk-in account instead.
        if (!com.pos.Edition.current().hasCustomers()) menuBox.getChildren().add(salesHistoryBtn);
        menuBox.getChildren().add(inventoryBtn);
        menuBox.getChildren().add(userMgmtBtn);
        if (com.pos.Edition.current().hasCustomers()) menuBox.getChildren().add(customerBtn);
        menuBox.getChildren().add(reportsBtn);
        menuBox.getChildren().add(sessionsBtn);
        menuBox.getChildren().add(approvalsBtn);
        if (com.pos.Edition.current().hasCustomers()) menuBox.getChildren().add(marketingBtn);
        menuBox.getChildren().add(subscriptionBtn);
        menuBox.getChildren().add(settingsBtn);

        return menuBox;
    }

    private VBox createFooter() {
        Button logoutBtn = createMenuButton(Icons.logout(18), "Logout", true);
        logoutBtn.setOnAction(e -> handleLogout());

        VBox footer = new VBox(5);
        footer.setPadding(new Insets(10));
        footer.setAlignment(Pos.CENTER);
        footer.setStyle("-fx-background-color: rgba(0,0,0,0.15);");
        footer.getChildren().add(logoutBtn);
        return footer;
    }

    private void handleLogout() {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to logout?");
        alert.setContentText("This will end your current session.");

        if (alert.showAndWait().get() == ButtonType.OK) {
            cleanupCurrentView();
            userService.logCheckOut(currentUser.getStaffID());
            new LoginView(stage).show();
        }
    }

    // ── Button helpers ────────────────────────────────────────────────────────

    private Button createMenuButton(Node icon, String text, boolean enabled) {
        Button btn = new Button(text);
        Icons.tinted(icon, "rgba(255,255,255,0.9)");
        btn.setGraphic(icon);
        btn.setGraphicTextGap(12);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        btn.setPadding(new Insets(15, 20, 15, 20));
        btn.setStyle(getDefaultButtonStyle());
        btn.setDisable(!enabled);
        btn.setTooltip(new Tooltip(enabled ? text : "Managers and admins only"));
        menuButtonLabels.put(btn, text);

        if (enabled) {
            btn.setOnMouseEntered(e -> {
                if (!btn.getStyle().equals(getActiveButtonStyle()))
                    btn.setStyle(getHoverButtonStyle());
            });
            btn.setOnMouseExited(e -> {
                if (!btn.getStyle().equals(getActiveButtonStyle()))
                    btn.setStyle(getDefaultButtonStyle());
            });
        }

        return btn;
    }

    private void resetButtonStyles() {
        Button[] buttons = {
            salesBtn, salesHistoryBtn, inventoryBtn, userMgmtBtn, customerBtn,
            reportsBtn, sessionsBtn, approvalsBtn, marketingBtn, subscriptionBtn, settingsBtn
        };
        for (Button b : buttons) {
            if (b != null) b.setStyle(getDefaultButtonStyle());
        }
    }

    // Padding/font are baked into these inline style strings (not left to
    // setPadding()/setFont() elsewhere) deliberately: an inline style always
    // wins over any stylesheet rule, so the nav's size can never again be
    // silently changed by a global CSS tweak.
    private static final String NAV_SIZE = "-fx-padding: 15 20; -fx-font-size: 14px; -fx-font-weight: bold;";

    private String getDefaultButtonStyle() {
        return "-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.85);" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;" + NAV_SIZE;
    }

    private String getHoverButtonStyle() {
        return "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;" + NAV_SIZE;
    }

    private String getActiveButtonStyle() {
        return "-fx-background-color: " + Theme.BRAND_DARK + "; -fx-text-fill: white;" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;" + NAV_SIZE;
    }
}
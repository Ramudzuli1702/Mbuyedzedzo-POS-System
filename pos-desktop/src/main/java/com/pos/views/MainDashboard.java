package com.pos.views;

import com.pos.models.User;
import com.pos.services.UserService;
import com.pos.services.WiFiHandler;
import com.pos.components.WiFiStatusButton;
import com.pos.database.DatabaseConnection;
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

    private Button salesBtn;
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

        navigateTo("sales");

        Scene scene = new Scene(mainLayout, 1400, 800);

        // Make the BorderPane fill the entire scene as the window resizes
        mainLayout.prefWidthProperty().bind(scene.widthProperty());
        mainLayout.prefHeightProperty().bind(scene.heightProperty());

        stage.setScene(scene);
        stage.setTitle(com.pos.Branding.APP_NAME + " — " + currentUser.getFullNames());
        stage.setOnCloseRequest(this::handleCloseRequest);
        stage.setMinWidth(1100);
        stage.setMinHeight(600);
        stage.show();
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

        switch (section) {
            case "sales" -> {
                salesBtn.setStyle(getActiveButtonStyle());
                SalesView view = new SalesView(currentUser);
                BorderPane.setMargin(view.getView(), Insets.EMPTY);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "inventory" -> {
                inventoryBtn.setStyle(getActiveButtonStyle());
                InventoryView view = new InventoryView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "users" -> {
                userMgmtBtn.setStyle(getActiveButtonStyle());
                UserManagementView view = new UserManagementView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "customers" -> {
                customerBtn.setStyle(getActiveButtonStyle());
                CustomerView view = new CustomerView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "reports" -> {
                reportsBtn.setStyle(getActiveButtonStyle());
                ReportsView view = new ReportsView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "sessions" -> {
                sessionsBtn.setStyle(getActiveButtonStyle());
                SessionView view = new SessionView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "approvals" -> {
                approvalsBtn.setStyle(getActiveButtonStyle());
                SupervisorApprovalView view = new SupervisorApprovalView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "marketing" -> {
                marketingBtn.setStyle(getActiveButtonStyle());
                MarketingView view = new MarketingView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "subscription" -> {
                subscriptionBtn.setStyle(getActiveButtonStyle());
                SubscriptionView view = new SubscriptionView(currentUser);
                mainLayout.setCenter(view.getView());
                currentView = view;
            }
            case "settings" -> {
                settingsBtn.setStyle(getActiveButtonStyle());
                SettingsView view = new SettingsView(currentUser);
                mainLayout.setCenter(view.getView());
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
        sb.setPrefWidth(250);
        sb.setMinWidth(250);
        sb.setMaxWidth(250);
        sb.setStyle("-fx-background-color: #0f766e;");

        // Header — fixed at top
        VBox header = createSidebarHeader();

        // Menu — scrollable if it ever overflows
        VBox menu = createMenuBox();
        ScrollPane menuScroll = new ScrollPane(menu);
        menuScroll.setFitToWidth(true);
        menuScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        menuScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        menuScroll.setStyle(
            "-fx-background: #0f766e; -fx-background-color: #0f766e;" +
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
        userName.setTextFill(Color.web("#ecf0f1"));

        Label userRole = new Label(currentUser.getUserType());
        userRole.setFont(Font.font("System", 12));
        userRole.setTextFill(Color.web("#95a5a6"));
        userRole.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 12; -fx-padding: 4 12;"
        );

        WiFiStatusButton wifiBtn = new WiFiStatusButton();
        wifiBtn.setMaxWidth(Double.MAX_VALUE);

        header.getChildren().addAll(systemTitle, userName, userRole, wifiBtn);
        return header;
    }

    private VBox createMenuBox() {
        menuBox = new VBox(5);
        menuBox.setPadding(new Insets(20, 10, 20, 10));

        boolean full = currentUser.hasFullAccess();

        salesBtn     = createMenuButton("💳  Sales",           true);
        inventoryBtn = createMenuButton("📦  Inventory",       full);
        userMgmtBtn  = createMenuButton("👥  User Management", full);
        customerBtn  = createMenuButton("🛍️  Customers",       full);
        reportsBtn   = createMenuButton("📊  Reports",         full);
        sessionsBtn  = createMenuButton("🕐  Sessions",        true);
        approvalsBtn = createMenuButton("✅  Approvals",       full);
        marketingBtn = createMenuButton("📣  Marketing",       full);
        subscriptionBtn = createMenuButton("🔑  Subscription", true);
        settingsBtn  = createMenuButton("⚙️  Settings",        full);

        salesBtn.setOnAction(e     -> navigateTo("sales"));
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
        Button logoutBtn = createMenuButton("🚪  Logout", true);
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

    private Button createMenuButton(String text, boolean enabled) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        btn.setPadding(new Insets(15, 20, 15, 20));
        btn.setStyle(getDefaultButtonStyle());
        btn.setDisable(!enabled);

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
            salesBtn, inventoryBtn, userMgmtBtn, customerBtn,
            reportsBtn, sessionsBtn, approvalsBtn, marketingBtn, subscriptionBtn, settingsBtn
        };
        for (Button b : buttons) {
            if (b != null) b.setStyle(getDefaultButtonStyle());
        }
    }

    private String getDefaultButtonStyle() {
        return "-fx-background-color: transparent; -fx-text-fill: #ecf0f1;" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";
    }

    private String getHoverButtonStyle() {
        return "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";
    }

    private String getActiveButtonStyle() {
        return "-fx-background-color: #0c5c57; -fx-text-fill: white;" +
               "-fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";
    }
}
package com.pos.views;

import com.pos.models.Customer;
import com.pos.models.User;
import com.pos.services.CustomerService;
import com.pos.services.ExchangeReturnService;
import com.pos.services.SessionService;
import com.pos.services.SessionService.BusinessSession;
import com.pos.services.SessionService.SessionSaleSummary;
import com.pos.services.UserService;
import com.pos.utils.ReceiptGenerator;
import javafx.collections.ObservableList;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class SessionView {
    private static final DateTimeFormatter TIME_FMT     = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private User currentUser;
    private SessionService sessionService;
    private UserService userService;
    private final CustomerService customerService = new CustomerService();

    private TableView<BusinessSession> sessionTable;
    private TableView<SessionActivity> activityFeed;
    private TableView<SessionSaleSummary> receiptsTable;

    private VBox sessionStrip;
    private Label statusLabel;
    private Label cashLabel;
    private Label cardLabel;
    private Label totalLabel;
    private Button actionButton;

    private TabPane tabPane;
    private Tab liveTab;
    private Label liveCountLabel;
    private boolean userPickedTab = false;

    private Timeline refreshTimeline;

    private boolean isSupervisor() {
        return currentUser.hasFullAccess();
    }

    public SessionView(User user) {
        this.currentUser = user;
        this.sessionService = new SessionService();
        this.userService = new UserService();

        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> refreshLiveData()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
    }

    /** Stop the background refresh timer. Called by MainDashboard when navigating away. */
    public void cleanup() {
        if (refreshTimeline != null) refreshTimeline.stop();
    }

    public static class SessionActivity {
        private String type;
        private String description;
        private LocalDateTime timestamp;
        private String status;
        private double amount;

        public SessionActivity(String type, String description, LocalDateTime timestamp, String status, double amount) {
            this.type = type;
            this.description = description;
            this.timestamp = timestamp;
            this.status = status;
            this.amount = amount;
        }

        public String getType()             { return type; }
        public String getDescription()      { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getStatus()           { return status; }
        public double getAmount()           { return amount; }

        public String getTypeLabel() {
            return switch (type) {
                case "SALE"     -> "Sale";
                case "RETURN"   -> "Return";
                case "EXCHANGE" -> "Exchange";
                default         -> type;
            };
        }

        @Override
        public String toString() {
            String statusBadge = status != null ? " [" + status + "]" : "";
            String amountStr   = amount > 0 ? String.format(" - R%.2f", amount) : "";
            return String.format("%s %s%s%s",
                    timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    description,
                    statusBadge,
                    amountStr);
        }

        // Value identity so the live feed can skip a rebuild when nothing changed.
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SessionActivity a)) return false;
            return Double.compare(a.amount, amount) == 0
                    && java.util.Objects.equals(type, a.type)
                    && java.util.Objects.equals(description, a.description)
                    && java.util.Objects.equals(timestamp, a.timestamp)
                    && java.util.Objects.equals(status, a.status);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(type, description, timestamp, status, amount);
        }
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");

        layout.setTop(createTopBar());

        VBox mainContent = new VBox(16);
        mainContent.setPadding(new Insets(20));

        sessionStrip = createSessionStrip();

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        liveTab = new Tab("Live Feed", createActivityFeed());
        Tab historyTab = new Tab("Session History", createSessionTable());
        Tab receiptsTab = new Tab("Receipts", createReceiptsTab());
        receiptsTab.setOnSelectionChanged(e -> { if (receiptsTab.isSelected()) refreshReceiptsTab(); });
        tabPane.getTabs().addAll(liveTab, historyTab, receiptsTab);
        tabPane.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> userPickedTab = true);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        mainContent.getChildren().addAll(sessionStrip, tabPane);
        layout.setCenter(mainContent);

        refreshView();
        refreshTimeline.play();

        return layout;
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Business Sessions");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (isSupervisor()) {
            Button authKeyBtn = new Button("Auth Key");
            authKeyBtn.setStyle(
                    "-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;"
                    + "-fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
            authKeyBtn.setOnAction(e -> showAuthKeyDialog());
            topBar.getChildren().addAll(title, spacer, authKeyBtn);
        } else {
            topBar.getChildren().addAll(title, spacer);
        }

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> refreshView());
        topBar.getChildren().add(refreshBtn);

        return topBar;
    }

    /**
     * Compact full-width strip above the tabs: session status + live cash/card/total
     * + the Start/End button. Replaces the old tall "Current Session" card.
     */
    private VBox createSessionStrip() {
        VBox wrap = new VBox(10);
        wrap.setPadding(new Insets(18, 20, 18, 20));
        wrap.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");

        HBox row = new HBox(28);
        row.setAlignment(Pos.CENTER_LEFT);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setMinWidth(260);
        statusLabel = new Label("No active session");
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 15));
        statusBox.getChildren().add(statusLabel);

        cashLabel  = metricValue();
        cardLabel  = metricValue();
        totalLabel = metricValue();
        totalLabel.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actionButton = new Button("Start Session");
        actionButton.setOnAction(e -> handleSessionAction());
        styleActionButton(false);

        row.getChildren().addAll(
                statusBox,
                metric("Cash", cashLabel),
                metric("Card", cardLabel),
                metric("Total", totalLabel),
                spacer,
                actionButton);

        wrap.getChildren().add(row);

        if (!isSupervisor()) {
            Label accessNote = new Label(
                    "ℹ️ Starting or ending a session without a manager/admin is allowed, "
                    + "but the session is flagged for review and countersignature.");
            accessNote.setWrapText(true);
            accessNote.setStyle("-fx-text-fill: #856404; -fx-background-color: #fff3cd;"
                    + "-fx-padding: 8 12; -fx-background-radius: 6; -fx-font-size: 12;");
            wrap.getChildren().add(accessNote);
        }

        return wrap;
    }

    private Label metricValue() {
        Label l = new Label("R 0.00");
        l.setFont(Font.font("System", FontWeight.BOLD, 18));
        l.setTextFill(Color.web("#334155"));
        return l;
    }

    private VBox metric(String caption, Label valueLabel) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER_LEFT);
        Label c = new Label(caption);
        c.setFont(Font.font("System", 11));
        c.setTextFill(Color.web("#64748b"));
        box.getChildren().addAll(c, valueLabel);
        return box;
    }

    private void styleActionButton(boolean sessionActive) {
        String colour = sessionActive ? "#dc2626" : "#16a34a";
        actionButton.setText(sessionActive ? "End Session" : "Start Session");
        actionButton.setStyle(
                "-fx-background-color: " + colour + "; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-font-size: 14; -fx-padding: 11 26; -fx-background-radius: 8; -fx-cursor: hand;");
    }

    /** Live feed tab content — a real table that updates in place. */
    private VBox createActivityFeed() {
        VBox feedBox = new VBox(12);
        feedBox.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");
        feedBox.setPadding(new Insets(18));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label feedTitle = new Label("Activity this session");
        feedTitle.setFont(Font.font("System", FontWeight.BOLD, 15));
        liveCountLabel = new Label("");
        liveCountLabel.setTextFill(Color.web("#64748b"));
        liveCountLabel.setFont(Font.font("System", 12));
        header.getChildren().addAll(feedTitle, liveCountLabel);

        activityFeed = new TableView<>();
        activityFeed.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        activityFeed.setPlaceholder(new Label("No sales, returns or exchanges yet this session."));
        VBox.setVgrow(activityFeed, Priority.ALWAYS);

        TableColumn<SessionActivity, String> timeCol = new TableColumn<>("Time");
        timeCol.setMaxWidth(110);
        timeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getTimestamp().format(TIME_FMT)));

        TableColumn<SessionActivity, SessionActivity> typeCol = new TableColumn<>("Type");
        typeCol.setMaxWidth(120);
        typeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue()));
        typeCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(SessionActivity a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); setStyle(""); return; }
                setText(a.getTypeLabel());
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + typeColour(a.getType()) + ";");
            }
        });

        TableColumn<SessionActivity, String> descCol = new TableColumn<>("Detail");
        descCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()));

        TableColumn<SessionActivity, String> statusCol = new TableColumn<>("Status");
        statusCol.setMaxWidth(110);
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getStatus() != null ? d.getValue().getStatus() : ""));

        TableColumn<SessionActivity, String> amtCol = new TableColumn<>("Amount");
        amtCol.setMaxWidth(120);
        amtCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        amtCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getAmount() > 0 ? String.format("R %.2f", d.getValue().getAmount()) : "—"));

        activityFeed.getColumns().addAll(timeCol, typeCol, descCol, statusCol, amtCol);

        feedBox.getChildren().addAll(header, activityFeed);
        return feedBox;
    }

    private static String typeColour(String type) {
        return switch (type) {
            case "SALE"     -> "#16a34a";
            case "RETURN"   -> "#d97706";
            case "EXCHANGE" -> "#9b59b6";
            default         -> "#334155";
        };
    }

    // ── Receipts tab ─────────────────────────────────────────────────────────

    private VBox createReceiptsTab() {
        VBox box = new VBox(12);
        box.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");
        box.setPadding(new Insets(18));

        Label title = new Label("Receipts — this session");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));

        receiptsTable = new TableView<>();
        receiptsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        receiptsTable.setPlaceholder(new Label("No sales yet this session."));
        VBox.setVgrow(receiptsTable, Priority.ALWAYS);

        TableColumn<SessionSaleSummary, String> saleIdCol = new TableColumn<>("Sale ID");
        saleIdCol.setMaxWidth(90);
        saleIdCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(d.getValue().saleID())));

        TableColumn<SessionSaleSummary, String> timeCol = new TableColumn<>("Time");
        timeCol.setMaxWidth(110);
        timeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().saleDate().format(TIME_FMT)));

        TableColumn<SessionSaleSummary, String> staffCol = new TableColumn<>("Sold By");
        staffCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().staffName()));

        TableColumn<SessionSaleSummary, String> amtCol = new TableColumn<>("Amount");
        amtCol.setMaxWidth(120);
        amtCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        amtCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("R %.2f", d.getValue().amount())));

        TableColumn<SessionSaleSummary, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(140);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = com.pos.components.Ui.actionButton("View / Print", "#2563eb", "View or print this sale's receipt");
            {
                viewBtn.setOnAction(e -> {
                    SessionSaleSummary sale = getTableView().getItems().get(getIndex());
                    showReceiptDialog(sale.saleID());
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        receiptsTable.getColumns().addAll(saleIdCol, timeCol, staffCol, amtCol, actionCol);

        box.getChildren().addAll(title, receiptsTable);
        return box;
    }

    private void refreshReceiptsTab() {
        BusinessSession active = sessionService.getActiveSession();
        if (active == null || receiptsTable == null) return;
        receiptsTable.setItems(sessionService.getSessionSales(active.getSessionID()));
    }

    /** Reconstructs and shows the receipt for a sale made during this session, with a Print button. */
    private void showReceiptDialog(int saleId) {
        Integer accountId = customerService.getAccountIdForSale(saleId);
        if (accountId == null) {
            showAlert("Not Found", "Could not find which account this sale belongs to.", Alert.AlertType.ERROR);
            return;
        }
        Customer customer = customerService.getCustomerById(accountId);
        if (customer == null) {
            showAlert("Not Found", "Could not load the account for this sale.", Alert.AlertType.ERROR);
            return;
        }

        com.pos.services.CustomerService.Sale sale = null;
        for (var s : customerService.getCustomerPurchases(accountId)) {
            if (s.getSaleID() == saleId) { sale = s; break; }
        }
        if (sale == null) {
            showAlert("Not Found", "Could not load this sale's items.", Alert.AlertType.ERROR);
            return;
        }

        String receiptText = ReceiptGenerator.generateSaleReceipt(
                sale, customer, customerService, new ExchangeReturnService());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Receipt — Sale #" + saleId);

        TextArea area = new TextArea(receiptText);
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12;");
        area.setPrefSize(420, 480);

        Button printBtn = new Button("Print");
        printBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13;");
        printBtn.setOnAction(e -> ReceiptGenerator.printReceipt(receiptText));

        HBox buttonBar = new HBox(printBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(10, area, buttonBar);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private VBox createSessionTable() {
        VBox tableBox = new VBox(15);
        tableBox.setStyle(
                "-fx-background-color: white; -fx-background-radius: 14;"
                + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");
        tableBox.setPadding(new Insets(20));

        Label tableTitle = new Label("Session History");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        sessionTable = new TableView<>();
        sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BusinessSession, Integer> idCol = new TableColumn<>("Session ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("sessionID"));
        idCol.setPrefWidth(100);

        TableColumn<BusinessSession, String> supervisorCol = new TableColumn<>("Started By");
        supervisorCol.setCellValueFactory(new PropertyValueFactory<>("supervisorName"));
        supervisorCol.setPrefWidth(150);

        TableColumn<BusinessSession, LocalDateTime> startCol = new TableColumn<>("Start Time");
        startCol.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        startCol.setPrefWidth(180);
        startCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.format(DATE_TIME_FMT));
            }
        });

        TableColumn<BusinessSession, LocalDateTime> endCol = new TableColumn<>("End Time");
        endCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        endCol.setPrefWidth(180);
        endCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Active");
                    setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                } else {
                    setText(item.format(DATE_TIME_FMT));
                    setStyle("");
                }
            }
        });

        TableColumn<BusinessSession, BigDecimal> totalCol = new TableColumn<>("Total Sales");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalSales"));
        totalCol.setPrefWidth(120);
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "R " + String.format("%.2f", item));
            }
        });

        TableColumn<BusinessSession, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("Active".equals(item)
                            ? "-fx-background-color: #d4edda; -fx-text-fill: #155724;"
                            : "-fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
                }
            }
        });

        TableColumn<BusinessSession, Boolean> signedCol = new TableColumn<>("Signed");
        signedCol.setCellValueFactory(new PropertyValueFactory<>("declarationSigned"));
        signedCol.setPrefWidth(90);
        signedCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean signed, boolean empty) {
                super.updateItem(signed, empty);
                if (empty || signed == null) {
                    setText(null); setStyle(""); return;
                }
                if (signed) {
                    setText("Signed");
                    setStyle("-fx-text-fill: #155724; -fx-font-weight: bold;");
                } else {
                    BusinessSession s = getTableView().getItems().get(getIndex());
                    if ("Closed".equals(s.getStatus())) {
                        setText("Pending");
                        setStyle("-fx-text-fill: #856404; -fx-font-weight: bold;");
                    } else {
                        setText("—");
                        setStyle("-fx-text-fill: #aaa;");
                    }
                }
            }
        });

        TableColumn<BusinessSession, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = com.pos.components.Ui.viewButton();
            {
                viewBtn.setOnAction(e ->
                    showSessionDetailsDialog(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        sessionTable.getColumns().addAll(idCol, supervisorCol, startCol, endCol, totalCol, statusCol, signedCol, actionCol);
        VBox.setVgrow(sessionTable, Priority.ALWAYS);
        tableBox.getChildren().addAll(tableTitle, sessionTable);
        return tableBox;
    }

    /** Timer-driven refresh: recompute totals and merge new feed rows, nothing else. */
    private void refreshLiveData() {
        BusinessSession active = sessionService.getActiveSession();
        if (active == null) return;

        sessionService.updateSessionTotals(active.getSessionID());
        BusinessSession refreshed = sessionService.getActiveSession();
        if (refreshed == null) return;
        var activities = sessionService.getSessionActivities(refreshed.getSessionID());

        Platform.runLater(() -> {
            applyTotals(refreshed);
            mergeActivityFeed(activities);
        });
    }

    /** Full refresh: session strip, feed, history table, default tab. */
    private void refreshView() {
        BusinessSession active = sessionService.getActiveSession();

        if (active != null) {
            sessionService.updateSessionTotals(active.getSessionID());
            active = sessionService.getActiveSession();

            statusLabel.setText("Session #" + active.getSessionID() + " active — started "
                    + active.getStartDate().format(TIME_FMT)
                    + " by " + active.getSupervisorName());
            statusLabel.setTextFill(Color.web("#16a34a"));
            applyTotals(active);
            styleActionButton(true);
            actionButton.setDisable(false);
            refreshReceiptsTab();

            mergeActivityFeed(sessionService.getSessionActivities(active.getSessionID()));
            if (!userPickedTab) tabPane.getSelectionModel().select(liveTab);
        } else {
            statusLabel.setText("  No active session");
            statusLabel.setTextFill(Color.web("#64748b"));
            cashLabel.setText("R 0.00");
            cardLabel.setText("R 0.00");
            totalLabel.setText("R 0.00");
            styleActionButton(false);
            actionButton.setDisable(false);

            activityFeed.getItems().clear();
            liveCountLabel.setText("");
            if (!userPickedTab) tabPane.getSelectionModel().select(1); // history
        }

        sessionTable.getItems().setAll(sessionService.getAllSessions());
    }

    private void applyTotals(BusinessSession s) {
        cashLabel.setText("R " + String.format("%.2f", s.getTotalCashSales()));
        cardLabel.setText("R " + String.format("%.2f", s.getTotalCardSales()));
        totalLabel.setText("R " + String.format("%.2f", s.getTotalSales()));
    }

    /** Replace feed contents only when they actually differ, so the table
     *  doesn't flicker or lose the user's scroll position every tick. */
    private void mergeActivityFeed(java.util.List<SessionActivity> latest) {
        if (!activityFeed.getItems().equals(latest)) {
            activityFeed.getItems().setAll(latest);
        }
        int n = latest.size();
        liveCountLabel.setText(n == 0 ? "" : "· " + n + (n == 1 ? " event" : " events"));
    }

    private void handleSessionAction() {
        BusinessSession activeSession = sessionService.getActiveSession();

        if (activeSession != null) {
            if (isSupervisor()) {
                showEndSessionDialog(activeSession);
            } else {
                showCashierEndSessionDialog(activeSession);
            }
        } else {
            if (isSupervisor()) {
                startSession();
            } else {
                showCashierStartSessionDialog();
            }
        }
    }

    private void startSession() {
        if (sessionService.startSession(currentUser.getStaffID())) {
            showAlert("Success", "Business session started successfully", Alert.AlertType.INFORMATION);
            refreshView();
        } else {
            showAlert("Error", "Failed to start session", Alert.AlertType.ERROR);
        }
    }

    private void showCashierStartSessionDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Start Business Session");
        dialog.setHeaderText("Cashier Session Start");

        ButtonType startBtn = new ButtonType("Start Session", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label warningLabel = new Label(
                "  No manager or admin is present. You are starting this session as a cashier.\n"
                + "The session will be flagged for manager/admin review.\n"
                + "Please confirm your identity to proceed.");
        warningLabel.setWrapText(true);
        warningLabel.setStyle(
                "-fx-text-fill: #856404; -fx-background-color: #fff3cd;"
                + "-fx-padding: 10 14; -fx-background-radius: 6; -fx-font-size: 12;");
        grid.add(warningLabel, 0, 0, 2, 1);

        grid.add(new Label("Your Name:"), 0, 1);
        Label nameLabel = new Label(currentUser.getFullNames());
        nameLabel.setStyle("-fx-font-weight: bold;");
        grid.add(nameLabel, 1, 1);

        grid.add(new Label("Confirm Password:"), 0, 2);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your login password");
        grid.add(passwordField, 1, 2);

        grid.add(new Label("Notes (optional):"), 0, 3);
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(2);
        notesArea.setPromptText("Reason for starting without manager or admin...");
        grid.add(notesArea, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == startBtn) {
            if (passwordField.getText().isEmpty()) {
                showAlert("Error", "Please confirm your password to start a session", Alert.AlertType.ERROR);
                return;
            }

            User verified = userService.login(currentUser.getEmailAddress(), passwordField.getText());
            if (verified == null) {
                showAlert("Error", "Incorrect password. Session not started.", Alert.AlertType.ERROR);
                return;
            }

            if (sessionService.startSessionAsCashier(currentUser.getStaffID(), notesArea.getText())) {
                showAlert("Session Started",
                        "Session started successfully.\nA manager or admin should review and countersign when available.",
                        Alert.AlertType.INFORMATION);
                refreshView();
            } else {
                showAlert("Error", "Failed to start session", Alert.AlertType.ERROR);
            }
        }
    }

    private void showCashierEndSessionDialog(BusinessSession session) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("End Business Session");
        dialog.setHeaderText("Cashier Session End");

        ButtonType endBtn = new ButtonType("End Session", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(endBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label warningLabel = new Label(
                "  You are ending this session without a manager or admin.\n"
                + "The session will be flagged for manager/admin review and countersignature.");
        warningLabel.setWrapText(true);
        warningLabel.setStyle(
                "-fx-text-fill: #856404; -fx-background-color: #fff3cd;"
                + "-fx-padding: 10 14; -fx-background-radius: 6; -fx-font-size: 12;");
        grid.add(warningLabel, 0, 0, 2, 1);

        grid.add(new Label("Session ID:"), 0, 1);
        grid.add(new Label(String.valueOf(session.getSessionID())), 1, 1);

        grid.add(new Label("Cash Sales:"), 0, 2);
        grid.add(new Label("R " + String.format("%.2f", session.getTotalCashSales())), 1, 2);

        grid.add(new Label("Card Sales:"), 0, 3);
        grid.add(new Label("R " + String.format("%.2f", session.getTotalCardSales())), 1, 3);

        grid.add(new Label("Total Sales:"), 0, 4);
        Label totalSalesLabel = new Label("R " + String.format("%.2f", session.getTotalSales()));
        totalSalesLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(totalSalesLabel, 1, 4);

        grid.add(new Separator(), 0, 5, 2, 1);

        grid.add(new Label("Confirm Password:"), 0, 6);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your login password");
        grid.add(passwordField, 1, 6);

        grid.add(new Label("Notes (optional):"), 0, 7);
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.setPromptText("Any discrepancies or notes for the manager/admin...");
        grid.add(notesArea, 1, 7);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == endBtn) {
            if (passwordField.getText().isEmpty()) {
                showAlert("Error", "Please confirm your password to end the session", Alert.AlertType.ERROR);
                return;
            }

            User verified = userService.login(currentUser.getEmailAddress(), passwordField.getText());
            if (verified == null) {
                showAlert("Error", "Incorrect password. Session not ended.", Alert.AlertType.ERROR);
                return;
            }

            if (sessionService.endSessionAsCashier(session.getSessionID(), notesArea.getText())) {
                generateSessionPDFs(session.getSessionID());
                showAlert("Session Ended",
                        "Session ended successfully.\nReports generated. A manager/admin should countersign when available.",
                        Alert.AlertType.INFORMATION);
                refreshView();
            } else {
                showAlert("Error", "Failed to end session", Alert.AlertType.ERROR);
            }
        }
    }

    private void showAuthKeyDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Authorization Key");
        dialog.setHeaderText("View Your Manager/Admin Authorization Key");

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your login password");

        Label keyLabel = new Label("");
        keyLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
        keyLabel.setStyle("-fx-text-fill: #16a34a;");
        keyLabel.setVisible(false);

        Button showKeyBtn = new Button("Show Key");
        showKeyBtn.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-padding: 8 16; -fx-font-weight: bold;");

        showKeyBtn.setOnAction(e -> {
            String password = passwordField.getText();
            if (password.isEmpty()) {
                showAlert("Error", "Please enter your password", Alert.AlertType.ERROR);
                return;
            }
            User verifiedUser = userService.login(currentUser.getEmailAddress(), password);
            if (verifiedUser != null) {
                String authKey = sessionService.getOrCreateAuthKey(currentUser.getStaffID());
                keyLabel.setText(authKey);
                keyLabel.setVisible(true);
                passwordField.setDisable(true);
                showKeyBtn.setText("Key Shown");
                showKeyBtn.setDisable(true);
            } else {
                showAlert("Error", "Invalid password", Alert.AlertType.ERROR);
                passwordField.clear();
            }
        });

        grid.add(new Label("Password:"), 0, 0);
        grid.add(passwordField, 1, 0);
        grid.add(new Label("Your Key:"), 0, 1);
        grid.add(keyLabel, 1, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonBar buttonBar = (ButtonBar) dialog.getDialogPane().lookup(".button-bar");
        buttonBar.getButtons().add(0, showKeyBtn);

        dialog.showAndWait();
    }

    private void showEndSessionDialog(BusinessSession session) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("End Business Session");
        dialog.setHeaderText("End of Day Declaration");

        ButtonType endButtonType = new ButtonType("Sign & End Session", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(endButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Session ID:"), 0, 0);
        grid.add(new Label(String.valueOf(session.getSessionID())), 1, 0);

        grid.add(new Label("Cash Sales:"), 0, 1);
        grid.add(new Label("R " + String.format("%.2f", session.getTotalCashSales())), 1, 1);

        grid.add(new Label("Card Sales:"), 0, 2);
        grid.add(new Label("R " + String.format("%.2f", session.getTotalCardSales())), 1, 2);

        grid.add(new Label("Total Sales:"), 0, 3);
        Label totalLabel = new Label("R " + String.format("%.2f", session.getTotalSales()));
        totalLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(totalLabel, 1, 3);

        grid.add(new Separator(), 0, 4, 2, 1);

        Label declLabel = new Label("I declare that the above totals are accurate:");
        declLabel.setWrapText(true);
        grid.add(declLabel, 0, 5, 2, 1);

        grid.add(new Label("Authorization Code:"), 0, 6);
        PasswordField authField = new PasswordField();
        authField.setPromptText("Enter your 6-digit code");
        grid.add(authField, 1, 6);

        grid.add(new Label("Notes (optional):"), 0, 7);
        TextArea notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.setPromptText("Any discrepancies or notes...");
        grid.add(notesArea, 1, 7);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == endButtonType) {
            String authCode = authField.getText();
            if (authCode.isEmpty()) {
                showAlert("Error", "Authorization code is required", Alert.AlertType.ERROR);
                return;
            }
            if (!sessionService.verifySupervisorCode(currentUser.getStaffID(), authCode)) {
                showAlert("Error", "Invalid authorization code", Alert.AlertType.ERROR);
                return;
            }
            if (sessionService.endSession(session.getSessionID(), authCode, notesArea.getText())) {
                generateSessionPDFs(session.getSessionID());
                showAlert("Success", "Session ended and reports generated successfully", Alert.AlertType.INFORMATION);
                refreshView();
            } else {
                showAlert("Error", "Failed to end session", Alert.AlertType.ERROR);
            }
        }
    }

    private void showSessionDetailsDialog(BusinessSession session) {
        boolean closed  = "Closed".equals(session.getStatus());
        boolean pending = closed && !session.isDeclarationSigned();

        VBox summary = new VBox(10);
        summary.getChildren().addAll(
            com.pos.components.Ui.detailRow("Started by:", session.getSupervisorName()),
            com.pos.components.Ui.detailRow("Start time:", session.getStartDate().format(DATE_TIME_FMT)),
            com.pos.components.Ui.detailRow("End time:", session.getEndDate() != null
                ? session.getEndDate().format(DATE_TIME_FMT) : "Active"),
            com.pos.components.Ui.detailRow("Total sales:", "R " + String.format("%.2f", session.getTotalSales())),
            com.pos.components.Ui.detailRow("Status:", session.getStatus()),
            com.pos.components.Ui.detailRow("Declaration:", session.isDeclarationSigned() ? "Signed"
                : (pending ? "Pending sign-off" : "—"))
        );

        java.util.List<Button> actions = new java.util.ArrayList<>();

        Button reportsBtn = com.pos.components.Ui.actionButton("Generate Reports", "#2563eb", "Generate the session PDF reports");
        reportsBtn.setDisable(!closed);
        reportsBtn.setOnAction(e -> {
            if (closed) {
                viewSessionReports(session);
            } else {
                showAlert("Session Active", "Cannot generate reports for an active session", Alert.AlertType.WARNING);
            }
        });
        actions.add(reportsBtn);

        if (isSupervisor() && pending) {
            Button countersignBtn = com.pos.components.Ui.actionButton("Countersign", "#9b59b6", "Manager/admin sign-off for a cashier session");
            countersignBtn.setOnAction(e -> showCountersignDialog(session));
            actions.add(countersignBtn);
        }

        com.pos.components.Ui.showDetailDialog("Session Details", "Session #" + session.getSessionID(),
            summary, actions.toArray(new Button[0]));
    }

    private void showCountersignDialog(BusinessSession session) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Countersign Session");
        dialog.setHeaderText("Manager / Admin Countersignature — Session #" + session.getSessionID());

        ButtonType signBtn = new ButtonType("Sign & Approve", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signBtn, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(480);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label infoLabel = new Label(
                "This session was started or ended by a cashier without a manager present.\n"
                + "Please review the totals below and sign off with your authorization code.");
        infoLabel.setWrapText(true);
        infoLabel.setStyle(
                "-fx-text-fill: #0c5460; -fx-background-color: #d1ecf1;"
                + "-fx-padding: 10 14; -fx-background-radius: 6; -fx-font-size: 12;");
        grid.add(infoLabel, 0, 0, 2, 1);

        grid.add(new Label("Started By:"), 0, 1);
        grid.add(new Label(session.getSupervisorName()), 1, 1);

        grid.add(new Label("Start Time:"), 0, 2);
        grid.add(new Label(session.getStartDate()
                .format(DATE_TIME_FMT)), 1, 2);

        grid.add(new Label("End Time:"), 0, 3);
        grid.add(new Label(session.getEndDate() != null
                ? session.getEndDate().format(DATE_TIME_FMT)
                : "—"), 1, 3);

        grid.add(new Label("Cash Sales:"), 0, 4);
        grid.add(new Label("R " + String.format("%.2f", session.getTotalCashSales())), 1, 4);

        grid.add(new Label("Card Sales:"), 0, 5);
        grid.add(new Label("R " + String.format("%.2f", session.getTotalCardSales())), 1, 5);

        grid.add(new Label("Total Sales:"), 0, 6);
        Label totalLbl = new Label("R " + String.format("%.2f", session.getTotalSales()));
        totalLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(totalLbl, 1, 6);

        if (session.getNotes() != null && !session.getNotes().isBlank()) {
            grid.add(new Separator(), 0, 7, 2, 1);
            grid.add(new Label("Cashier Notes:"), 0, 8);
            Label notesLbl = new Label(session.getNotes());
            notesLbl.setWrapText(true);
            notesLbl.setStyle("-fx-text-fill: #555;");
            grid.add(notesLbl, 1, 8);
        }

        grid.add(new Separator(), 0, 9, 2, 1);

        Label declLabel = new Label("I confirm the above totals are accurate and authorise this session:");
        declLabel.setWrapText(true);
        grid.add(declLabel, 0, 10, 2, 1);

        grid.add(new Label("Your Auth Code:"), 0, 11);
        PasswordField authField = new PasswordField();
        authField.setPromptText("Enter your 6-digit authorization code");
        grid.add(authField, 1, 11);

        grid.add(new Label("Notes (optional):"), 0, 12);
        TextArea countersignNotes = new TextArea();
        countersignNotes.setPrefRowCount(2);
        countersignNotes.setPromptText("Any comments or discrepancies noted...");
        grid.add(countersignNotes, 1, 12);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == signBtn) {
            String authCode = authField.getText().trim();
            if (authCode.isEmpty()) {
                showAlert("Error", "Authorization code is required", Alert.AlertType.ERROR);
                return;
            }
            if (!sessionService.verifySupervisorCode(currentUser.getStaffID(), authCode)) {
                showAlert("Error", "Invalid authorization code", Alert.AlertType.ERROR);
                return;
            }
            if (sessionService.countersignSession(
                    session.getSessionID(),
                    currentUser.getStaffID(),
                    authCode,
                    countersignNotes.getText())) {
                generateSessionPDFs(session.getSessionID());
                showAlert("Countersigned",
                        "Session #" + session.getSessionID() + " has been countersigned successfully.\n"
                        + "Updated reports have been generated.",
                        Alert.AlertType.INFORMATION);
                refreshView();
            } else {
                showAlert("Error", "Failed to countersign session", Alert.AlertType.ERROR);
            }
        }
    }

    private void viewSessionReports(BusinessSession session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Session Reports");
        alert.setHeaderText("Generate Session Reports");
        alert.setContentText("Generate PDF reports for Session #" + session.getSessionID() + "?");

        ButtonType declarationBtn = new ButtonType("Declaration Only");
        ButtonType statementBtn   = new ButtonType("Statement Only");
        ButtonType bothBtn        = new ButtonType("Both Reports");
        ButtonType cancelBtn      = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(declarationBtn, statementBtn, bothBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if      (result.get() == declarationBtn) generateDeclarationPDF(session.getSessionID());
            else if (result.get() == statementBtn)   generateStatementPDF(session.getSessionID());
            else if (result.get() == bothBtn)        generateSessionPDFs(session.getSessionID());
        }
    }

    private void generateSessionPDFs(int sessionID) {
        boolean dec  = generateDeclarationPDF(sessionID);
        boolean stmt = generateStatementPDF(sessionID);

        if (dec && stmt)      showAlert("Success", "Both reports generated successfully", Alert.AlertType.INFORMATION);
        else if (dec || stmt) showAlert("Partial Success", "One report generated successfully", Alert.AlertType.WARNING);
        else                  showAlert("Error", "Failed to generate reports", Alert.AlertType.ERROR);
    }

    private boolean generateDeclarationPDF(int sessionID) {
        try {
            // An absolute, user-writable folder — a relative "reports/..." path
            // resolves against the installed app's own directory (e.g. Program
            // Files), which a standard user usually can't write to.
            File reportsDir = new File(new com.pos.services.SettingsService().getReportsSavePath());
            if (!reportsDir.exists()) reportsDir.mkdirs();

            String filename = new File(reportsDir, "session_" + sessionID + "_declaration_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf").getPath();

            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, new FileOutputStream(filename));
            document.open();

            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED),
                    18, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED),
                    14, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED), 11);
            com.itextpdf.text.Font warningFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED),
                    11, com.itextpdf.text.Font.BOLD,
                    new com.itextpdf.text.BaseColor(133, 100, 4));

            com.itextpdf.text.Paragraph title = new com.itextpdf.text.Paragraph(
                    "END OF DAY SESSION DECLARATION", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            document.add(new com.itextpdf.text.Paragraph(
                    "===============================================", normalFont));
            document.add(Chunk.NEWLINE);

            BusinessSession session = sessionService.getSessionById(sessionID);

            document.add(new com.itextpdf.text.Paragraph("Session ID: " + session.getSessionID(), normalFont));
            document.add(new com.itextpdf.text.Paragraph("Started By: " + session.getSupervisorName(), normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "Start Time: " + session.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "End Time:   " + session.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    normalFont));

            if (!session.isDeclarationSigned()) {
                document.add(Chunk.NEWLINE);
                com.itextpdf.text.Paragraph flagNote = new com.itextpdf.text.Paragraph(
                        "WARNING: SESSION FLAGGED FOR MANAGER/ADMIN REVIEW — No manager/admin auth code was used.", warningFont);
                PdfPCell flagCell = new PdfPCell(flagNote);
                flagCell.setBackgroundColor(new com.itextpdf.text.BaseColor(255, 243, 205));
                PdfPTable flagTable = new PdfPTable(1);
                flagTable.addCell(flagCell);
                document.add(flagTable);
            }

            document.add(Chunk.NEWLINE);
            document.add(new com.itextpdf.text.Paragraph(
                    "-----------------------------------------------", normalFont));
            document.add(new com.itextpdf.text.Paragraph("SALES SUMMARY", headerFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "-----------------------------------------------", normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    String.format("Cash Sales:  R %.2f", session.getTotalCashSales()), normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    String.format("Card Sales:  R %.2f", session.getTotalCardSales()), normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    String.format("Total Sales: R %.2f", session.getTotalSales()), normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "-----------------------------------------------", normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new com.itextpdf.text.Paragraph("DECLARATION", headerFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "I hereby declare that the above totals are accurate", normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "and all transactions have been properly recorded.", normalFont));
            document.add(Chunk.NEWLINE);
            document.add(new com.itextpdf.text.Paragraph(
                    "Signed: " + (session.isDeclarationSigned() ? "YES (Manager/Admin)" : "CASHIER ONLY — Awaiting manager/admin countersignature"),
                    normalFont));

            if (session.getNotes() != null && !session.getNotes().isEmpty()) {
                document.add(Chunk.NEWLINE);
                document.add(new com.itextpdf.text.Paragraph("Notes:", headerFont));
                document.add(new com.itextpdf.text.Paragraph(session.getNotes(), normalFont));
            }

            document.add(Chunk.NEWLINE);
            document.add(new com.itextpdf.text.Paragraph(
                    "===============================================", normalFont));

            document.close();
            com.pos.components.PdfViewerDialog.show(null, new File(filename), "Session Declaration");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean generateStatementPDF(int sessionID) {
        try {
            File reportsDir = new File(new com.pos.services.SettingsService().getReportsSavePath());
            if (!reportsDir.exists()) reportsDir.mkdirs();

            String filename = new File(reportsDir, "session_" + sessionID + "_statement_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf").getPath();

            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, new FileOutputStream(filename));
            document.open();

            com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED),
                    16, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED),
                    12, com.itextpdf.text.Font.BOLD);
            com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED), 10);
            com.itextpdf.text.Font smallFont = new com.itextpdf.text.Font(
                    com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                            com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED), 8);

            com.itextpdf.text.Paragraph title = new com.itextpdf.text.Paragraph(
                    "DETAILED SESSION STATEMENT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            BusinessSession session = sessionService.getSessionById(sessionID);

            document.add(new com.itextpdf.text.Paragraph(
                    "Session ID: " + session.getSessionID() + " | Started By: " + session.getSupervisorName(),
                    normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    "Period: " + session.getStartDate().format(DATE_TIME_FMT)
                    + " to " + session.getEndDate().format(DATE_TIME_FMT),
                    normalFont));

            if (!session.isDeclarationSigned()) {
                document.add(Chunk.NEWLINE);
                com.itextpdf.text.Font warningFont = new com.itextpdf.text.Font(
                        com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA,
                                com.itextpdf.text.pdf.BaseFont.WINANSI, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED),
                        10, com.itextpdf.text.Font.BOLD,
                        new com.itextpdf.text.BaseColor(133, 100, 4));
                document.add(new com.itextpdf.text.Paragraph(
                        "WARNING: SESSION FLAGGED FOR MANAGER/ADMIN REVIEW", warningFont));
            }

            document.add(Chunk.NEWLINE);

            java.util.List<SessionService.SessionActivityDetail> activities =
                    sessionService.getSessionActivityDetails(sessionID);

            com.itextpdf.text.BaseColor color1 = new com.itextpdf.text.BaseColor(240, 248, 255);
            com.itextpdf.text.BaseColor color2 = new com.itextpdf.text.BaseColor(245, 245, 220);

            document.add(new com.itextpdf.text.Paragraph("SALES TRANSACTIONS", headerFont));
            PdfPTable transTable = new PdfPTable(8);
            transTable.setWidthPercentage(100);
            transTable.setSpacingBefore(10);
            transTable.setSpacingAfter(15);

            for (String h : new String[]{"Time","Sale ID","Customer","Product","Qty","Amount","Payment","Staff"})
                transTable.addCell(new PdfPCell(new Phrase(h, smallFont)));

            double totalSalesInTable = 0.0;
            String prevSaleId = null;
            com.itextpdf.text.BaseColor currentSaleColor = color1;

            for (SessionService.SessionActivityDetail activity : activities) {
                if ("SALE".equals(activity.getType())) {
                    String currentSaleId = String.valueOf(activity.getSaleID());
                    if (prevSaleId == null || !currentSaleId.equals(prevSaleId)) {
                        currentSaleColor = (currentSaleColor == color1) ? color2 : color1;
                        prevSaleId = currentSaleId;
                    }
                    totalSalesInTable += activity.getAmount();

                    addColoredCell(transTable, activity.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")), smallFont, currentSaleColor);
                    addColoredCell(transTable, currentSaleId, smallFont, currentSaleColor);
                    addColoredCell(transTable, activity.getCustomerName() != null ? activity.getCustomerName() : "", smallFont, currentSaleColor);
                    addColoredCell(transTable, activity.getProductName(), smallFont, currentSaleColor);
                    addColoredCell(transTable, String.valueOf(activity.getQuantity()), smallFont, currentSaleColor);
                    addColoredCell(transTable, String.format("R%.2f", activity.getAmount()), smallFont, currentSaleColor);
                    addColoredCell(transTable, activity.getPaymentMethod() != null ? activity.getPaymentMethod() : "", smallFont, currentSaleColor);
                    addColoredCell(transTable, activity.getStaffName(), smallFont, currentSaleColor);
                }
            }

            PdfPCell emptySpan = new PdfPCell(new Phrase(""));
            emptySpan.setColspan(5);
            emptySpan.setBorder(com.itextpdf.text.Rectangle.BOTTOM);
            transTable.addCell(emptySpan);
            PdfPCell totalCell = new PdfPCell(new Phrase("Total: R" + String.format("%.2f", totalSalesInTable), smallFont));
            totalCell.setColspan(3);
            totalCell.setBorder(com.itextpdf.text.Rectangle.BOTTOM);
            transTable.addCell(totalCell);
            document.add(transTable);

            document.add(new com.itextpdf.text.Paragraph("RETURNS", headerFont));
            PdfPTable returnTable = new PdfPTable(9);
            returnTable.setWidthPercentage(100);
            returnTable.setSpacingBefore(10);
            returnTable.setSpacingAfter(15);
            for (String h : new String[]{"Time","Return ID","Customer","Product","Qty","Refund","Reason","Status","Staff"})
                returnTable.addCell(new PdfPCell(new Phrase(h, smallFont)));

            String prevReturnId = null;
            com.itextpdf.text.BaseColor currentReturnColor = color1;
            for (SessionService.SessionActivityDetail activity : activities) {
                if ("RETURN".equals(activity.getType())) {
                    String currentReturnId = String.valueOf(activity.getReturnID());
                    if (prevReturnId == null || !currentReturnId.equals(prevReturnId)) {
                        currentReturnColor = (currentReturnColor == color1) ? color2 : color1;
                        prevReturnId = currentReturnId;
                    }
                    addColoredCell(returnTable, activity.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")), smallFont, currentReturnColor);
                    addColoredCell(returnTable, currentReturnId, smallFont, currentReturnColor);
                    addColoredCell(returnTable, activity.getCustomerName() != null ? activity.getCustomerName() : "", smallFont, currentReturnColor);
                    addColoredCell(returnTable, activity.getProductName(), smallFont, currentReturnColor);
                    addColoredCell(returnTable, String.valueOf(activity.getQuantity()), smallFont, currentReturnColor);
                    addColoredCell(returnTable, String.format("R%.2f", activity.getAmount()), smallFont, currentReturnColor);
                    addColoredCell(returnTable, activity.getReason() != null ? activity.getReason() : "", smallFont, currentReturnColor);
                    addColoredCell(returnTable, activity.getStatus(), smallFont, currentReturnColor);
                    addColoredCell(returnTable, activity.getStaffName(), smallFont, currentReturnColor);
                }
            }
            document.add(returnTable);

            document.add(new com.itextpdf.text.Paragraph("EXCHANGES", headerFont));
            PdfPTable exchangeTable = new PdfPTable(9);
            exchangeTable.setWidthPercentage(100);
            exchangeTable.setSpacingBefore(10);
            exchangeTable.setSpacingAfter(15);
            for (String h : new String[]{"Time","Exchange ID","Customer","Original Product","New Product","Top-up","Reason","Status","Staff"})
                exchangeTable.addCell(new PdfPCell(new Phrase(h, smallFont)));

            String prevExchangeId = null;
            com.itextpdf.text.BaseColor currentExchangeColor = color1;
            for (SessionService.SessionActivityDetail activity : activities) {
                if ("EXCHANGE".equals(activity.getType())) {
                    String currentExchangeId = String.valueOf(activity.getExchangeID());
                    if (prevExchangeId == null || !currentExchangeId.equals(prevExchangeId)) {
                        currentExchangeColor = (currentExchangeColor == color1) ? color2 : color1;
                        prevExchangeId = currentExchangeId;
                    }
                    addColoredCell(exchangeTable, activity.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")), smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, currentExchangeId, smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, activity.getCustomerName() != null ? activity.getCustomerName() : "", smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, activity.getProductName(), smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, activity.getNewProductName(), smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, String.format("R%.2f", activity.getTopUp()), smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, activity.getReason() != null ? activity.getReason() : "", smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, activity.getStatus(), smallFont, currentExchangeColor);
                    addColoredCell(exchangeTable, activity.getStaffName(), smallFont, currentExchangeColor);
                }
            }
            document.add(exchangeTable);

            document.add(Chunk.NEWLINE);
            document.add(new com.itextpdf.text.Paragraph("SUMMARY", headerFont));
            document.add(new com.itextpdf.text.Paragraph(
                    String.format("Total Cash Sales: R%.2f", session.getTotalCashSales()), normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    String.format("Total Card Sales: R%.2f", session.getTotalCardSales()), normalFont));
            document.add(new com.itextpdf.text.Paragraph(
                    String.format("Total Sales:      R%.2f", session.getTotalSales()), normalFont));

            document.close();
            com.pos.components.PdfViewerDialog.show(null, new File(filename), "Session Statement");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void addColoredCell(PdfPTable table, String text,
                                com.itextpdf.text.Font font,
                                com.itextpdf.text.BaseColor color) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(color);
        table.addCell(cell);
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        com.pos.components.Ui.showAlert(sessionTable, title, content, type);
    }
}
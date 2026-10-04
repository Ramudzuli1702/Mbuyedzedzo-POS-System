package com.pos.views;

import com.pos.models.User;
import com.pos.services.SettingsService;
import com.pos.services.SettingsService.BackupResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * SettingsView — two-column card layout inspired by MarketingView.
 *
 * Left column  : Business Profile card  +  File Paths card
 * Right column : Email card             +  Backup card
 */
public class SettingsView {

    private final User currentUser;
    private final SettingsService settings;
    private Stage ownerStage;

    public SettingsView(User user) {
        this.currentUser = user;
        this.settings    = new SettingsService();
    }

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());

        HBox body = new HBox(20);
        body.setPadding(new Insets(20));

        // Left column — fixed width, two stacked cards
        VBox leftCol = new VBox(20);
        leftCol.setPrefWidth(480);
        leftCol.setMinWidth(420);
        leftCol.getChildren().addAll(
            buildBusinessProfileCard(),
            buildFilePathsCard()
        );

        // Right column — grows to fill remaining space
        VBox rightCol = new VBox(20);
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        rightCol.getChildren().addAll(
            buildEmailCard(),
            buildBackupCard(),
            buildDiagnosticsCard()
        );

        body.getChildren().addAll(leftCol, rightCol);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f5f7fa; -fx-background-color: #f5f7fa;");

        layout.setCenter(scroll);
        return layout;
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private HBox createTopBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(20));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Settings");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        if (!currentUser.hasFullAccess()) {
            Label note = new Label("  View only — contact a manager to change settings");
            note.setFont(Font.font("System", 12));
            note.setTextFill(Color.web("#d97706"));
            bar.getChildren().addAll(title, note);
        } else {
            bar.getChildren().add(title);
        }
        return bar;
    }

    // ── 1. Business Profile card ──────────────────────────────────────────────

    private VBox buildBusinessProfileCard() {
        VBox card = card();

        Label heading = cardHeading("Business Profile");
        Label sub = new Label("Appears on all receipts and reports.");
        sub.setFont(Font.font("System", 12));
        sub.setTextFill(Color.web("#64748b"));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        TextField nameField    = field(settings.getBusinessName());
        TextArea  addrField    = textArea(settings.getBusinessAddress(), 3);
        TextField phoneField   = field(settings.getBusinessPhone());
        TextField emailField   = field(settings.getBusinessEmail());
        TextField vatField     = field(settings.getBusinessVatNo());
        TextField websiteField = field(settings.getBusinessWebsite());
        TextArea  footerField  = textArea(settings.getReceiptFooter(), 2);

        ComboBox<String> receiptSizeCombo = new ComboBox<>();
        receiptSizeCombo.getItems().addAll(
            "58mm thermal (32 columns)",
            "80mm thermal (40 columns)",
            "112mm thermal (56 columns)"
        );
        receiptSizeCombo.setStyle(fieldStyle());
        receiptSizeCombo.setMaxWidth(Double.MAX_VALUE);
        int currentCols = settings.getReceiptColumns();
        receiptSizeCombo.setValue(switch (currentCols) {
            case 32 -> "58mm thermal (32 columns)";
            case 56 -> "112mm thermal (56 columns)";
            default -> "80mm thermal (40 columns)";
        });

        addRow(grid, 0, "Business Name *", nameField);
        addRow(grid, 1, "Address",         addrField);
        addRow(grid, 2, "Phone",           phoneField);
        addRow(grid, 3, "Email",           emailField);
        addRow(grid, 4, "VAT Number",      vatField);
        addRow(grid, 5, "Website",         websiteField);
        addRow(grid, 6, "Receipt Footer",  footerField);
        addRow(grid, 7, "Receipt Printer Size", receiptSizeCombo);

        Label status = statusLabel();

        Button saveBtn = saveButton();
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> {
            if (nameField.getText().trim().isEmpty()) {
                status("Business name cannot be empty.", status, false);
                return;
            }
            settings.saveBusinessSetting("business.name",    nameField.getText().trim());
            settings.saveBusinessSetting("business.address", addrField.getText().trim());
            settings.saveBusinessSetting("business.phone",   phoneField.getText().trim());
            settings.saveBusinessSetting("business.email",   emailField.getText().trim());
            settings.saveBusinessSetting("business.vatNo",   vatField.getText().trim());
            settings.saveBusinessSetting("business.website", websiteField.getText().trim());
            settings.saveBusinessSetting("receipt.footer",   footerField.getText().trim());
            int cols = switch (receiptSizeCombo.getValue()) {
                case "58mm thermal (32 columns)"  -> 32;
                case "112mm thermal (56 columns)" -> 56;
                default -> 40;
            };
            settings.saveBusinessSetting("receipt.columns", String.valueOf(cols));
            status("Business profile saved.", status, true);
        });

        card.getChildren().addAll(heading, sub, new Separator(), grid, saveBtn, status);
        return card;
    }

    // Licence/subscription management moved to its own "Subscription" nav
    // item (SubscriptionView) — a shop owner looks for that under its own
    // tab, not buried in Settings, and it has room for renewal/plan details
    // this card didn't.

    // ── 2. File Paths card ────────────────────────────────────────────────────

    private VBox buildFilePathsCard() {
        VBox card = card();

        Label heading = cardHeading("File Save Locations");
        Label sub = new Label("Where receipts, reports, and backups are saved on this machine.");
        sub.setFont(Font.font("System", 12));
        sub.setTextFill(Color.web("#64748b"));
        sub.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        TextField receiptPath = field(settings.getReceiptSavePath());
        TextField reportPath  = field(settings.getReportsSavePath());
        TextField backupPath  = field(settings.getBackupSavePath());

        addRowWithBrowse(grid, 0, "Receipts Folder", receiptPath);
        addRowWithBrowse(grid, 1, "Reports Folder",  reportPath);
        addRowWithBrowse(grid, 2, "Backups Folder",  backupPath);

        Label status = statusLabel();

        Button saveBtn = saveButton();
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> {
            settings.saveBusinessSetting("receipt.savePath", ensureTrailingSlash(receiptPath.getText()));
            settings.saveBusinessSetting("reports.savePath", ensureTrailingSlash(reportPath.getText()));
            settings.saveBusinessSetting("backup.savePath",  ensureTrailingSlash(backupPath.getText()));
            status("File paths saved.", status, true);
        });

        card.getChildren().addAll(heading, sub, new Separator(), grid, saveBtn, status);
        return card;
    }

    // ── 3. Email card ─────────────────────────────────────────────────────────

    private VBox buildEmailCard() {
        VBox card = card();

        Label heading = cardHeading("Email (SMTP) Configuration");
        Label sub = new Label(
            "Credentials are stored encrypted on this machine only.\n" +
            "Gmail recommended — use an App Password, not your account password.\n" +
            "Get one at: myaccount.google.com → Security → App passwords"
        );
        sub.setFont(Font.font("System", 12));
        sub.setTextFill(Color.web("#64748b"));
        sub.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        TextField     usernameField = field(settings.getEmailUsername());
        PasswordField passwordField = new PasswordField();
        passwordField.setText(settings.getEmailPassword());
        passwordField.setStyle(fieldStyle());
        passwordField.setMaxWidth(Double.MAX_VALUE);
        TextField fromNameField = field(settings.getEmailFromName());

        addRow(grid, 0, "Gmail Address", usernameField);
        addRow(grid, 1, "App Password",  passwordField);
        addRow(grid, 2, "Sender Name",   fromNameField);

        Label hint = new Label(
            "ℹ️  App Password: 16 chars, no spaces (e.g. abcdefghijklmnop)"
        );
        hint.setFont(Font.font("System", 11));
        hint.setTextFill(Color.web("#1d4ed8"));
        hint.setWrapText(true);

        Label status = statusLabel();

        Button saveBtn = saveButton();
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> {
            settings.saveEmailCredentials(
                usernameField.getText().trim(),
                passwordField.getText(),
                fromNameField.getText().trim()
            );
            status("Email settings saved (encrypted).", status, true);
        });

        Button testBtn = new Button("Send Test Email");
        testBtn.setMaxWidth(Double.MAX_VALUE);
        testBtn.setStyle(
            "-fx-background-color: #2563eb; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;"
        );
        testBtn.setOnAction(e -> {
            String user = usernameField.getText().trim();
            if (user.isEmpty()) {
                status("Enter a Gmail address first.", status, false);
                return;
            }
            status("Sending test email to " + user + "...", status, true);
            new Thread(() -> {
                try {
                    settings.saveEmailCredentials(user, passwordField.getText(), fromNameField.getText());
                    javafx.application.Platform.runLater(() ->
                        status("Test sent! Check inbox at " + user, status, true));
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                        status("Failed: " + ex.getMessage(), status, false));
                }
            }).start();
        });

        card.getChildren().addAll(heading, sub, new Separator(), grid, hint, saveBtn, testBtn, status);
        return card;
    }

    // ── 4. Backup card ────────────────────────────────────────────────────────

    private VBox buildBackupCard() {
        VBox card = card();

        Label heading = cardHeading("Database Backup");

        VBox infoBox = new VBox(8,
            infoLine("Creates a full SQL dump of all data"),
            infoLine("Saved to: " + settings.getBackupSavePath()),
            infoLine("Includes all transactions, customers, products, and settings"),
            infoLine("Requires mysqldump installed (comes with MySQL)")
        );

        Label status = statusLabel();

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(26, 26);
        spinner.setVisible(false);

        Button backupBtn = new Button("Create Backup Now");
        backupBtn.setMaxWidth(Double.MAX_VALUE);
        backupBtn.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13;" +
            "-fx-padding: 11 24; -fx-background-radius: 8; -fx-cursor: hand;"
        );

        HBox backupRow = new HBox(12, backupBtn, spinner);
        backupRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(backupBtn, Priority.ALWAYS);

        backupBtn.setOnAction(e -> {
            if (!currentUser.hasFullAccess()) {
                status("Only managers and admins can create backups.", status, false);
                return;
            }
            backupBtn.setDisable(true);
            spinner.setVisible(true);
            status("Creating backup...", status, true);

            new Thread(() -> {
                BackupResult result = new SettingsService().createBackup();
                javafx.application.Platform.runLater(() -> {
                    backupBtn.setDisable(false);
                    spinner.setVisible(false);
                    if (result.success()) {
                        status("" + result.message(), status, true);
                    } else {
                        status("" + result.message(), status, false);
                    }
                });
            }, "DB-Backup").start();
        });

        // Recent backups list
        Label historyHeading = new Label("Recent Backups");
        historyHeading.setFont(Font.font("System", FontWeight.BOLD, 13));
        historyHeading.setTextFill(Color.web("#1e293b"));

        ListView<String> historyList = new ListView<>();
        historyList.setPrefHeight(180);
        historyList.setStyle("-fx-background-radius: 6; -fx-border-color: #e0e0e0; -fx-border-radius: 6;");
        refreshBackupHistory(historyList);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle(
            "-fx-background-color: #f1f5f9; -fx-text-fill: #1e293b;" +
            "-fx-padding: 7 14; -fx-background-radius: 6; -fx-cursor: hand;"
        );
        refreshBtn.setOnAction(e -> refreshBackupHistory(historyList));

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        HBox historyHeader = new HBox(10, historyHeading, hSpacer, refreshBtn);
        historyHeader.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
            heading, infoBox, new Separator(),
            backupRow, status,
            new Separator(),
            historyHeader, historyList
        );
        return card;
    }

    private VBox buildDiagnosticsCard() {
        VBox card = card();

        Label heading = cardHeading("Diagnostics");

        VBox infoBox = new VBox(8,
            infoLine("Errors and activity are written to a rolling log file"),
            infoLine("Folder: " + com.pos.utils.LogSetup.logDir())
        );

        Button openLogs = new Button("Open Logs Folder");
        openLogs.setMaxWidth(Double.MAX_VALUE);
        openLogs.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13;" +
            "-fx-padding: 11 24; -fx-background-radius: 8; -fx-cursor: hand;"
        );
        openLogs.setOnAction(e -> com.pos.utils.LogSetup.openLogFolder());

        card.getChildren().addAll(heading, infoBox, new Separator(), openLogs);
        return card;
    }

    private void refreshBackupHistory(ListView<String> list) {
        list.getItems().clear();
        File dir = new File(new SettingsService().getBackupSavePath());
        if (!dir.exists()) {
            list.getItems().add("No backups found yet.");
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".sql"));
        if (files == null || files.length == 0) {
            list.getItems().add("No backups found yet.");
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = 0; i < Math.min(10, files.length); i++) {
            long kb = files[i].length() / 1024;
            list.getItems().add(files[i].getName() + "  (" + kb + " KB)");
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Base card — white, rounded, subtle shadow. */
    private VBox card() {
        VBox card = new VBox(14);
        card.setPadding(new Insets(20));
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);"
        );
        return card;
    }

    private Label cardHeading(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 16));
        label.setTextFill(Color.web("#0f766e"));
        return label;
    }

    private TextField field(String value) {
        TextField tf = new TextField(value);
        tf.setStyle(fieldStyle());
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private TextArea textArea(String value, int rows) {
        TextArea ta = new TextArea(value);
        ta.setPrefRowCount(rows);
        ta.setWrapText(true);
        ta.setStyle(fieldStyle());
        ta.setMaxWidth(Double.MAX_VALUE);
        return ta;
    }

    private String fieldStyle() {
        return "-fx-font-size: 13; -fx-padding: 9; -fx-background-radius: 6;" +
               "-fx-border-color: #e0e0e0; -fx-border-radius: 6;";
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label + ":");
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        lbl.setTextFill(Color.web("#1e293b"));
        lbl.setMinWidth(120);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
    }

    private void addRowWithBrowse(GridPane grid, int row, String label, TextField field) {
        Label lbl = new Label(label + ":");
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        lbl.setTextFill(Color.web("#1e293b"));
        lbl.setMinWidth(120);

        Button browse = new Button("Browse…");
        browse.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 8 12; -fx-cursor: hand;");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select " + label);
            File current = new File(field.getText().isEmpty() ? "." : field.getText());
            if (current.exists()) chooser.setInitialDirectory(current);
            File chosen = chooser.showDialog(ownerStage);
            if (chosen != null) field.setText(chosen.getAbsolutePath() + "/");
        });

        HBox row1 = new HBox(8, field, browse);
        row1.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);

        grid.add(lbl, 0, row);
        grid.add(row1, 1, row);
    }

    private HBox infoLine(String text) {
        javafx.scene.Node bullet = com.pos.utils.Icons.tinted(com.pos.utils.Icons.dot(6), "#94a3b8");
        Label t = new Label(text);
        t.setFont(Font.font("System", 12));
        t.setTextFill(Color.web("#1e293b"));
        HBox row = new HBox(10, bullet, t);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label statusLabel() {
        Label lbl = new Label("");
        lbl.setWrapText(true);
        lbl.setFont(Font.font("System", 12));
        return lbl;
    }

    private void status(String msg, Label lbl, boolean success) {
        lbl.setText(msg);
        lbl.setTextFill(Color.web(success ? "#16a34a" : "#dc2626"));
    }

    private Button saveButton() {
        Button btn = new Button("Save Changes");
        btn.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 10 24;" +
            "-fx-background-radius: 6; -fx-cursor: hand;"
        );
        btn.setDisable(!currentUser.hasFullAccess());
        return btn;
    }

    private String ensureTrailingSlash(String path) {
        path = path.trim();
        return path.endsWith("/") || path.endsWith("\\") ? path : path + "/";
    }
}
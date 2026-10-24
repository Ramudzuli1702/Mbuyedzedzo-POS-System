package com.pos.views;

import com.pos.setup.FirstRunSetup;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * SetupView — shown while FirstRunSetup runs in a background thread.
 *
 * Displays:
 *   • App name / branding
 *   • An indeterminate progress bar
 *   • A live status label
 *   • A password-prompt panel (hidden until needed)
 *   • An error panel (hidden until something goes wrong)
 */
public class SetupView {

    private final Stage stage;

    private Label       statusLabel;
    private Label       errorLabel;
    private ProgressBar progressBar;
    private Label       restoreChosenLabel;
    private File        chosenBackupFile;

    // Password-prompt panel
    private VBox        promptPanel;
    private PasswordField passwordField;
    private TextField   passwordVisible;
    private Label       wrongPasswordHint;
    private Label       passwordHeading;
    private Label       passwordDescription;
    private PasswordField confirmPassword;
    private Button      submitButton;
    private Button      retryButton;
    private FirstRunSetup.PasswordRequest passwordRequest;
    private CompletableFuture<String> passwordAnswer;
    private Runnable retryAction;

    public SetupView(Stage stage) {
        this.stage = stage;
    }

    // ── Show ───────────────────────────────────────────────────────────────────

    public void show() {
        VBox root = new VBox(24);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(60, 80, 60, 80));
        root.setStyle("-fx-background-color: #0f766e;");

        // ── Branding ──
        Label appName = new Label(com.pos.Branding.DISPLAY_NAME);
        appName.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        appName.setFont(Font.font("System", FontWeight.BOLD, 32));
        appName.setTextFill(Color.WHITE);

        Label tagLine = new Label("Setting up your system — this only happens once");
        tagLine.setFont(Font.font("System", 15));
        tagLine.setTextFill(Color.web("#ccfbf1"));

        // ── Progress bar ──
        progressBar = new ProgressBar(-1);
        progressBar.setPrefWidth(480);
        progressBar.setPrefHeight(12);
        progressBar.setStyle(
            "-fx-accent: #ccfbf1;" +
            "-fx-background-color: rgba(255,255,255,0.2);" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;"
        );

        // ── Status label ──
        statusLabel = new Label("Initialising...");
        statusLabel.setFont(Font.font("System", 14));
        statusLabel.setTextFill(Color.web("#ccfbf1"));
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(480);
        statusLabel.setAlignment(Pos.CENTER);

        // ── Restore from backup (optional, for a replacement/new PC) ──
        VBox restorePanel = buildRestorePanel();

        // ── Password prompt panel (hidden until needed) ──
        promptPanel = buildPasswordPromptPanel();
        promptPanel.setVisible(false);
        promptPanel.setManaged(false);

        // ── Error label (hidden until something goes wrong) ──
        errorLabel = new Label();
        errorLabel.setFont(Font.font("System", 13));
        errorLabel.setTextFill(Color.web("#fca5a5"));
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(520);
        errorLabel.setAlignment(Pos.CENTER);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setPadding(new Insets(16));
        errorLabel.setStyle(
            "-fx-background-color: rgba(239,68,68,0.15);" +
            "-fx-background-radius: 8;"
        );

        retryButton = new Button("Retry setup");
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(e -> {
            if (retryAction != null) {
                resetForRetry();
                retryAction.run();
            }
        });

        root.getChildren().addAll(
            appName, tagLine, progressBar, statusLabel,
            restorePanel, promptPanel, errorLabel, retryButton
        );

        // Password confirmation and diagnostic text must remain reachable on small screens.
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background: #0f766e; -fx-background-color: #0f766e;");
        root.setMinHeight(Region.USE_PREF_SIZE);
        Scene scene = new Scene(scroll);
        stage.setScene(scene);
        stage.show();
    }

    // ── Restore-from-backup panel ───────────────────────────────────────────────

    /**
     * Setting up on a replacement/new PC after an old one died or was
     * swapped out shouldn't mean starting from an empty shop — this lets
     * that first-run pick a previous backup .sql file (made from Settings on
     * the old machine) to restore instead of the usual blank database.
     * Picking a file here doesn't do anything by itself; POSApplication
     * imports it right after the fresh schema is created, overwriting it
     * with the backup's real data.
     */
    private VBox buildRestorePanel() {
        VBox panel = new VBox(6);
        panel.setAlignment(Pos.CENTER);

        Button chooseBtn = new Button("Restore from a backup file instead...");
        chooseBtn.setFont(Font.font("System", 13));
        chooseBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #ccfbf1;" +
            "-fx-underline: true;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: transparent;"
        );

        restoreChosenLabel = new Label();
        restoreChosenLabel.setFont(Font.font("System", 12));
        restoreChosenLabel.setTextFill(Color.web("#ccfbf1"));
        restoreChosenLabel.setVisible(false);
        restoreChosenLabel.setManaged(false);

        chooseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a backup file (.sql)");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQL backup files", "*.sql"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                chosenBackupFile = file;
                restoreChosenLabel.setText("Will restore from: " + file.getName());
                restoreChosenLabel.setVisible(true);
                restoreChosenLabel.setManaged(true);
            }
        });

        panel.getChildren().addAll(chooseBtn, restoreChosenLabel);
        return panel;
    }

    /** The backup file picked on this screen, or null if setup should proceed fresh/empty. */
    public File getChosenBackupFile() {
        return chosenBackupFile;
    }

    // ── Password prompt panel builder ──────────────────────────────────────────

    private VBox buildPasswordPromptPanel() {
        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(480);
        panel.setPadding(new Insets(20));
        panel.setStyle(
            "-fx-background-color: rgba(255,255,255,0.10);" +
            "-fx-background-radius: 10;"
        );

        Label heading = new Label("Server password");
        passwordHeading = heading;
        heading.setFont(Font.font("System", FontWeight.BOLD, 16));
        heading.setTextFill(Color.WHITE);

        Label sub = new Label(
            "Choose a server password. We will handle the installation and configuration."
        );
        passwordDescription = sub;
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web("#ccfbf1"));
        sub.setWrapText(true);
        sub.setAlignment(Pos.CENTER);

        // Wrong-password hint (hidden initially)
        wrongPasswordHint = new Label("  Incorrect password — please try again.");
        wrongPasswordHint.setFont(Font.font("System", 13));
        wrongPasswordHint.setTextFill(Color.web("#fca5a5"));
        wrongPasswordHint.setVisible(false);
        wrongPasswordHint.setManaged(false);

        // Password field row
        passwordField   = new PasswordField();
        passwordField.setPromptText("Server password");
        passwordVisible = new TextField();
        passwordVisible.setPromptText("Server password");
        confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm server password");
        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);

        for (Control f : new Control[]{ passwordField, passwordVisible, confirmPassword }) {
            ((Region) f).setPrefWidth(300);
            f.setStyle(
                "-fx-background-color: rgba(255,255,255,0.9);" +
                "-fx-background-radius: 6;" +
                "-fx-font-size: 14;"
            );
        }

        // Keep the two fields in sync
        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!passwordVisible.getText().equals(n)) passwordVisible.setText(n);
        });
        passwordVisible.textProperty().addListener((obs, o, n) -> {
            if (!passwordField.getText().equals(n)) passwordField.setText(n);
        });

        CheckBox showPwd = new CheckBox("Show password");
        showPwd.setTextFill(Color.web("#ccfbf1"));
        showPwd.setFont(Font.font("System", 13));
        showPwd.selectedProperty().addListener((obs, wasOn, isOn) -> {
            passwordField.setVisible(!isOn);
            passwordField.setManaged(!isOn);
            passwordVisible.setVisible(isOn);
            passwordVisible.setManaged(isOn);
            if (isOn) { passwordVisible.requestFocus(); passwordVisible.end(); }
            else      { passwordField.requestFocus();   passwordField.end();   }
        });

        // Buttons
        Button connectBtn = new Button("Connect");
        submitButton = connectBtn;
        connectBtn.setFont(Font.font("System", FontWeight.BOLD, 14));
        connectBtn.setStyle(
            "-fx-background-color: #0d9488;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 8 28;"
        );
        connectBtn.setDefaultButton(true);
        connectBtn.setOnAction(e -> submitPassword());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setFont(Font.font("System", 13));
        cancelBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #ccfbf1;" +
            "-fx-border-color: #ccfbf1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 8 20;"
        );
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> cancelPassword());

        HBox buttons = new HBox(12, connectBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(heading, sub, wrongPasswordHint,
                                   passwordField, passwordVisible,
                                   confirmPassword, showPwd, buttons);
        return panel;
    }

    // ── Public update methods (thread-safe) ────────────────────────────────────

    public void setStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            System.out.println("[Setup] " + message);
        });
    }

    public void setProgress(double value) {
        Platform.runLater(() -> progressBar.setProgress(value));
    }

    public void showError(String message) {
        Platform.runLater(() -> {
            progressBar.setProgress(0);
            statusLabel.setText("Setup failed. See details below.");
            statusLabel.setTextFill(Color.web("#fca5a5"));
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            // Hide the prompt panel if it was showing
            promptPanel.setVisible(false);
            promptPanel.setManaged(false);
            retryButton.setVisible(true);
            retryButton.setManaged(true);
        });
    }

    public void setOnRetry(Runnable action) { retryAction = action; }

    private void resetForRetry() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        statusLabel.setTextFill(Color.web("#ccfbf1"));
        progressBar.setProgress(-1);
    }

    // ── Password prompting (called from setup thread, blocks until answered) ───

    /**
     * Shows the password-prompt panel and blocks the calling (setup) thread
     * until the user submits a password or cancels.
     *
     * @param request whether to choose a new password or enter an existing one
     * @return the password entered, or {@code null} if the user cancelled
     */
    public String promptForPassword(FirstRunSetup.PasswordRequest request) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            passwordRequest = request;
            passwordAnswer = answer;
            passwordHeading.setText(request.newPassword() ? "Choose your server password" : "Connect to your existing server");
            passwordDescription.setText(request.newPassword()
                    ? "Choose and confirm a password of at least 8 characters. Keep it safe. We will install and configure your database automatically."
                    : "Enter the MySQL root password already used on this computer. Your existing password and databases will be kept.");
            submitButton.setText(request.newPassword() ? "Set up database" : "Connect");
            confirmPassword.setVisible(request.newPassword());
            confirmPassword.setManaged(request.newPassword());
            confirmPassword.clear();
            wrongPasswordHint.setText("Incorrect password. Please try again.");
            wrongPasswordHint.setVisible(request.wrongPassword());
            wrongPasswordHint.setManaged(request.wrongPassword());
            passwordField.clear();
            passwordVisible.clear();
            progressBar.setProgress(0); // waiting for the user
            promptPanel.setVisible(true);
            promptPanel.setManaged(true);
            passwordField.requestFocus();
        });

        try {
            return answer.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    /**
     * Returns a {@link FirstRunSetup.PasswordPrompter} backed by this view.
     * Pass this to {@code FirstRunSetup.run()} as the second argument.
     */
    public FirstRunSetup.PasswordPrompter asPasswordPrompter() {
        return this::promptForPassword;
    }

    // ── Private button handlers (run on FX thread) ────────────────────────────

    private void submitPassword() {
        if (passwordAnswer == null || passwordAnswer.isDone()) return;
        String pwd = passwordField.isVisible()
            ? passwordField.getText()
            : passwordVisible.getText();
        if (passwordRequest.newPassword()) {
            String problem = FirstRunSetup.passwordProblem(pwd);
            if (problem == null && !pwd.equals(confirmPassword.getText())) problem = "The passwords do not match.";
            if (problem != null) {
                wrongPasswordHint.setText(problem);
                wrongPasswordHint.setVisible(true);
                wrongPasswordHint.setManaged(true);
                return;
            }
        }
        promptPanel.setVisible(false);
        promptPanel.setManaged(false);
        passwordField.clear();
        passwordVisible.clear();
        confirmPassword.clear();
        progressBar.setProgress(-1);
        passwordAnswer.complete(pwd);
    }

    private void cancelPassword() {
        promptPanel.setVisible(false);
        promptPanel.setManaged(false);
        passwordField.clear();
        passwordVisible.clear();
        confirmPassword.clear();
        if (passwordAnswer != null) passwordAnswer.complete(null);
    }
}

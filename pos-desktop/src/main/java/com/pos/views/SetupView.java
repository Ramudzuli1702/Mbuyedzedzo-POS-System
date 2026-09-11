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
import javafx.stage.Stage;

import java.util.concurrent.SynchronousQueue;

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

    // Password-prompt panel
    private VBox        promptPanel;
    private PasswordField passwordField;
    private TextField   passwordVisible;
    private Label       wrongPasswordHint;

    /**
     * A SynchronousQueue used to hand the user-entered password back to the
     * setup thread that is blocking in promptForPassword().
     * null is placed in the queue when the user cancels.
     */
    private final SynchronousQueue<String> passwordQueue = new SynchronousQueue<>();

    // Sentinel object placed in the queue when the user cancels
    private static final String CANCEL_SENTINEL = "\u0000CANCEL";

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
        Label appName = new Label(com.pos.Branding.APP_NAME);
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

        root.getChildren().addAll(
            appName, tagLine, progressBar, statusLabel,
            promptPanel, errorLabel
        );

        Scene scene = new Scene(root, 1200, 700);
        stage.setScene(scene);
        stage.show();
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

        Label heading = new Label("MySQL Root Password Required");
        heading.setFont(Font.font("System", FontWeight.BOLD, 16));
        heading.setTextFill(Color.WHITE);

        Label sub = new Label(
            "MySQL is already installed on this machine with a password you set.\n" +
            "Enter it below so the POS system can create its database."
        );
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web("#ccfbf1"));
        sub.setWrapText(true);
        sub.setAlignment(Pos.CENTER);

        // Wrong-password hint (hidden initially)
        wrongPasswordHint = new Label("⚠  Incorrect password — please try again.");
        wrongPasswordHint.setFont(Font.font("System", 13));
        wrongPasswordHint.setTextFill(Color.web("#fca5a5"));
        wrongPasswordHint.setVisible(false);
        wrongPasswordHint.setManaged(false);

        // Password field row
        passwordField   = new PasswordField();
        passwordVisible = new TextField();
        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);

        for (Control f : new Control[]{ passwordField, passwordVisible }) {
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
                                   showPwd, buttons);
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
        });
    }

    // ── Password prompting (called from setup thread, blocks until answered) ───

    /**
     * Shows the password-prompt panel and blocks the calling (setup) thread
     * until the user submits a password or cancels.
     *
     * @param wrongPassword if true, shows the "incorrect password" hint
     * @return the password entered, or {@code null} if the user cancelled
     */
    public String promptForPassword(boolean wrongPassword) {
        Platform.runLater(() -> {
            wrongPasswordHint.setVisible(wrongPassword);
            wrongPasswordHint.setManaged(wrongPassword);
            passwordField.clear();
            passwordVisible.clear();
            progressBar.setProgress(-1); // keep it spinning
            promptPanel.setVisible(true);
            promptPanel.setManaged(true);
            passwordField.requestFocus();
        });

        try {
            String result = passwordQueue.take(); // blocks until submitPassword/cancelPassword
            if (CANCEL_SENTINEL.equals(result)) return null;
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        String pwd = passwordField.isVisible()
            ? passwordField.getText()
            : passwordVisible.getText();
        promptPanel.setVisible(false);
        promptPanel.setManaged(false);
        try { passwordQueue.put(pwd); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cancelPassword() {
        promptPanel.setVisible(false);
        promptPanel.setManaged(false);
        try { passwordQueue.put(CANCEL_SENTINEL); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
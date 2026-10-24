package com.pos.views;

import com.pos.database.DatabaseConnection;
import com.pos.utils.PasswordUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * RegisterView — shown the very first time the app launches (Staff table is empty).
 *
 * Creates the initial administrator account.
 * After successful registration the user is taken straight to LoginView.
 *
 * The first user always gets:
 *   UserType = 'Admin'
 *   Status   = 'Active'
 */
public class RegisterView {

    private final Stage stage;

    public RegisterView(Stage stage) {
        this.stage = stage;
    }

    // ── Show ───────────────────────────────────────────────────────────────────

    public void show() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f766e;");

        VBox card = buildCard();
        root.setCenter(card);

        Scene scene = new Scene(root); // no explicit size — see LoginView
        root.prefWidthProperty().bind(scene.widthProperty());
        root.prefHeightProperty().bind(scene.heightProperty());
        stage.setScene(scene);
        stage.show();
    }

    // ── Card ───────────────────────────────────────────────────────────────────

    private VBox buildCard() {
        VBox card = new VBox(18);
        card.setMaxWidth(480);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(44));
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 15;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
        );

        // ── Header ──
        Label title = new Label(com.pos.Branding.DISPLAY_NAME);
        title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        title.setFont(Font.font("System", FontWeight.BOLD, 26));
        title.setTextFill(Color.web("#0f766e"));

        Label subtitle = new Label(
            "Create your administrator account to get started.\n" +
            "This is a one-time setup step."
        );
        subtitle.setFont(Font.font("System", 13));
        subtitle.setTextFill(Color.web("#666666"));
        subtitle.setTextAlignment(TextAlignment.CENTER);
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(380);

        Separator sep = new Separator();
        sep.setMaxWidth(380);

        // ── Fields ──
        TextField fullNameField    = styledField("Full name");
        TextField emailField       = styledField("Email address");
        PasswordField passwordField    = styledPassword("Password  (min 8 characters)");
        PasswordField confirmField  = styledPassword("Confirm password");

        // ── Error label ──
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setFont(Font.font(12));
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(380);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // ── Submit button ──
        Button registerBtn = new Button("Create Administrator Account");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white;" +
            "-fx-font-size: 14; -fx-font-weight: bold;" +
            "-fx-padding: 14; -fx-background-radius: 8; -fx-cursor: hand;"
        );
        registerBtn.setOnMouseEntered(e -> registerBtn.setStyle(
            "-fx-background-color: #0c5c57; -fx-text-fill: white;" +
            "-fx-font-size: 14; -fx-font-weight: bold;" +
            "-fx-padding: 14; -fx-background-radius: 8; -fx-cursor: hand;"
        ));
        registerBtn.setOnMouseExited(e -> registerBtn.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white;" +
            "-fx-font-size: 14; -fx-font-weight: bold;" +
            "-fx-padding: 14; -fx-background-radius: 8; -fx-cursor: hand;"
        ));

        registerBtn.setOnAction(e -> handleRegister(
            fullNameField, emailField, passwordField, confirmField, errorLabel
        ));

        // Allow Enter key on last field
        confirmField.setOnAction(e -> registerBtn.fire());

        card.getChildren().addAll(
            title, subtitle, sep,
            labeledField("Full Name",        fullNameField),
            labeledField("Email Address",    emailField),
            labeledField("Password",         passwordField),
            labeledField("Confirm Password", confirmField),
            errorLabel,
            registerBtn
        );

        return card;
    }

    // ── Logic ──────────────────────────────────────────────────────────────────

    private void handleRegister(TextField fullNameField, TextField emailField,
                                 PasswordField passwordField, PasswordField confirmField,
                                 Label errorLabel) {
        String fullName = fullNameField.getText().trim();
        String email    = emailField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmField.getText();

        // ── Validation ──
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError(errorLabel, "Please fill in all fields.");
            return;
        }
        if (!email.matches("^[\\w.+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            showError(errorLabel, "Please enter a valid email address.");
            return;
        }
        if (password.length() < 8) {
            showError(errorLabel, "Password must be at least 8 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            showError(errorLabel, "Passwords do not match.");
            return;
        }

        // ── Persist ──
        if(!new com.pos.services.UserService().bootstrapAdmin(fullName,email,password)) {
            showError(errorLabel,"The administrator could not be created. An account may already exist; restart the app to sign in.");return;
        }
        passwordField.clear();confirmField.clear();
        new LoginView(stage).show();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private VBox labeledField(String labelText, Control field) {
        VBox box = new VBox(6);
        Label label = new Label(labelText);
        label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        label.setTextFill(Color.web("#374151"));
        box.getChildren().addAll(label, field);
        return box;
    }

    private TextField styledField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        applyFieldStyle(f);
        return f;
    }

    private PasswordField styledPassword(String prompt) {
        PasswordField f = new PasswordField();
        f.setPromptText(prompt);
        applyFieldStyle(f);
        return f;
    }

    private void applyFieldStyle(Control f) {
        f.setStyle(
            "-fx-padding: 11;" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: #d1d5db;" +
            "-fx-border-radius: 8;" +
            "-fx-font-size: 13;"
        );
        f.setMaxWidth(Double.MAX_VALUE);
    }
}

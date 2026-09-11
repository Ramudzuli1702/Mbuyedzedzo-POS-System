package com.pos.views;

import com.pos.database.DatabaseConnection;
import com.pos.models.User;
import com.pos.services.UserService;
import com.pos.utils.BrandAssets;
import com.pos.utils.PasswordUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class LoginView {
    private Stage stage;
    private UserService userService;
    
    public LoginView(Stage stage) {
        this.stage = stage;
        this.userService = new UserService();
    }
    
    public void show() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f766e;");
        
        VBox loginCard = createLoginCard();
        root.setCenter(loginCard);
        
        Scene scene = new Scene(root, 1200, 700);
        stage.setScene(scene);
        stage.show();
    }
    
    private VBox createLoginCard() {
        VBox card = new VBox(20);
        card.setMaxWidth(450);
        card.setMaxHeight(550);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");
        
        ImageView logo = BrandAssets.logo(72);

        Label title = new Label(com.pos.Branding.APP_NAME);
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#0f766e"));

        Label subtitle = new Label("Sign in to continue");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.web("#666666"));
        
        VBox emailBox = new VBox(8);
        Label emailLabel = new Label("Email Address");
        emailLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        TextField emailField = new TextField();
        emailField.setPromptText("Enter your email");
        emailField.setStyle("-fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #e0e0e0; -fx-border-radius: 8;");
        emailField.setFont(Font.font(14));
        emailBox.getChildren().addAll(emailLabel, emailField);
        
        VBox passwordBox = new VBox(8);
        Label passwordLabel = new Label("Password");
        passwordLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setStyle("-fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #e0e0e0; -fx-border-radius: 8;");
        passwordField.setFont(Font.font(14));
        passwordBox.getChildren().addAll(passwordLabel, passwordField);
        
        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.RED);
        errorLabel.setFont(Font.font(12));
        errorLabel.setVisible(false);
        
        Button loginBtn = new Button("Sign In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14; -fx-background-radius: 8; -fx-cursor: hand;");
        
        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle("-fx-background-color: #0c5c57; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14; -fx-background-radius: 8; -fx-cursor: hand;"));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14; -fx-background-radius: 8; -fx-cursor: hand;"));
        
        loginBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            
            if (email.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Please fill in all fields");
                errorLabel.setVisible(true);
                return;
            }
            
            User user = userService.login(email, password);
            if (user != null) {
                if ("Active".equals(user.getStatus())) {
                    userService.logCheckIn(user.getStaffID());
                    
                    MainDashboard dashboard = new MainDashboard(stage, user);
                    dashboard.show();
                } else {
                    errorLabel.setText("Your account is inactive. Contact administrator.");
                    errorLabel.setVisible(true);
                }
            } else {
                errorLabel.setText("Invalid email or password");
                errorLabel.setVisible(true);
            }
        });
        
        passwordField.setOnAction(e -> loginBtn.fire());
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        if (logo != null) card.getChildren().add(logo);
        card.getChildren().addAll(title, subtitle, emailBox, passwordBox, errorLabel, loginBtn, spacer);
        
        return card;
    }
}
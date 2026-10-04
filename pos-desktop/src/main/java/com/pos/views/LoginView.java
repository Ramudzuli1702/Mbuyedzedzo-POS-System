package com.pos.views;

import com.pos.database.DatabaseConnection;
import com.pos.models.User;
import com.pos.services.UserService;
import com.pos.utils.BrandAssets;
import com.pos.utils.PasswordUtil;
import com.pos.utils.Theme;
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
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, " + Theme.BRAND_DARK + ", " + Theme.BRAND + " 55%, #14998f);");
        
        VBox loginCard = createLoginCard();
        root.setCenter(loginCard);
        
        // No explicit width/height passed to Scene — a BorderPane with just a
        // centered child otherwise only computes a preferred size big enough
        // for that child, which is what was actually making the window shrink
        // to a small content-sized footprint. Binding the root's pref size to
        // the scene's own width/height makes it fill whatever size the window
        // (maximized, by POSApplication) actually ends up being.
        Scene scene = new Scene(root);
        root.prefWidthProperty().bind(scene.widthProperty());
        root.prefHeightProperty().bind(scene.heightProperty());
        stage.setScene(scene);
        stage.show();
    }
    
    private VBox createLoginCard() {
        VBox card = new VBox(18);
        card.setMaxWidth(440);
        card.setMaxHeight(580);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(44));
        card.setStyle("-fx-background-color: " + Theme.SURFACE + "; -fx-background-radius: " + Theme.RADIUS_LG + "px;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 36, 0, 0, 14);");

        // logoMark is the genuinely transparent (RGBA) asset — logo() is a flat
        // RGB PNG with a baked-in white background, invisible on this white card
        // but not truly transparent (e.g. if the card style ever changes).
        ImageView logo = BrandAssets.logoMark(110);

        Label title = new Label(com.pos.Branding.APP_NAME);
        title.setFont(Font.font("System", FontWeight.BOLD, 23));
        title.setTextFill(Color.web(Theme.TEXT));

        Label subtitle = new Label("Sign in to continue");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.web(Theme.TEXT_MUTED));

        VBox emailBox = new VBox(8);
        Label emailLabel = new Label("Email Address");
        emailLabel.setFont(Theme.label());
        emailLabel.setTextFill(Color.web(Theme.TEXT));
        TextField emailField = new TextField();
        emailField.setPromptText("Enter your email");
        emailField.setStyle(Theme.input());
        emailField.setFont(Font.font(14));
        emailBox.getChildren().addAll(emailLabel, emailField);

        VBox passwordBox = new VBox(8);
        Label passwordLabel = new Label("Password");
        passwordLabel.setFont(Theme.label());
        passwordLabel.setTextFill(Color.web(Theme.TEXT));
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setStyle(Theme.input());
        passwordField.setFont(Font.font(14));
        passwordBox.getChildren().addAll(passwordLabel, passwordField);

        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.web(Theme.DANGER));
        errorLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);

        Button loginBtn = new Button("Sign In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        Theme.hover(loginBtn,
                Theme.primaryButton() + "-fx-font-size: 15px; -fx-padding: 14;",
                Theme.primaryHover() + "-fx-font-size: 15px; -fx-padding: 14;");
        
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

        if (logo != null) {
            // VBox stretches resizable children (ImageView included) to its full
            // width by default. A StackPane wrapper alone isn't enough — StackPane
            // itself passes that same stretch on to its child, so the (narrower,
            // aspect-preserved) image still ends up anchored top-left inside a
            // too-wide box. Capping the wrapper's max width to its own content
            // size stops the stretch at the source, so what VBox centers is a
            // box that's actually only as wide as the logo.
            StackPane logoBox = new StackPane(logo);
            logoBox.setAlignment(Pos.CENTER);
            logoBox.setMaxWidth(Region.USE_PREF_SIZE);
            card.getChildren().add(logoBox);
        }
        card.getChildren().addAll(title, subtitle, emailBox, passwordBox, errorLabel, loginBtn, spacer);
        
        return card;
    }
}
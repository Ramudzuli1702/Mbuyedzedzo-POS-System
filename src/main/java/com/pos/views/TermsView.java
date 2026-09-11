package com.pos.views;

import com.pos.Branding;
import com.pos.Terms;
import com.pos.setup.TermsAcceptance;
import com.pos.utils.BrandAssets;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

/**
 * First-run licence-agreement gate. Shown once — right after the splash, and
 * only when {@link TermsAcceptance#isAccepted()} is false — before setup or
 * login can proceed. Declining exits the application.
 */
public class TermsView {

    private final Stage stage;
    private final Runnable onAccepted;
    private final Runnable onDeclined;

    public TermsView(Stage stage, Runnable onAccepted, Runnable onDeclined) {
        this.stage = stage;
        this.onAccepted = onAccepted;
        this.onDeclined = onDeclined;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f766e;");

        root.setTop(header());
        root.setCenter(centerCard());

        Scene scene = new Scene(root, 1200, 700);
        stage.setScene(scene);
        stage.show();
    }

    private VBox header() {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28, 24, 20, 24));

        ImageView logo = BrandAssets.logoMark(48);
        Label title = new Label("Before you continue");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        Label sub = new Label("Please review and accept the licence agreement for " + Branding.APP_NAME);
        sub.setFont(Font.font("System", 13));
        sub.setTextFill(Color.web("#ccfbf1"));

        if (logo != null) {
            StackPane logoBox = new StackPane(logo);
            logoBox.setAlignment(Pos.CENTER);
            logoBox.setMaxWidth(Region.USE_PREF_SIZE);
            box.getChildren().add(logoBox);
        }
        box.getChildren().addAll(title, sub);
        return box;
    }

    private StackPane centerCard() {
        VBox card = new VBox(16);
        card.setMaxWidth(760);
        card.setMaxHeight(560);
        card.setPadding(new Insets(28));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 5);");

        Text termsText = new Text(Terms.text());
        termsText.setFont(Font.font("System", 13));
        TextFlow flow = new TextFlow(termsText);
        flow.setLineSpacing(2);

        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        scroll.setPadding(new Insets(4));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        CheckBox agree = new CheckBox("I have read and agree to the terms above.");
        agree.setFont(Font.font("System", 13));

        Button acceptBtn = new Button("Accept & Continue");
        acceptBtn.setDefaultButton(true);
        acceptBtn.setDisable(true);
        acceptBtn.setStyle(
                "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
        acceptBtn.setOnAction(e -> {
            TermsAcceptance.accept();
            onAccepted.run();
        });
        agree.selectedProperty().addListener((obs, was, isNow) -> acceptBtn.setDisable(!isNow));

        Button declineBtn = new Button("Decline & Exit");
        declineBtn.setCancelButton(true);
        declineBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-border-color: #cbd5e1;"
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 20; -fx-cursor: hand;");
        declineBtn.setOnAction(e -> onDeclined.run());

        HBox buttons = new HBox(12, declineBtn, acceptBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(scroll, agree, buttons);

        StackPane wrapper = new StackPane(card);
        wrapper.setPadding(new Insets(0, 24, 28, 24));
        wrapper.setAlignment(Pos.CENTER);
        return wrapper;
    }
}

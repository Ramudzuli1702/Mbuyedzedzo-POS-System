package com.pos.views;

import com.pos.Branding;
import com.pos.utils.BrandAssets;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The brand moment shown for a beat on every launch, before the app decides
 * whether this is a first run (terms + setup) or a normal one (straight to
 * login). Purely cosmetic — {@code onDone} is called once the pause elapses.
 */
public final class SplashView {

    private static final Duration HOLD = Duration.millis(1400);

    private SplashView() {}

    public static void show(Stage stage, Runnable onDone) {
        VBox root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #0f766e;");

        ImageView logo = BrandAssets.logoMark(160);
        if (logo != null) {
            StackPane logoBox = new StackPane(logo);
            logoBox.setAlignment(Pos.CENTER);
            logoBox.setMaxWidth(Region.USE_PREF_SIZE);
            root.getChildren().add(logoBox);
        }

        javafx.scene.control.Label name = new javafx.scene.control.Label(Branding.APP_NAME);
        name.setFont(Font.font("System", FontWeight.BOLD, 32));
        name.setTextFill(Color.WHITE);

        javafx.scene.control.Label tagline = new javafx.scene.control.Label(Branding.APP_TAGLINE);
        tagline.setFont(Font.font("System", 15));
        tagline.setTextFill(Color.web("#ccfbf1"));

        root.getChildren().addAll(name, tagline);

        Scene scene = new Scene(root, 1200, 700);
        stage.setScene(scene);
        stage.show();

        PauseTransition pause = new PauseTransition(HOLD);
        pause.setOnFinished(e -> onDone.run());
        pause.play();
    }
}

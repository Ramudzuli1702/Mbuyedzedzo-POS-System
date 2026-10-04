package com.pos.components;

import com.pos.utils.Icons;
import com.pos.utils.Theme;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * A brief "it worked" / "it didn't" notification that floats over the bottom
 * right of the window and fades away on its own — so after an action like
 * adding a product or ending a session, the cashier gets a clear, low-effort
 * confirmation instead of having to infer success from the screen not
 * changing (or a wall of text in a blocking Alert dialog for routine actions).
 *
 * Usage: {@code Toast.success(anyNodeInTheScene, "Product added");}
 */
public final class Toast {

    private static final double WIDTH = 340;
    private static final Duration FADE_IN  = Duration.millis(180);
    private static final Duration HOLD     = Duration.millis(2600);
    private static final Duration FADE_OUT = Duration.millis(320);

    // Tracks toasts currently on screen per-window so multiple in quick
    // succession stack upward instead of overlapping.
    private static final List<Popup> active = new ArrayList<>();

    private Toast() {}

    public static void success(Node anchor, String message) {
        show(anchor, message, Theme.SUCCESS, Icons.tinted(Icons.check(18), Theme.SUCCESS));
    }

    public static void error(Node anchor, String message) {
        show(anchor, message, Theme.DANGER, Icons.tinted(Icons.close(18), Theme.DANGER));
    }

    public static void info(Node anchor, String message) {
        show(anchor, message, Theme.INFO, Icons.tinted(Icons.dot(14), Theme.INFO));
    }

    private static void show(Node anchor, String message, String accentColor, Node icon) {
        if (anchor == null || anchor.getScene() == null || anchor.getScene().getWindow() == null) return;
        Window window = anchor.getScene().getWindow();

        Label text = new Label(message);
        text.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        text.setTextFill(Color.web(Theme.TEXT));
        text.setWrapText(true);
        text.setMaxWidth(WIDTH - 70);

        HBox card = new HBox(12, icon, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 18, 14, 16));
        card.setPrefWidth(WIDTH);
        card.setMaxWidth(WIDTH);
        card.setStyle(
            "-fx-background-color: " + Theme.SURFACE + ";"
            + "-fx-background-radius: " + Theme.RADIUS_MD + "px;"
            + "-fx-border-color: " + accentColor + "; -fx-border-width: 0 0 0 4;"
            + "-fx-border-radius: " + Theme.RADIUS_MD + "px;"
            + Theme.SHADOW_RAISED
        );
        card.setOpacity(0);

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setHideOnEscape(false);
        popup.getContent().add(card);
        popup.show(window);

        Platform.runLater(() -> {
            double w = card.getWidth() > 0 ? card.getWidth() : WIDTH;
            double h = card.getHeight() > 0 ? card.getHeight() : 56;
            double stackOffset = active.size() * (h + 10);
            popup.setX(window.getX() + window.getWidth() - w - 24);
            popup.setY(window.getY() + window.getHeight() - h - 24 - stackOffset);
        });

        active.add(popup);

        FadeTransition in = new FadeTransition(FADE_IN, card);
        in.setToValue(1);
        PauseTransition hold = new PauseTransition(HOLD);
        FadeTransition out = new FadeTransition(FADE_OUT, card);
        out.setToValue(0);

        SequentialTransition sequence = new SequentialTransition(in, hold, out);
        sequence.setOnFinished(e -> {
            popup.hide();
            active.remove(popup);
        });
        sequence.play();
    }
}

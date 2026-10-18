package com.pos.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import com.pos.utils.Theme;

/**
 * A row of equal-width summary/KPI cards used at the top of the management
 * views: a label, slate value, teal accent and optional supporting text.
 */
public final class SummaryCards {

    public record Card(String caption, String value, String colourHex, String sub) {
        public Card(String caption, String value, String colourHex) {
            this(caption, value, colourHex, null);
        }
    }

    private SummaryCards() {}

    public static VBox card(Card c) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(Theme.statCard(Theme.BRAND));
        Label caption = new Label(c.caption());
        caption.setFont(Theme.label());
        caption.setTextFill(Color.web(Theme.TEXT_MUTED));
        caption.setWrapText(true);
        Label value = new Label(c.value());
        value.setFont(Font.font("System", FontWeight.BOLD, 28));
        value.setTextFill(Color.web(Theme.TEXT));
        value.setWrapText(true);
        javafx.scene.layout.Region accent = new javafx.scene.layout.Region();
        accent.setPrefSize(28, 3); accent.setMaxSize(28, 3);
        accent.setStyle("-fx-background-color: " + Theme.BRAND + "; -fx-background-radius: 2;");
        card.getChildren().addAll(caption, value, accent);
        if (c.sub() != null && !c.sub().isBlank()) {
            Label sub = new Label(c.sub()); sub.setWrapText(true);
            sub.setFont(Theme.meta()); sub.setTextFill(Color.web(Theme.TEXT_MUTED));
            card.getChildren().add(sub);
        }
        return card;
    }

    public static HBox row(Card... cards) {
        HBox row = new HBox(16);
        row.setPadding(new Insets(0, 0, 4, 0));
        for (Card c : cards) {
            VBox card = card(c);
            card.setPrefWidth(0);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }
}

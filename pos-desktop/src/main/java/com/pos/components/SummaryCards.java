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

/**
 * A row of equal-width summary/KPI cards used at the top of the management
 * views. Consistent look everywhere: white card, big value, small caption,
 * a coloured value.
 */
public final class SummaryCards {

    public record Card(String caption, String value, String colourHex, String sub) {
        public Card(String caption, String value, String colourHex) {
            this(caption, value, colourHex, null);
        }
    }

    private SummaryCards() {}

    public static HBox row(Card... cards) {
        HBox row = new HBox(16);
        row.setPadding(new Insets(0, 0, 4, 0));
        for (Card c : cards) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(16, 18, 16, 18));
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 8, 0, 0, 2);");

            Label value = new Label(c.value());
            value.setFont(Font.font("System", FontWeight.BOLD, 24));
            value.setTextFill(Color.web(c.colourHex()));

            Label caption = new Label(c.caption());
            caption.setFont(Font.font("System", 12));
            caption.setTextFill(Color.web("#64748b"));

            card.getChildren().addAll(value, caption);

            if (c.sub() != null && !c.sub().isBlank()) {
                Label sub = new Label(c.sub());
                sub.setFont(Font.font("System", 11));
                sub.setTextFill(Color.web("#94a3b8"));
                card.getChildren().add(sub);
            }
            row.getChildren().add(card);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

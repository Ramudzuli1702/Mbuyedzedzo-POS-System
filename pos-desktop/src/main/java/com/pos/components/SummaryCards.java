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
            card.setStyle(Theme.statCard(c.colourHex()));

            Label value = new Label(c.value());
            value.setFont(Font.font("System", FontWeight.BOLD, 24));
            value.setTextFill(Color.web(c.colourHex()));

            Label caption = new Label(c.caption());
            caption.setFont(Theme.body());
            caption.setTextFill(Color.web(Theme.TEXT_MUTED));

            card.getChildren().addAll(value, caption);

            if (c.sub() != null && !c.sub().isBlank()) {
                Label sub = new Label(c.sub());
                sub.setFont(Theme.meta());
                sub.setTextFill(Color.web(Theme.TEXT_FAINT));
                card.getChildren().add(sub);
            }
            row.getChildren().add(card);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

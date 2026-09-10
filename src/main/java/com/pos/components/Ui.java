package com.pos.components;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

/** Small shared UI factories for a consistent look across views. */
public final class Ui {

    private Ui() {}

    /**
     * A compact, readable table-row action button: a short text label plus a
     * tooltip. Replaces bare emoji icons that are hard to identify.
     */
    public static Button actionButton(String text, String colourHex, String tooltip) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + colourHex + "; -fx-text-fill: white;"
            + "-fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 5 12;"
            + "-fx-background-radius: 5; -fx-cursor: hand;");
        if (tooltip != null && !tooltip.isBlank()) b.setTooltip(new Tooltip(tooltip));
        return b;
    }
}

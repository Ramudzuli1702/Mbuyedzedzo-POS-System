package com.pos.components;

import com.pos.utils.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

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

    /**
     * The single action a table row exposes: "View". Every per-row operation
     * (edit, delete, approve, restock, ...) lives inside the detail dialog
     * this opens, instead of a cluster of small buttons crowding the row.
     */
    public static Button viewButton() {
        Button b = new Button("View");
        Theme.hover(b,
            Theme.secondaryButton() + "-fx-font-size: 11; -fx-padding: 5 16;",
            Theme.secondaryHover()  + "-fx-font-size: 11; -fx-padding: 5 16;");
        return b;
    }

    /** A label/value row for a read-only detail dialog (e.g. "Category:" / "Snacks"). */
    public static HBox detailRow(String label, String value) {
        Label l = new Label(label);
        l.setFont(Theme.label());
        l.setTextFill(Color.web(Theme.TEXT_MUTED));
        l.setMinWidth(130);

        Label v = new Label(value == null ? "" : value);
        v.setFont(Theme.body());
        v.setTextFill(Color.web(Theme.TEXT));
        v.setWrapText(true);

        HBox row = new HBox(10, l, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * A row's "View" button opens one of these: a read-only summary up top
     * (built by the caller, typically with {@link #detailRow}) and the row's
     * actions as a button bar at the bottom — the same pattern used across
     * Inventory, Customers, Users, Promo Codes, Sessions and Approvals.
     */
    /**
     * The single "did it work" entry point every view's own showAlert(...)
     * helper should delegate to — a short, no-critical-data pass/fail message
     * becomes a {@link Toast} (auto-dismissing, doesn't block the cashier);
     * anything with real content to read (a receipt, a generated password) or
     * that needs a deliberate decision (WARNING/CONFIRMATION) stays a blocking
     * dialog, unchanged.
     */
    public static void showAlert(Node anchor, String title, String content, Alert.AlertType type) {
        boolean routineConfirmation =
            (type == Alert.AlertType.INFORMATION || type == Alert.AlertType.ERROR)
            && content != null
            && content.length() <= 200
            && !content.contains("Password:")
            && (title == null || !title.startsWith("Receipt"));

        if (routineConfirmation) {
            if (type == Alert.AlertType.ERROR) Toast.error(anchor, content);
            else Toast.success(anchor, content);
            return;
        }

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void showDetailDialog(String title, String header, Node summary, Button... actions) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: " + Theme.SURFACE + ";");
        dialog.getDialogPane().setPrefWidth(460);

        VBox content = new VBox(18);
        content.setPadding(new Insets(4, 4, 4, 4));

        if (header != null && !header.isBlank()) {
            Label headerLabel = new Label(header);
            headerLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
            headerLabel.setTextFill(Color.web(Theme.TEXT));
            content.getChildren().add(headerLabel);
        }

        content.getChildren().add(summary);

        if (actions != null && actions.length > 0) {
            HBox actionBar = new HBox(10);
            actionBar.setAlignment(Pos.CENTER_LEFT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            actionBar.getChildren().add(spacer);
            for (Button a : actions) {
                // Closing the dialog before the action runs matches how every
                // existing action already behaves: it shows its own result
                // alert and refreshes the table itself.
                var original = a.getOnAction();
                a.setOnAction(e -> {
                    dialog.close();
                    if (original != null) original.handle(e);
                });
                actionBar.getChildren().add(a);
            }
            content.getChildren().add(actionBar);
        }

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}

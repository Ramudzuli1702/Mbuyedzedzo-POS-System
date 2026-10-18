package com.pos.components;

import com.pos.utils.Theme;
import javafx.application.Platform;
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

    public static javafx.scene.layout.GridPane formGrid() {
        var grid=new javafx.scene.layout.GridPane();grid.setHgap(20);grid.setVgap(14);
        var label=new javafx.scene.layout.ColumnConstraints();label.setMinWidth(150);
        var value=new javafx.scene.layout.ColumnConstraints();value.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(label,value);return grid;
    }
    public static void formField(javafx.scene.layout.GridPane grid,int row,String label,Node control) {
        Label caption=new Label(label);caption.setFont(Theme.label());caption.setTextFill(Color.web(Theme.TEXT_MUTED));
        if(control instanceof javafx.scene.layout.Region region)region.setMaxWidth(Double.MAX_VALUE);
        if(control instanceof javafx.scene.control.Control)control.setStyle(Theme.input());
        grid.add(caption,0,row);grid.add(control,1,row);
    }
    public static VBox section(String title,Node... children) {
        Label heading=new Label(title);heading.setFont(Theme.sectionTitle());heading.setTextFill(Color.web(Theme.TEXT));
        VBox box=new VBox(14,heading);box.getChildren().addAll(children);box.setPadding(new Insets(20));box.setStyle(Theme.card());return box;
    }
    /** Details and editable fields share one scrollable, labeled display. Save never dismisses validation errors. */
    public static Dialog<Void> editor(String title,String heading,Node content,java.util.function.BooleanSupplier save) {
        Dialog<Void> dialog=new Dialog<>();dialog.setTitle(title);dialog.setResizable(true);
        Label recordHeading=new Label(heading);recordHeading.setFont(Font.font("System",FontWeight.BOLD,24));recordHeading.setTextFill(Color.web(Theme.BRAND));recordHeading.setWrapText(true);
        Label caption=new Label(title);caption.setFont(Theme.body());caption.setTextFill(Color.web(Theme.TEXT_MUTED));
        VBox header=new VBox(6,recordHeading,caption);header.setPadding(new Insets(20));header.setStyle(Theme.topBar());dialog.getDialogPane().setHeader(header);
        dialog.getDialogPane().setStyle(Theme.page());dialog.getDialogPane().setPrefSize(900,680);
        Label feedback=new Label();feedback.setWrapText(true);
        VBox body=new VBox(16,content,feedback);body.setPadding(new Insets(16));
        javafx.scene.control.ScrollPane scroll=new javafx.scene.control.ScrollPane(body);scroll.setFitToWidth(true);scroll.setStyle(Theme.page());
        dialog.getDialogPane().setContent(scroll);
        if(save!=null){
            ButtonType saveType=new ButtonType("Save changes",javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().add(saveType);
            Node button=dialog.getDialogPane().lookupButton(saveType);button.setStyle(Theme.primaryButton());
            button.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
                e.consume();
                try { boolean saved=save.getAsBoolean();feedback.setText(saved?"Changes saved.":"Could not save these changes. Please retry.");feedback.setTextFill(Color.web(saved?Theme.SUCCESS:Theme.DANGER)); }
                catch(IllegalArgumentException ex){feedback.setText(ex.getMessage());feedback.setTextFill(Color.web(Theme.DANGER));}
            });
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setStyle(Theme.secondaryButton());
        return dialog;
    }

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
        VBox body=new VBox(16,section("Record information",summary));
        Dialog<Void> dialog=editor(title,header,body,null);
        if(actions!=null&&actions.length>0){
            javafx.scene.layout.FlowPane bar=new javafx.scene.layout.FlowPane(12,12);
            for(Button action:actions){
                var original=action.getOnAction();
                action.setOnAction(e->{dialog.close();if(original!=null)Platform.runLater(()->original.handle(e));});bar.getChildren().add(action);
            }
            body.getChildren().add(section("Available actions",bar));
        }
        dialog.showAndWait();
    }
}

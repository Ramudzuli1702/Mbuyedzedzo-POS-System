package com.pos.views;

import com.pos.models.User;
import com.pos.services.PromoService;
import com.pos.services.PromoService.Promo;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * PromoView — full promo management UI.
 * Embedded as a tab inside InventoryView or as a standalone view.
 *
 * Shows all promos in a table with:
 *   - Status badge (Active / Expired / Scheduled / Used up / Inactive)
 *   - Discount type and value
 *   - Usage counter
 *   - Edit, toggle active, delete actions
 */
public class PromoView {

    private final User currentUser;
    private final PromoService promoService;
    private TableView<Promo> table;

    public PromoView(User user) {
        this.currentUser  = user;
        this.promoService = new PromoService();
    }

    public VBox getView() {
        // One white padded card — title bar + table together — so it matches
        // the "Product List" card on the Products tab.
        VBox card = new VBox(15);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");
        card.setPadding(new Insets(20));
        card.getChildren().addAll(buildTopBar(), buildTable());

        VBox root = new VBox(card);
        root.setPadding(new Insets(20));
        VBox.setVgrow(card, Priority.ALWAYS);
        return root;
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Promo Codes");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ New Promo");
        addBtn.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 9 18; -fx-background-radius: 6; -fx-cursor: hand;"
        );
        addBtn.setOnAction(e -> showPromoDialog(null));
        addBtn.setDisable(!currentUser.hasFullAccess());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #1e293b; -fx-font-weight: bold; -fx-padding: 9 16; -fx-background-radius: 6; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadTable());

        bar.getChildren().addAll(title, spacer, refreshBtn, addBtn);
        return bar;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private VBox buildTable() {
        VBox box = new VBox(0);

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No promos yet. Click '+ New Promo' to create one."));
        table.setPrefHeight(420);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Promo, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("promoCode"));
        codeCol.setPrefWidth(100);

        TableColumn<Promo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("promoName"));

        TableColumn<Promo, Void> discountCol = new TableColumn<>("Discount");
        discountCol.setPrefWidth(120);
        discountCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); return; }
                Promo p = getTableView().getItems().get(getIndex());
                setText(p.getDiscountDisplay());
                setStyle("-fx-font-weight: bold; -fx-text-fill: #0f766e;");
            }
        });

        TableColumn<Promo, Void> minCol = new TableColumn<>("Min Purchase");
        minCol.setPrefWidth(110);
        minCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); return; }
                Promo p = getTableView().getItems().get(getIndex());
                BigDecimal min = p.getMinimumPurchase();
                setText(min == null || min.compareTo(BigDecimal.ZERO) == 0
                    ? "—" : "R" + String.format("%.2f", min));
            }
        });

        TableColumn<Promo, Void> usageCol = new TableColumn<>("Usage");
        usageCol.setPrefWidth(90);
        usageCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); return; }
                Promo p = getTableView().getItems().get(getIndex());
                String limit = p.getUsageLimit() != null ? String.valueOf(p.getUsageLimit()) : "∞";
                setText(p.getUsageCount() + " / " + limit);
            }
        });

        TableColumn<Promo, Void> validCol = new TableColumn<>("Valid Until");
        validCol.setPrefWidth(120);
        validCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); return; }
                Promo p = getTableView().getItems().get(getIndex());
                setText(p.getValidTill() != null
                    ? p.getValidTill().toLocalDate().toString() : "No expiry");
            }
        });

        TableColumn<Promo, Void> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setText(null); setStyle(""); return; }
                Promo p = getTableView().getItems().get(getIndex());
                String status = p.getStatusDisplay();
                setText(status);
                setStyle(switch (status) {
                    case "Active"    -> "-fx-text-fill: #155724; -fx-font-weight: bold;";
                    case "Expired"   -> "-fx-text-fill: #721c24; -fx-font-weight: bold;";
                    case "Scheduled" -> "-fx-text-fill: #856404; -fx-font-weight: bold;";
                    default          -> "-fx-text-fill: #64748b;";
                });
            }
        });

        TableColumn<Promo, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = com.pos.components.Ui.viewButton();
            {
                viewBtn.setOnAction(e ->
                    showPromoDetailsDialog(getTableView().getItems().get(getIndex())));
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        table.getColumns().addAll(codeCol, nameCol, discountCol, minCol, usageCol, validCol, statusCol, actionCol);
        loadTable();

        box.getChildren().add(table);
        return box;
    }

    private void showPromoDetailsDialog(Promo p) {
        String limit = p.getUsageLimit() != null ? String.valueOf(p.getUsageLimit()) : "∞";
        BigDecimal min = p.getMinimumPurchase();

        VBox summary = new VBox(10);
        summary.getChildren().addAll(
            com.pos.components.Ui.detailRow("Discount:", p.getDiscountDisplay()),
            com.pos.components.Ui.detailRow("Minimum purchase:",
                min == null || min.compareTo(BigDecimal.ZERO) == 0 ? "None" : "R" + String.format("%.2f", min)),
            com.pos.components.Ui.detailRow("Usage:", p.getUsageCount() + " / " + limit),
            com.pos.components.Ui.detailRow("Valid until:",
                p.getValidTill() != null ? p.getValidTill().toLocalDate().toString() : "No expiry"),
            com.pos.components.Ui.detailRow("Status:", p.getStatusDisplay())
        );

        if (!currentUser.hasFullAccess()) {
            com.pos.components.Ui.showDetailDialog("Promo Code", p.getPromoCode() + " — " + p.getPromoName(), summary);
            return;
        }

        Button editBtn   = com.pos.components.Ui.actionButton("Edit", "#d97706", "Edit this promo");
        Button toggleBtn = com.pos.components.Ui.actionButton(p.isActive() ? "Pause" : "Enable",
            p.isActive() ? "#64748b" : "#16a34a", "Pause / enable this promo");
        Button deleteBtn = com.pos.components.Ui.actionButton("Delete", "#dc2626", "Delete this promo");

        editBtn.setOnAction(e -> showPromoDialog(p));
        toggleBtn.setOnAction(e -> {
            promoService.toggleActive(p.getPromoID(), !p.isActive());
            loadTable();
        });
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete promo \"" + p.getPromoCode() + "\"?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.YES) {
                    promoService.deletePromo(p.getPromoID());
                    loadTable();
                }
            });
        });

        com.pos.components.Ui.showDetailDialog("Promo Code", p.getPromoCode() + " — " + p.getPromoName(), summary,
            editBtn, toggleBtn, deleteBtn);
    }

    private void loadTable() {
        ObservableList<Promo> promos = promoService.getAllPromos();
        table.setItems(promos);
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────────

    private void showPromoDialog(Promo existing) {
        boolean isEdit = existing != null;
        Dialog<Promo> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Promo" : "New Promo Code");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefWidth(480);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField codeField  = tf(isEdit ? existing.getPromoCode() : "", "e.g. SUMMER20");
        TextField nameField  = tf(isEdit ? existing.getPromoName()  : "", "e.g. Summer Sale 2025");
        codeField.setDisable(isEdit); // code cannot change after creation

        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton percentRb = new RadioButton("Percentage (%)");
        RadioButton fixedRb   = new RadioButton("Fixed amount (R)");
        percentRb.setToggleGroup(typeGroup);
        fixedRb.setToggleGroup(typeGroup);

        if (!isEdit || "PERCENT".equals(existing.getDiscountType())) percentRb.setSelected(true);
        else fixedRb.setSelected(true);

        TextField valueField = tf(isEdit ? existing.getDiscountValue().toPlainString() : "", "e.g. 20");
        TextField minField   = tf(isEdit && existing.getMinimumPurchase() != null
                ? existing.getMinimumPurchase().toPlainString() : "0", "0 = no minimum");

        DatePicker fromPicker = new DatePicker(isEdit && existing.getValidFrom() != null
                ? existing.getValidFrom().toLocalDate() : null);
        DatePicker tillPicker = new DatePicker(isEdit && existing.getValidTill() != null
                ? existing.getValidTill().toLocalDate() : null);

        TextField limitField = tf(isEdit && existing.getUsageLimit() != null
                ? String.valueOf(existing.getUsageLimit()) : "", "Leave empty = unlimited");

        CheckBox activeCheck = new CheckBox("Active");
        activeCheck.setSelected(!isEdit || existing.isActive());

        int row = 0;
        addRow(grid, row++, "Promo Code *",   codeField);
        addRow(grid, row++, "Name",           nameField);
        addRow(grid, row++, "Discount Type",  new HBox(14, percentRb, fixedRb));
        addRow(grid, row++, "Discount Value *", valueField);
        addRow(grid, row++, "Min Purchase (R)", minField);
        addRow(grid, row++, "Valid From",     fromPicker);
        addRow(grid, row++, "Valid Until",    tillPicker);
        addRow(grid, row++, "Usage Limit",    limitField);
        grid.add(activeCheck, 1, row);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            String code = codeField.getText().trim().toUpperCase();
            String valStr = valueField.getText().trim();
            if (code.isEmpty() || valStr.isEmpty()) {
                showAlert("Code and discount value are required.");
                return null;
            }
            BigDecimal val;
            try { val = new BigDecimal(valStr); } catch (NumberFormatException e) {
                showAlert("Discount value must be a number.");
                return null;
            }

            Promo p = isEdit ? existing : new Promo();
            p.setPromoCode(code);
            p.setPromoName(nameField.getText().trim());
            p.setDiscountType(percentRb.isSelected() ? "PERCENT" : "FIXED");
            p.setDiscountValue(val);
            BigDecimal min = BigDecimal.ZERO;
            try { min = new BigDecimal(minField.getText().trim()); } catch (Exception ignored) {}
            p.setMinimumPurchase(min);
            p.setValidFrom(fromPicker.getValue() != null
                ? LocalDateTime.of(fromPicker.getValue(), LocalTime.MIDNIGHT) : null);
            p.setValidTill(tillPicker.getValue() != null
                ? LocalDateTime.of(tillPicker.getValue(), LocalTime.of(23, 59)) : null);
            String limitStr = limitField.getText().trim();
            p.setUsageLimit(limitStr.isEmpty() ? null : Integer.parseInt(limitStr));
            p.setActive(activeCheck.isSelected());
            return p;
        });

        Optional<Promo> result = dialog.showAndWait();
        result.ifPresent(p -> {
            boolean ok = isEdit ? promoService.updatePromo(p) : promoService.addPromo(p);
            if (ok) loadTable();
            else showAlert("Failed to save promo. Code may already exist.");
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TextField tf(String value, String prompt) {
        TextField tf = new TextField(value);
        tf.setPromptText(prompt);
        tf.setStyle("-fx-font-size: 13; -fx-padding: 8;");
        tf.setPrefWidth(260);
        return tf;
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label + ":");
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        lbl.setTextFill(Color.web("#1e293b"));
        lbl.setMinWidth(130);
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }
}

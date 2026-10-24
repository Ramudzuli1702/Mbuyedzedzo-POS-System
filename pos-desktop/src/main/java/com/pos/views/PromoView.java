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
        TabPane tabs=new TabPane(new Tab("Retail offers",new PromotionView(currentUser).getView()),new Tab("Basket coupons",root));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);VBox wrapper=new VBox(tabs);VBox.setVgrow(tabs,Priority.ALWAYS);return wrapper;
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

    private void showPromoDetailsDialog(Promo p) { showPromoDialog(p); }

    private void loadTable() {
        ObservableList<Promo> promos = promoService.getAllPromos();
        table.setItems(promos);
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────────

    private void showPromoDialog(Promo existing) {
        boolean edit=existing!=null,allowed=currentUser.hasFullAccess();
        var grid=com.pos.components.Ui.formGrid();
        TextField code=new TextField(edit?existing.getPromoCode():""),name=new TextField(edit?existing.getPromoName():"");code.setDisable(edit);
        ComboBox<String> type=new ComboBox<>();type.getItems().addAll("PERCENT","FIXED");type.setValue(edit?existing.getDiscountType():"PERCENT");
        TextField value=new TextField(edit?existing.getDiscountValue().toPlainString():""),minimum=new TextField(edit&&existing.getMinimumPurchase()!=null?existing.getMinimumPurchase().toPlainString():"0");
        DatePicker from=new DatePicker(edit&&existing.getValidFrom()!=null?existing.getValidFrom().toLocalDate():null),until=new DatePicker(edit&&existing.getValidTill()!=null?existing.getValidTill().toLocalDate():null);
        TextField limit=new TextField(edit&&existing.getUsageLimit()!=null?existing.getUsageLimit().toString():"");limit.setPromptText("Blank means unlimited");
        CheckBox active=new CheckBox("Active");active.setSelected(!edit||existing.isActive());
        com.pos.components.Ui.formField(grid,0,"Promo code",code);com.pos.components.Ui.formField(grid,1,"Name",name);
        com.pos.components.Ui.formField(grid,2,"Discount type",type);com.pos.components.Ui.formField(grid,3,"Discount value",value);
        com.pos.components.Ui.formField(grid,4,"Minimum purchase (R)",minimum);com.pos.components.Ui.formField(grid,5,"Valid from",from);
        com.pos.components.Ui.formField(grid,6,"Valid until",until);com.pos.components.Ui.formField(grid,7,"Usage limit",limit);grid.add(active,1,8);grid.setDisable(!allowed);
        VBox content=new VBox(16,com.pos.components.Ui.section("Promotion information",grid));
        if(edit)content.getChildren().add(com.pos.components.Ui.section("Usage",com.pos.components.Ui.detailRow("Uses", ""+existing.getUsageCount()),com.pos.components.Ui.detailRow("Status",existing.getStatusDisplay())));
        Dialog<Void> dialog=com.pos.components.Ui.editor(edit?"Promo details":"New promo",edit?existing.getPromoCode():"New promotion",content,allowed?()->{
            try{
                BigDecimal discount=new BigDecimal(value.getText()),min=new BigDecimal(minimum.getText());
                Integer max=limit.getText().isBlank()?null:Integer.valueOf(limit.getText());
                if(code.getText().isBlank()||discount.signum()<=0||min.signum()<0||("PERCENT".equals(type.getValue())&&discount.compareTo(BigDecimal.valueOf(100))>0)||max!=null&&max<1)throw new IllegalArgumentException("Enter a code, positive discount, nonnegative minimum and positive usage limit.");
                if(from.getValue()!=null&&until.getValue()!=null&&until.getValue().isBefore(from.getValue()))throw new IllegalArgumentException("The end date must be on or after the start date.");
                Promo updated=new Promo();if(edit)updated.setPromoID(existing.getPromoID());updated.setPromoCode(code.getText().trim().toUpperCase());updated.setPromoName(name.getText().trim());
                updated.setDiscountType(type.getValue());updated.setDiscountValue(discount);updated.setMinimumPurchase(min);updated.setUsageLimit(max);updated.setActive(active.isSelected());
                updated.setValidFrom(from.getValue()==null?null:LocalDateTime.of(from.getValue(),LocalTime.MIDNIGHT));updated.setValidTill(until.getValue()==null?null:LocalDateTime.of(until.getValue(),LocalTime.of(23,59)));
                boolean saved=edit?promoService.updatePromo(updated):promoService.addPromo(updated);if(saved)loadTable();return saved;
            }catch(NumberFormatException ex){throw new IllegalArgumentException("Enter valid amounts and a whole usage limit.");}
        }:null);
        if(edit&&allowed){Button delete=new Button("Delete promo");delete.setStyle(com.pos.utils.Theme.dangerButton());delete.setOnAction(e->{Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Delete this promo?",ButtonType.YES,ButtonType.NO);if(confirm.showAndWait().orElse(ButtonType.NO)==ButtonType.YES){promoService.deletePromo(existing.getPromoID());loadTable();dialog.close();}});content.getChildren().add(com.pos.components.Ui.section("Promotion actions",delete));}
        dialog.showAndWait();
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

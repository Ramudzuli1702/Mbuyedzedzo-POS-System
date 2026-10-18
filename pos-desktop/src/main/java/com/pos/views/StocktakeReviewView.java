package com.pos.views;

import com.pos.models.User;
import com.pos.services.*;
import com.pos.services.StocktakeService.*;
import com.pos.utils.Theme;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Review immutable phone snapshots before applying any stock correction. */
public final class StocktakeReviewView {
    private final User user;
    private final StocktakeService service=new StocktakeService();
    private final ListView<CountSheet> sheets=new ListView<>();
    private final TableView<Line> lines=new TableView<>();
    private final Label status=new Label("Load submitted stocktakes to review counts.");
    private final Label detail=new Label("Select a stocktake. Uncounted products are left unchanged.");
    private final TextArea notes=new TextArea();
    private final Button approve=new Button("Approve stock corrections"), reject=new Button("Reject / request recount"), refresh=new Button("Refresh stocktakes");
    private boolean busy;
    private final Button export = new Button("Export to Excel");
    public StocktakeReviewView(User user) { this.user=user; }

    public BorderPane getView() {
        BorderPane root=new BorderPane();root.setStyle(Theme.page());root.setPadding(new Insets(20));
        Label title=new Label("Stocktake verification");title.setFont(Theme.sectionTitle());
        status.setWrapText(true);status.setStyle("-fx-text-fill: "+Theme.TEXT_MUTED+";");
        VBox header=new VBox(8,title,status,refresh);header.setPadding(new Insets(0,0,16,0));root.setTop(header);
        sheets.setPrefWidth(285);sheets.setPlaceholder(new Label("No submitted stocktakes"));
        sheets.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(CountSheet sheet,boolean empty) {
                super.updateItem(sheet,empty);
                setText(empty||sheet==null?null:sheet.status()+" · "+sheet.staffName()+"\n"+sheet.createdAt()+"\n"+sheet.items().stream().filter(l->l.counted()!=null).count()+" products counted");
            }
        });
        sheets.getSelectionModel().selectedItemProperty().addListener((o,a,b)->select(b));
        column("Product",l->l.name(),180);column("Barcode",l->l.barcode(),120);
        column("Loaded stock",l->""+l.expected(),90);column("Physical count",l->l.counted()==null?"Not counted":""+l.counted(),95);
        column("Variance",l->l.counted()==null?"—":String.format("%+d",l.counted()-l.expected()),85);
        column("Current stock",l->l.current()==null?"Deleted":""+l.current(),95);
        column("Verification",l->l.counted()==null?"Unchanged":l.conflict()?"Stock moved — recount required":"Ready",170);
        lines.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        lines.setPlaceholder(new Label("Choose a submitted stocktake"));
        lines.setRowFactory(table->new TableRow<>() {
            @Override protected void updateItem(Line line,boolean empty) {
                super.updateItem(line,empty);
                setStyle(!empty&&line!=null&&line.counted()!=null&&line.conflict()?"-fx-background-color: "+Theme.WARNING_TINT+";":"");
            }
        });
        detail.setWrapText(true);notes.setPromptText("Review notes (required when rejecting; up to 1,000 characters)");notes.setPrefRowCount(2);
        approve.setStyle(Theme.primaryButton());reject.setStyle(Theme.dangerButton());refresh.setStyle(Theme.secondaryButton());
        export.setStyle(Theme.secondaryButton());
        HBox actions=new HBox(12,approve,reject,export);
        export.setOnAction(e->exportSelected());
        VBox review=new VBox(12,detail,lines,notes,actions);review.setPadding(new Insets(16));review.setStyle(Theme.card());VBox.setVgrow(lines,Priority.ALWAYS);
        SplitPane body=new SplitPane(sheets,review);body.setDividerPositions(.25);root.setCenter(body);
        refresh.setOnAction(e->refresh());approve.setOnAction(e->decide(true));reject.setOnAction(e->decide(false));
        updateButtons();refresh();return root;
    }
    private void column(String title,Function<Line,String> text,int width) {
        TableColumn<Line,String> column=new TableColumn<>(title);column.setPrefWidth(width);
        column.setCellValueFactory(cell->new SimpleStringProperty(text.apply(cell.getValue())));lines.getColumns().add(column);
    }
    private void select(CountSheet sheet) {
        lines.setItems(FXCollections.observableArrayList(sheet==null?List.of():sheet.items()));notes.clear();
        detail.setText(sheet==null?"Select a stocktake. Uncounted products are left unchanged.":
                sheet.staffName()+" · "+sheet.status()+"\nStaff notes: "+sheet.notes()
                +(sheet.reviewer()==null?"":"\nReviewed by "+sheet.reviewer()+": "+sheet.reviewNotes()));
        updateButtons();
    }
    private void updateButtons() {
        CountSheet sheet=sheets.getSelectionModel().getSelectedItem();
        boolean pending=sheet!=null&&"Pending".equals(sheet.status());
        approve.setDisable(busy||!pending||sheet.items().stream().anyMatch(l->l.counted()!=null&&l.conflict()));
        reject.setDisable(busy||!pending);refresh.setDisable(busy);sheets.setDisable(busy);
        export.setDisable(busy||sheet==null);
    }
    private void exportSelected() {
        CountSheet sheet=sheets.getSelectionModel().getSelectedItem();if(sheet==null||busy)return;
        javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();chooser.setTitle("Export stocktake to Excel");
        try { chooser.setInitialDirectory(new SettingsService().sheetExportDirectory(true)); }
        catch (Exception ex) { status.setText("Check the stocktake sheet folder in Settings: "+ex.getMessage()); return; }
        chooser.setInitialFileName(com.pos.utils.DocumentNames.name("Stocktake", sheet.id(), "xlsx"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Workbook","*.xlsx"));
        java.io.File file=chooser.showSaveDialog(sheets.getScene().getWindow());if(file==null)return;
        busy=true;updateButtons();status.setText("Exporting stocktake...");
        CompletableFuture.runAsync(()->{try{new StocktakeExcelService().exportToExcel(sheet,file);}catch(Exception ex){throw new RuntimeException(ex);}})
                .whenComplete((ignored,error)->Platform.runLater(()->{busy=false;updateButtons();status.setText(error==null?"Exported "+file.getName():message(error));}));
    }
    private void refresh() {
        busy=true;updateButtons();status.setText("Loading stocktakes…");
        CompletableFuture.supplyAsync(()-> { try{return service.list(user.getStaffID());}catch(Exception e){throw new RuntimeException(e);} })
                .whenComplete((result,error)->Platform.runLater(()-> {
                    busy=false;
                    if(error==null) { sheets.setItems(FXCollections.observableArrayList(result));status.setText("Compare loaded stock, physical counts and current stock. Highlighted changes require a fresh count."); }
                    else status.setText(message(error));
                    updateButtons();
                }));
    }
    private void decide(boolean accepted) {
        CountSheet sheet=sheets.getSelectionModel().getSelectedItem();if(sheet==null||busy)return;
        String reason=notes.getText().trim();
        if(reason.length()>1000 || !accepted&&reason.isBlank()) { status.setText("Enter a review reason of up to 1,000 characters.");return; }
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(accepted?"Approve stocktake":"Reject stocktake");
        confirm.setHeaderText(accepted?"Apply the physical counts to live stock?":"Reject this stocktake and keep live stock unchanged?");
        confirm.setContentText(sheet.items().stream().filter(l->l.counted()!=null).count()+" counted products · "+sheet.staffName());
        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        busy=true;updateButtons();status.setText("Saving review…");
        CompletableFuture.supplyAsync(()-> {try{return service.review(sheet.id(),user.getStaffID(),accepted,reason);}catch(Exception e){throw new RuntimeException(e);}})
                .whenComplete((reviewed,error)->Platform.runLater(()-> {
                    busy=false;
                    if(error!=null) { status.setText(message(error));updateButtons();return; }
                    JsonObject notification=new JsonObject();notification.addProperty("type","stocktake_status");
                    notification.addProperty("stocktakeId",reviewed.id());notification.addProperty("status",reviewed.status());notification.addProperty("reviewNotes",reviewed.reviewNotes());
                    CompletableFuture.runAsync(()->WiFiHandler.getInstance().sendData(notification.toString()));refresh();
                }));
    }
    private String message(Throwable error) {
        while(error.getCause()!=null)error=error.getCause();
        return error instanceof Rejected?error.getMessage():"The desktop could not load or save this review. Please retry.";
    }
}

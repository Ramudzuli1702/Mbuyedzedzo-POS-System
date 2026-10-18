package com.pos.views;

import com.pos.models.User;
import com.pos.services.StocktakeExcelService;
import com.pos.services.StocktakeService;
import com.pos.services.StocktakeService.*;
import com.pos.utils.Theme;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Signed-in staff count on desktop or round-trip a snapshot through Excel. */
public final class StocktakingView {
    private final User user;
    private final StocktakeService service;
    private final StocktakeExcelService excel = new StocktakeExcelService();
    private final BorderPane root = new BorderPane();
    private final ComboBox<CountSheet> drafts = new ComboBox<>();
    private final TableView<Line> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label status = new Label("Loading your stocktakes..."), summary = new Label(), notes = new Label();
    private final Button start = new Button("New stocktake"), refresh = new Button("Refresh"),
            export = new Button("Export to Excel"), importCounts = new Button("Import from Excel"),
            submit = new Button("Submit for approval"), editNotes = new Button("Edit notes");
    private CountSheet sheet;
    private boolean busy, binding;

    public StocktakingView(User user) { this(user, new StocktakeService()); }
    public StocktakingView(User user, StocktakeService service) { this.user = user; this.service = service; }

    public BorderPane getView() {
        root.setStyle(Theme.page()); root.setPadding(new Insets(20));
        table.setId("stocktakeProducts"); submit.setId("stocktakeSubmit");
        importCounts.setId("stocktakeImport"); export.setId("stocktakeExport"); summary.setId("stocktakeSummary");
        Label title = new Label("Stocktaking"); title.setFont(Theme.sectionTitle());
        Label description = new Label("Count inventory here or in Excel. An Admin or Manager approves corrections before stock changes.");
        description.setWrapText(true); description.setStyle("-fx-text-fill: " + Theme.TEXT_MUTED + ";");
        status.setWrapText(true); notes.setWrapText(true);
        drafts.setPromptText("Choose one of your saved stocktakes"); drafts.setPrefWidth(420);
        drafts.setConverter(new StringConverter<>() {
            public String toString(CountSheet s) { return s == null ? "" : s.status() + " | " + s.createdAt() + " | " + s.id().substring(0, 8); }
            public CountSheet fromString(String s) { return null; }
        });
        drafts.setOnAction(e -> { if (!binding && drafts.getValue() != null) load(drafts.getValue().id()); });
        HBox actions = new HBox(10, drafts, refresh, start);
        VBox top = new VBox(10, title, description, actions, status); top.setPadding(new Insets(0, 0, 16, 0)); root.setTop(top);
        search.setPromptText("Search products or scan a barcode and press Enter"); search.setStyle(Theme.input());
        search.textProperty().addListener((o, a, b) -> filter()); HBox.setHgrow(search, Priority.ALWAYS);
        search.setOnAction(e -> {
            if (!editable()) return;
            List<Line> matches = sheet.items().stream().filter(line -> Objects.equals(line.barcode(), search.getText().trim())).toList();
            if (matches.size() == 1) count(matches.get(0));
            else message(matches.isEmpty() ? "No exact barcode match. Search by name and select Count." : "Several products share this barcode. Select the product by name.", true);
        });
        column(table, "Product", Line::name, 240); column(table, "Barcode", Line::barcode, 160);
        column(table, "Category", Line::category, 140); column(table, "Loaded stock", l -> "" + l.expected(), 110);
        column(table, "Physical count", l -> l.counted() == null ? "Not counted" : "" + l.counted(), 110);
        column(table, "Variance", l -> l.counted() == null ? "" : String.format("%+d", (long) l.counted() - l.expected()), 100);
        TableColumn<Line, String> action = new TableColumn<>("Count"); action.setPrefWidth(110);
        action.setCellFactory(c -> new TableCell<>() {
            private final Button button = new Button("Count");
            { button.setStyle(Theme.secondaryButton()); button.setOnAction(e -> count(getTableRow().getItem())); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); button.setDisable(!editable()); setGraphic(empty ? null : button);
            }
        });
        action.setCellValueFactory(c -> new SimpleStringProperty("")); table.getColumns().add(action);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); table.setFixedCellSize(48);
        table.setPlaceholder(new Label("Start a new stocktake or select a saved one."));
        HBox toolbar = new HBox(10, search, export, importCounts);
        VBox body = new VBox(12, toolbar, table); body.setPadding(new Insets(16)); body.setStyle(Theme.card());
        VBox.setVgrow(table, Priority.ALWAYS); root.setCenter(body);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, editNotes, spacer, submit);
        VBox bottom = new VBox(10, summary, notes, footer); bottom.setPadding(new Insets(16, 0, 0, 0)); root.setBottom(bottom);
        for (Button button : List.of(refresh, export, importCounts, editNotes)) Theme.hover(button, Theme.secondaryButton(), Theme.secondaryHover());
        for (Button button : List.of(start, submit)) Theme.hover(button, Theme.primaryButton(), Theme.primaryHover());
        start.setOnAction(e -> run(() -> service.start(UUID.randomUUID().toString(), user.getStaffID()), this::show));
        refresh.setOnAction(e -> refresh()); export.setOnAction(e -> export()); importCounts.setOnAction(e -> importCounts());
        submit.setOnAction(e -> submit()); editNotes.setOnAction(e -> notes());
        updateControls(); refresh(); return root;
    }

    private void refresh() {
        String selected = sheet == null ? null : sheet.id();
        run(() -> service.listOwn(user.getStaffID()), result -> {
            binding = true; drafts.setItems(FXCollections.observableArrayList(result)); binding = false;
            CountSheet current = result.stream().filter(s -> s.id().equals(selected)).findFirst().orElse(result.isEmpty() ? null : result.get(0));
            if (current != null) show(current); else message("Start a new stocktake to load inventory.", false);
        });
    }
    private void load(String id) { run(() -> service.resume(id, user.getStaffID()), this::show); }
    private void show(CountSheet result) {
        sheet = result; binding = true;
        drafts.getItems().removeIf(s -> s.id().equals(result.id())); drafts.getItems().add(0, result); drafts.setValue(result);
        binding = false; filter(); updateControls();
        long counted = sheet.items().stream().filter(l -> l.counted() != null).count();
        summary.setText(counted + " / " + sheet.items().size() + " products counted | " + sheet.status() + " | " + sheet.id());
        notes.setText("Notes: " + sheet.notes() + (sheet.reviewNotes().isBlank() ? "" : "\nReview: " + sheet.reviewNotes()));
        message("Draft".equals(sheet.status()) ? "Counts are saved on the desktop. Blank means not counted; enter zero when none remain."
                : "Pending".equals(sheet.status()) ? "Submitted. Stock stays unchanged until an Admin or Manager approves. Refresh to check the decision."
                : sheet.status() + " by " + sheet.reviewer() + ". This sheet is read-only; export it for your records.", false);
    }
    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        table.setItems(FXCollections.observableArrayList(sheet == null ? List.of() : sheet.items().stream()
                .filter(l -> (l.name() + " " + Objects.toString(l.barcode(), "") + " " + Objects.toString(l.category(), "")).toLowerCase(Locale.ROOT).contains(query)).toList()));
    }
    private boolean editable() { return !busy && sheet != null && "Draft".equals(sheet.status()); }
    private void updateControls() {
        drafts.setDisable(busy); start.setDisable(busy); refresh.setDisable(busy); export.setDisable(busy || sheet == null);
        importCounts.setDisable(!editable()); editNotes.setDisable(!editable());
        submit.setDisable(!editable() || sheet.items().stream().noneMatch(l -> l.counted() != null)); table.refresh();
    }
    private List<Count> counts() { return sheet.items().stream().filter(l -> l.counted() != null).map(l -> new Count(l.productId(), l.counted())).toList(); }
    private void save(List<Count> values, String note) { String id = sheet.id(); run(() -> service.saveDraft(id, user.getStaffID(), values, note), this::show); }
    private void count(Line line) {
        if (!editable() || line == null) return;
        TextInputDialog dialog = new TextInputDialog(line.counted() == null ? "" : "" + line.counted());
        dialog.initOwner(root.getScene().getWindow()); dialog.setTitle("Physical count"); dialog.setHeaderText(line.name() + " | Loaded stock: " + line.expected());
        dialog.setContentText("Whole quantity (blank clears the count):"); dialog.getEditor().setStyle(Theme.input());
        dialog.showAndWait().ifPresent(value -> {
            Integer number = null;
            try { if (!value.trim().isEmpty()) { number = Integer.valueOf(value.trim()); if (number < 0 || number > 1000000) throw new NumberFormatException(); } }
            catch (NumberFormatException e) { message("Enter a whole physical quantity from 0 to 1,000,000, or leave it blank.", true); return; }
            List<Count> values = new ArrayList<>(counts()); values.removeIf(c -> c.productId() == line.productId());
            if (number != null) values.add(new Count(line.productId(), number)); save(values, sheet.notes());
        });
    }
    private void notes() {
        TextInputDialog dialog = new TextInputDialog(sheet.notes()); dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Stocktake notes"); dialog.setHeaderText("Notes for the reviewer (up to 1,000 characters)"); dialog.getEditor().setStyle(Theme.input());
        dialog.showAndWait().ifPresent(value -> save(counts(), value.trim()));
    }
    private void submit() {
        if (!editable()) return;
        if (!confirm("Submit stocktake", "Submit " + counts().size() + " counted products for approval?",
                "Uncounted products remain unchanged. The submitted sheet cannot be edited. Review it under Approvals > Stocktakes.")) return;
        String id = sheet.id(), note = sheet.notes(); List<Count> values = counts();
        run(() -> service.submit(id, user.getStaffID(), values, note), this::show);
    }
    private void export() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Export stocktake to Excel");
        chooser.setInitialFileName("stocktake-" + sheet.id() + ".xlsx"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File file = chooser.showSaveDialog(root.getScene().getWindow()); if (file == null) return;
        CountSheet snapshot = sheet;
        run(() -> { excel.exportToExcel(snapshot, file); return file; }, saved -> message("Exported " + saved.getName() + ". Fill Physical Count and import into this same draft.", false));
    }
    private void importCounts() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Import stocktake counts from Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx", "*.xls"));
        File file = chooser.showOpenDialog(root.getScene().getWindow()); if (file == null) return;
        CountSheet snapshot = sheet;
        run(() -> excel.analyze(file, snapshot), plan -> {
            if (!plan.valid()) {
                Alert alert = new Alert(Alert.AlertType.ERROR); alert.initOwner(root.getScene().getWindow());
                alert.setTitle("Stocktake import needs corrections"); alert.setHeaderText(plan.errors().size() + " errors. No counts were imported.");
                TextArea errors = new TextArea(String.join("\n", plan.errors().stream().limit(100).toList())); errors.setEditable(false); errors.setWrapText(true);
                alert.getDialogPane().setContent(errors); alert.getDialogPane().setPrefSize(760, 420); alert.showAndWait(); return;
            }
            Dialog<ButtonType> review = new Dialog<>(); review.initOwner(root.getScene().getWindow()); review.setTitle("Review Excel stocktake counts");
            review.setHeaderText("Replace this draft with " + plan.counts().size() + " physical counts? Blank or omitted products become uncounted.");
            Map<Integer, Line> byId = new HashMap<>(); snapshot.items().forEach(l -> byId.put(l.productId(), l));
            TableView<Count> preview = new TableView<>(FXCollections.observableArrayList(plan.counts()));
            column(preview, "Product", c -> byId.get(c.productId()).name(), 260);
            column(preview, "Loaded stock", c -> "" + byId.get(c.productId()).expected(), 120);
            column(preview, "Physical count", c -> "" + c.quantity(), 120);
            column(preview, "Variance", c -> String.format("%+d", (long) c.quantity() - byId.get(c.productId()).expected()), 100);
            preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); preview.setPlaceholder(new Label("All products will be marked not counted."));
            review.getDialogPane().setContent(preview); review.getDialogPane().setPrefSize(820, 460);
            ButtonType apply = new ButtonType("Import counts", ButtonBar.ButtonData.OK_DONE);
            review.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);
            review.getDialogPane().lookupButton(apply).setStyle(Theme.primaryButton());
            if (review.showAndWait().orElse(ButtonType.CANCEL) == apply) save(plan.counts(), snapshot.notes());
        });
    }
    private boolean confirm(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.initOwner(root.getScene().getWindow());
        alert.setTitle(title); alert.setHeaderText(header); alert.setContentText(content);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private <T> void run(Callable<T> work, Consumer<T> success) {
        busy = true; updateControls(); message("Working...", false);
        CompletableFuture.supplyAsync(() -> { try { return work.call(); } catch (Exception e) { throw new RuntimeException(e); } })
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    busy = false; updateControls();
                    if (error == null) success.accept(result);
                    else { Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause(); message(cause.getMessage(), true); }
                }));
    }
    private void message(String text, boolean error) { status.setText(text); status.setStyle("-fx-text-fill: " + (error ? Theme.DANGER : Theme.TEXT_MUTED) + ";"); }
    private <T> void column(TableView<T> target, String title, Function<T, String> text, int width) {
        TableColumn<T, String> column = new TableColumn<>(title); column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(Objects.toString(text.apply(cell.getValue()), ""))); target.getColumns().add(column);
    }
}

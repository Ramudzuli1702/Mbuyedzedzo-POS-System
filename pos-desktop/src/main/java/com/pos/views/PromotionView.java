package com.pos.views;

import com.pos.components.Ui;
import com.pos.models.User;
import com.pos.services.*;
import com.pos.services.PromotionEngine.*;
import com.pos.utils.*;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.stage.FileChooser;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Retail offers share the existing form design and Excel review workflow. */
public final class PromotionView {
    private final User user;private final PromotionService service;
    private final TableView<PromotionService.Campaign> table=new TableView<>();private final Label status=new Label();
    public PromotionView(User user){this(user,new PromotionService());}
    public PromotionView(User user,PromotionService service){this.user=user;this.service=service;}
    public BorderPane getView() {
        BorderPane root=new BorderPane();root.setStyle(Theme.page());root.setPadding(new Insets(20));
        Label heading=new Label("Retail promotions");heading.setStyle("-fx-font-size:24; -fx-font-weight:bold; -fx-text-fill:"+Theme.BRAND+";");
        Button create=button("New offer",()->edit(null)),template=button("Excel template",()->export(true)),export=button("Export offers",()->export(false)),importButton=button("Import drafts",this::importFile),refresh=button("Refresh",this::refresh);
        create.setDisable(!user.hasFullAccess());importButton.setDisable(!user.hasFullAccess());
        status.setWrapText(true);VBox header=new VBox(12,heading,new Label("Scheduled offers apply automatically. Excel imports remain drafts until approved."),new FlowPane(12,12,create,template,export,importButton,refresh),status);header.setPadding(new Insets(0,0,16,0));root.setTop(header);
        column(table,"Promotion",c->c.draft().name(),200);column(table,"Type",c->typeName(c.draft().type()),110);column(table,"Status",PromotionService.Campaign::status,170);
        column(table,"Dates",c->c.draft().from().toLocalDate()+" to "+c.draft().until().minusNanos(1).toLocalDate(),190);
        column(table,"Applications",c->""+c.applications(),100);column(table,"Savings",c->"R"+c.savings(),110);
        TableColumn<PromotionService.Campaign,Void> action=new TableColumn<>("");action.setCellFactory(col->new TableCell<>() {
            final Button view=Ui.viewButton();{view.setOnAction(e->details(getTableView().getItems().get(getIndex())));}
            @Override protected void updateItem(Void item,boolean empty){super.updateItem(item,empty);setGraphic(empty?null:view);}
        });table.getColumns().add(action);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);table.setPlaceholder(new Label("No retail offers yet. Create one or import the Excel template."));root.setCenter(table);refresh();return root;
    }
    private static Button button(String title,Runnable task){Button b=new Button(title);b.setStyle(Theme.secondaryButton());b.setOnAction(e->task.run());return b;}
    private static String typeName(Type type){return switch(type){case PRICE->"Temporary price";case PERCENT->"Percentage off";case AMOUNT->"Rand off";case MULTIBUY->"Quantity deal";case BUY_GET->"Buy X, get Y";case BUNDLE->"Fixed bundle";};}
    private static <T> void column(TableView<T> table,String name,Function<T,String> text,int width){TableColumn<T,String> c=new TableColumn<>(name);c.setCellValueFactory(v->new SimpleStringProperty(text.apply(v.getValue())));c.setPrefWidth(width);table.getColumns().add(c);}
    private void refresh(){try{table.getItems().setAll(service.list());status.setText("Review drafts, test a basket, then approve with your single-use authorization key.");}catch(Exception e){status.setText("Promotions could not be loaded: "+e.getMessage());}}
    private FileChooser chooser(String title,String name)throws Exception {
        FileChooser chooser=new FileChooser();chooser.setTitle(title);chooser.setInitialDirectory(new SettingsService().sheetExportDirectory(false));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel workbook","*.xlsx"));if(name!=null)chooser.setInitialFileName(DocumentNames.name("Promotions",name,"xlsx"));return chooser;
    }
    private void export(boolean template){try {
        var file=chooser(template?"Save promotion template":"Export promotion drafts",template?"Template":"Offers").showSaveDialog(table.getScene().getWindow());if(file==null)return;
        if(!file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx"))file=new java.io.File(file.getPath()+".xlsx");
        new PromotionExcelService().export(file,template?List.of():service.list().stream().map(PromotionService.Campaign::draft).toList(),service.catalog());status.setText("Saved "+file.getName());
    }catch(Exception ex){status.setText(ex.getMessage());}}
    private void importFile(){try {
        var file=chooser("Import promotion workbook",null).showOpenDialog(table.getScene().getWindow());if(file==null)return;
        var plan=new PromotionExcelService().analyze(file,service.catalog());
        TextArea preview=new TextArea(plan.summary()+"\n\n"+String.join("\n",plan.warnings())+"\n\n"+String.join("\n",plan.errors()));preview.setEditable(false);preview.setWrapText(true);preview.setPrefRowCount(18);
        VBox content=new VBox(16,Ui.section("Import preview",preview),new Label("Only drafts are saved. Existing approved offers remain unchanged until a replacement is approved."));
        if(plan.valid())Ui.createForm("Import promotions",plan.rules().size()+" draft offers",content,"Import drafts",()->{try {service.saveDrafts(user.getStaffID(),plan.rules());refresh();return true;}catch(Exception ex){throw new IllegalArgumentException(ex.getMessage());}}).showAndWait();
        else Ui.editor("Import promotions","Correct the workbook errors",content,null).showAndWait();
    }catch(Exception ex){status.setText(ex.getMessage());}}
    private void details(PromotionService.Campaign campaign){try {
        edit(service.list().stream().filter(c->c.draft().id().equals(campaign.draft().id())).findFirst().orElseThrow().draft());
    }catch(Exception ex){status.setText(ex.getMessage());}}
    private void review(PromotionService.Campaign campaign,boolean approve) {
        PasswordField key=new PasswordField();key.setPromptText("6-digit authorization key");var grid=Ui.formGrid();Ui.formField(grid,0,"Authorization key",key);
        Label note=new Label("Generate your key in Sessions → Auth Key. It is valid once for 15 minutes. Approval publishes this draft; disabling stops new applications without changing past sales.");note.setWrapText(true);
        Ui.createForm(approve?"Approve promotion":"Disable promotion",campaign.draft().name(),Ui.section("Supervisor confirmation",grid,note),approve?"Approve draft":"Disable offer",()->{
            try{service.review(user.getStaffID(),key.getText().trim(),campaign.draft().id(),campaign.draft().revision(),approve);key.clear();refresh();return true;}catch(Exception ex){throw new IllegalArgumentException(ex.getMessage());}
        }).showAndWait();
    }
    private static TableView<Item> itemTable(Map<Integer,String> names){TableView<Item> result=new TableView<>();column(result,"Group",Item::group,100);column(result,"Barcode",Item::barcode,150);column(result,"Product",i->names.getOrDefault(i.productId(),"Unavailable"),220);column(result,"Group quantity",i->""+i.quantity(),100);result.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);return result;}
    private void edit(Rule existing){try {
        List<PromotionService.Product> catalog=service.catalog();Map<Integer,String> names=new HashMap<>();catalog.forEach(p->names.put(p.id(),p.name()));
        TextField id=new TextField(existing==null?"OFFER_"+System.currentTimeMillis():existing.id()),name=new TextField(existing==null?"":existing.name()),code=new TextField(existing==null?"":existing.code());id.setDisable(existing!=null);
        ComboBox<Type> type=new ComboBox<>(FXCollections.observableArrayList(Type.values()));type.setValue(existing==null?Type.PRICE:existing.type());
        type.setConverter(new javafx.util.StringConverter<>(){public String toString(Type t){return t==null?"":typeName(t);}public Type fromString(String s){return null;}});
        DatePicker from=new DatePicker(existing==null?LocalDate.now():existing.from().toLocalDate()),to=new DatePicker(existing==null?LocalDate.now().plusDays(6):existing.until().minusNanos(1).toLocalDate());
        TextField value=new TextField(existing==null?"":existing.value().toPlainString()),buy=new TextField(existing==null?"1":""+existing.buy()),get=new TextField(existing==null?"1":""+existing.get()),limit=new TextField(existing==null?"0":""+existing.limit());
        CheckBox distinct=new CheckBox("Require different product barcodes (multibuy only)");distinct.setSelected(existing!=null&&existing.distinct());distinct.disableProperty().bind(type.valueProperty().isNotEqualTo(Type.MULTIBUY));
        var basics=Ui.formGrid();Ui.formField(basics,0,"Promotion ID",id);Ui.formField(basics,1,"Name",name);Ui.formField(basics,2,"Offer type",type);Ui.formField(basics,3,"Starts",from);Ui.formField(basics,4,"Ends (inclusive)",to);Ui.formField(basics,5,"Coupon code (optional)",code);
        var amounts=Ui.formGrid();Ui.formField(amounts,0,"Offer value",value);Ui.formField(amounts,1,"Buy quantity",buy);Ui.formField(amounts,2,"Reward quantity",get);Ui.formField(amounts,3,"Per-sale limit (0 = none)",limit);
        Label help=new Label();help.setWrapText(true);Runnable hint=()->help.setText(switch(type.getValue()){
            case PRICE->"Offer value is the special price per unit in rand. Use ITEM for the product group.";
            case PERCENT->"Offer value is the percentage off each selected unit. Use ITEM for the group.";
            case AMOUNT->"Offer value is the rand discount per selected unit. Use ITEM for the group.";
            case MULTIBUY->"Offer value is the total price for Buy quantity. Use ITEM for the group. Different products means distinct barcodes.";
            case BUY_GET->"Offer value is the reward discount percentage: 100 = free, 50 = half price. Add BUY and GET groups; use identical or separate lists.";
            case BUNDLE->"Offer value is the complete bundle price. Add a named group for each requirement. Alternatives share a group and required quantity.";
        });hint.run();type.setOnAction(e->{hint.run();if(type.getValue()!=Type.MULTIBUY)distinct.setSelected(false);});
        TableView<Item> items=itemTable(names);items.setPrefHeight(220);if(existing!=null)items.getItems().setAll(existing.items());
        ComboBox<PromotionService.Product> product=new ComboBox<>(FXCollections.observableArrayList(catalog));product.setPrefWidth(420);product.setPromptText("Select a product and pack size");product.setConverter(new javafx.util.StringConverter<>() {public String toString(PromotionService.Product p){return p==null?"":p.barcode()+" — "+p.name();}public PromotionService.Product fromString(String s){return null;}});
        TextField group=new TextField("ITEM"),required=new TextField("1");var selection=Ui.formGrid();Ui.formField(selection,0,"Product",product);Ui.formField(selection,1,"Group",group);Ui.formField(selection,2,"Required group quantity",required);
        Label feedback=new Label();feedback.setWrapText(true);
        Button add=button("Add product",()->{try{var p=product.getValue();if(p==null)throw new IllegalArgumentException("Select a product.");String g=group.getText().trim().toUpperCase(Locale.ROOT);int q=Integer.parseInt(required.getText().trim());if(q<1)throw new IllegalArgumentException("Quantity must be positive.");items.getItems().removeIf(i->i.productId()==p.id()&&i.group().equals(g));items.getItems().add(new Item(p.id(),p.barcode(),g,q));feedback.setText("");}catch(Exception ex){feedback.setText(ex.getMessage());}});
        Button remove=button("Remove selected",()->items.getItems().remove(items.getSelectionModel().getSelectedItem()));
        VBox content=new VBox(16,Ui.section("Offer information",basics),Ui.section("Pricing and quantities",amounts,distinct,help),Ui.section("Product selection",selection,new HBox(12,add,remove),feedback,items));
        var dirty=new javafx.beans.property.SimpleBooleanProperty(false);
        for(TextField field:List.of(id,name,code,value,buy,get,limit))field.textProperty().addListener((o,a,b)->dirty.set(true));
        type.valueProperty().addListener((o,a,b)->dirty.set(true));from.valueProperty().addListener((o,a,b)->dirty.set(true));to.valueProperty().addListener((o,a,b)->dirty.set(true));distinct.selectedProperty().addListener((o,a,b)->dirty.set(true));items.getItems().addListener((javafx.collections.ListChangeListener<Item>)change->dirty.set(true));
        var dialog=Ui.createForm("Promotion",existing==null?"New retail offer":existing.name(),content,"Save draft",()->{
            try {
                Rule r=new Rule(id.getText().trim().toUpperCase(Locale.ROOT),name.getText().trim(),type.getValue(),from.getValue().atStartOfDay(),to.getValue().plusDays(1).atStartOfDay(),Integer.parseInt(limit.getText().trim()),distinct.isSelected(),code.getText().trim().toUpperCase(Locale.ROOT),new BigDecimal(value.getText().trim()),Integer.parseInt(buy.getText().trim()),Integer.parseInt(get.getText().trim()),List.copyOf(items.getItems()),existing==null?0:existing.revision());
                service.saveDrafts(user.getStaffID(),List.of(r));refresh();return true;
            }catch(Exception ex){throw new IllegalArgumentException(ex.getMessage()==null?"Check all dates, quantities and offer values.":ex.getMessage());}
        });
        if(existing!=null) {
            var campaign=service.list().stream().filter(c->c.draft().id().equals(existing.id())).findFirst().orElseThrow();
            Button approve=button("Approve draft",()->{review(campaign,true);dialog.close();}),disable=button("Disable offer",()->{review(campaign,false);dialog.close();}),test=button("Test basket",()->testBasket(existing));
            approve.disableProperty().bind(dirty.or(new javafx.beans.property.SimpleBooleanProperty(!user.hasFullAccess())));
            disable.disableProperty().bind(dirty.or(new javafx.beans.property.SimpleBooleanProperty(!user.hasFullAccess()||!campaign.state().equals("APPROVED"))));test.disableProperty().bind(dirty);
            Label warnings=new Label(String.join("\n",PromotionExcelService.warnings(existing,catalog)));warnings.setWrapText(true);
            content.getChildren().addFirst(Ui.section("Review and approval",Ui.detailRow("Status",campaign.status()),Ui.detailRow("Draft revision",""+existing.revision()),warnings,new Label("Save edited fields before testing or approving the draft."),new FlowPane(12,12,approve,disable,test)));
        }
        dialog.showAndWait();
    }catch(Exception ex){status.setText(ex.getMessage());}}
    private void testBasket(Rule draft){try {
        var catalog=service.catalog();List<Rule> rules=new ArrayList<>();for(var c:service.list())if(c.state().equals("APPROVED")&&c.published()!=null&&!c.draft().id().equals(draft.id()))rules.add(c.published());rules.add(draft);
        ComboBox<PromotionService.Product> product=new ComboBox<>(FXCollections.observableArrayList(catalog));product.setConverter(new javafx.util.StringConverter<>() {public String toString(PromotionService.Product p){return p==null?"":p.barcode()+" — "+p.name();}public PromotionService.Product fromString(String s){return null;}});
        TextField quantity=new TextField("1"),code=new TextField(draft.code());DatePicker date=new DatePicker(draft.from().toLocalDate());List<Cart> cart=new ArrayList<>();TextArea result=new TextArea();result.setEditable(false);result.setWrapText(true);result.setPrefRowCount(12);
        var grid=Ui.formGrid();Ui.formField(grid,0,"Product",product);Ui.formField(grid,1,"Quantity",quantity);Ui.formField(grid,2,"Test date",date);Ui.formField(grid,3,"Coupon code",code);
        Map<Integer,String> names=new HashMap<>();catalog.forEach(p->names.put(p.id(),p.name()));
        Button add=button("Add to test basket",()->{try {
            var p=product.getValue();if(p==null)throw new IllegalArgumentException("Select a product.");
            int q=Integer.parseInt(quantity.getText().trim());if(q<1)throw new IllegalArgumentException("Enter a positive whole quantity.");
            Cart old=cart.stream().filter(i->i.productId()==p.id()).findFirst().orElse(null);
            int total=Math.addExact(q,old==null?0:old.quantity());if(total>1000000)throw new IllegalArgumentException("Use at most 1,000,000 units per product.");
            if(old!=null)cart.remove(old);cart.add(new Cart(p.id(),p.price(),total));
            result.setText(cart.stream().map(i->i.quantity()+" × "+names.get(i.productId())+" at R"+i.price().toPlainString()+" each").collect(java.util.stream.Collectors.joining("\n")));
        }catch(Exception ex){result.setText(ex.getMessage());}});
        Button calculate=button("Calculate offers",()->{try{var quote=PromotionEngine.price(cart,rules,date.getValue().atTime(12,0),code.getText());result.setText("Regular total: R"+quote.subtotal()+"\nSavings: R"+quote.discount()+"\nAmount due: R"+quote.total()+"\n\n"+quote.description());}catch(Exception ex){result.setText(ex.getMessage());}});
        Button clear=button("Clear basket",()->{cart.clear();result.clear();});
        Ui.editor("Test promotions",draft.name(),new VBox(16,Ui.section("Test basket — no stock or sales are changed",grid,new FlowPane(12,12,add,calculate,clear)),Ui.section("Pricing result",result)),null).showAndWait();
    }catch(Exception ex){status.setText(ex.getMessage());}}
}

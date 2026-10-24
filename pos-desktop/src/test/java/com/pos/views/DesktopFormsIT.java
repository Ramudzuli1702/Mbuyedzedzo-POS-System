package com.pos.views;

import com.pos.components.*;
import com.pos.models.*;
import com.pos.services.*;
import com.pos.utils.BrandAssets;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.scene.*;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JavaFX controls with in-memory services; never opens shop database settings. */
class DesktopFormsIT {
    @Test void creationValidationPerformanceAndWindowBranding() throws Exception {
        Platform.startup(()->{});Platform.setImplicitExit(false);
        CompletableFuture<Void> result=new CompletableFuture<>();
        Platform.runLater(()->{
            Stage owner=new Stage();owner.setX(-20000);owner.setY(-20000);owner.setScene(new Scene(new VBox(),1180,900));
            owner.getScene().getStylesheets().add(getClass().getResource("/css/app-theme.css").toExternalForm());
            try {
                BrandAssets.installWindowIcons();BrandAssets.installWindowIcons();owner.show();
                assertEquals(7,owner.getIcons().size());BrandAssets.applyAppIcons(owner);assertEquals(7,owner.getIcons().size());
                SidebarBrand branding=new SidebarBrand();VBox sidebar=new VBox(branding);sidebar.setStyle("-fx-background-color: #0f766e;");
                sidebar.setAlignment(javafx.geometry.Pos.TOP_CENTER);sidebar.setPadding(new javafx.geometry.Insets(20,8,20,8));
                sidebar.setMaxWidth(250);owner.getScene().setRoot(sidebar);sidebar.applyCss();sidebar.layout();
                assertTrue(branding.lookupAll(".label").stream().anyMatch(n->n instanceof Label label&&label.getText().equals("Mbuyedzedzo")&&label.isVisible()));
                snapshot(branding,"sidebar-brand-expanded");
                branding.setCollapsed(true);sidebar.setMaxWidth(72);sidebar.applyCss();sidebar.layout();
                assertTrue(branding.getBoundsInParent().getWidth()<=56.1,"Collapsed logo width: "+branding.getBoundsInParent().getWidth());
                assertFalse(branding.lookupAll(".label").stream().anyMatch(Node::isVisible));snapshot(branding,"sidebar-brand-collapsed");
                assertEquals("Mbuyedzedzo\n"+com.pos.Branding.PRODUCT_LINE,com.pos.Branding.DISPLAY_NAME);
                assertFalse(com.pos.Branding.WINDOW_TITLE.contains("POS System"));
                var customers=new CustomerService(){
                    @Override public boolean addCustomer(Customer customer){assertEquals("Customer Fixture",customer.getFullNames());return true;}
                };
                int[] saved={0};Dialog<Void> customer=CustomerForm.create(1,customers,c->saved[0]++);
                customer.initOwner(owner);customer.setX(-20000);customer.show();
                assertEquals(7,((Stage)customer.getDialogPane().getScene().getWindow()).getIcons().size());
                button(customer.getDialogPane(),"Add customer").fire();assertTrue(customer.isShowing());assertEquals(0,saved[0]);
                ((TextField)field(customer.getDialogPane(),"Full name")).setText("Customer Fixture");
                ((TextField)field(customer.getDialogPane(),"Email address")).setText("fixture@example.test");
                ((TextField)field(customer.getDialogPane(),"Phone number")).setText("0000000000");
                ((DatePicker)field(customer.getDialogPane(),"Date of birth")).setValue(LocalDate.now().minusYears(25));
                snapshot(customer.getDialogPane(),"customer-form");
                button(customer.getDialogPane(),"Add customer").fire();assertFalse(customer.isShowing());assertEquals(1,saved[0]);

                var user=new User(1,"Test Manager","manager@example.test","Manager","Active");
                UserService users=new FixtureUsers(user);
                UserManagementView view=new UserManagementView(user,users);BorderPane root=view.getView();
                owner.getScene().setRoot(root);root.applyCss();root.layout();
                button(root,"Performance Report").fire();root.applyCss();root.layout();
                assertTrue(root.lookupAll(".chart").size()==2);
                var performance=(BorderPane)root.getCenter();assertTrue(performance.getStyleClass().contains("reports-view"));
                for(Node node:root.lookupAll(".chart")) {
                    var chart=(LineChart<Number,Number>)node;assertEquals(2,chart.getData().size());assertTrue(chart.getCreateSymbols());
                    assertEquals("Test Manager",chart.getData().get(0).getName());
                    var points=chart.getData().get(0).getData();assertEquals(1,points.get(0).getXValue().intValue());assertEquals(5,points.get(1).getXValue().intValue());
                }
                assertEquals("0x0f766eff",((javafx.scene.shape.Shape)root.lookup(".default-color0.chart-series-line")).getStroke().toString());
                assertEquals("0x475569ff",((javafx.scene.shape.Shape)root.lookup(".default-color1.chart-series-line")).getStroke().toString());
                snapshot(root,"staff-performance");
                button(root,"Back to users").fire();

                CompletableFuture<Void> addCheck=new CompletableFuture<>();
                Platform.runLater(()->{
                    try {
                        Stage window=window("Add user");Parent form=window.getScene().getRoot();
                        assertEquals(7,window.getIcons().size());button(form,"Add user").fire();assertTrue(window.isShowing());
                        ((TextField)field(form,"Full name")).setText("New Staff");((TextField)field(form,"Email address")).setText("new@example.test");
                        ((PasswordField)field(form,"Password")).setText("Fixture!123");((PasswordField)field(form,"Confirm password")).setText("Fixture!123");
                        snapshot(form,"add-user-form");button(form,"Add user").fire();assertFalse(window.isShowing());addCheck.complete(null);
                    }catch(Throwable e){addCheck.completeExceptionally(e);for(var w:List.copyOf(Window.getWindows()))if(w!=owner)w.hide();}
                });
                var add=UserManagementView.class.getDeclaredMethod("showAddUserDialog");add.setAccessible(true);add.invoke(view);addCheck.join();

                CompletableFuture<Void> keyCheck=new CompletableFuture<>();
                Platform.runLater(()->{
                    try {
                        Stage window=window("Authorization key");Parent form=window.getScene().getRoot();
                        button(form,"Generate key").fire();assertTrue(button(form,"Generate key").isDisabled()==false);
                        ((PasswordField)field(form,"Confirm password")).setText("Fixture!123");button(form,"Generate key").fire();
                        assertTrue(form.lookupAll(".label").stream().anyMatch(n->n instanceof Label label&&label.getText().equals("123456")));
                        snapshot(form,"authorization-key");button(form,"Close").fire();keyCheck.complete(null);
                    }catch(Throwable e){keyCheck.completeExceptionally(e);for(var w:List.copyOf(Window.getWindows()))if(w!=owner)w.hide();}
                });
                AuthorizationKeyDialog.show(user,user,users,new SessionService(){@Override public String getOrCreateAuthKey(int staff){return "123456";}});keyCheck.join();
                Alert alert=new Alert(Alert.AlertType.INFORMATION);alert.initOwner(owner);alert.setX(-20000);alert.show();
                assertEquals(7,((Stage)alert.getDialogPane().getScene().getWindow()).getIcons().size());alert.close();
                result.complete(null);
            }catch(Throwable e){result.completeExceptionally(e);}
            finally {for(var window:List.copyOf(Window.getWindows()))window.hide();}
        });
        try {result.get(90,TimeUnit.SECONDS);}finally{Platform.exit();}
    }
    static Stage window(String title){return Window.getWindows().stream().filter(w->w instanceof Stage s&&title.equals(s.getTitle())).map(w->(Stage)w).findFirst().orElseThrow();}
    static Button button(Parent root,String text){return root.lookupAll(".button").stream().filter(n->n instanceof Button b&&text.equals(b.getText())).map(n->(Button)n).findFirst().orElseThrow();}
    static Node field(Parent root,String text){return root.lookupAll(".label").stream().filter(n->n instanceof Label l&&text.equals(l.getText())&&l.getLabelFor()!=null).map(n->((Label)n).getLabelFor()).findFirst().orElseThrow();}
    static void snapshot(Parent root,String name) throws Exception {
        root.applyCss();root.layout();java.nio.file.Path output=java.nio.file.Path.of("target","ui-discipline");java.nio.file.Files.createDirectories(output);
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",output.resolve(name+".png").toFile());
    }
    static class FixtureUsers extends UserService {
        final User manager;FixtureUsers(User manager){this.manager=manager;}
        @Override public ObservableList<User> getAllUsers(){return FXCollections.observableArrayList(manager);}
        @Override public boolean addUser(User user,String password){assertEquals("New Staff",user.getFullNames());assertEquals("Fixture!123",password);return true;}
        @Override public User login(String email,String password){return "Fixture!123".equals(password)?manager:null;}
        @Override public List<StaffPerformance> getStaffPerformance(int year,int month){return List.of(new StaffPerformance(1,"Test Manager",1000,8),new StaffPerformance(2,"Test Cashier",500,4));}
        @Override public Map<Integer,Map<LocalDate,Double>> getDailySalesAllStaff(YearMonth month){return Map.of(1,Map.of(month.atDay(5),600d,month.atDay(1),400d),2,Map.of(month.atDay(1),500d));}
        @Override public Map<Integer,Map<LocalDate,Double>> getDailyHoursAllStaff(YearMonth month){return Map.of(1,Map.of(month.atDay(5),4d,month.atDay(1),4d),2,Map.of(month.atDay(1),4d));}
    }
}

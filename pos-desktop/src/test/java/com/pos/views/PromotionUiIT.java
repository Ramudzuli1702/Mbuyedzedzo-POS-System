package com.pos.views;

import com.pos.database.DatabaseConnection;
import com.pos.it.ItSupport;
import com.pos.models.User;
import com.pos.services.*;
import com.pos.services.PromotionEngine.*;
import com.pos.utils.BrandAssets;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static com.pos.views.DesktopFormsIT.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual themed controls against the disposable integration database. */
class PromotionUiIT {
    @Test void draftValidationEditingApprovalAndBasketPreview() throws Exception {
        ItSupport.requireDatabase();
        ItSupport.truncate("PromotionReview","PromotionRedemption","PromotionCampaign");
        try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement()) {
            s.executeUpdate("INSERT INTO Staff (StaffID,FullNames,EmailAddress,UserPassword,UserType,Status) VALUES (9001,'UI Manager','ui-promo@example.test','fixture','Manager','Active')");
            s.executeUpdate("INSERT INTO Category (CategoryID,CategoryName) VALUES (9001,'UI Drinks')");
            s.executeUpdate("INSERT INTO Product (ProductID,StaffID,CategoryID,ProductName,BarCode,Quantity,Price,CostPrice) VALUES (9001,9001,9001,'UI 2L Cola','UI-PROMO-COLA',30,25,12)");
        }
        Platform.startup(()->{});Platform.setImplicitExit(false);
        CompletableFuture<Void> done=new CompletableFuture<>();
        Platform.runLater(()->{
            Stage owner=new Stage();
            try {
                BrandAssets.installWindowIcons();
                var service=new PromotionService();
                var view=new PromotionView(new User(9001,"UI Manager","ui-promo@example.test","Manager","Active"),service);
                Parent root=view.getView();owner.setScene(new Scene(root,1180,900));owner.setX(-20000);owner.setY(-20000);owner.show();root.applyCss();root.layout();
                modal(()->button(root,"New offer").fire(),()->{
                    Stage stage=window("Promotion");Parent form=stage.getScene().getRoot();
                    assertEquals(7,stage.getIcons().size());
                    button(form,"Save draft").fire();assertTrue(stage.isShowing(),"Validation must preserve the form");
                    ((TextField)field(form,"Promotion ID")).setText("UI_COLA");
                    ((TextField)field(form,"Name")).setText("Three 2L colas for R60");
                    @SuppressWarnings("unchecked") ComboBox<Type> type=(ComboBox<Type>)field(form,"Offer type");type.setValue(Type.MULTIBUY);
                    ((TextField)field(form,"Offer value")).setText("60");((TextField)field(form,"Buy quantity")).setText("3");
                    selectCola(form);button(form,"Add product").fire();snapshot(form,"promotion-create");
                    button(form,"Save draft").fire();assertFalse(stage.isShowing());
                });
                var draft=service.list().get(0).draft();assertNull(service.list().get(0).published());assertEquals(1,draft.revision());
                var edit=PromotionView.class.getDeclaredMethod("edit",Rule.class);edit.setAccessible(true);
                modal(()->edit.invoke(view,draft),()->{
                    Parent form=window("Promotion").getScene().getRoot();
                    assertEquals("60.00",((TextField)field(form,"Offer value")).getText());
                    assertFalse(button(form,"Approve draft").isDisabled());
                    ((TextField)field(form,"Name")).setText("Updated cola offer");
                    assertTrue(button(form,"Approve draft").isDisabled());assertTrue(button(form,"Test basket").isDisabled());
                    snapshot(form,"promotion-review");button(form,"Close").fire();
                });
                assertTrue(new SessionService().createSupervisorCode(9001,"123456"));
                modal(()->edit.invoke(view,draft),()->{
                    Parent form=window("Promotion").getScene().getRoot();
                    modal(()->button(form,"Approve draft").fire(),()->{
                        Stage confirmation=window("Approve promotion");Parent approval=confirmation.getScene().getRoot();
                        ((PasswordField)field(approval,"Authorization key")).setText("123456");
                        snapshot(approval,"promotion-approval");button(approval,"Approve draft").fire();assertFalse(confirmation.isShowing());
                    });
                });
                assertEquals("APPROVED",service.list().get(0).state());assertNotNull(service.list().get(0).published());
                var preview=PromotionView.class.getDeclaredMethod("testBasket",Rule.class);preview.setAccessible(true);
                modal(()->preview.invoke(view,draft),()->{
                    Parent form=window("Test promotions").getScene().getRoot();selectCola(form);
                    ((TextField)field(form,"Quantity")).setText("3");button(form,"Add to test basket").fire();
                    TextArea result=(TextArea)form.lookup(".text-area");assertTrue(result.getText().contains("UI 2L Cola"));assertFalse(result.getText().contains("Cart["));
                    ((TextField)field(form,"Quantity")).setText("-1");button(form,"Add to test basket").fire();
                    button(form,"Calculate offers").fire();assertTrue(result.getText().contains("Amount due: R60.00"),result.getText());
                    assertTrue(result.getText().contains("Savings: R15.00"));snapshot(form,"promotion-test-basket");button(form,"Close").fire();
                });
                done.complete(null);
            }catch(Throwable e){done.completeExceptionally(e);}
            finally{for(Window w:List.copyOf(Window.getWindows()))w.hide();}
        });
        try{done.get(90,TimeUnit.SECONDS);}finally{Platform.exit();}
    }
    static void selectCola(Parent form) {
        @SuppressWarnings("unchecked") ComboBox<PromotionService.Product> products=(ComboBox<PromotionService.Product>)field(form,"Product");
        products.setValue(products.getItems().stream().filter(p->p.id()==9001).findFirst().orElseThrow());
    }
    interface Checked {void run()throws Exception;}
    static void modal(Checked open,Checked inspect)throws Exception {
        CompletableFuture<Void> check=new CompletableFuture<>();
        Platform.runLater(()->{try{inspect.run();check.complete(null);}catch(Throwable e){check.completeExceptionally(e);for(Window w:List.copyOf(Window.getWindows()))if(w instanceof Stage s&&!s.getTitle().isEmpty())w.hide();}});
        open.run();check.join();
    }
}

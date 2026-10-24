package com.pos.services;

import com.pos.database.*;
import com.pos.it.ItSupport;
import com.pos.services.PromotionEngine.*;
import com.pos.views.SalesView.CartItem;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PromotionIT {
    PromotionService service=new PromotionService();
    @BeforeAll static void setup(){ItSupport.requireDatabase();}
    @BeforeEach void fixture()throws Exception {
        com.pos.utils.LoginAttempts.SHARED.success("approval:1");
        ItSupport.truncate("PromotionReview","PromotionRedemption","PromotionCampaign","Returns","Exchanges","SessionTransactions","Transactions","BusinessSessions","SupervisorCodes","AddInventory","Product","Account","Staff","Category","Promo");
        sql("INSERT INTO Staff (StaffID,FullNames,EmailAddress,UserPassword,UserType,Status) VALUES (1,'Manager','manager@example.test','test','Manager','Active'),(2,'Cashier','cashier@example.test','test','Cashier','Active')");
        sql("INSERT INTO Account (AccountID,StaffID,FullNames,EmailAddress,DateOfBirth,ContactNo) VALUES (1,1,'Customer','customer@example.test','2000-01-01','000')");sql("INSERT INTO Category VALUES (1,'Groceries')");
        sql("INSERT INTO Product (ProductID,StaffID,CategoryID,ProductName,BarCode,Quantity,Price,CostPrice) VALUES (1,1,1,'2L Cola','0001',30,25,12),(2,1,1,'2kg Flour','0002',30,20,10),(3,1,1,'2kg Rice','0003',30,30,15),(4,1,1,'2L Oil','0004',30,35,15),(5,1,1,'30 Eggs','0005',30,50,25),(6,1,1,'Snack','0006',30,10,5)");
        sql("INSERT INTO BusinessSessions (SupervisorID,StartDate,Status,TotalSales,TotalCashSales,TotalCardSales) VALUES (1,CURRENT_TIMESTAMP,'Active',0,0,0)");key();
    }
    static void sql(String sql)throws Exception{try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement()){s.executeUpdate(sql);}}
    static int number(String sql)throws Exception{try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    static void amount(String expected,String sql)throws Exception{try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();assertEquals(0,new BigDecimal(expected).compareTo(r.getBigDecimal(1)));}}
    static void key(){assertTrue(SupervisorAuthorization.create(1,"123456"));}
    Rule cola(String price){var now=LocalDate.now(ZoneId.of("Africa/Johannesburg"));return new Rule("COLA3","Three colas",Type.MULTIBUY,now.minusDays(1).atStartOfDay(),now.plusDays(7).atStartOfDay(),0,false,"",new BigDecimal(price),3,0,List.of(new Item(1,"0001","ITEM",1)),0);}
    javafx.collections.ObservableList<CartItem> cart(){return FXCollections.observableArrayList(new CartItem(1,"2L Cola",new BigDecimal("25.00"),3));}
    void enable(Rule rule)throws Exception {service.saveDrafts(1,List.of(rule));service.review(1,"123456",rule.id(),service.list().get(0).draft().revision(),true);}
    boolean sell(Quote quote,String code){return new TransactionService().processPromotedTransaction(2,2,1,cart(),new TransactionService.PaymentInfo("Cash",quote.total(),BigDecimal.ZERO),code,quote);}
    @Test void activationRequiresCorrectOneUseManagerKeyAndDraftEditsDoNotChangeLivePricing()throws Exception {
        assertThrows(SQLException.class,()->service.saveDrafts(2,List.of(cola("60"))));service.saveDrafts(1,List.of(cola("60")));
        assertEquals(new BigDecimal("75.00"),service.quote(cart(),"").total());assertThrows(SQLException.class,()->service.review(1,"654321","COLA3",1,true));
        service.review(1,"123456","COLA3",1,true);assertEquals(new BigDecimal("60.00"),service.quote(cart(),"").total());
        service.saveDrafts(1,List.of(cola("50")));assertEquals(new BigDecimal("60.00"),service.quote(cart(),"").total());
        assertThrows(SQLException.class,()->service.review(1,"123456","COLA3",2,true));key();
        assertThrows(SQLException.class,()->service.review(1,"123456","COLA3",1,true));service.review(1,"123456","COLA3",2,true);
        assertEquals(new BigDecimal("50.00"),service.quote(cart(),"").total());assertEquals(2,number("SELECT COUNT(*) FROM PromotionReview"));assertEquals(1,number("SELECT COUNT(*) FROM PromotionCampaign"));
    }
    @Test void checkoutStockSessionReportsReceiptsAndRefundsUseTheAllocatedPaidAmount()throws Exception {
        enable(cola("60"));Quote quote=service.quote(cart(),"");assertTrue(sell(quote,""));
        amount("60","SELECT SUM(SalePrice*Quantity) FROM Transactions");amount("60","SELECT TotalSales FROM BusinessSessions WHERE Status='Active'");amount("15","SELECT SUM(Discount) FROM PromotionRedemption");
        assertEquals(27,number("SELECT Quantity FROM Product WHERE ProductID=1"));assertEquals(1,number("SELECT COUNT(*) FROM Transactions WHERE RegularUnitPrice=25 AND PromotionSnapshot IS NOT NULL"));
        assertTrue(new ReportService().getPromoPerformance(LocalDate.now(),LocalDate.now()).get(0).get("promoCode").toString().contains("Three colas"));
        int transaction=number("SELECT MIN(TransactionID) FROM Transactions");var returns=new ExchangeReturnService();int request=returns.requestReturn(transaction,1,2,"One returned",new BigDecimal("20.00"));key();assertTrue(returns.approveReturn(request,1,"123456"));
        assertEquals(28,number("SELECT Quantity FROM Product WHERE ProductID=1"));amount("40","SELECT TotalCashSales FROM BusinessSessions WHERE Status='Active'");
        int excessive=returns.requestReturn(transaction,1,2,"Wrong shelf-price refund",new BigDecimal("25.00"));key();assertFalse(returns.approveReturn(excessive,1,"123456"));assertEquals(28,number("SELECT Quantity FROM Product WHERE ProductID=1"));
        var customer=new com.pos.models.Customer();customer.setFullNames("Fixture");customer.setEmailAddress("fixture@example.test");
        var user=new com.pos.models.User(2,"Cashier","cashier@example.test","Cashier","Active");
        String receipt=com.pos.utils.ReceiptGenerator.generateReceipt(customer,cart(),user,new TransactionService.PaymentInfo("Cash",quote.total(),BigDecimal.ZERO),quote);assertTrue(receipt.contains("Three colas"));assertTrue(receipt.contains("60.00"));
    }
    @Test void changedOfferRejectsStaleCheckoutWithoutStockOrLedgerChanges()throws Exception {
        enable(cola("60"));Quote quote=service.quote(cart(),"");service.saveDrafts(1,List.of(cola("55")));key();service.review(1,"123456","COLA3",2,true);
        assertFalse(sell(quote,""));assertEquals(30,number("SELECT Quantity FROM Product WHERE ProductID=1"));assertEquals(0,number("SELECT COUNT(*) FROM Transactions"));assertEquals(0,number("SELECT COUNT(*) FROM PromotionRedemption"));amount("0","SELECT TotalSales FROM BusinessSessions WHERE Status='Active'");
    }
    @Test void stockFailureRollsBackOfferEvidenceAndSaleTogether()throws Exception {
        enable(cola("60"));Quote quote=service.quote(cart(),"");sql("UPDATE Product SET Quantity=2 WHERE ProductID=1");assertFalse(sell(quote,""));assertEquals(2,number("SELECT Quantity FROM Product WHERE ProductID=1"));assertEquals(0,number("SELECT COUNT(*) FROM Transactions"));assertEquals(0,number("SELECT COUNT(*) FROM PromotionRedemption"));
    }
    @Test void importsAreAtomicAndReimportDoesNotCreateDuplicateOffers()throws Exception {
        Rule good=cola("60"),bad=new Rule("BAD","Invalid",Type.PRICE,good.from(),good.until(),0,false,"",BigDecimal.ONE,0,0,List.of(new Item(999,"unknown","ITEM",1)),0);
        assertThrows(IllegalArgumentException.class,()->service.saveDrafts(1,List.of(good,bad)));assertTrue(service.list().isEmpty());service.saveDrafts(1,List.of(good));service.saveDrafts(1,List.of(good));assertEquals(1,service.list().size());assertEquals(1,service.list().get(0).draft().revision());assertNull(service.list().get(0).published());
    }
    @Test void couponsCompeteWithAutomaticOffersAndOnlyWinningCouponsConsumeUsage()throws Exception {
        enable(cola("60"));sql("INSERT INTO Promo (PromoCode,PromoName,DiscountType,DiscountValue,IsActive,UsageLimit,UsageCount) VALUES ('SAVE10','Ten off','FIXED',10,TRUE,10,0),('SAVE20','Twenty off','FIXED',20,TRUE,10,0)");
        Quote best=service.quote(cart(),"SAVE10");assertEquals(new BigDecimal("60.00"),best.total());assertTrue(sell(best,"SAVE10"));assertEquals(0,number("SELECT UsageCount FROM Promo WHERE PromoCode='SAVE10'"));
        best=service.quote(cart(),"SAVE20");assertEquals(new BigDecimal("55.00"),best.total());assertTrue(sell(best,"SAVE20"));assertEquals(1,number("SELECT UsageCount FROM Promo WHERE PromoCode='SAVE20'"));amount("115","SELECT SUM(SalePrice*Quantity) FROM Transactions");
    }
    @Test void basketCouponsCannotTakeRetailCodesAndDisablingNeedsANewKey()throws Exception {
        Rule automatic=cola("60");Rule coded=new Rule(automatic.id(),automatic.name(),automatic.type(),automatic.from(),automatic.until(),0,false,"COLAS",automatic.value(),3,0,automatic.items(),0);
        enable(coded);
        var coupon=new PromoService.Promo();coupon.setPromoCode("colas");coupon.setPromoName("Conflicting coupon");coupon.setDiscountType("FIXED");coupon.setDiscountValue(BigDecimal.ONE);coupon.setActive(true);
        assertFalse(new PromoService().addPromo(coupon));assertEquals(0,number("SELECT COUNT(*) FROM Promo"));
        coupon.setPromoCode("OTHER");assertTrue(new PromoService().addPromo(coupon));
        Rule conflicting=new Rule("CONFLICT","Conflict",coded.type(),coded.from(),coded.until(),0,false,"OTHER",coded.value(),3,0,coded.items(),0);
        assertThrows(IllegalArgumentException.class,()->service.saveDrafts(1,List.of(conflicting)));
        assertThrows(SQLException.class,()->service.review(1,"123456","COLA3",1,false));key();service.review(1,"123456","COLA3",1,false);
        assertEquals(new BigDecimal("75.00"),service.quote(cart()," ").total());assertEquals("DISABLED",service.list().get(0).state());assertEquals(2,number("SELECT COUNT(*) FROM PromotionReview"));
    }
}

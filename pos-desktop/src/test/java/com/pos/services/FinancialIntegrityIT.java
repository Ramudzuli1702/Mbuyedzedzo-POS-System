package com.pos.services;

import com.pos.database.*;
import com.pos.it.ItSupport;
import com.pos.views.SalesView.CartItem;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class FinancialIntegrityIT {
    @BeforeAll static void database(){
        try {ItSupport.requireDatabase();}
        catch(org.opentest4j.TestAbortedException e){
            if(System.getProperty("pos.it.jdbcUrl","").isBlank())throw e;
            throw new AssertionError("Configured financial test database failed to initialize",e);
        }
    }
    @BeforeEach void fixture() throws Exception {
        ItSupport.truncate("PromotionReview","PromotionRedemption","PromotionCampaign","Returns","Exchanges","SessionTransactions","Transactions","BusinessSessions","SupervisorCodes","AddInventory","Product","Account","Staff","Category","Promo");
        sql("INSERT INTO Staff (StaffID,FullNames,EmailAddress,UserPassword,UserType,Status) VALUES (1,'Manager','manager@example.test','test','Manager','Active'),(2,'Cashier','cashier@example.test','test','Cashier','Active')");
        sql("INSERT INTO Account (AccountID,StaffID,FullNames,EmailAddress,DateOfBirth,ContactNo) VALUES (1,1,'Customer','customer@example.test','2000-01-01','000')");
        sql("INSERT INTO Category VALUES (1,'Groceries')");
        sql("INSERT INTO Product (ProductID,StaffID,CategoryID,ProductName,BarCode,Quantity,Price,CostPrice) VALUES (1,1,1,'Item','123',10,1.00,0.50),(2,1,1,'Replacement','456',10,1.00,0.60)");
        sql("INSERT INTO BusinessSessions (SupervisorID,StartDate,Status,TotalSales,TotalCashSales,TotalCardSales) VALUES (1,CURRENT_TIMESTAMP,'Active',0,0,0)");
        assertTrue(SupervisorAuthorization.create(1,"123456"));
    }
    @Test void archiveAndForeignKeysPreserveLedger() throws Exception {
        assertTrue(sale(1));
        assertTrue(new ProductService().deleteProduct(1));assertTrue(new CustomerService().deleteCustomer(1));
        assertEquals(1,number("SELECT COUNT(*) FROM Transactions"));
        assertNull(new ProductService().getProductById(1));assertTrue(new CustomerService().getAllCustomers().isEmpty());
        assertThrows(SQLException.class,()->sql("DELETE FROM Product WHERE ProductID=1"));
        assertThrows(SQLException.class,()->sql("DELETE FROM Account WHERE AccountID=1"));
        assertThrows(SQLException.class,()->sql("DELETE FROM Staff WHERE StaffID=1"));
        assertTrue(DatabaseSetup.run());assertEquals(1,number("SELECT COUNT(*) FROM Transactions"));
    }
    @Test void staleMetadataSaveCannotUndoSaleOrRoundTripStockChange() throws Exception {
        var product=new ProductService().getProductById(1);assertTrue(sale(1));
        product.setProductName("New name");assertFalse(new ProductService().updateProduct(product));
        assertEquals(9,quantity(1));
        product=new ProductService().getProductById(1);
        sql("UPDATE Product SET Quantity=8 WHERE ProductID=1");sql("UPDATE Product SET Quantity=9 WHERE ProductID=1");
        assertFalse(new ProductService().updateProduct(product));
    }
    @Test void simultaneousSalesForLastUnitHaveOneWinnerAndOneSessionEntry() throws Exception {
        sql("UPDATE Product SET Quantity=1 WHERE ProductID=1");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{start.await();return sale(1);};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(0,quantity(1));assertEquals(1,number("SELECT COUNT(*) FROM Transactions"));
            assertEquals(1,number("SELECT COUNT(*) FROM SessionTransactions"));
            assertEquals(1,number("SELECT TotalSales FROM BusinessSessions WHERE Status='Active'"));
        } finally {pool.shutdownNow();}
    }
    @Test void failedCartRollsBackEveryStockChangeAndSaleLine() throws Exception {
        var cart=FXCollections.observableArrayList(new CartItem(1,"Item",new BigDecimal("1.00"),2),new CartItem(2,"Replacement",new BigDecimal("1.00"),11));
        assertFalse(new TransactionService().processTransaction(2,1,cart,new TransactionService.PaymentInfo("Cash",new BigDecimal("13.00"),BigDecimal.ZERO)));
        assertEquals(10,quantity(1));assertEquals(10,quantity(2));assertEquals(0,number("SELECT COUNT(*) FROM Transactions"));
        assertEquals(0,number("SELECT COUNT(*) FROM SessionTransactions"));
    }
    @Test void returnCannotRestockTwiceOrExceedOriginalSaleAndRequiresManager() throws Exception {
        assertTrue(sale(1));int transaction=number("SELECT MIN(TransactionID) FROM Transactions");
        ExchangeReturnService service=new ExchangeReturnService();
        int first=service.requestReturn(transaction,1,2,"Return",new BigDecimal("1.00"));
        assertFalse(service.approveReturn(first,2,"654321"));
        assertFalse(service.approveReturn(first,1,"wrong"));
        assertTrue(service.approveReturn(first,1,"123456"));assertFalse(service.approveReturn(first,1,"123456"));
        assertFalse(service.rejectReturn(first,1,"123456"));assertEquals(10,quantity(1));
        int second=service.requestReturn(transaction,1,2,"Again",new BigDecimal("1.00"));
        assertTrue(SupervisorAuthorization.create(1,"123456"));
        assertFalse(service.approveReturn(second,1,"123456"));assertEquals(10,quantity(1));
    }
    @Test void competingReturnsAgainstOneSaleHaveOneWinner() throws Exception {
        assertTrue(sale(1));int transaction=number("SELECT MIN(TransactionID) FROM Transactions");
        ExchangeReturnService service=new ExchangeReturnService();
        int a=service.requestReturn(transaction,1,2,"First",BigDecimal.ONE),b=service.requestReturn(transaction,1,2,"Second",BigDecimal.ONE);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->service.approveReturn(a,1,"123456"));var second=pool.submit(()->service.approveReturn(b,1,"123456"));
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));assertEquals(10,quantity(1));
        } finally {pool.shutdownNow();}
    }
    @Test void exchangeCreditsOriginalOnceAndRejectsUnsettledPriceDifference() throws Exception {
        assertTrue(sale(1));int transaction=number("SELECT MIN(TransactionID) FROM Transactions");ExchangeReturnService service=new ExchangeReturnService();
        int exchange=service.requestExchange(transaction,2,"Exchange",2,BigDecimal.ONE);
        assertTrue(service.approveExchange(exchange,1,"123456"));assertFalse(service.approveExchange(exchange,1,"123456"));
        assertEquals(10,quantity(1));assertEquals(9,quantity(2));
        assertEquals(1,number("SELECT COUNT(*) FROM Returns WHERE Status='Approved'"));
        assertEquals(2,number("SELECT COUNT(*) FROM Transactions"));
        new SessionService().updateSessionTotals(number("SELECT MAX(SessionID) FROM BusinessSessions"));
        assertEquals(1,number("SELECT TotalCashSales FROM BusinessSessions WHERE Status='Active'"));
        assertTrue(SupervisorAuthorization.create(1,"123456"));
        int another=service.requestExchange(transaction,2,"Again",2,BigDecimal.ONE);assertFalse(service.approveExchange(another,1,"123456"));
    }
    @Test void priceAndPromotionChangesFailClosed() throws Exception {
        sql("UPDATE Product SET Price=2.00 WHERE ProductID=1");assertFalse(sale(1));assertEquals(10,quantity(1));
    }
    @Test void refundCashBelongsToItsApprovalSessionAndIsSettledOnce() throws Exception {
        assertTrue(sale(1));int originalSession=number("SELECT MAX(SessionID) FROM BusinessSessions");
        int transaction=number("SELECT MIN(TransactionID) FROM Transactions");
        sql("UPDATE BusinessSessions SET Status='Closed',EndDate=CURRENT_TIMESTAMP");
        sql("INSERT INTO BusinessSessions (SupervisorID,StartDate,Status,TotalSales,TotalCashSales,TotalCardSales) VALUES (1,CURRENT_TIMESTAMP,'Active',0,0,0)");
        int refundSession=number("SELECT MAX(SessionID) FROM BusinessSessions");
        ExchangeReturnService service=new ExchangeReturnService();int request=service.requestReturn(transaction,1,2,"Late return",BigDecimal.ONE);
        assertTrue(service.approveReturn(request,1,"123456"));
        assertEquals(refundSession,number("SELECT SettlementSessionID FROM Returns WHERE ReturnID="+request));
        assertEquals(-1,number("SELECT TotalCashSales FROM BusinessSessions WHERE SessionID="+refundSession));
        new SessionService().updateSessionTotals(originalSession);
        assertEquals(1,number("SELECT TotalCashSales FROM BusinessSessions WHERE SessionID="+originalSession));
        assertTrue(SupervisorAuthorization.create(1,"123456"));assertFalse(service.approveReturn(request,1,"123456"));
        assertEquals(-1,number("SELECT TotalCashSales FROM BusinessSessions WHERE SessionID="+refundSession));
    }
    @Test void discountAndPromoReservationReconcileAndRollBackTogether() throws Exception {
        sql("INSERT INTO Promo (PromoCode,DiscountType,DiscountValue,IsActive,UsageLimit,UsageCount) VALUES ('TEST','FIXED',0.01,TRUE,1,0)");
        var promo=new PromoService().getAllPromos().get(0);
        var cart=FXCollections.observableArrayList(new CartItem(1,"Item",BigDecimal.ONE,3));
        var payment=new TransactionService.PaymentInfo("Cash",new BigDecimal("2.99"),BigDecimal.ZERO);
        assertTrue(new TransactionService().processTransaction(2,1,cart,payment,promo,new BigDecimal("0.01")));
        try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT SUM(SalePrice*Quantity) FROM Transactions")) {
            r.next();assertEquals(0,new BigDecimal("2.99").compareTo(r.getBigDecimal(1)));
        }
        assertEquals(1,number("SELECT UsageCount FROM Promo WHERE PromoCode='TEST'"));
        assertFalse(new TransactionService().processTransaction(2,1,cart,payment,promo,new BigDecimal("0.01")));assertEquals(7,quantity(1));
        sql("UPDATE Promo SET UsageCount=0");sql("UPDATE Product SET Quantity=0 WHERE ProductID=1");
        assertFalse(new TransactionService().processTransaction(2,1,cart,payment,promo,new BigDecimal("0.01")));
        assertEquals(0,number("SELECT UsageCount FROM Promo WHERE PromoCode='TEST'"));
    }
    @Test void duplicateAndEquivalentBarcodesCannotBeAssignedToActiveProducts() throws Exception {
        sql("UPDATE Product SET BarCode='0123456789012' WHERE ProductID=1");
        var duplicate=new ProductService().getProductById(1);duplicate.setProductID(0);duplicate.setBarCode("123456789012");
        assertFalse(new ProductService().addProduct(duplicate));
        assertTrue(new ProductService().deleteProduct(1));assertTrue(new ProductService().addProduct(duplicate));
    }
    @Test void historicalCostsAreImmutableAndUnknownHistoryIsNotInvented() throws Exception {
        assertTrue(sale(1));var reports=new ReportService();var today=java.time.LocalDate.now();
        assertEquals(0,new BigDecimal("0.50").compareTo(reports.getTotalCOGS(today,today)));
        assertEquals(0,new BigDecimal("0.50").compareTo(reports.getDailyGrossProfit(today,today).get(today)));
        sql("UPDATE Product SET CostPrice=0.90 WHERE ProductID=1");
        assertEquals(0,new BigDecimal("0.50").compareTo(reports.getTotalCOGS(today,today)));
        sql("UPDATE Transactions SET UnitCostAtSale=NULL");
        assertNull(reports.getTotalCOGS(today,today));assertNull(reports.getGrossProfit(today,today));
        assertNull(reports.getDailyGrossProfit(today,today).get(today));
        assertTrue(Double.isNaN(reports.getGrossProfitMargin(today,today)));
        assertNull(reports.getTopProductsDetailed(today,today,10).get(0).get("profit"));
    }
    @Test void runtimeAccountCanUpgradeOnlyItsOwnSchemaAndCannotAdministerMySql() throws Exception {
        String other=DatabaseConnection.DATABASE.replace('_','x');
        sql("CREATE DATABASE IF NOT EXISTS `"+other+"`");sql("CREATE TABLE IF NOT EXISTS `"+other+"`.Forbidden (ID INT)");
        RuntimeDatabaseAccount.Credentials credentials;
        try(Connection c=DatabaseConnection.getConnection()){credentials=RuntimeDatabaseAccount.create(c,DatabaseConnection.DATABASE);}
        String oldUser=DatabaseConnection.USERNAME,oldPassword=DatabaseConnection.PASSWORD;
        DatabaseConnection.closeAll();DatabaseConnection.USERNAME=credentials.username();DatabaseConnection.PASSWORD=credentials.password();
        try {
            assertTrue(DatabaseSetup.run());assertEquals(2,number("SELECT COUNT(*) FROM Staff"));
            assertThrows(SQLException.class,()->number("SELECT COUNT(*) FROM mysql.user"));
            assertThrows(SQLException.class,()->number("SELECT COUNT(*) FROM `"+other+"`.Forbidden"));
            assertThrows(SQLException.class,()->sql("CREATE USER 'forbidden_audit_user'@'localhost' IDENTIFIED BY 'NotARealPassword!123'"));
        } finally {DatabaseConnection.closeAll();DatabaseConnection.USERNAME=oldUser;DatabaseConnection.PASSWORD=oldPassword;}
    }
    @Test void firstAdministratorCreationIsSerialized() throws Exception {
        ItSupport.truncate("Returns","Exchanges","SessionTransactions","Transactions","BusinessSessions","SupervisorCodes","Product","Account","Staff");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->new UserService().bootstrapAdmin("One","one@example.test","Password123!"));
            var b=pool.submit(()->new UserService().bootstrapAdmin("Two","two@example.test","Password123!"));
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertEquals(1,number("SELECT COUNT(*) FROM Staff"));
        } finally {pool.shutdownNow();}
    }
    boolean sale(int qty) {
        return new TransactionService().processTransaction(2,1,FXCollections.observableArrayList(new CartItem(1,"Item",new BigDecimal("1.00"),qty)),new TransactionService.PaymentInfo("Cash",BigDecimal.valueOf(qty).setScale(2),BigDecimal.ZERO));
    }
    int quantity(int id) throws Exception{return number("SELECT Quantity FROM Product WHERE ProductID="+id);}
    int number(String sql) throws Exception {try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
    void sql(String sql) throws SQLException {try(Connection c=DatabaseConnection.getConnection();Statement s=c.createStatement()){s.executeUpdate(sql);}}
}

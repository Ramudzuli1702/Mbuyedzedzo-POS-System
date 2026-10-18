package com.pos.services;

import com.google.gson.*;
import com.pos.database.CompanionSchema;
import com.pos.utils.PasswordUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Creates/drops its own random schema on a disposable server; never loads POS connection settings. */
@EnabledIfSystemProperty(named="pos.companion.it.port",matches="[0-9]+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompanionIT {
    String rootUrl,url,schema,hash;
    StocktakeService service;
    MobileReceiptService receipts;
    @BeforeAll void database() throws Exception {
        int port=Integer.parseInt(System.getProperty("pos.companion.it.port"));assertTrue(port>1024&&port<65536&&port!=3306);
        rootUrl="jdbc:mysql://127.0.0.1:"+port+"/?useSSL=false&allowPublicKeyRetrieval=true";
        schema="companion_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=DriverManager.getConnection(rootUrl,"root","");Statement s=c.createStatement()){s.executeUpdate("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");}
        url=rootUrl.replace("/?","/"+schema+"?");hash=PasswordUtil.hashPassword("Stocktake!123");
        service=new StocktakeService(this::open);receipts=new MobileReceiptService(this::open);
        try(Connection c=open();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE Staff (StaffID INT PRIMARY KEY, FullNames VARCHAR(100), EmailAddress VARCHAR(255), UserType VARCHAR(20), UserPassword VARCHAR(255), Status VARCHAR(20))");
            s.executeUpdate("CREATE TABLE Category (CategoryID INT PRIMARY KEY,CategoryName VARCHAR(100))");
            s.executeUpdate("CREATE TABLE Product (ProductID INT PRIMARY KEY,ProductName VARCHAR(100),BarCode VARCHAR(100),CategoryID INT,Quantity INT NOT NULL)");
            CompanionSchema.ensure(c);CompanionSchema.ensure(c); // Upgrade and repeat launch are both safe.
        }
    }
    Connection open() throws SQLException{return DriverManager.getConnection(url,"root","");}
    @BeforeEach void fixture() throws Exception {
        try(Connection c=open();Statement s=c.createStatement()){
            for(String table:List.of("StocktakeLine","Stocktake","MobileReceiptOutbox","Product","Staff","Category"))s.executeUpdate("DELETE FROM "+table);
            s.executeUpdate("INSERT INTO Category VALUES (1,'Groceries')");
            s.executeUpdate("INSERT INTO Product (ProductID,ProductName,BarCode,CategoryID,Quantity) VALUES (1,'Test Bread','00001234-A',1,10),(2,'Test Milk','0123456789012',1,20)");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO Staff VALUES (?,?,?,?,?,?)")){
                for(int id=1;id<=3;id++){p.setInt(1,id);p.setString(2,id==2?"Test Manager":"Test Cashier");p.setString(3,id==1?"stock.cashier@example.test":"staff"+id+"@example.test");p.setString(4,id==2?"Manager":"Cashier");p.setString(5,hash);p.setString(6,id==3?"Inactive":"Active");p.executeUpdate();}
            }
        }
    }
    @AfterAll void cleanup() throws Exception {
        if(schema!=null&&schema.matches("companion_test_[a-f0-9]{32}"))try(Connection c=DriverManager.getConnection(rootUrl,"root","");Statement s=c.createStatement()){s.executeUpdate("DROP DATABASE `"+schema+"`");}
    }
    int quantity(int id) throws Exception {try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT Quantity FROM Product WHERE ProductID=?")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}}
    String start() throws Exception {String id=UUID.randomUUID().toString();service.start(id,1);return id;}
    @Test void countsWaitForApprovalAndUncountedProductsStayUntouched() throws Exception {
        String id=start();service.submit(id,1,List.of(new StocktakeService.Count(1,0)),"Empty shelf");assertEquals(10,quantity(1));
        assertThrows(StocktakeService.Rejected.class,()->service.review(id,1,true,""));assertEquals(10,quantity(1));
        StocktakeService.CountSheet reviewed=service.review(id,2,true,"Verified");assertEquals("Approved",reviewed.status());assertEquals("Test Manager",reviewed.reviewer());assertEquals(0,quantity(1));assertEquals(20,quantity(2));
        assertThrows(StocktakeService.Rejected.class,()->service.review(id,2,true,""));
    }
    @Test void identicalCountsCanBeApprovedAndRepeatedSubmissionsAreIdempotent() throws Exception {
        String id=start();service.submit(id,1,List.of(new StocktakeService.Count(1,10)),"");
        assertEquals(Integer.valueOf(10),service.submit(id,1,List.of(new StocktakeService.Count(1,99)),"retry").items().get(0).counted());
        service.review(id,2,true,"");assertEquals(10,quantity(1));
    }
    @Test void interveningStockMovementsRollBackEveryCorrectionEvenIfStockReturnsToOriginalQuantity() throws Exception {
        String id=start();service.submit(id,1,List.of(new StocktakeService.Count(1,7),new StocktakeService.Count(2,19)),"");
        try(Connection c=open();Statement s=c.createStatement()){s.executeUpdate("UPDATE Product SET Quantity=18 WHERE ProductID=2");s.executeUpdate("UPDATE Product SET Quantity=20 WHERE ProductID=2");}
        StocktakeService.Rejected error=assertThrows(StocktakeService.Rejected.class,()->service.review(id,2,true,""));assertEquals("STOCK_CHANGED",error.code);
        assertEquals(10,quantity(1));assertEquals(20,quantity(2));assertEquals("Pending",service.resume(id,1).status());
        service.review(id,2,false,"Stock moved; please recount");assertEquals(10,quantity(1));
    }
    @Test void snapshotOwnershipAndActiveStaffAreEnforced() throws Exception {
        String id=start();assertThrows(StocktakeService.Rejected.class,()->service.resume(id,2));
        assertThrows(StocktakeService.Rejected.class,()->service.submit(id,2,List.of(new StocktakeService.Count(1,3)),""));
        try(Connection c=open();Statement s=c.createStatement()){s.executeUpdate("UPDATE Staff SET Status='Inactive' WHERE StaffID=1");}
        assertThrows(StocktakeService.Rejected.class,()->service.submit(id,1,List.of(new StocktakeService.Count(1,3)),""));
    }
    @Test void authenticationRequiresOneTimeProofAndDoesNotTrustClientStaffId() throws Exception {
        StocktakeProtocol protocol=new StocktakeProtocol(service);
        JsonObject missing=new JsonObject();missing.addProperty("type","stocktake_start");missing.addProperty("stocktakeId",UUID.randomUUID().toString());
        assertEquals("AUTH_REQUIRED",protocol.handle(missing).get("code").getAsString());
        JsonObject challenge=challenge(),data=protocol.handle(challenge).getAsJsonObject("data");JsonObject login=login(data);
        String token=protocol.handle(login).getAsJsonObject("data").get("token").getAsString();assertFalse(protocol.handle(login).get("ok").getAsBoolean());
        missing.addProperty("token",token);missing.addProperty("staffId",2);
        JsonObject sheet=protocol.handle(missing).getAsJsonObject("data");assertEquals(1,sheet.get("staffId").getAsInt());
    }
    @Test void desktopDraftsResumeAndExcelImportWaitsForApproval() throws Exception {
        String id=start();service.saveDraft(id,1,List.of(new StocktakeService.Count(1,0)),"Desktop shelf count");
        assertEquals(10,quantity(1));assertTrue(service.listOwn(2).isEmpty());
        assertEquals(id,service.listOwn(1).get(0).id());assertEquals(Integer.valueOf(0),service.resume(id,1).items().get(0).counted());
        Path file=Files.createTempFile("stocktake-integration-",".xlsx");
        try {
            StocktakeExcelService excel=new StocktakeExcelService();excel.exportToExcel(service.resume(id,1),file.toFile());
            var plan=excel.analyze(file.toFile(),service.resume(id,1));assertTrue(plan.valid());
            service.saveDraft(id,1,plan.counts(),"Imported count");assertEquals(10,quantity(1));
            service.submit(id,1,plan.counts(),"Imported count");
            assertThrows(StocktakeService.Rejected.class,()->service.saveDraft(id,1,List.of(),"Too late"));
            service.review(id,2,true,"Verified spreadsheet");assertEquals(0,quantity(1));assertEquals(20,quantity(2));
        }finally{Files.deleteIfExists(file);}
    }
    @Test void invalidDraftSaveRollsBackAndEnforcesOwnership() throws Exception {
        String id=start();service.saveDraft(id,1,List.of(new StocktakeService.Count(1,7)),"Saved");
        assertThrows(StocktakeService.Rejected.class,()->service.saveDraft(id,2,List.of(),"Not mine"));
        assertThrows(StocktakeService.Rejected.class,()->service.saveDraft(id,1,List.of(new StocktakeService.Count(999,0)),"Invalid"));
        assertEquals(Integer.valueOf(7),service.resume(id,1).items().get(0).counted());assertEquals("Saved",service.resume(id,1).notes());
        service.saveDraft(id,1,List.of(),"Cleared");assertNull(service.resume(id,1).items().get(0).counted());assertEquals(10,quantity(1));
    }
    JsonObject challenge(){JsonObject j=new JsonObject();j.addProperty("type","auth_challenge");j.addProperty("email","stock.cashier@example.test");return j;}
    @Test void generalSessionLogoutAndPasswordChangesRevokeAccess() throws Exception {
        StocktakeProtocol protocol=new StocktakeProtocol(service);
        JsonObject data=protocol.handle(challenge()).getAsJsonObject("data");
        String token=protocol.handle(login(data)).getAsJsonObject("data").get("token").getAsString();
        JsonObject request=new JsonObject();request.addProperty("token",token);assertDoesNotThrow(()->protocol.requireSession(request));
        JsonObject logout=new JsonObject();logout.addProperty("type","auth_logout");logout.addProperty("token",token);
        assertTrue(protocol.handle(logout).get("ok").getAsBoolean());assertFalse(protocol.isAuthenticated());assertThrows(StocktakeService.Rejected.class,()->protocol.requireSession(request));
        data=protocol.handle(challenge()).getAsJsonObject("data");token=protocol.handle(login(data)).getAsJsonObject("data").get("token").getAsString();request.addProperty("token",token);
        try(Connection c=open();Statement s=c.createStatement()){s.executeUpdate("UPDATE Staff SET UserPassword='changed' WHERE StaffID=1");}
        assertThrows(StocktakeService.Rejected.class,()->protocol.requireSession(request));assertFalse(protocol.isAuthenticated());
    }
    JsonObject login(JsonObject data) throws Exception {JsonObject j=new JsonObject();j.addProperty("type","auth_login");j.add("challengeId",data.get("challengeId"));j.addProperty("proof",Base64.getEncoder().encodeToString(StocktakeProtocol.proof(hash,data.get("nonce").getAsString(),data.get("challengeId").getAsString())));return j;}
    @Test void realSocketPreservesBarcodesAndResendsReceiptsUntilAcknowledged() throws Exception {
        BlockingQueue<String> scanned=new LinkedBlockingQueue<>();WiFiHandler server=new WiFiHandler(service,receipts,()->List.of("Groceries"),0);
        assertTrue(server.startListening(scanned::add,p->{}));String receiptId;
        try {
            try(Socket phone=new Socket("127.0.0.1",server.getPort())){
                phone.setSoTimeout(10000);BufferedReader in=reader(phone);PrintWriter out=writer(phone);receive(in,"connected");
                JsonObject unsigned=new JsonObject();unsigned.addProperty("type","scan");unsigned.addProperty("data","UNSIGNED");out.println(unsigned);
                assertEquals("AUTH_REQUIRED",receive(in,"error").get("code").getAsString());assertNull(scanned.poll(100,TimeUnit.MILLISECONDS));
                unsigned.addProperty("type","add_product");unsigned.add("data",new JsonObject());out.println(unsigned);
                assertEquals("AUTH_REQUIRED",receive(in,"error").get("code").getAsString());
                server.sendReceipt("Queued before staff sign-in\nTOTAL R1.00");
                phone.setSoTimeout(250);assertThrows(SocketTimeoutException.class,()->in.readLine());phone.setSoTimeout(10000);
                JsonObject challenge=challenge();challenge.addProperty("requestId","challenge");out.println(challenge);
                JsonObject data=receive(in,"response").getAsJsonObject("data");JsonObject proof=login(data);proof.addProperty("requestId","login");out.println(proof);
                JsonObject signedIn=receive(in,"response");assertTrue(signedIn.get("ok").getAsBoolean());String token=signedIn.getAsJsonObject("data").get("token").getAsString();receive(in,"categories");
                JsonObject queued=receive(in,"receipt");JsonObject saved=new JsonObject();saved.addProperty("type","receipt_ack");saved.addProperty("id",queued.get("id").getAsString());saved.addProperty("token",token);out.println(saved);
                JsonObject scan=new JsonObject();scan.addProperty("type","scan");scan.addProperty("data","00001234-A");scan.addProperty("token",token);out.println(scan);receive(in,"scan_ack");assertEquals("00001234-A",scanned.poll(3,TimeUnit.SECONDS));
                server.sendReceipt("Date: 2026-10-18\nCashier: Test\nTOTAL   R12.50\nUnicode é and \"quotes\"\n");
                JsonObject message=receive(in,"receipt");receiptId=message.get("id").getAsString();assertTrue(message.get("text").getAsString().contains("Unicode é"));
            }
            long until=System.currentTimeMillis()+5000;while(server.isConnected()&&System.currentTimeMillis()<until)Thread.sleep(20);
            try(Socket phone=new Socket("127.0.0.1",server.getPort())){
                phone.setSoTimeout(10000);BufferedReader in=reader(phone);PrintWriter out=writer(phone);receive(in,"connected");
                JsonObject challenge=challenge();challenge.addProperty("requestId","rechallenge");out.println(challenge);
                JsonObject proof=login(receive(in,"response").getAsJsonObject("data"));proof.addProperty("requestId","relogin");out.println(proof);
                String token=receive(in,"response").getAsJsonObject("data").get("token").getAsString();receive(in,"categories");
                JsonObject resent=receive(in,"receipt");assertEquals(receiptId,resent.get("id").getAsString());
                JsonObject ack=new JsonObject();ack.addProperty("type","receipt_ack");ack.addProperty("id",receiptId);ack.addProperty("token",token);out.println(ack);
                long deadline=System.currentTimeMillis()+5000;while(!receipts.pending().isEmpty()&&System.currentTimeMillis()<deadline)Thread.sleep(20);
                assertTrue(new MobileReceiptService(this::open).pending().isEmpty());
            }
        } finally {server.stopListening();}
    }
    static BufferedReader reader(Socket socket) throws Exception{return new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));}
    static PrintWriter writer(Socket socket) throws Exception{return new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);}
    static JsonObject receive(BufferedReader input,String type) throws Exception {for(int n=0;n<20;n++){JsonObject j=JsonParser.parseString(input.readLine()).getAsJsonObject();if(type.equals(j.get("type").getAsString()))return j;}throw new AssertionError("Message not received: "+type);}

    @Test @EnabledIfSystemProperty(named="pos.companion.ui",matches="true")
    void emulatorWorkflow() throws Exception {
        WiFiHandler server=new WiFiHandler(service,receipts,()->List.of("Groceries"),0);assertTrue(server.startListening(s->{},p->{}));
        Path endpoint=Path.of("target/companion-ui-endpoint.txt"),finish=Path.of("target/companion-ui-finish.txt");Files.deleteIfExists(finish);
        Files.writeString(endpoint,"10.0.2.2:"+server.getPort());
        try {
            server.sendReceipt("SALES RECEIPT\nDate: 2026-10-18 12:00:00\nCashier: Test Manager\nItem          Qty Amount\nTest Bread\n  1 x 12.50             R12.50\nSubtotal               R12.50\nTOTAL                  R12.50\n");
              long until=System.currentTimeMillis()+Long.getLong("pos.companion.ui.timeoutMillis",300000L);StocktakeService.CountSheet pending=null;
            while(System.currentTimeMillis()<until){pending=service.list(2).stream().filter(s->"Pending".equals(s.status())).findFirst().orElse(null);if(pending!=null)break;Thread.sleep(500);}
            assertNotNull(pending,"No stocktake was submitted by the emulator");
            assertEquals(Integer.valueOf(7),pending.items().get(0).counted());service.review(pending.id(),2,true,"Verified in integration test");assertEquals(7,quantity(1));
            JsonObject status=new JsonObject();status.addProperty("type","stocktake_status");status.addProperty("stocktakeId",pending.id());status.addProperty("status","Approved");status.addProperty("reviewNotes","Verified in integration test");server.sendData(status.toString());
            Files.writeString(Path.of("target/companion-ui-approved.txt"),pending.id());
            until=System.currentTimeMillis()+120000;while(!Files.exists(finish)&&System.currentTimeMillis()<until)Thread.sleep(500);
        } finally {server.stopListening();Files.deleteIfExists(endpoint);}
    }
    @Test @EnabledIfSystemProperty(named="pos.companion.desktop.ui",matches="true")
    void desktopStocktakeViewLoadsSavedCountsAndExportsControls() throws Exception {
        String id=start();service.saveDraft(id,1,List.of(new StocktakeService.Count(1,7)),"Desktop test count");
        CompletableFuture<javafx.scene.layout.BorderPane> rendered=new CompletableFuture<>();
        javafx.application.Platform.startup(()->{});
        javafx.application.Platform.setImplicitExit(false);
        try {
            javafx.application.Platform.runLater(()->{
                try {
                    var user=new com.pos.models.User(1,"Test Cashier","stock.cashier@example.test","Cashier","Active");
                    var root=new com.pos.views.StocktakingView(user,service).getView();
                    new javafx.scene.Scene(root,1200,800);root.applyCss();root.layout();rendered.complete(root);
                }catch(Throwable e){rendered.completeExceptionally(e);}
            });
            var root=rendered.get(20,TimeUnit.SECONDS);boolean loaded=false;
            for(int i=0;i<100;i++){
                CompletableFuture<Boolean> check=new CompletableFuture<>();
                javafx.application.Platform.runLater(()->{
                    try {
                        var table=(javafx.scene.control.TableView<?>)root.lookup("#stocktakeProducts");
                        check.complete(table.getItems().size()==2&&!root.lookup("#stocktakeSubmit").isDisable());
                    }catch(Throwable e){check.completeExceptionally(e);}
                });
                if(check.get(5,TimeUnit.SECONDS)){loaded=true;break;}Thread.sleep(100);
            }
            assertTrue(loaded,"Saved draft did not load into desktop screen");
            CompletableFuture<Void> captured=new CompletableFuture<>();
            javafx.application.Platform.runLater(()->{
                try {
                    assertFalse(root.lookup("#stocktakeImport").isDisable());assertFalse(root.lookup("#stocktakeExport").isDisable());
                    assertTrue(((javafx.scene.control.Label)root.lookup("#stocktakeSummary")).getText().startsWith("1 / 2"));
                    root.applyCss();root.layout();
                    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",Path.of("target/desktop-stocktaking.png").toFile());
                    var form=com.pos.components.Ui.formGrid();var name=new javafx.scene.control.TextField("Test Bread");
                    com.pos.components.Ui.formField(form,0,"Product name",name);
                    var barcode=new javafx.scene.image.ImageView(com.pos.utils.BarcodeUtil.getBarcodeImage(com.pos.utils.BarcodeUtil.generateBarcode("00001234-A")));
                    var editor=com.pos.components.Ui.editor("Product details","Test Bread",new javafx.scene.layout.VBox(16,com.pos.components.Ui.section("Product information",form),com.pos.components.Ui.section("Barcode label",barcode)),()->{throw new IllegalArgumentException("Enter a valid quantity.");});
                    editor.initStyle(javafx.stage.StageStyle.UNDECORATED);editor.getDialogPane().getScene().getWindow().setOpacity(0);editor.show();
                    var pane=editor.getDialogPane();pane.applyCss();pane.layout();
                    var save=pane.lookupButton(pane.getButtonTypes().get(0));javafx.event.Event.fireEvent(save,new javafx.event.ActionEvent());
                    assertTrue(pane.lookupAll(".label").stream().filter(n->n instanceof javafx.scene.control.Label).map(n->((javafx.scene.control.Label)n).getText()).anyMatch(t->t.equals("Enter a valid quantity.")));
                    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(pane.snapshot(null,null),null),"png",Path.of("target/product-detail-design.png").toFile());
                    editor.close();
                    captured.complete(null);
                }catch(Throwable e){captured.completeExceptionally(e);}
            });
            captured.get(20,TimeUnit.SECONDS);
        }finally{javafx.application.Platform.exit();}
    }
}

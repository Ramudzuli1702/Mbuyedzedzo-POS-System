package com.pos.services;

import com.google.gson.*;
import com.pos.models.Product;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One paired scanner connection, UTF-8 line-delimited JSON, no credential/payload logging. */
public class WiFiHandler {
    private static final Logger log=LoggerFactory.getLogger(WiFiHandler.class);
    private static final int SIGN_IN_TIMEOUT=120000;
    private static WiFiHandler instance;
    public static synchronized WiFiHandler getInstance() { if(instance==null)instance=new WiFiHandler();return instance; }
    private final Gson gson=new Gson();
    private final MobileReceiptService receipts;
    private final StocktakeService stocktakes;
    private final Supplier<List<String>> categoryNames;
    private final int basePort;
    private final boolean secure;
    private ScannerTls tls;
    private final java.util.concurrent.Semaphore pendingConnections=new java.util.concurrent.Semaphore(4);
    private volatile long desktopEpoch;
    public long desktopEpoch(){return desktopEpoch;}
    public WiFiHandler() { this(new StocktakeService(),new MobileReceiptService(),()->new CategoryService().getAllCategories(),8888,true); }
    WiFiHandler(StocktakeService stocktakes,MobileReceiptService receipts,Supplier<List<String>> categories,int basePort) {
        this(stocktakes,receipts,categories,basePort,false); // Isolated protocol tests only.
    }
    WiFiHandler(StocktakeService stocktakes,MobileReceiptService receipts,Supplier<List<String>> categories,int basePort,boolean secure) {
        this.stocktakes=stocktakes;this.receipts=receipts;this.categoryNames=categories;this.basePort=basePort;this.secure=secure;
    }
    WiFiHandler(StocktakeService stocktakes,MobileReceiptService receipts,Supplier<List<String>> categories,int basePort,ScannerTls tls) {
        this(stocktakes,receipts,categories,basePort,true);this.tls=Objects.requireNonNull(tls);
    }
    private final Set<String> receiptsInFlight=ConcurrentHashMap.newKeySet();
    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;
    private volatile PrintWriter output;
    private volatile boolean running,connected;
    private volatile Consumer<String> onScanReceived;
    private volatile Consumer<Product> onProductReceived;
    private int actualPort=-1;
    private volatile StocktakeProtocol activeSession;

    public synchronized boolean startListening(Consumer<String> scan,Consumer<Product> product) {
        onScanReceived=scan;onProductReceived=product;
        if(running)return true;
        if(secure && tls==null)try {tls=new ScannerTls(ScannerTls.defaultIdentity());}
        catch(Exception e){log.error("Secure scanner identity could not be opened. Scanner remains offline.",e);return false;}
        for(int port=basePort;port<basePort+10;port++) try {
            serverSocket=secure?tls.socket(port):new ServerSocket(port);actualPort=port;running=true;
            actualPort=serverSocket.getLocalPort();
            Thread thread=new Thread(this::acceptConnections,"WiFi-Server");thread.setDaemon(true);thread.start();return true;
        } catch(BindException e) { /* Try the next pairing port. */ }
        catch(IOException e) { log.warn("Scanner server could not start",e);return false; }
        return false;
    }
    private void acceptConnections() {
        while(running) {
            try {
                ServerSocket listener=serverSocket;if(listener==null)break;
                Socket socket=listener.accept();
                if(!pendingConnections.tryAcquire()){socket.close();continue;}
                Thread worker=new Thread(()->{
                    try {serve(socket);}finally{pendingConnections.release();}
                },"WiFi-Client");worker.setDaemon(true);worker.start();
            }catch(IOException e){if(running)log.info("Scanner listener closed: {}",e.getClass().getSimpleName());}
        }
    }
    private void serve(Socket socket) {
            try {
                socket.setSoTimeout(10000);
                if(secure)((javax.net.ssl.SSLSocket)socket).startHandshake();
                StocktakeProtocol stocktake=new StocktakeProtocol(stocktakes);
                synchronized(this) {
                    if(!running || connected && activeSession!=null && activeSession.isAuthenticated()){socket.close();return;}
                    closeConnection();clientSocket=socket;
                socket.setKeepAlive(true);socket.setTcpNoDelay(true);
                output=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
                connected=true;receiptsInFlight.clear();
                activeSession=stocktake;
                }
                socket.setSoTimeout(SIGN_IN_TIMEOUT);
                JsonObject hello=new JsonObject();hello.addProperty("type","connected");hello.addProperty("protocolVersion",4);hello.addProperty("requiresLogin",true);
                hello.addProperty("message","Connected to "+com.pos.Branding.BRAND_NAME);sendData(gson.toJson(hello));
                try(BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;
                    while(connected && clientSocket==socket && (line=readFrame(input,8*1024*1024))!=null) {
                        synchronized(this) {
                        if(clientSocket!=socket)break;
                        try {
                            JsonElement parsed=JsonParser.parseString(line);
                            if(parsed.isJsonArray())parsed=parsed.getAsJsonArray().get(0); // Legacy phone wrapper.
                            JsonObject message=parsed.getAsJsonObject();
                            String type=message.get("type").getAsString();
                            if(type.startsWith("stocktake_") || type.startsWith("auth_")) {
                                JsonObject response=stocktake.handle(message);sendData(gson.toJson(response));
                                socket.setSoTimeout(stocktake.isAuthenticated()?30*60*1000:SIGN_IN_TIMEOUT);
                                if(type.equals("auth_logout") || type.equals("stocktake_logout"))receiptsInFlight.clear();
                                if((type.equals("auth_login") || type.equals("stocktake_login")) && response.get("ok").getAsBoolean()) { sendCategories();drainReceipts(); }
                            } else {
                            stocktake.requireSession(message);
                            switch(type) {
                                case "request_categories" -> sendCategories();
                                case "scan" -> {
                                    String code=message.get("data").getAsString();
                                    Consumer<String> callback=onScanReceived;
                                    if(callback!=null) { callback.accept(code);reply("scan_ack","Scan received"); }
                                    else reply("error","Open Sales on the desktop to scan products.");
                                }
                                case "add_product" -> addProduct(message.getAsJsonObject("data"));
                                case "receipt_ack" -> {
                                    String id=message.get("id").getAsString();
                                    if(receiptsInFlight.remove(id))receipts.acknowledge(id);
                                    drainReceipts();
                                }
                                default -> reply("error","Unsupported scanner request. Update the desktop and phone together.");
                            }
                            }
                        } catch(StocktakeService.Rejected e) {
                            JsonObject error=new JsonObject();error.addProperty("type","error");error.addProperty("code",e.code);error.addProperty("message",e.getMessage());sendData(error.toString());
                        } catch(Exception e) { reply("error","The desktop could not process this request. Please retry."); }
                        }
                    }
                }
            } catch(IOException e) {
                if(running) {
                    if(e instanceof javax.net.ssl.SSLException)
                        log.info("Secure scanner connection failed ({}). Use the updated Android app and scan this desktop's current connection QR code; IP-only clients are incompatible.",e.getClass().getSimpleName());
                    else if(e instanceof SocketTimeoutException)
                        log.info("Scanner connection timed out. Reconnect and sign in again.");
                    else log.info("Scanner connection closed: {}",e.getClass().getSimpleName());
                }
            }
            finally { synchronized(this){if(clientSocket==socket)closeConnection();}try{socket.close();}catch(IOException ignored){} }
    }
    public static String readFrame(Reader reader,int limit) throws IOException {
        StringBuilder result=new StringBuilder();int ch;
        while((ch=reader.read())!=-1) {
            if(ch=='\n')return result.toString();
            if(ch!='\r')result.append((char)ch);
            if(result.length()>limit)throw new IOException("Scanner frame too large");
        }
        return result.isEmpty()?null:result.toString();
    }
    private void sendCategories() {
        try {
            JsonObject message=new JsonObject();message.addProperty("type","categories");
            message.add("data",gson.toJsonTree(categoryNames.get()));sendData(gson.toJson(message));
        } catch(Exception e) { reply("error","Categories could not be loaded. Please retry."); }
    }
    private void addProduct(JsonObject data) {
        Consumer<Product> callback=onProductReceived;
        if(callback==null) { reply("error","Open Inventory on the desktop to add products.");return; }
        Product product=new Product();product.setProductName(data.get("name").getAsString());
        product.setBarCode(data.get("barcode").getAsString());product.setQuantity(data.get("quantity").getAsBigDecimal().intValueExact());
        product.setCategoryName(data.has("category")?data.get("category").getAsString():null);
        product.setPrice(money(data,"price"));product.setCostPrice(money(data,"costPrice"));
        ProductService.validate(product);
        callback.accept(product);reply("product_ack","Product received by desktop inventory.");
    }
    private BigDecimal money(JsonObject data,String key) {
        return !data.has(key)||data.get(key).isJsonNull()?BigDecimal.ZERO:new BigDecimal(data.get(key).getAsString().replace(',','.'));
    }
    private void reply(String type,String message) {
        JsonObject json=new JsonObject();json.addProperty("type",type);json.addProperty("message",message);sendData(gson.toJson(json));
    }
    public void sendReceipt(String text) {
        try { receipts.queue(text);drainReceipts(); }
        catch(Exception e) { log.warn("Phone receipt could not be queued; the saved desktop receipt is still available",e); }
    }
    private synchronized void drainReceipts() {
        if(!connected || activeSession==null || !activeSession.isAuthenticated())return;
        try {
            for(JsonObject message:receipts.pending()) {
                String id=message.get("id").getAsString();
                if(receiptsInFlight.add(id))sendData(gson.toJson(message));
            }
        } catch(Exception e) { log.warn("Pending phone receipts could not be loaded",e); }
    }
    public synchronized void sendData(String data) {
        if(data.contains("\"stocktake_status\"") && (activeSession==null || !activeSession.isAuthenticated()))return;
        PrintWriter writer=output;
        if(!connected || writer==null)return;
        writer.println(data);writer.flush();
        if(writer.checkError())closeConnection();
    }
    public void closeConnection() {
        activeSession=null;
        connected=false;Socket socket=clientSocket;clientSocket=null;
        try { if(socket!=null)socket.close(); } catch(IOException ignored) {}
        output=null;receiptsInFlight.clear();
    }
    public void stopListening() {
        running=false;closeConnection();
        try { if(serverSocket!=null)serverSocket.close(); } catch(IOException ignored) {}
        serverSocket=null;
    }
    public synchronized void cleanup() { desktopEpoch++;stopListening();onScanReceived=null;onProductReceived=null; }
    public boolean isConnected() { return connected; }
    public boolean isServerRunning() { return running; }
    public int getPort() { return actualPort; }
    public String getStatus() { return connected?"Connected":running?"Waiting for phone":"Not connected"; }
    public void setProductCallback(Consumer<Product> callback) { onProductReceived=callback; }
    public void setScanCallback(Consumer<String> callback) { onScanReceived=callback; }
    public String getConnectionPayload() {
        if(secure){if(tls==null)throw new IllegalStateException("Start the secure scanner first.");return "POS-TLS:"+getLocalIP()+":"+actualPort+":"+tls.fingerprint();}
        return "POS:"+getLocalIP()+":"+actualPort;
    }
    public String getLocalIP() {
        try {
            for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if(!network.isUp() || network.isLoopback() || network.isVirtual())continue;
                for(InetAddress address:Collections.list(network.getInetAddresses()))
                    if(address instanceof Inet4Address && address.isSiteLocalAddress())return address.getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch(Exception e) { return "127.0.0.1"; }
    }
}

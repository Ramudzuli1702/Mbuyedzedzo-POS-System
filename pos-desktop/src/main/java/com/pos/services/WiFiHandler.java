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
    private static WiFiHandler instance;
    public static synchronized WiFiHandler getInstance() { if(instance==null)instance=new WiFiHandler();return instance; }
    private final Gson gson=new Gson();
    private final MobileReceiptService receipts;
    private final StocktakeService stocktakes;
    private final Supplier<List<String>> categoryNames;
    private final int basePort;
    public WiFiHandler() { this(new StocktakeService(),new MobileReceiptService(),()->new CategoryService().getAllCategories(),8888); }
    WiFiHandler(StocktakeService stocktakes,MobileReceiptService receipts,Supplier<List<String>> categories,int basePort) {
        this.stocktakes=stocktakes;this.receipts=receipts;this.categoryNames=categories;this.basePort=basePort;
    }
    private final Set<String> receiptsInFlight=ConcurrentHashMap.newKeySet();
    private volatile ServerSocket serverSocket;
    private volatile Socket clientSocket;
    private volatile PrintWriter output;
    private volatile boolean running,connected;
    private volatile Consumer<String> onScanReceived;
    private volatile Consumer<Product> onProductReceived;
    private int actualPort=-1;

    public synchronized boolean startListening(Consumer<String> scan,Consumer<Product> product) {
        onScanReceived=scan;onProductReceived=product;
        if(running)return true;
        for(int port=basePort;port<basePort+10;port++) try {
            serverSocket=new ServerSocket(port);actualPort=port;running=true;
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
                Socket socket=listener.accept();clientSocket=socket;
                socket.setKeepAlive(true);socket.setTcpNoDelay(true);
                output=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
                connected=true;receiptsInFlight.clear();
                StocktakeProtocol stocktake=new StocktakeProtocol(stocktakes); // Auth never survives a new socket.
                JsonObject hello=new JsonObject();hello.addProperty("type","connected");hello.addProperty("protocolVersion",2);
                hello.addProperty("message","Connected to "+com.pos.Branding.APP_NAME);sendData(gson.toJson(hello));
                sendCategories();drainReceipts();
                try(BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;
                    while(connected && (line=readFrame(input,8*1024*1024))!=null) {
                        try {
                            JsonElement parsed=JsonParser.parseString(line);
                            if(parsed.isJsonArray())parsed=parsed.getAsJsonArray().get(0); // Legacy phone wrapper.
                            JsonObject message=parsed.getAsJsonObject();
                            String type=message.get("type").getAsString();
                            if(type.startsWith("stocktake_")) sendData(gson.toJson(stocktake.handle(message)));
                            else switch(type) {
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
                        } catch(Exception e) { reply("error","The desktop could not process this request. Please retry."); }
                    }
                }
            } catch(IOException e) { if(running)log.info("Scanner connection closed: {}",e.getClass().getSimpleName()); }
            finally { closeConnection(); }
        }
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
        product.setBarCode(data.get("barcode").getAsString());product.setQuantity(data.get("quantity").getAsInt());
        product.setCategoryName(data.has("category")?data.get("category").getAsString():null);
        product.setPrice(money(data,"price"));product.setCostPrice(money(data,"costPrice"));
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
        if(!connected)return;
        try {
            for(JsonObject message:receipts.pending()) {
                String id=message.get("id").getAsString();
                if(receiptsInFlight.add(id))sendData(gson.toJson(message));
            }
        } catch(Exception e) { log.warn("Pending phone receipts could not be loaded",e); }
    }
    public synchronized void sendData(String data) {
        PrintWriter writer=output;
        if(!connected || writer==null)return;
        writer.println(data);writer.flush();
        if(writer.checkError())closeConnection();
    }
    public void closeConnection() {
        connected=false;Socket socket=clientSocket;clientSocket=null;
        try { if(socket!=null)socket.close(); } catch(IOException ignored) {}
        output=null;receiptsInFlight.clear();
    }
    public void stopListening() {
        running=false;closeConnection();
        try { if(serverSocket!=null)serverSocket.close(); } catch(IOException ignored) {}
        serverSocket=null;
    }
    public void cleanup() { stopListening();onScanReceived=null;onProductReceived=null; }
    public boolean isConnected() { return connected; }
    public boolean isServerRunning() { return running; }
    public int getPort() { return actualPort; }
    public String getStatus() { return connected?"Connected":running?"Waiting for phone":"Not connected"; }
    public void setProductCallback(Consumer<Product> callback) { onProductReceived=callback; }
    public void setScanCallback(Consumer<String> callback) { onScanReceived=callback; }
    public String getConnectionPayload() { return "POS:"+getLocalIP()+":"+actualPort; }
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

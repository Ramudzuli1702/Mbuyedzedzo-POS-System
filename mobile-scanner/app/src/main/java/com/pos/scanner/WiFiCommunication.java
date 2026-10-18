package com.pos.scanner;

import android.content.*;
import android.os.*;
import android.util.Base64;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** App-scoped connection; navigation does not disconnect the scanner. */
public final class WiFiCommunication {
    public static final String ACTION_CATEGORIES_UPDATED="CATEGORIES_UPDATED";
    public static final String ACTION_STOCKTAKE_STATUS="STOCKTAKE_STATUS";
    private static WiFiCommunication instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Gson gson=new Gson();
    private final Object connectionLock=new Object(),writeLock=new Object();
    private final ExecutorService sender=Executors.newSingleThreadExecutor();
    private final Map<String,Callback> requests=new ConcurrentHashMap<>();
    private volatile Socket socket;
    private volatile PrintWriter output;
    private volatile boolean connected,reconnect;
    private volatile int generation,attempts;
    private String host,address;private int port;
    private volatile String token,staffName;
    private volatile int staffId;
    private volatile List<String> categories=Collections.emptyList();
    public interface Callback { void complete(JsonObject response); }
    private WiFiCommunication(Context context){this.context=context.getApplicationContext();}
    public static synchronized WiFiCommunication getInstance(Context context){if(instance==null)instance=new WiFiCommunication(context);return instance;}

    public boolean connect(String endpoint) {
        if(endpoint==null)return false;
        String[] parts=endpoint.trim().split(":",-1);
        if(parts.length>2||parts[0].trim().isEmpty())return false;
        int requestedPort;
        try { requestedPort=parts.length==2?Integer.parseInt(parts[1]):8888;if(requestedPort<1||requestedPort>65535)return false; }
        catch(NumberFormatException e){return false;}
        if(connected&&endpoint.trim().equals(address))return true;
        closeConnection();
        host=parts[0].trim();port=requestedPort;address=host+":"+port;attempts=0;reconnect=true;
        int current=++generation;open(current);return true;
    }
    private void open(int current) {
        new Thread(()-> {
            Socket opened=new Socket();
            try {
                opened.connect(new InetSocketAddress(host,port),7000);opened.setKeepAlive(true);opened.setTcpNoDelay(true);
                synchronized(connectionLock) {
                    if(current!=generation||!reconnect){opened.close();return;}
                    socket=opened;output=new PrintWriter(new OutputStreamWriter(opened.getOutputStream(),StandardCharsets.UTF_8),true);
                    connected=true;attempts=0;categories=Collections.emptyList();clearAuth();
                }
                notifyConnection(true);
                IncomingFrames frames=new IncomingFrames();
                try(BufferedReader input=new BufferedReader(new InputStreamReader(opened.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;
                    while(current==generation&&(line=readLine(input))!=null) {
                        IncomingFrames.Frame frame=frames.accept(line);
                        if(frame.json!=null)handle(frame.json);
                        if(frame.receipt!=null)saveReceipt(Receipt.fromString(frame.receipt),false);
                    }
                }
            } catch(Exception ignored) { /* A single status event below handles failure and reconnect. */ }
            finally {
                try{opened.close();}catch(IOException ignored){}
                if(current==generation) {
                    connected=false;output=null;socket=null;clearAuth();failRequests("DISCONNECTED","Connection lost. Your stocktake draft is saved on this phone.");
                    notifyConnection(false);
                    if(reconnect&&attempts<5){int delay=2000*++attempts;main.postDelayed(()->{if(current==generation&&reconnect&&!connected)open(current);},delay);}
                }
            }
        },"POS-Connection").start();
    }
    private static String readLine(Reader input) throws IOException {
        StringBuilder line=new StringBuilder();int ch;
        while((ch=input.read())!=-1){if(ch=='\n')return line.toString();if(ch!='\r')line.append((char)ch);if(line.length()>8*1024*1024)throw new IOException("Message too large");}
        return line.length()==0?null:line.toString();
    }
    private void handle(String text) {
        try {
            JsonElement parsed=JsonParser.parseString(text);if(parsed.isJsonArray())parsed=parsed.getAsJsonArray().get(0);
            JsonObject json=parsed.getAsJsonObject();String type=json.get("type").getAsString();
            switch(type) {
                case "response": {
                    if(json.has("code")&&"AUTH_REQUIRED".equals(json.get("code").getAsString()))clearAuth();
                    String id=json.get("requestId").getAsString();Callback callback=requests.remove(id);
                    if(callback!=null)main.post(()->callback.complete(json));break;
                }
                case "categories": {
                    List<String> values=new ArrayList<>();for(JsonElement e:json.getAsJsonArray("data"))values.add(e.getAsString());
                    categories=Collections.unmodifiableList(values);broadcast(new Intent(ACTION_CATEGORIES_UPDATED));break;
                }
                case "receipt": {
                    Receipt receipt=Receipt.fromString(json.get("text").getAsString());receipt.setReceiptId(json.get("id").getAsString());saveReceipt(receipt,true);break;
                }
                case "stocktake_status": {
                    Intent event=new Intent(ACTION_STOCKTAKE_STATUS);event.putExtra("message",json.toString());broadcast(event);break;
                }
                default: break;
            }
        } catch(RuntimeException ignored) { /* Ignore malformed server data, without logging receipts or credentials. */ }
    }
    private void saveReceipt(Receipt receipt,boolean acknowledge) {
        ReceiptStorage storage=ReceiptStorage.getInstance(context);boolean exists=storage.getReceiptById(receipt.getReceiptId())!=null;
        if(!storage.saveReceipt(receipt))return;
        if(acknowledge){JsonObject ack=new JsonObject();ack.addProperty("type","receipt_ack");ack.addProperty("id",receipt.getReceiptId());sendNow(ack.toString());}
        if(!exists){Intent event=new Intent("RECEIPT_RECEIVED");event.putExtra("receipt",receipt);broadcast(event);}
    }
    private void broadcast(Intent intent){LocalBroadcastManager.getInstance(context).sendBroadcast(intent);}
    private void notifyConnection(boolean connected){Intent event=new Intent("WIFI_CONNECTED");event.putExtra("connected",connected);broadcast(event);}
    private void clearAuth(){token=null;staffId=0;staffName=null;}
    public boolean isSignedIn(){return token!=null;}
    public int getStaffId(){return staffId;}
    public String getStaffName(){return staffName==null?"Staff":staffName;}
    public boolean isConnected(){return connected;}
    public String getServerAddress(){return address;}
    public List<String> getCategories(){return categories;}
    public void requestCategories(){JsonObject j=new JsonObject();j.addProperty("type","request_categories");sendData(j.toString());}
    public boolean sendScan(String code){JsonObject j=new JsonObject();j.addProperty("type","scan");j.addProperty("data",code);return sendNow(j.toString());}
    public void sendData(String text){sender.execute(()->sendNow(text));}
    private boolean sendNow(String text) {
        synchronized(writeLock) {
            PrintWriter writer=output;if(!connected||writer==null)return false;
            writer.println(text);writer.flush();
            if(writer.checkError()){Socket old=socket;try{if(old!=null)old.close();}catch(IOException ignored){}return false;}
            return true;
        }
    }
    public void request(JsonObject request,Callback callback) {
        String id=UUID.randomUUID().toString();request.addProperty("requestId",id);
        if(token!=null)request.addProperty("token",token);
        requests.put(id,callback);
        main.postDelayed(()-> {Callback waiting=requests.remove(id);if(waiting!=null)waiting.complete(error("TIMEOUT","The desktop did not respond. Check the connection and use the updated desktop."));},20000);
        sender.execute(()-> {if(!sendNow(request.toString())){Callback waiting=requests.remove(id);if(waiting!=null)main.post(()->waiting.complete(error("DISCONNECTED","Connect to the desktop to continue.")));}});
    }
    public void login(String email,char[] password,Callback callback) {
        clearAuth();JsonObject challenge=new JsonObject();challenge.addProperty("type","stocktake_challenge");challenge.addProperty("email",email);
        request(challenge,response-> {
            if(!response.get("ok").getAsBoolean()){Arrays.fill(password,'\0');callback.complete(response);return;}
            sender.execute(()-> {
                try {
                    JsonObject data=response.getAsJsonObject("data");
                    byte[] proof=PasswordProof.create(password,data.get("algorithm").getAsString(),data.get("iterations").getAsInt(),Base64.decode(data.get("salt").getAsString(),Base64.DEFAULT),data.get("nonce").getAsString(),data.get("challengeId").getAsString());
                    JsonObject login=new JsonObject();login.addProperty("type","stocktake_login");login.add("challengeId",data.get("challengeId"));login.addProperty("proof",Base64.encodeToString(proof,Base64.NO_WRAP));Arrays.fill(proof,(byte)0);
                    request(login,result-> {
                        if(result.get("ok").getAsBoolean()){JsonObject account=result.getAsJsonObject("data");token=account.get("token").getAsString();staffId=account.get("staffId").getAsInt();staffName=account.get("name").getAsString();}
                        callback.complete(result);
                    });
                } catch(Exception e){Arrays.fill(password,'\0');main.post(()->callback.complete(error("AUTH_FAILED","Could not complete sign-in. Check your password and update the desktop.")));}
            });
        });
    }
    public void logout(){if(token!=null){JsonObject j=new JsonObject();j.addProperty("type","stocktake_logout");request(j,r->{});}clearAuth();}
    public void closeConnection() {
        reconnect=false;++generation;connected=false;clearAuth();
        Socket old=socket;socket=null;output=null;try{if(old!=null)old.close();}catch(IOException ignored){}
        failRequests("DISCONNECTED","Connection closed. Your saved counts will be kept.");notifyConnection(false);
    }
    private void failRequests(String code,String message){for(String id:requests.keySet()){Callback cb=requests.remove(id);if(cb!=null)main.post(()->cb.complete(error(code,message)));}}
    public static JsonObject error(String code,String message){JsonObject j=new JsonObject();j.addProperty("ok",false);j.addProperty("code",code);j.addProperty("message",message);return j;}
}

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
    public static final String ACTION_AUTH_CHANGED="AUTH_CHANGED";
    private static WiFiCommunication instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Gson gson=new Gson();
    private final Object connectionLock=new Object(),writeLock=new Object();
    private final ExecutorService sender=Executors.newSingleThreadExecutor();
    private final Map<String,Callback> requests=new ConcurrentHashMap<>();
    private final Map<String,Integer> loginRequests=new ConcurrentHashMap<>();
    private volatile int authGeneration;
    private volatile Socket socket;
    private volatile PrintWriter output;
    private volatile boolean connected,reconnect;
    private volatile String connectionMessage="Connect to the POS desktop over WiFi";
    private volatile int generation,attempts;
    private String host,address,fingerprint;private int port;
    private volatile String token,staffName;
    private volatile long authenticatedUntil;
    private volatile int staffId;
    private volatile List<String> categories=Collections.emptyList();
    public interface Callback { void complete(JsonObject response); }
    private WiFiCommunication(Context context){this.context=context.getApplicationContext();}
    public static synchronized WiFiCommunication getInstance(Context context){if(instance==null)instance=new WiFiCommunication(context);return instance;}

    public boolean connect(String endpoint) {
        PairingEndpoint pairing=PairingEndpoint.parse(endpoint);
        if(pairing==null)return false;
        if(connected&&endpoint.trim().equals(address))return true;
        closeConnection();
        connectionMessage="Connecting to the desktop...";
        host=pairing.host;port=pairing.port;fingerprint=pairing.fingerprint;address=pairing.encoded;attempts=0;reconnect=true;
        int current=++generation;open(current);return true;
    }
    private void open(int current) {
        final String openingHost=host,openingFingerprint=fingerprint;final int openingPort=port;
        new Thread(()-> {
            Socket opened=null;
            boolean retry=true;
            try {
                javax.net.ssl.SSLSocket secure=(javax.net.ssl.SSLSocket)PinnedDesktop.factory(openingFingerprint).createSocket();opened=secure;
                secure.setEnabledProtocols(new String[]{"TLSv1.2"});
                opened.connect(new InetSocketAddress(openingHost,openingPort),7000);opened.setSoTimeout(10000);
                secure.startHandshake();opened.setKeepAlive(true);opened.setTcpNoDelay(true);
                try(BufferedReader input=new BufferedReader(new InputStreamReader(opened.getInputStream(),StandardCharsets.UTF_8))) {
                ScannerHandshake.requireSupported(readLine(input));
                opened.setSoTimeout(0);
                synchronized(connectionLock) {
                    if(current!=generation||!reconnect){opened.close();return;}
                    socket=opened;output=new PrintWriter(new OutputStreamWriter(opened.getOutputStream(),StandardCharsets.UTF_8),true);
                    connected=true;connectionMessage="Connected. Sign in to continue.";categories=Collections.emptyList();clearAuth();
                }
                notifyConnection(true);
                IncomingFrames frames=new IncomingFrames();
                    String line;
                    while(current==generation&&(line=readLine(input))!=null) {
                        IncomingFrames.Frame frame=frames.accept(line);
                        if(frame.json!=null)handle(frame.json);
                        if(frame.receipt!=null)saveReceipt(Receipt.fromString(frame.receipt),false);
                    }
                    if(current==generation)connectionMessage="The desktop closed the connection. Reconnect and sign in again.";
                }
            } catch(Exception failure) {
                retry=ConnectionFailure.retry(failure);
                if(current==generation)connectionMessage=ConnectionFailure.message(failure);
            }
            finally {
                try{if(opened!=null)opened.close();}catch(IOException ignored){}
                if(current==generation) {
                    ++authGeneration;loginRequests.clear();
                    connected=false;output=null;socket=null;clearAuth();failRequests("DISCONNECTED","Connection lost. Your stocktake draft is saved on this phone.");
                    notifyConnection(false);
                    if(retry&&reconnect&&attempts<5){int delay=2000*++attempts;main.postDelayed(()->{if(current==generation&&reconnect&&!connected)open(current);},delay);}
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
                    Integer loginAttempt=loginRequests.remove(id);
                    if(callback!=null&&json.has("ok")&&json.get("ok").getAsBoolean()&&json.has("data")&&json.get("data").isJsonObject()){
                        JsonObject account=json.getAsJsonObject("data");
                        if(account.has("token") && loginAttempt!=null && loginAttempt==authGeneration){
                            staffId=account.get("staffId").getAsInt();
                            ReceiptStorage.getInstance(context).setScope(fingerprint,staffId);
                            token=account.get("token").getAsString();authenticatedUntil=SystemClock.elapsedRealtime()+30*60*1000;staffName=account.get("name").getAsString();
                            attempts=0;
                        }
                    }
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
                case "error": {
                    if(json.has("code")&&"AUTH_REQUIRED".equals(json.get("code").getAsString()))clearAuth();break;
                }
                default: break;
            }
        } catch(RuntimeException ignored) { /* Ignore malformed server data, without logging receipts or credentials. */ }
    }
    private void saveReceipt(Receipt receipt,boolean acknowledge) {
        if(!isSignedIn())return;
        ReceiptStorage storage=ReceiptStorage.getInstance(context);boolean exists=storage.getReceiptById(receipt.getReceiptId())!=null;
        if(!storage.saveReceipt(receipt))return;
        if(acknowledge){JsonObject ack=new JsonObject();ack.addProperty("type","receipt_ack");ack.addProperty("id",receipt.getReceiptId());sendNow(ack.toString());}
        if(!exists){Intent event=new Intent("RECEIPT_RECEIVED");event.putExtra("receipt",receipt);broadcast(event);}
    }
    private void broadcast(Intent intent){LocalBroadcastManager.getInstance(context).sendBroadcast(intent);}
    private void notifyConnection(boolean connected){Intent event=new Intent("WIFI_CONNECTED");event.putExtra("connected",connected);broadcast(event);}
    private void clearAuth(){token=null;authenticatedUntil=0;staffId=0;staffName=null;ReceiptStorage.getInstance(context).clearScope();broadcast(new Intent(ACTION_AUTH_CHANGED));}
    public boolean isSignedIn(){if(token!=null&&SystemClock.elapsedRealtime()>=authenticatedUntil)clearAuth();return connected&&token!=null;}
    public int getStaffId(){return staffId;}
    public String getStaffName(){return staffName==null?"Staff":staffName;}
    public boolean isConnected(){return connected;}
    public String getConnectionMessage(){return connectionMessage;}
    public String getServerAddress(){return address;}
    public List<String> getCategories(){return categories;}
    public void requestCategories(){JsonObject j=new JsonObject();j.addProperty("type","request_categories");sendData(j.toString());}
    public boolean sendScan(String code){if(!isSignedIn())return false;JsonObject j=new JsonObject();j.addProperty("type","scan");j.addProperty("data",code);return sendNow(j.toString());}
    public void sendData(String text){sender.execute(()->sendNow(text));}
    private boolean sendNow(String text) {
        try{JsonObject payload=JsonParser.parseString(text).getAsJsonObject();if(token!=null)payload.addProperty("token",token);text=payload.toString();}catch(RuntimeException ignored){}
        synchronized(writeLock) {
            PrintWriter writer=output;if(!connected||writer==null)return false;
            writer.println(text);writer.flush();
            if(token!=null)authenticatedUntil=SystemClock.elapsedRealtime()+30*60*1000;
            if(writer.checkError()){Socket old=socket;try{if(old!=null)old.close();}catch(IOException ignored){}return false;}
            return true;
        }
    }
    public void request(JsonObject request,Callback callback) {
        String id=UUID.randomUUID().toString();request.addProperty("requestId",id);
        if(request.has("type") && "auth_login".equals(request.get("type").getAsString()))loginRequests.put(id,authGeneration);
        if(token!=null)request.addProperty("token",token);
        requests.put(id,callback);
        main.postDelayed(()-> {loginRequests.remove(id);Callback waiting=requests.remove(id);if(waiting!=null)waiting.complete(error("TIMEOUT","The desktop did not respond. Check the connection and use the updated desktop."));},20000);
        sender.execute(()-> {if(!sendNow(request.toString())){Callback waiting=requests.remove(id);if(waiting!=null)main.post(()->waiting.complete(error("DISCONNECTED","Connect to the desktop to continue.")));}});
    }
    public void login(String email,char[] password,Callback callback) {
        final int loginAttempt=++authGeneration;
        final int loginGeneration=generation;
        clearAuth();JsonObject challenge=new JsonObject();challenge.addProperty("type","auth_challenge");challenge.addProperty("email",email);
        request(challenge,response-> {
            if(loginAttempt!=authGeneration){Arrays.fill(password,'\0');callback.complete(error("AUTH_FAILED","Sign-in was cancelled."));return;}
            if(!response.get("ok").getAsBoolean()){Arrays.fill(password,'\0');callback.complete(response);return;}
            sender.execute(()-> {
                try {
                    JsonObject data=response.getAsJsonObject("data");
                    byte[] proof=PasswordProof.create(password,data.get("algorithm").getAsString(),data.get("iterations").getAsInt(),Base64.decode(data.get("salt").getAsString(),Base64.DEFAULT),data.get("nonce").getAsString(),data.get("challengeId").getAsString());
                    JsonObject login=new JsonObject();login.addProperty("type","auth_login");login.add("challengeId",data.get("challengeId"));login.addProperty("proof",Base64.encodeToString(proof,Base64.NO_WRAP));Arrays.fill(proof,(byte)0);
                    request(login,result-> {
                        if(loginAttempt!=authGeneration || loginGeneration!=generation||!connected){callback.complete(error("DISCONNECTED","Connection changed. Sign in again."));return;}
                        if(result.get("ok").getAsBoolean())broadcast(new Intent(ACTION_AUTH_CHANGED));
                        callback.complete(result);
                    });
                } catch(Exception e){Arrays.fill(password,'\0');main.post(()->callback.complete(error("AUTH_FAILED","Could not complete sign-in. Check your password and update the desktop.")));}
            });
        });
    }
    public void logout(){++authGeneration;loginRequests.clear();if(token!=null){JsonObject j=new JsonObject();j.addProperty("type","auth_logout");request(j,r->{});}clearAuth();}
    public void closeConnection() {
        reconnect=false;++generation;++authGeneration;loginRequests.clear();connected=false;clearAuth();
        connectionMessage="Connect to the POS desktop over WiFi";
        Socket old=socket;socket=null;output=null;try{if(old!=null)old.close();}catch(IOException ignored){}
        failRequests("DISCONNECTED","Connection closed. Your saved counts will be kept.");notifyConnection(false);
    }
    private void failRequests(String code,String message){for(String id:requests.keySet()){Callback cb=requests.remove(id);if(cb!=null)main.post(()->cb.complete(error(code,message)));}}
    public static JsonObject error(String code,String message){JsonObject j=new JsonObject();j.addProperty("ok",false);j.addProperty("code",code);j.addProperty("message",message);return j;}
}

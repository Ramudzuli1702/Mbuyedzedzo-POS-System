package com.pos.scanner;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class ReceiptStorage {
    
    private static ReceiptStorage instance;
    private SharedPreferences preferences;
    private Gson gson;
    private static final String PREFS_NAME = "receipts_prefs";
    private static final String KEY_RECEIPTS = "receipts_list";
    private String scope;
    private static final String KEY_ALIAS="mbuyedzedzo-receipts-v2";
    
    private ReceiptStorage(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        // Old receipts have no reliable store/staff owner. Do not expose them to the next login.
        preferences.edit().remove(KEY_RECEIPTS).commit();
    }
    
    public static synchronized ReceiptStorage getInstance(Context context) {
        if (instance == null) {
            instance = new ReceiptStorage(context.getApplicationContext());
        }
        return instance;
    }
    
    public synchronized boolean saveReceipt(Receipt receipt) {
        if(scope==null || receipt==null)return false;
        List<Receipt> receipts = getAllReceipts();
        for (Receipt existing : receipts) if (existing != null && java.util.Objects.equals(existing.getReceiptId(), receipt.getReceiptId())) return true;
        receipts.add(0, receipt); // Add to beginning
        
        // Keep only last 100 receipts
        if (receipts.size() > 100) {
            receipts = receipts.subList(0, 100);
        }
        
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
            cipher.updateAAD(scope.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(gson.toJson(receipts).getBytes(StandardCharsets.UTF_8));
            String stored=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
            return preferences.edit().putString(scope, stored).commit();
        }catch(Exception e){return false;}
    }
    
    public synchronized List<Receipt> getAllReceipts() {
        if(scope==null)return new ArrayList<>();
        String stored = preferences.getString(scope, null);
        if (stored == null) {
            return new ArrayList<>();
        }
        
        Type type = new TypeToken<List<Receipt>>(){}.getType();
        List<Receipt> receipts;
        try {
            String[] parts=stored.split(":",-1);
            if(parts.length!=2)return new ArrayList<>();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            cipher.updateAAD(scope.getBytes(StandardCharsets.UTF_8));
            String json=new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
            receipts = gson.fromJson(json, type);
        }
        catch (Exception e) { return new ArrayList<>(); }
        return receipts != null ? receipts : new ArrayList<>();
    }
    
    public synchronized void clearAllReceipts() {
        if(scope!=null)preferences.edit().remove(scope).apply();
    }

    public synchronized void setScope(String desktop,int staff) {
        if(desktop==null || !desktop.matches("[a-f0-9]{64}") || staff<=0)throw new IllegalArgumentException("Verified desktop and staff required.");
        scope="v2_"+desktop+"_"+staff;
    }
    public synchronized void clearScope(){scope=null;}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY_ALIAS))return (SecretKey)store.getKey(KEY_ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    
    public Receipt getReceiptById(String receiptId) {
        List<Receipt> receipts = getAllReceipts();
        for (Receipt receipt : receipts) {
            if (receipt != null && receiptId != null && receiptId.equals(receipt.getReceiptId())) {
                return receipt;
            }
        }
        return null;
    }
    
    public int getReceiptCount() {
        return getAllReceipts().size();
    }
}

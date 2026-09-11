package com.pos.scanner;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ReceiptStorage {
    
    private static ReceiptStorage instance;
    private SharedPreferences preferences;
    private Gson gson;
    private static final String PREFS_NAME = "receipts_prefs";
    private static final String KEY_RECEIPTS = "receipts_list";
    
    private ReceiptStorage(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized ReceiptStorage getInstance(Context context) {
        if (instance == null) {
            instance = new ReceiptStorage(context.getApplicationContext());
        }
        return instance;
    }
    
    public void saveReceipt(Receipt receipt) {
        List<Receipt> receipts = getAllReceipts();
        receipts.add(0, receipt); // Add to beginning
        
        // Keep only last 100 receipts
        if (receipts.size() > 100) {
            receipts = receipts.subList(0, 100);
        }
        
        String json = gson.toJson(receipts);
        preferences.edit().putString(KEY_RECEIPTS, json).apply();
    }
    
    public List<Receipt> getAllReceipts() {
        String json = preferences.getString(KEY_RECEIPTS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        
        Type type = new TypeToken<List<Receipt>>(){}.getType();
        List<Receipt> receipts = gson.fromJson(json, type);
        return receipts != null ? receipts : new ArrayList<>();
    }
    
    public void clearAllReceipts() {
        preferences.edit().remove(KEY_RECEIPTS).apply();
    }
    
    public Receipt getReceiptById(String receiptId) {
        List<Receipt> receipts = getAllReceipts();
        for (Receipt receipt : receipts) {
            if (receipt.getReceiptId().equals(receiptId)) {
                return receipt;
            }
        }
        return null;
    }
    
    public int getReceiptCount() {
        return getAllReceipts().size();
    }
}
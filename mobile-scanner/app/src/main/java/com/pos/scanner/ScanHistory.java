package com.pos.scanner;

import java.io.Serializable;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ScanHistory {

    private static ScanHistory instance;
    private SharedPreferences prefs;
    private Gson gson = new Gson();
    private static final String PREF_NAME = "pos_scans";
    private static final String KEY_SCANS = "scans_list";

    private ScanHistory(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ScanHistory getInstance(Context context) {
        if (instance == null) {
            instance = new ScanHistory(context.getApplicationContext());
        }
        return instance;
    }

    public void addScan(String barcode) {
        List<ScanEntry> scans = getAllScans();
        scans.add(0, new ScanEntry(barcode));
        String json = gson.toJson(scans);
        prefs.edit().putString(KEY_SCANS, json).apply();
    }

    public List<ScanEntry> getAllScans() {
        String json = prefs.getString(KEY_SCANS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<ScanEntry>>(){}.getType();
        List<ScanEntry> scans = gson.fromJson(json, type);
        return scans != null ? scans : new ArrayList<>();
    }

    public static class ScanEntry implements Serializable {
        private String barcode;
        private String timestamp;

        public ScanEntry(String barcode) {
            this.barcode = barcode;
            this.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        }

        // Getters
        public String getBarcode() { return barcode; }
        public String getTimestamp() { return timestamp; }
    }
}
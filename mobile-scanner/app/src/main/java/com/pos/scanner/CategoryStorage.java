package com.pos.scanner;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
/**
 * Storage for categories received from desktop
 * Similar to ReceiptStorage but for categories
 */
public class CategoryStorage {
   
    private static final String TAG = "CategoryStorage";
    private static final String PREFS_NAME = "CategoryPrefs";
    private static final String KEY_CATEGORIES = "categories";
   
    private static CategoryStorage instance;
    private final SharedPreferences prefs;
    private final Gson gson;
   
    private CategoryStorage(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
   
    public static synchronized CategoryStorage getInstance(Context context) {
        if (instance == null) {
            instance = new CategoryStorage(context);
        }
        return instance;
    }
   
    /**
     * Save categories list
     */
    public void saveCategories(List<String> categories) {
        try {
            String json = gson.toJson(categories);
            prefs.edit().putString(KEY_CATEGORIES, json).apply();
            Log.d(TAG, "✅ Saved " + categories.size() + " categories: " + categories.toString());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving categories: " + e.getMessage(), e);
        }
    }
   
    /**
     * Get all saved categories
     */
    public List<String> getAllCategories() {
        try {
            String json = prefs.getString(KEY_CATEGORIES, null);
           
            if (json != null) {
                Type listType = new TypeToken<List<String>>(){}.getType();
                List<String> categories = gson.fromJson(json, listType);
                Log.d(TAG, "✅ Loaded " + categories.size() + " categories from storage");
                return categories;
            } else {
                Log.d(TAG, "ℹ️ No categories in storage yet");
                return new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading categories: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
   
    /**
     * Clear all categories
     */
    public void clearCategories() {
        prefs.edit().remove(KEY_CATEGORIES).apply();
        Log.d(TAG, "🗑️ Categories cleared");
    }
   
    /**
     * Check if categories exist
     */
    public boolean hasCategories() {
        return prefs.contains(KEY_CATEGORIES);
    }
}
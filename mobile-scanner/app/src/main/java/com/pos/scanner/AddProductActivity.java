package com.pos.scanner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public class AddProductActivity extends AppCompatActivity {
    private static final String TAG = "AddProductActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int SCAN_BARCODE_REQUEST = 101;
    private static final String ACTION_CATEGORIES_RECEIVED = "CATEGORIES_RECEIVED";
    private static final String ACTION_WIFI_CONNECTED = "WIFI_CONNECTED";
    private EditText productNameField;
    private Spinner categorySpinner;
    private EditText barcodeField;
    private EditText quantityField;
    private EditText priceField;
    private Button scanBarcodeButton;
    private Button submitButton;
    private Button cancelButton;
    private TextView statusText;
    private MaterialCardView statusCard;
    private TextView categoriesListText;
    private MaterialCardView categoriesCard;
    private WiFiCommunication wifiComm;
    private final Gson gson = new Gson();
    private ArrayAdapter<String> categoryAdapter;
    private final List<String> availableCategories = new ArrayList<>();

    private Handler handler;
    /* ===================== Broadcast Receivers ===================== */
    // Listen for new categories (like ReceiptActivity listens for receipts)
    private final BroadcastReceiver categoriesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CATEGORIES_RECEIVED.equals(intent.getAction())) {
                Log.d(TAG, "📂 Categories broadcast received - reloading from storage");
                loadCategories(); // Load from storage, just like receipts
            }
        }
    };
    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WIFI_CONNECTED.equals(intent.getAction())) {
                boolean connected = intent.getBooleanExtra("connected", false);
                Log.d(TAG, "📡 WiFi status: " + (connected ? "Connected" : "Disconnected"));
                runOnUiThread(() -> updateConnectionStatus());
                if (connected) {
                    // Request categories when connected
                    requestCategoriesFromDesktop();
                }
            }
        }
    };

    /* ===================== Lifecycle ===================== */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "🚀 onCreate");
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_add_product);
        initializeViews();
        setupCategorySpinner();
        setupWiFi();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 onResume");

        // Register receivers
        registerReceivers();

        // Update UI
        updateConnectionStatus();

        // LOAD CATEGORIES FROM STORAGE (like ReceiptActivity loads receipts)
        loadCategories();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(categoriesReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(wifiReceiver);
            Log.d(TAG, "✅ Receivers unregistered");
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Error unregistering receivers: " + e.getMessage());
        }
    }

    /*
     * ===================== Category Loading - SAME AS RECEIPT LOADING
     * =====================
     */
    private void loadCategories() {
        Log.d(TAG, "📂 Loading categories from storage...");

        // Load in background thread (like ReceiptActivity does)
        new Thread(() -> {
            List<String> loadedCategories = CategoryStorage.getInstance(this).getAllCategories();

            handler.post(() -> {
                Log.d(TAG, "🔍 Inside handler.post - before clear");
                availableCategories.clear();

                if (loadedCategories.isEmpty()) {
                    Log.d(TAG, "⚠️ No categories in storage");
                    availableCategories.add("No categories available");
                } else {
                    availableCategories.addAll(loadedCategories);
                    Log.d(TAG, "✅ Loaded " + loadedCategories.size() + " categories: " + loadedCategories.toString());
                }
                Log.d(TAG, "🔍 After addAll - availableCategories size: " + availableCategories.size() + ", contents: "
                        + availableCategories.toString());

                updateCategorySpinner();
                updateCategoriesDisplay();
            });
        }).start();
    }

    /* ===================== Initialization ===================== */
    private void initializeViews() {
        productNameField = findViewById(R.id.productNameField);
        categorySpinner = findViewById(R.id.categorySpinner);
        barcodeField = findViewById(R.id.barcodeField);
        quantityField = findViewById(R.id.quantityField);
        priceField = findViewById(R.id.priceField);
        scanBarcodeButton = findViewById(R.id.scanBarcodeButton);
        submitButton = findViewById(R.id.submitButton);
        cancelButton = findViewById(R.id.cancelButton);
        statusText = findViewById(R.id.statusText);
        statusCard = findViewById(R.id.statusCard);
        categoriesListText = findViewById(R.id.categoriesListText);
        categoriesCard = findViewById(R.id.categoriesCard);
        quantityField.setText("1");
    }

    private void setupCategorySpinner() {
        Log.d(TAG, "🔧 setupCategorySpinner - initial availableCategories: " + availableCategories.toString());
        // FIXED: Create adapter WITHOUT passing the shared list to avoid clear()
        // affecting availableCategories
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        // Add initial placeholder manually
        categoryAdapter.add("Loading...");
        categoryAdapter.notifyDataSetChanged();
        Log.d(TAG, "🔧 Added 'Loading...' placeholder - adapter count: " + categoryAdapter.getCount());
        Log.d(TAG, "🔧 Spinner adapter set with " + categoryAdapter.getCount() + " items");
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                Log.d(TAG, "Selected: " + selected + " (position " + position + ")");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "Nothing selected in spinner");
            }
        });
    }

    private void updateCategorySpinner() {
        Log.d(TAG, "🔄 updateCategorySpinner called - current availableCategories: " + availableCategories.toString());
        // Remove the redundant runOnUiThread since we're already on main thread
        categoryAdapter.clear();
        Log.d(TAG, "🔄 Adapter cleared - now 0 items");
        categoryAdapter.addAll(availableCategories);
        categoryAdapter.notifyDataSetChanged();
        Log.d(TAG, "🔄 Adapter addAll called with " + availableCategories.size() + " items - new adapter count: "
                + categoryAdapter.getCount());

        if (categorySpinner.getAdapter().getCount() > 0) {
            categorySpinner.setSelection(0);
            Log.d(TAG, "🔄 Spinner selection set to 0");
        }
        categorySpinner.invalidate(); // Force redraw
        categorySpinner.requestLayout(); // Force layout update

        Log.d(TAG, "✅ Spinner updated - final adapter count: " + categoryAdapter.getCount());

        // EXTRA DEBUG: Log spinner's current items
        for (int i = 0; i < categoryAdapter.getCount(); i++) {
            Log.d(TAG, "🔍 Spinner item " + i + ": " + categoryAdapter.getItem(i));
        }
    }

    private void updateCategoriesDisplay() {
        runOnUiThread(() -> {
            categoriesCard.setVisibility(View.VISIBLE);
            if (availableCategories.isEmpty() ||
                    (availableCategories.size() == 1 && "No categories available".equals(availableCategories.get(0)))) {
                categoriesListText.setText(
                        "Waiting for categories...\n\nMake sure:\n• Desktop app is running\n• WiFi is connected\n• Desktop has categories");
            } else if (availableCategories.size() == 1 && "Loading...".equals(availableCategories.get(0))) {
                categoriesListText.setText("Loading categories...");
            } else {
                StringBuilder builder = new StringBuilder();
                builder.append("Available categories (").append(availableCategories.size()).append("):\n\n");
                for (String cat : availableCategories) {
                    builder.append("• ").append(cat).append("\n");
                }
                categoriesListText.setText(builder.toString().trim());
            }
            Log.d(TAG, "📋 Categories display updated");
        });
    }

    private void requestCategoriesFromDesktop() {
        if (wifiComm == null || !wifiComm.isConnected()) {
            Log.w(TAG, "⚠️ Cannot request - not connected");
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("type", "request_categories");
        root.addProperty("timestamp", System.currentTimeMillis());
        wifiComm.sendData(gson.toJson(root));
        Log.d(TAG, "📤 Requested categories from desktop");
    }

    private void setupWiFi() {
        wifiComm = WiFiCommunication.getInstance(this);
    }

    private void updateConnectionStatus() {
        boolean connected = wifiComm != null && wifiComm.isConnected();
        if (connected) {
            statusText.setText("Connected to desktop");
            statusText.setTextColor(getResources().getColor(R.color.status_success, null));
            statusCard.setCardBackgroundColor(getResources().getColor(R.color.status_success_bg, null));
        } else {
            statusText.setText("Not connected");
            statusText.setTextColor(getResources().getColor(R.color.brand_primary_dark, null));
            statusCard.setCardBackgroundColor(getResources().getColor(R.color.brand_primary_light, null));
        }
    }

    private void registerReceivers() {
        LocalBroadcastManager.getInstance(this).registerReceiver(
                categoriesReceiver, new IntentFilter(ACTION_CATEGORIES_RECEIVED));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                wifiReceiver, new IntentFilter(ACTION_WIFI_CONNECTED));
        Log.d(TAG, "✅ Receivers registered");
    }

    /* ===================== Click Handling ===================== */
    private void setupClickListeners() {
        scanBarcodeButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                Intent intent = new Intent(this, ScannerActivity.class);
                intent.putExtra("mode", "add_product");
                intent.putExtra("suppress_send", true);
                startActivityForResult(intent, SCAN_BARCODE_REQUEST);
            }
        });
        submitButton.setOnClickListener(v -> submitProduct());
        cancelButton.setOnClickListener(v -> finish());

        // Tap to refresh categories
        categoriesCard.setOnClickListener(v -> {
            if (wifiComm != null && wifiComm.isConnected()) {
                Toast.makeText(this, "Requesting categories...", Toast.LENGTH_SHORT).show();
                requestCategoriesFromDesktop();
            } else {
                Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.CAMERA },
                    CAMERA_PERMISSION_CODE);
            return false;
        }
        return true;
    }

    /* ===================== Product Submission ===================== */
    private void submitProduct() {
        String name = productNameField.getText().toString().trim();
        String barcode = barcodeField.getText().toString().trim();
        String qtyStr = quantityField.getText().toString().trim();
        String priceStr = priceField.getText().toString().trim();
        // FIXED: Add null-safe check for selected item to prevent NPE
        Object selectedItem = categorySpinner.getSelectedItem();
        String category = (selectedItem != null) ? selectedItem.toString() : "";
        Log.d(TAG, "📝 Submit attempt - Category selected: '" + category + "' (item: " + selectedItem + ")");
        if (name.isEmpty() || barcode.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("Loading...".equals(category) || "No categories available".equals(category) || category.isEmpty()) {
            Toast.makeText(this, "Please select a valid category", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wifiComm == null || !wifiComm.isConnected()) {
            Toast.makeText(this, "Not connected to desktop", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            int quantity = Integer.parseInt(qtyStr);
            double price = Double.parseDouble(priceStr);
            // FIXED: Send in background thread to avoid NetworkOnMainThreadException
            new Thread(() -> {
                sendProductToDesktop(name, category, barcode, quantity, price);
                // Post UI update to main thread
                handler.post(() -> {
                    Toast.makeText(this, "Product sent to desktop", Toast.LENGTH_SHORT).show();
                    clearForm();
                });
            }).start();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid quantity or price", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "❌ Invalid number format: " + e.getMessage());
        } catch (Exception e) {
            Toast.makeText(this, "Error submitting product", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "❌ Unexpected error in submitProduct: " + e.getMessage(), e);
        }
    }

    private void sendProductToDesktop(String name, String category, String barcode, int quantity, double price) {
        // Built with Gson (not string interpolation) so a name/category/barcode
        // containing a quote or backslash can't corrupt the JSON stream.
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        data.addProperty("category", category);
        data.addProperty("barcode", barcode);
        data.addProperty("quantity", quantity);
        data.addProperty("price", price);

        JsonObject root = new JsonObject();
        root.addProperty("type", "add_product");
        root.add("data", data);
        root.addProperty("timestamp", System.currentTimeMillis());

        String json = gson.toJson(root);
        wifiComm.sendData(json);
        Log.d(TAG, "Product JSON sent: " + json);
    }

    private void clearForm() {
        productNameField.setText("");
        if (categorySpinner.getAdapter() != null && categorySpinner.getAdapter().getCount() > 0) {
            categorySpinner.setSelection(0);
        }
        barcodeField.setText("");
        quantityField.setText("1");
        priceField.setText("");
        productNameField.requestFocus();
    }

    /* ===================== Barcode Result ===================== */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_BARCODE_REQUEST && resultCode == RESULT_OK && data != null) {
            String barcode = data.getStringExtra("barcode");
            barcodeField.setText(barcode != null ? barcode : "");
            productNameField.requestFocus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
        }
    }
}
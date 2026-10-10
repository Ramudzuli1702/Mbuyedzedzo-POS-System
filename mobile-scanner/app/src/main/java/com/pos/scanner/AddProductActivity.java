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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;

public class AddProductActivity extends AppCompatActivity {
    private static final String TAG = "AddProductActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int SCAN_BARCODE_REQUEST = 101;
    private static final String ACTION_WIFI_CONNECTED = "WIFI_CONNECTED";
    private EditText productNameField;
    private TextInputLayout categoryLayout;
    private AutoCompleteTextView categoryDropdown;
    private EditText barcodeField;
    private EditText quantityField;
    private EditText costPriceField;
    private EditText priceField;
    private Button scanBarcodeButton;
    private Button submitButton;
    private Button cancelButton;
    private TextView statusText;
    private MaterialCardView statusCard;
    private WiFiCommunication wifiComm;
    private final Gson gson = new Gson();

    private Handler handler;

    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_WIFI_CONNECTED.equals(action)) {
                boolean connected = intent.getBooleanExtra("connected", false);
                Log.d(TAG, "📡 WiFi status: " + (connected ? "Connected" : "Disconnected"));
                runOnUiThread(() -> updateConnectionStatus());
            } else if (WiFiCommunication.ACTION_CATEGORIES_UPDATED.equals(action)) {
                Log.d(TAG, "📂 Category list updated");
                runOnUiThread(() -> refreshCategoryDropdown());
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
        setupWiFi();
        setupClickListeners();
        refreshCategoryDropdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_WIFI_CONNECTED);
        filter.addAction(WiFiCommunication.ACTION_CATEGORIES_UPDATED);
        LocalBroadcastManager.getInstance(this).registerReceiver(wifiReceiver, filter);
        updateConnectionStatus();
        refreshCategoryDropdown();

        // The desktop pushes categories on connect, but if we're already
        // connected and have none (e.g. the desktop was updated mid-session),
        // ask for them explicitly.
        if (wifiComm != null && wifiComm.isConnected() && wifiComm.getCategories().isEmpty()) {
            wifiComm.requestCategories();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(wifiReceiver);
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Error unregistering receiver: " + e.getMessage());
        }
    }

    /* ===================== Initialization ===================== */
    private void initializeViews() {
        productNameField = findViewById(R.id.productNameField);
        categoryLayout = findViewById(R.id.categoryLayout);
        categoryDropdown = findViewById(R.id.categoryDropdown);
        barcodeField = findViewById(R.id.barcodeField);
        quantityField = findViewById(R.id.quantityField);
        costPriceField = findViewById(R.id.costPriceField);
        priceField = findViewById(R.id.priceField);
        scanBarcodeButton = findViewById(R.id.scanBarcodeButton);
        submitButton = findViewById(R.id.submitButton);
        cancelButton = findViewById(R.id.cancelButton);
        statusText = findViewById(R.id.statusText);
        statusCard = findViewById(R.id.statusCard);
        quantityField.setText("1");
    }

    private void setupWiFi() {
        wifiComm = WiFiCommunication.getInstance(this);
    }

    /**
     * Fills the dropdown from the desktop's real category list. Keeps the
     * current selection if it still exists; clears it if the category was
     * renamed/deleted on the desktop in the meantime.
     */
    private void refreshCategoryDropdown() {
        if (wifiComm == null) return;

        List<String> categories = wifiComm.getCategories();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categories);
        categoryDropdown.setAdapter(adapter);

        String current = categoryDropdown.getText().toString();
        if (!current.isEmpty() && !categories.contains(current)) {
            categoryDropdown.setText("", false);
        }

        if (categories.isEmpty()) {
            categoryLayout.setHelperText(wifiComm.isConnected()
                    ? "Loading categories from desktop…"
                    : "Connect to the desktop to load categories");
        } else {
            categoryLayout.setHelperText(null);
        }
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

    /* ===================== Click Handling ===================== */
    private void setupClickListeners() {
        categoryDropdown.setOnItemClickListener((parent, view, position, id) ->
                categoryLayout.setError(null));

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
        String category = categoryDropdown.getText().toString().trim();
        String barcode = barcodeField.getText().toString().trim();
        String qtyStr = quantityField.getText().toString().trim();
        String costPriceStr = costPriceField.getText().toString().trim();
        String priceStr = priceField.getText().toString().trim();

        if (name.isEmpty() || barcode.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wifiComm == null || !wifiComm.isConnected()) {
            Toast.makeText(this, "Not connected to desktop", Toast.LENGTH_LONG).show();
            return;
        }

        // A category is required whenever the desktop has given us a list to
        // choose from. If no list ever arrived (older desktop build), send
        // without one — the desktop files it under its default category.
        List<String> available = wifiComm.getCategories();
        if (!available.isEmpty()) {
            if (category.isEmpty()) {
                categoryLayout.setError("Select a category");
                Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!available.contains(category)) {
                categoryLayout.setError("Pick a category from the list");
                return;
            }
        }
        categoryLayout.setError(null);

        try {
            int quantity = Integer.parseInt(qtyStr);
            // Purchase price is optional here — the cashier can fill in the real
            // cost later in Inventory; the desktop side already defaults a
            // missing cost price to 0 rather than rejecting the product.
            double costPrice = costPriceStr.isEmpty() ? 0.0 : Double.parseDouble(costPriceStr);
            double price = Double.parseDouble(priceStr);
            new Thread(() -> {
                sendProductToDesktop(name, category, barcode, quantity, costPrice, price);
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

    private void sendProductToDesktop(String name, String category, String barcode,
                                      int quantity, double costPrice, double price) {
        // Built with Gson (not string interpolation) so a name/barcode
        // containing a quote or backslash can't corrupt the JSON stream.
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        if (category != null && !category.isEmpty()) {
            data.addProperty("category", category);
        }
        data.addProperty("barcode", barcode);
        data.addProperty("quantity", quantity);
        data.addProperty("costPrice", costPrice);
        data.addProperty("price", price);

        JsonObject root = new JsonObject();
        root.addProperty("type", "add_product");
        root.add("data", data);
        root.addProperty("timestamp", System.currentTimeMillis());

        String json = gson.toJson(root);
        wifiComm.sendData(json);
        Log.d(TAG, "Product JSON sent: " + json);
    }

    /** Clears the per-product fields. The category is deliberately kept —
     *  staff usually add several products from the same aisle in a row. */
    private void clearForm() {
        productNameField.setText("");
        barcodeField.setText("");
        quantityField.setText("1");
        costPriceField.setText("");
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
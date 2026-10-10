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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class AddProductActivity extends AppCompatActivity {
    private static final String TAG = "AddProductActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int SCAN_BARCODE_REQUEST = 101;
    private static final String ACTION_WIFI_CONNECTED = "WIFI_CONNECTED";
    private EditText productNameField;
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
            if (ACTION_WIFI_CONNECTED.equals(intent.getAction())) {
                boolean connected = intent.getBooleanExtra("connected", false);
                Log.d(TAG, "📡 WiFi status: " + (connected ? "Connected" : "Disconnected"));
                runOnUiThread(() -> updateConnectionStatus());
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                wifiReceiver, new IntentFilter(ACTION_WIFI_CONNECTED));
        updateConnectionStatus();
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
        try {
            int quantity = Integer.parseInt(qtyStr);
            // Purchase price is optional here — the cashier can fill in the real
            // cost later in Inventory; the desktop side already defaults a
            // missing cost price to 0 rather than rejecting the product.
            double costPrice = costPriceStr.isEmpty() ? 0.0 : Double.parseDouble(costPriceStr);
            double price = Double.parseDouble(priceStr);
            new Thread(() -> {
                sendProductToDesktop(name, barcode, quantity, costPrice, price);
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

    private void sendProductToDesktop(String name, String barcode, int quantity, double costPrice, double price) {
        // Built with Gson (not string interpolation) so a name/barcode
        // containing a quote or backslash can't corrupt the JSON stream.
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
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

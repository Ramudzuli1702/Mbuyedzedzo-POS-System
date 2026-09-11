package com.pos.scanner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.pos.scanner.ScannerActivity;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CONNECT_SCAN_REQUEST = 200;
    private static final String PREFS_NAME = "POSPrefs";
    private static final String PREF_SERVER_IP = "server_ip";

    private TextView statusText;
    private TextView connectionStatus;
    private TextView serverIPText;
    private View scanButton;
    private View addProductButton;
    private View viewReceiptsButton;
    private Button connectButton;
    private MaterialCardView connectionCard;

    private WiFiCommunication wifiComm;
    private boolean isConnected = false;
    private String savedServerIP = "";

    // WiFi connection broadcast receiver
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("WIFI_CONNECTED".equals(intent.getAction())) {
                boolean connected = intent.getBooleanExtra("connected", false);
                updateConnectionStatus(connected);
            }
        }
    };

    // Receipts broadcast receiver
    private final BroadcastReceiver receiptReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("RECEIPT_RECEIVED".equals(intent.getAction())) {
                Receipt receipt = (Receipt) intent.getSerializableExtra("receipt");
                if (receipt != null) {
                    Toast.makeText(MainActivity.this, 
                        "Receipt received and saved!", 
                        Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        loadSavedIP();
        checkCameraPermission();
        setupWiFi();
        setupClickListeners();

        // Register broadcast receivers
        LocalBroadcastManager.getInstance(this).registerReceiver(
                connectionReceiver, new IntentFilter("WIFI_CONNECTED"));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiptReceiver, new IntentFilter("RECEIPT_RECEIVED"));
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        connectionStatus = findViewById(R.id.connectionStatus);
        serverIPText = findViewById(R.id.serverIPText);
        scanButton = findViewById(R.id.scanButton);
        addProductButton = findViewById(R.id.addProductButton);
        viewReceiptsButton = findViewById(R.id.viewReceiptsButton);
        connectionCard = findViewById(R.id.connectionCard);
        connectButton = findViewById(R.id.connectButton);
    }

    private void loadSavedIP() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        savedServerIP = prefs.getString(PREF_SERVER_IP, "");
        
        if (!savedServerIP.isEmpty()) {
            serverIPText.setText("Server: " + savedServerIP);
        }
    }

    private void saveServerIP(String ip) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREF_SERVER_IP, ip).apply();
        savedServerIP = ip;
    }

    private void setupClickListeners() {
        scanButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
                startActivity(intent);
            }
        });

        // FIXED: Added missing listener for Add Product button
        addProductButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            startActivity(intent);
        });

        viewReceiptsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ReceiptActivity.class);
            startActivity(intent);
        });

        connectButton.setOnClickListener(v -> {
            if (isConnected) {
                disconnectWiFi();
            } else {
                showIPInputDialog();
            }
        });

        connectionCard.setOnClickListener(v -> {
            if (!isConnected) {
                showIPInputDialog();
            } else {
                Toast.makeText(this, "Connected to " + savedServerIP, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showIPInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect to POS System");
        builder.setMessage("Enter the IP address shown on the POS desktop application.\n\nExample: 192.168.1.100");

        // Create input field
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("192.168.1.100");
        
        // Pre-fill with saved IP
        if (!savedServerIP.isEmpty()) {
            input.setText(savedServerIP);
            input.setSelection(savedServerIP.length());
        }
        
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String ip = input.getText().toString().trim();
            if (!ip.isEmpty()) {
                saveServerIP(ip);
                connectWiFi(ip);
            } else {
                Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Scan QR", (dialog, which) -> launchConnectScanner());

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /** Open the camera to scan the "connect" QR shown on the POS desktop. */
    private void launchConnectScanner() {
        if (!checkCameraPermission()) return;
        Intent intent = new Intent(this, ScannerActivity.class);
        intent.putExtra("mode", "connect");
        intent.putExtra("suppress_send", true);   // don't forward it as a scan
        startActivityForResult(intent, CONNECT_SCAN_REQUEST);
    }

    /**
     * Accepts "POS:192.168.1.5:8888", "POS:192.168.1.5", "192.168.1.5:8888"
     * or a bare IP, and returns the "host[:port]" part — or null if the QR
     * is not a POS connection code.
     */
    static String parseConnectPayload(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        boolean hadPrefix = s.regionMatches(true, 0, "POS:", 0, 4);
        if (hadPrefix) s = s.substring(4).trim();
        if (s.isEmpty()) return null;
        // Without the POS: prefix, only treat dotted strings as addresses
        // (so a plain product barcode isn't mistaken for one).
        if (!hadPrefix && !s.contains(".")) return null;
        if (!s.matches("[A-Za-z0-9.\\-]+(:\\d{1,5})?")) return null;
        return s;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONNECT_SCAN_REQUEST && resultCode == RESULT_OK && data != null) {
            String payload = data.getStringExtra("scan_result");
            String address = parseConnectPayload(payload);
            if (address != null) {
                saveServerIP(address);
                connectWiFi(address);
            } else {
                Toast.makeText(this, "That QR code isn't a POS connection code.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
            return false;
        }
        return true;
    }

    private void setupWiFi() {
        wifiComm = WiFiCommunication.getInstance(this);
        updateConnectionStatus(wifiComm.isConnected());
    }

    private void connectWiFi(String ipAddress) {
        Toast.makeText(this, "Connecting to " + ipAddress + "...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            boolean success = wifiComm.connect(ipAddress);
            
            runOnUiThread(() -> {
                if (success) {
                    // Connection status will be updated via broadcast receiver
                    serverIPText.setText("Server: " + ipAddress);
                } else {
                    Toast.makeText(this, 
                        "Connection failed. Make sure:\n" +
                        "1. POS desktop app is running\n" +
                        "2. Both devices are on same WiFi\n" +
                        "3. IP address is correct", 
                        Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void disconnectWiFi() {
        wifiComm.closeConnection();
        Toast.makeText(this, "Disconnected from POS System", Toast.LENGTH_SHORT).show();
    }

    /** Update UI connection status */
    private void updateConnectionStatus(boolean connected) {
        isConnected = connected;

        runOnUiThread(() -> {
            if (connected) {
                connectionStatus.setText("Connected to POS System");
                connectionStatus.setTextColor(getResources().getColor(R.color.status_success));
                connectionCard.setCardBackgroundColor(getResources().getColor(R.color.status_success_bg));
                statusText.setText("Ready to scan");
                connectButton.setText("Disconnect");
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.status_error));

                if (!savedServerIP.isEmpty()) {
                    serverIPText.setText("Server: " + savedServerIP);
                }
            } else {
                connectionStatus.setText("Not connected — standalone mode");
                connectionStatus.setTextColor(getResources().getColor(R.color.brand_primary_dark));
                connectionCard.setCardBackgroundColor(getResources().getColor(R.color.brand_primary_light));
                statusText.setText("Connect to the POS desktop over WiFi");
                connectButton.setText("Connect");
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.brand_primary));
            }
        });
    }

    /** Called by ScannerActivity when a QR code is scanned */
    public void onQRCodeScanned(String code) {
        Toast.makeText(this, "Scanned: " + code, Toast.LENGTH_SHORT).show();

        // Send scan to desktop if WiFi connected
        if (wifiComm != null && wifiComm.isConnected()) {
            wifiComm.sendScan(code);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiptReceiver);
        if (wifiComm != null) {
            wifiComm.closeConnection();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wifiComm != null) {
            updateConnectionStatus(wifiComm.isConnected());
        }
    }
}
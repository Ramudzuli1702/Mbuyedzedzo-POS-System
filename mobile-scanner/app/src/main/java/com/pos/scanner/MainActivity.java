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
    private boolean loginOpening;
    private boolean resumed;
    private Button signOut;

    // WiFi connection broadcast receiver
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("WIFI_CONNECTED".equals(intent.getAction())) {
                boolean connected = intent.getBooleanExtra("connected", false);
                updateConnectionStatus(connected);
                ensureSignedIn();
            }
            if(WiFiCommunication.ACTION_AUTH_CHANGED.equals(intent.getAction())){updateConnectionStatus(wifiComm.isConnected());ensureSignedIn();}
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
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionReceiver,new IntentFilter(WiFiCommunication.ACTION_AUTH_CHANGED));
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
        findViewById(R.id.stocktakeButton).setOnClickListener(v -> startActivity(new Intent(this, StocktakeActivity.class)));
        connectionCard = findViewById(R.id.connectionCard);
        connectButton = findViewById(R.id.connectButton);
        signOut = findViewById(R.id.signOutButton);
        signOut.setOnClickListener(v -> {wifiComm.logout();ensureSignedIn();});
    }

    private void loadSavedIP() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        savedServerIP = prefs.getString(PREF_SERVER_IP, "");
        
        if (!savedServerIP.isEmpty()) {
            serverIPText.setText("Server: " + PairingEndpoint.display(savedServerIP));
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
        builder.setMessage("Scan the QR code in the desktop connection panel, or paste its copied connection code here.");

        // Create input field
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Paste the desktop connection code");
        
        // Pre-fill with saved IP
        if (!savedServerIP.isEmpty()) {
            input.setText(savedServerIP);
            input.setSelection(savedServerIP.length());
        }
        
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String ip = input.getText().toString().trim();
            if (PairingEndpoint.parse(ip)!=null) {
                saveServerIP(ip);
                connectWiFi(ip);
            } else {
                Toast.makeText(this, "Scan the desktop QR code or paste its connection code", Toast.LENGTH_SHORT).show();
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
     * Accepts the desktop's secure pairing code, including its certificate identity.
     */
    static String parseConnectPayload(String raw) {
        PairingEndpoint endpoint=PairingEndpoint.parse(raw);
        return endpoint==null?null:endpoint.encoded;
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
        Toast.makeText(this, "Connecting to " + PairingEndpoint.display(ipAddress) + "...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            boolean success = wifiComm.connect(ipAddress);
            
            runOnUiThread(() -> {
                if (success) {
                    // Connection status will be updated via broadcast receiver
                    serverIPText.setText("Server: " + PairingEndpoint.display(ipAddress));
                } else {
                    Toast.makeText(this, 
                        "Connection failed. Make sure:\n" +
                        "1. POS desktop app is running\n" +
                        "2. Both devices are on same WiFi\n" +
                        "3. Scan the new QR code from the updated desktop",
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
            boolean signedIn=wifiComm!=null&&wifiComm.isSignedIn();
            for(View action:new View[]{scanButton,addProductButton,viewReceiptsButton,findViewById(R.id.stocktakeButton)}) { action.setEnabled(signedIn);action.setAlpha(signedIn?1f:0.5f); }
            signOut.setVisibility(signedIn?View.VISIBLE:View.GONE);
            if (connected) {
                connectionStatus.setText("Connected to POS System");
                connectionStatus.setTextColor(getResources().getColor(R.color.status_success));
                connectionCard.setCardBackgroundColor(getResources().getColor(R.color.status_success_bg));
                statusText.setText(signedIn?"Signed in as "+wifiComm.getStaffName():"Sign in to continue");
                connectButton.setText("Disconnect");
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.status_error));

                if (!savedServerIP.isEmpty()) {
                    serverIPText.setText("Server: " + PairingEndpoint.display(savedServerIP));
                }
            } else {
                connectionStatus.setText("Not connected to desktop");
                connectionStatus.setTextColor(getResources().getColor(R.color.brand_primary_dark));
                connectionCard.setCardBackgroundColor(getResources().getColor(R.color.brand_primary_light));
                statusText.setText(wifiComm==null?"Connect to the POS desktop over WiFi":wifiComm.getConnectionMessage());
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
        // The app-scoped connection stays alive while other screens are open.
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed=true;
        loginOpening=false;
        if (wifiComm != null) {
            updateConnectionStatus(wifiComm.isConnected());
            ensureSignedIn();
        }
    }
    private void ensureSignedIn(){
        if(resumed&&wifiComm!=null&&wifiComm.isConnected()&&!wifiComm.isSignedIn()&&!loginOpening&&!isFinishing()){
            loginOpening=true;startActivity(new Intent(this,LoginActivity.class));
        }
    }
    @Override protected void onPause(){resumed=false;super.onPause();}
}

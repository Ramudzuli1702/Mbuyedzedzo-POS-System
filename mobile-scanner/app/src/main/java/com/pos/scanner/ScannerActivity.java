package com.pos.scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

public class ScannerActivity extends AppCompatActivity {

    private DecoratedBarcodeView barcodeView;
    private TextView lastScannedText;
    private TextView scanCountText;
    private ImageButton flashButton;
    private ImageButton closeButton;

    private boolean isFlashOn = false;
    private int scanCount = 0;

    private WiFiCommunication wifiComm;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private Handler uiHandler;

    // ✅ Listen for WiFi disconnect so UI can reflect connection state
    private final BroadcastReceiver wifiStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connected = intent.getBooleanExtra("connected", false);
            updateConnectionUI(connected);
        }
    };

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null) return;
            handleScan(result.getText());
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
            // Optional
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        uiHandler = new Handler(Looper.getMainLooper());

        initializeViews();
        initializeWiFi();
        setupScanner();
        setupClickListeners();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

        // Register for WiFi status changes
        LocalBroadcastManager.getInstance(this).registerReceiver(
            wifiStatusReceiver,
            new IntentFilter("WIFI_CONNECTED")
        );
    }

    private void initializeViews() {
        barcodeView = findViewById(R.id.barcode_scanner);
        lastScannedText = findViewById(R.id.lastScannedText);
        scanCountText = findViewById(R.id.scanCountText);
        flashButton = findViewById(R.id.flashButton);
        closeButton = findViewById(R.id.closeButton);
    }

    private void initializeWiFi() {
        wifiComm = WiFiCommunication.getInstance(this);
    }

    private void setupScanner() {
        barcodeView.decodeContinuous(callback);
        barcodeView.setStatusText("Point camera at barcode/QR code");
    }

    private void setupClickListeners() {
        flashButton.setOnClickListener(v -> toggleFlash());
        closeButton.setOnClickListener(v -> finish());
    }

    private void toggleFlash() {
        if (isFlashOn) {
            barcodeView.setTorchOff();
            flashButton.setImageResource(R.drawable.ic_flash_off);
            isFlashOn = false;
        } else {
            barcodeView.setTorchOn();
            flashButton.setImageResource(R.drawable.ic_flash_on);
            isFlashOn = true;
        }
    }

    private void handleScan(String scannedData) {
        String mode = getIntent().getStringExtra("mode");
        boolean suppressSend = getIntent().getBooleanExtra("suppress_send", false);
        boolean isAddProductMode = "add_product".equals(mode) || suppressSend;

        // Quick UI feedback (non-blocking)
        uiHandler.post(() -> {
            scanCount++;
            lastScannedText.setText("Last scan: " + scannedData);
            scanCountText.setText("Scans this session: " + scanCount);
        });

        vibrate();
        beep();

        if (isAddProductMode) {
            // Return the scanned text to the caller immediately.
            // "barcode"     — used by Add Product
            // "scan_result" — generic, used by the Connect-by-QR flow
            Intent resultIntent = new Intent();
            resultIntent.putExtra("barcode", scannedData);
            resultIntent.putExtra("scan_result", scannedData);
            setResult(RESULT_OK, resultIntent);
            finish();
            return;
        }

        // ✅ Offload network/storage to background thread
        new Thread(() -> {
            if (wifiComm != null && wifiComm.isConnected()) {
                sendToPC(scannedData);
            } else {
                storeLocally(scannedData);
            }
        }).start();

        // Pause scanner briefly to avoid duplicate scans
        barcodeView.pause();
        barcodeView.postDelayed(barcodeView::resume, 2000);
    }

    private void sendToPC(String data) {
        try {
            wifiComm.sendScan(data);
            uiHandler.post(() ->
                Toast.makeText(ScannerActivity.this, "Sent to POS: " + data, Toast.LENGTH_SHORT).show()
            );
        } catch (Exception e) {
            e.printStackTrace();
            uiHandler.post(() ->
                Toast.makeText(ScannerActivity.this, "Failed to send — saved locally", Toast.LENGTH_SHORT).show()
            );
            storeLocally(data);
        }
    }

    private void storeLocally(String data) {
        ScanHistory.getInstance(this).addScan(data);
        uiHandler.post(() ->
            Toast.makeText(ScannerActivity.this, "Saved locally: " + data, Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Update any UI elements that reflect connection status
     */
    private void updateConnectionUI(boolean connected) {
        // Optional: update a status indicator view if you have one
        // e.g. statusDot.setBackgroundResource(connected ? R.drawable.dot_green : R.drawable.dot_red);
        // statusText.setText(connected ? "Connected" : "Reconnecting...");
    }

    private void vibrate() {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
    }

    private void beep() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister broadcast receiver to avoid leaks
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wifiStatusReceiver);

        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}
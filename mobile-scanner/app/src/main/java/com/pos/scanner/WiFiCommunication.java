package com.pos.scanner;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * WiFi client for Android scanner app
 * Connects to desktop POS system over local network
 */
public class WiFiCommunication {

    private static final String TAG = "WiFiComm";
    private static final int DEFAULT_PORT = 8888;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private static WiFiCommunication instance;
    private final Context context;
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private volatile boolean isConnected = false;
    private volatile boolean shouldReconnect = true; // Set false on intentional disconnect
    private Thread readThread;
    private String serverIP;
    private int serverPort;
    private String lastAddress; // Saved for auto-reconnect
    private int reconnectAttempts = 0;
    private Gson gson = new Gson();

    private WiFiCommunication(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public static synchronized WiFiCommunication getInstance(Context ctx) {
        if (instance == null) {
            instance = new WiFiCommunication(ctx);
        }
        return instance;
    }

    /**
     * Connect to the desktop POS system
     * @param address Full address like "192.168.1.100:8889" or just IP (uses default port)
     */
    public boolean connect(String address) {
        if (isConnected) {
            Log.i(TAG, "Already connected");
            return true;
        }

        if (address == null || address.trim().isEmpty()) {
            Log.e(TAG, "Address is required");
            notifyConnected(false);
            return false;
        }

        // Save address for auto-reconnect
        lastAddress = address.trim();
        shouldReconnect = true;
        reconnectAttempts = 0;

        // Parse address
        serverPort = DEFAULT_PORT;
        if (lastAddress.contains(":")) {
            String[] parts = lastAddress.split(":");
            if (parts.length == 2) {
                serverIP = parts[0].trim();
                try {
                    serverPort = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid port in address: " + parts[1]);
                    notifyConnected(false);
                    return false;
                }
            } else {
                Log.e(TAG, "Invalid address format: " + lastAddress);
                notifyConnected(false);
                return false;
            }
        } else {
            serverIP = lastAddress;
        }

        connectInternal();
        return true;
    }

    /**
     * Internal connection logic (used by both connect() and auto-reconnect)
     */
    private void connectInternal() {
        Log.i(TAG, "🔄 Connecting to " + serverIP + ":" + serverPort + "...");

        new Thread(() -> {
            try {
                socket = new Socket(serverIP, serverPort);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream(), true);

                isConnected = true;
                reconnectAttempts = 0;
                Log.i(TAG, "✅ Connected to " + serverIP + ":" + serverPort);

                notifyConnected(true);

                // Start read thread
                readThread = new Thread(this::readFromDesktop, "WiFi-Read");
                readThread.start();

            } catch (IOException e) {
                Log.e(TAG, "❌ Connection failed to " + serverIP + ":" + serverPort + " - " + e.getMessage());
                cleanupSocket();
                notifyConnected(false);

                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );

                // Schedule reconnect if not intentionally disconnected
                scheduleReconnect();
            }
        }, "WiFi-Connect").start();
    }

    /**
     * Schedule an auto-reconnect attempt with backoff
     */
    private void scheduleReconnect() {
        if (!shouldReconnect || lastAddress == null) {
            Log.d(TAG, "🚫 Reconnect disabled or no address saved");
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "⚠️ Max reconnect attempts (" + MAX_RECONNECT_ATTEMPTS + ") reached. Giving up.");
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, "Could not reconnect to POS after " + MAX_RECONNECT_ATTEMPTS + " attempts.", Toast.LENGTH_LONG).show()
            );
            return;
        }

        reconnectAttempts++;
        long delay = RECONNECT_DELAY_MS * reconnectAttempts; // Simple backoff
        Log.i(TAG, "🔄 Scheduling reconnect attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + " in " + delay + "ms...");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isConnected && shouldReconnect) {
                Log.i(TAG, "🔄 Auto-reconnecting (attempt " + reconnectAttempts + ")...");
                connectInternal();
            }
        }, delay);
    }

    /**
     * Read data from desktop — persistent loop, handles reconnect on drop
     */
    private void readFromDesktop() {
        Log.d(TAG, "===========================================");
        Log.d(TAG, "👂 READ THREAD STARTED");
        Log.d(TAG, "===========================================");

        try {
            String line;
            int lineCount = 0;

            while (isConnected && (line = input.readLine()) != null) {
                lineCount++;

                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                Log.d(TAG, "📨 LINE #" + lineCount + " RECEIVED");
                Log.d(TAG, "Raw line: [" + line + "]");

                String trimmedLine = line.trim();

                // Handle receipt messages (multi-line)
                if (trimmedLine.contains("===END_RECEIPT===")) {
                    Log.d(TAG, "📄 RECEIPT END MARKER - Processing skipped (implement as needed)");
                    continue;
                }

                // ✅ FIX: Parse the line directly, not an accumulated buffer
                if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                    Log.d(TAG, "🔍 JSON OBJECT DETECTED - Parsing...");
                    parseAndHandleJson(trimmedLine);

                } else if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]")) {
                    Log.d(TAG, "🔍 JSON ARRAY DETECTED - Parsing...");
                    // Unwrap array and try parsing inner object
                    String inner = trimmedLine.substring(1, trimmedLine.length() - 1).trim();
                    if (inner.startsWith("{") && inner.endsWith("}")) {
                        Log.d(TAG, "🔍 Unwrapped inner object - Parsing...");
                        parseAndHandleJson(inner);
                    } else {
                        Log.w(TAG, "⚠️ Unrecognised array content, skipping");
                    }

                } else {
                    Log.d(TAG, "⚠️ Non-JSON line, skipping: " + trimmedLine);
                }

                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }

            Log.d(TAG, "❌ Read loop ended (null line or disconnected)");

        } catch (IOException e) {
            if (isConnected) {
                Log.e(TAG, "❌ IOException in read thread: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            Log.d(TAG, "===========================================");
            Log.d(TAG, "👋 READ THREAD STOPPED");
            Log.d(TAG, "===========================================");

            cleanupSocket();
            notifyConnected(false);

            // ✅ FIX: Auto-reconnect instead of dying permanently
            scheduleReconnect();
        }
    }

    /**
     * Parse a JSON string and dispatch to the correct handler
     */
    private void parseAndHandleJson(String jsonString) {
        try {
            JsonObject json = gson.fromJson(jsonString, JsonObject.class);

            if (json == null) {
                Log.e(TAG, "❌ JSON parsing returned NULL");
                return;
            }

            if (!json.has("type")) {
                Log.e(TAG, "❌ JSON has no 'type' field. Keys: " + json.keySet().toString());
                return;
            }

            String type = json.get("type").getAsString();
            Log.d(TAG, "✅ JSON parsed. Type: [" + type + "]");

            switch (type) {
                case "categories":
                    Log.d(TAG, "🎯 CATEGORIES MESSAGE DETECTED");
                    handleCategoriesReceived(json);
                    break;

                case "scan_ack":
                case "product_ack":
                    String ackMsg = json.has("message") ? json.get("message").getAsString() : "no message";
                    Log.d(TAG, "✅ ACK received: " + ackMsg);
                    break;

                case "error":
                    String errMsg = json.has("message") ? json.get("message").getAsString() : "no message";
                    Log.w(TAG, "⚠️ ERROR from server: " + errMsg);
                    break;

                case "connected":
                    String connMsg = json.has("message") ? json.get("message").getAsString() : "no message";
                    Log.d(TAG, "✅ CONNECTED message: " + connMsg);
                    break;

                default:
                    Log.d(TAG, "ℹ️ Unknown message type: " + type);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ JSON PARSE EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle categories received from desktop
     */
    private void handleCategoriesReceived(JsonObject json) {
        Log.d(TAG, "╔════════════════════════════════════════╗");
        Log.d(TAG, "║      HANDLING CATEGORIES RECEIVED      ║");
        Log.d(TAG, "╚════════════════════════════════════════╝");

        try {
            if (!json.has("data")) {
                Log.e(TAG, "❌ JSON has no 'data' field. Keys: " + json.keySet().toString());
                return;
            }

            JsonArray categoriesArray = json.getAsJsonArray("data");

            if (categoriesArray == null) {
                Log.e(TAG, "❌ Categories array is NULL");
                return;
            }

            Log.d(TAG, "✅ Categories array size: " + categoriesArray.size());

            List<String> categories = new ArrayList<>();
            for (int i = 0; i < categoriesArray.size(); i++) {
                String cat = categoriesArray.get(i).getAsString();
                if (cat != null && !cat.trim().isEmpty()) {
                    categories.add(cat.trim());
                    Log.d(TAG, "  [" + i + "] " + cat.trim());
                }
            }

            Log.d(TAG, "📊 Total categories parsed: " + categories.size());

            // Save to storage
            CategoryStorage.getInstance(context).saveCategories(categories);
            Log.d(TAG, "✅ Saved to CategoryStorage");

            // Broadcast
            Intent intent = new Intent("CATEGORIES_RECEIVED");
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            Log.d(TAG, "✅ Broadcast sent: CATEGORIES_RECEIVED");

            // Toast
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, categories.size() + " categories loaded", Toast.LENGTH_LONG).show()
            );

            Log.d(TAG, "╔════════════════════════════════════════╗");
            Log.d(TAG, "║    CATEGORIES HANDLING COMPLETE ✓      ║");
            Log.d(TAG, "╚════════════════════════════════════════╝");

        } catch (Exception e) {
            Log.e(TAG, "❌ EXCEPTION in handleCategoriesReceived: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send scan data to desktop
     */
    public void sendScan(String code) {
        // Escape any special characters in the barcode to keep JSON valid
        String escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = String.format(
            "{\"type\":\"scan\",\"data\":\"%s\",\"timestamp\":%d}",
            escapedCode,
            System.currentTimeMillis()
        );
        sendData(json);
    }

    /**
     * Send raw data string to desktop
     */
    public void sendData(String data) {
        if (!isConnected || output == null) {
            Log.w(TAG, "❌ Cannot send — not connected");
            return;
        }

        try {
            output.println(data);
            output.flush();

            if (output.checkError()) {
                Log.e(TAG, "❌ PrintWriter error after send — connection may be broken");
                closeConnection();
                return;
            }

            Log.d(TAG, "📤 Sent: " + data.substring(0, Math.min(100, data.length())) + (data.length() > 100 ? "..." : ""));
        } catch (Exception e) {
            Log.e(TAG, "❌ Send failed: " + e.getMessage(), e);
            closeConnection();
        }
    }

    /**
     * Intentionally close the connection (disables auto-reconnect)
     */
    public void closeConnection() {
        shouldReconnect = false; // Intentional — don't auto-reconnect
        cleanupSocket();
        notifyConnected(false);
        Log.i(TAG, "🔌 Connection intentionally closed");
    }

    /**
     * Clean up socket/stream resources without changing shouldReconnect
     */
    private void cleanupSocket() {
        isConnected = false;

        if (readThread != null && readThread.isAlive()) {
            readThread.interrupt();
            try {
                readThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readThread = null;
        }

        try { if (input != null) input.close(); } catch (IOException ignored) {}
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}

        input = null;
        output = null;
        socket = null;

        Log.i(TAG, "🧹 Socket resources cleaned up");
    }

    /**
     * Broadcast connection status change
     */
    private void notifyConnected(boolean connected) {
        Intent intent = new Intent("WIFI_CONNECTED");
        intent.putExtra("connected", connected);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        Log.d(TAG, connected ? "✅ WiFi Connected" : "❌ WiFi Disconnected");

        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, connected ? "Connected to POS!" : "Disconnected from POS", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Check if currently connected
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Get server address in IP:PORT format
     */
    public String getServerAddress() {
        if (serverIP == null) return null;
        return serverIP + ":" + serverPort;
    }
}
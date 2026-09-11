package com.pos.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pos.models.Product;
import com.pos.services.CategoryService;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * WiFi server for desktop POS system
 * Handles scans and product additions from Android app
 */
public class WiFiHandler {

    private static final int BASE_PORT = 8888;
    private static final int MAX_PORT_TRIES = 10;

    private static WiFiHandler instance;

    public static synchronized WiFiHandler getInstance() {
        if (instance == null) {
            instance = new WiFiHandler();
        }
        return instance;
    }

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader input;
    private PrintWriter output;

    private Thread serverThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean isConnected = new AtomicBoolean(false);

    private Consumer<String> onScanReceived;
    private Consumer<Product> onProductReceived;
    private Gson gson = new Gson();
    private int actualPort = -1;

    /**
     * Start WiFi server and listen for connections
     */
    public boolean startListening(Consumer<String> onScanReceived, Consumer<Product> onProductReceived) {
        this.onScanReceived = onScanReceived;
        this.onProductReceived = onProductReceived;

        if (isRunning.get()) {
            return true;
        }

        boolean started = false;
        String localIP = getLocalIP();

        for (int tryPort = BASE_PORT; tryPort < BASE_PORT + MAX_PORT_TRIES; tryPort++) {
            try {
                serverSocket = new ServerSocket(tryPort);
                actualPort = tryPort;

                System.out.println("✅ WiFi Server started on " + localIP + ":" + tryPort);
                System.out.println("📱 Enter this IP in the Android app to connect: " + localIP + ":" + tryPort);
                System.out.println("🔍 Use 'netstat -an | grep " + tryPort + "' to verify listening");

                isRunning.set(true);

                // Start server thread
                serverThread = new Thread(this::acceptConnections, "WiFi-Server");
                serverThread.setDaemon(true);
                serverThread.start();

                started = true;
                break;

            } catch (IOException e) {
                if (e.getMessage().contains("Address already in use")) {
                    System.err.println("⚠️ Port " + tryPort + " in use, trying next...");
                    continue;
                } else {
                    System.err.println("❌ Failed to start WiFi server: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
        }

        if (!started) {
            System.err.println("❌ Failed to find available port after " + MAX_PORT_TRIES + " tries");
            System.err.println("💡 Try closing other applications using ports " + BASE_PORT + " to "
                    + (BASE_PORT + MAX_PORT_TRIES - 1));
            return false;
        }

        return true;
    }

    /**
     * Get the actual port being used
     */
    public int getPort() {
        return actualPort;
    }

    /**
     * Accept incoming connections
     */
    private void acceptConnections() {
        System.out.println("🚀 acceptConnections thread started on port " + actualPort);
        while (isRunning.get()) {
            try {
                System.out.println("⏳ Waiting for connection on port " + actualPort + "...");

                // Accept client connection
                clientSocket = serverSocket.accept();
                String clientIP = clientSocket.getInetAddress().getHostAddress();

                System.out.println("✅ Android device connected: " + clientIP + " on port " + actualPort);
                System.out.println("🔌 Setting up streams...");

                // Setup streams
                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                output = new PrintWriter(clientSocket.getOutputStream(), true);
                isConnected.set(true);

                System.out.println("📡 Streams ready. isConnected = " + isConnected.get());

                // Send welcome message
                sendData("{\"type\":\"connected\",\"message\":\"Connected to " + com.pos.Branding.APP_NAME + "\"}");

                // FIXED: Send all categories to the app for dropdown population
                sendCategoriesToApp();

                System.out.println("🚀 Starting handleClient()...");
                // Handle this connection
                handleClient();

                System.out.println("🏁 handleClient() ended - connection closed");

            } catch (IOException e) {
                if (isRunning.get()) {
                    System.err.println("❌ Connection error on port " + actualPort + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        System.out.println("🛑 acceptConnections loop ended");
    }

    /**
     * Send all available categories to the connected Android app
     */
    private void sendCategoriesToApp() {
        try {
            CategoryService categoryService = new CategoryService();
            ObservableList<String> categories = categoryService.getAllCategories();

            // NEW: Print each category being sent
            System.out.println("📂 Categories being sent:");
            for (String category : categories) {
                System.out.println("   - " + category);
            }

            JsonObject categoriesJson = new JsonObject();
            categoriesJson.addProperty("type", "categories");

            JsonArray categoriesArray = new JsonArray();
            for (String category : categories) {
                categoriesArray.add(category);
            }
            categoriesJson.add("data", categoriesArray);

            String jsonMessage = gson.toJson(categoriesJson);

            // OPTIONAL: Also print the full JSON for debugging
            System.out.println("📄 Full categories JSON: " + jsonMessage);

            sendData(jsonMessage);

            System.out.println("📂 Sent " + categories.size() + " categories to Android app");
        } catch (Exception e) {
            System.err.println("❌ Failed to send categories: " + e.getMessage());
            // Send error response
            JsonObject errorJson = new JsonObject();
            errorJson.addProperty("type", "error");
            errorJson.addProperty("message", "Failed to fetch categories");
            sendData(gson.toJson(errorJson));
        }
    }

    /**
     * Handle client communication
     */
    private void handleClient() {
        StringBuilder messageBuilder = new StringBuilder();

        System.out.println("===========================================");
        System.out.println("👂 HANDLE CLIENT STARTED");
        System.out.println("===========================================");

        System.out.println("👂 Listening for data from Android...");

        try {
            String line;
            int lineCount = 0;

            while (isConnected.get() && (line = input.readLine()) != null) {
                lineCount++;

                System.out.println("");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("📨 LINE #" + lineCount + " RECEIVED");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("Raw line: [" + line + "]");
                System.out.println("Line length: " + line.length());
                System.out.println("Starts with '{': " + line.trim().startsWith("{"));
                System.out.println("Starts with '[': " + line.trim().startsWith("["));
                System.out.println("Ends with '}': " + line.trim().endsWith("}"));
                System.out.println("Ends with ']': " + line.trim().endsWith("]"));

                messageBuilder.append(line).append("\n");

                // Check for complete receipt
                String fullMessage = messageBuilder.toString().trim();
                if (fullMessage.contains("===END_RECEIPT===")) {
                    System.out.println("📄 Receipt detected - processing...");
                    // Process receipt if needed
                    messageBuilder.setLength(0);
                    continue;
                }

                // Parse JSON messages (check trimmed full buffer for complete JSON object or
                // array)
                String trimmedBuffer = messageBuilder.toString().trim();
                boolean isComplete = false;
                String jsonToProcess = null;

                if (trimmedBuffer.startsWith("{") && trimmedBuffer.endsWith("}")) {
                    // Single object
                    System.out.println("🔍 SINGLE JSON OBJECT DETECTED");
                    jsonToProcess = trimmedBuffer;
                    isComplete = true;
                } else if (trimmedBuffer.startsWith("[") && trimmedBuffer.endsWith("]")) {
                    // Array - extract first object if single-item
                    System.out.println("🔍 JSON ARRAY DETECTED - Extracting inner object");
                    try {
                        JsonArray jsonArray = gson.fromJson(trimmedBuffer, JsonArray.class);
                        if (jsonArray.size() > 0) {
                            JsonObject innerJson = jsonArray.get(0).getAsJsonObject();
                            jsonToProcess = gson.toJson(innerJson);
                            isComplete = true;
                            System.out.println("✅ Extracted inner JSON: " + jsonToProcess);
                        } else {
                            System.err.println("⚠️ Empty JSON array");
                        }
                    } catch (Exception arrayEx) {
                        System.err.println("❌ Failed to parse array: " + arrayEx.getMessage());
                    }
                }

                if (isComplete && jsonToProcess != null) {
                    System.out.println("🔍 PROCESSING EXTRACTED JSON");
                    try {
                        JsonObject json = gson.fromJson(jsonToProcess, JsonObject.class);

                        if (json == null) {
                            System.err.println("❌ JSON parsing returned NULL");
                        } else if (!json.has("type")) {
                            System.err.println("❌ JSON has no 'type' field");
                            System.err.println("JSON keys: " + json.keySet().toString());
                        } else {
                            String type = json.get("type").getAsString();
                            System.out.println("✅ JSON PARSED SUCCESSFULLY");
                            System.out.println("Type: [" + type + "]");

                            processReceivedData(json);
                        }
                    } catch (Exception jsonEx) {
                        System.err.println("❌ JSON PARSE EXCEPTION: " + jsonEx.getClass().getName());
                        System.err.println("Exception message: " + jsonEx.getMessage());
                        System.err.println("JSON to process: " + jsonToProcess);
                        jsonEx.printStackTrace();
                    }

                    messageBuilder.setLength(0);
                } else {
                    System.out.println(
                            "⚠️ NOT COMPLETE JSON - Accumulating in buffer (size: " + trimmedBuffer.length() + ")");
                    System.out.println(
                            "Buffer preview: " + trimmedBuffer.substring(0, Math.min(200, trimmedBuffer.length())));
                }

                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("");
            }

            System.out.println("❌ Read loop ended (connection closed or null line)");

        } catch (IOException e) {
            if (isConnected.get()) {
                System.err.println("❌ IOException in handleClient: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            System.out.println("===========================================");
            System.out.println("👋 HANDLE CLIENT STOPPED");
            System.out.println("===========================================");
            closeConnection();
        }
    }

    /**
     * Process received data from Android
     */
    private void processReceivedData(JsonObject json) {
        try {
            if (json == null || !json.has("type")) {
                System.err.println("⚠️ Invalid JSON received");
                return;
            }

            String type = json.get("type").getAsString();
            System.out.println("📥 Processed message type: " + type);

            switch (type) {
                case "scan":
                    handleScan(json);
                    break;

                case "add_product":
                    handleAddProduct(json);
                    break;

                case "request_categories":
                    // Re-send categories when requested
                    System.out.println("📂 Categories re-requested by Android");
                    sendCategoriesToApp();
                    break;

                default:
                    System.out.println("⚠️ Unknown message type: " + type);
                    System.out.println("Full JSON: " + gson.toJson(json));
            }

        } catch (Exception e) {
            System.err.println("❌ Error processing data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle barcode/QR scan
     */
    private void handleScan(JsonObject json) {
        if (!json.has("data")) {
            System.err.println("⚠️ Scan data missing");
            return;
        }

        String code = json.get("data").getAsString();
        System.out.println("📦 Scan received: " + code);

        if (onScanReceived != null) {
            onScanReceived.accept(code);
        }

        // Send acknowledgment
        sendData("{\"type\":\"scan_ack\",\"message\":\"Scan received\"}");
    }

    /**
     * Handle product addition from Android
     */
    private void handleAddProduct(JsonObject json) {
        if (!json.has("data")) {
            System.err.println("⚠️ Product data missing");
            sendData("{\"type\":\"error\",\"message\":\"Product data missing\"}");
            return;
        }

        try {
            JsonObject productData = json.getAsJsonObject("data");

            // Extract product fields
            String name = productData.get("name").getAsString();
            String category = productData.get("category").getAsString();
            String barcode = productData.get("barcode").getAsString();
            int quantity = productData.get("quantity").getAsInt();

            // FIXED: Handle both dot and comma for price by getting as string and
            // normalizing
            JsonElement priceElement = productData.get("price");
            String priceStr;
            if (priceElement.isJsonPrimitive() && priceElement.getAsJsonPrimitive().isNumber()) {
                // If it's parsed as number already (dot used), get as double
                priceStr = String.valueOf(priceElement.getAsDouble());
            } else {
                // Otherwise, get as string and normalize comma to dot
                priceStr = priceElement.getAsString().replace(",", ".");
            }
            double price = Double.parseDouble(priceStr);

            System.out.println("➕ Product received from Android:");
            System.out.println("   Name: " + name);
            System.out.println("   Category: " + category);
            System.out.println("   Barcode: " + barcode);
            System.out.println("   Quantity: " + quantity);
            System.out.println("   Price: R " + price + " (parsed from: '" + priceStr + "')");

            // Create Product object
            Product product = new Product();
            product.setProductName(name);
            product.setBarCode(barcode);
            product.setQuantity(quantity);
            product.setPrice(BigDecimal.valueOf(price));

            // Store category name temporarily (will need to be resolved to ID)
            product.setCategoryName(category);

            // Call callback to handle product addition
            if (onProductReceived != null) {
                onProductReceived.accept(product);

                // Send success response
                sendData("{\"type\":\"product_ack\",\"message\":\"Product received and added to inventory\"}");
            } else {
                System.err.println("⚠️ No product handler registered");
                sendData("{\"type\":\"error\",\"message\":\"No product handler available\"}");
            }

        } catch (Exception e) {
            System.err.println("❌ Error processing product: " + e.getMessage());
            e.printStackTrace();
            sendData("{\"type\":\"error\",\"message\":\"Failed to process product: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Send receipt to Android device
     */
    public void sendReceipt(String receiptText) {
        sendData(receiptText + "\n===END_RECEIPT===\n");
    }

    /**
     * Send data to Android device
     */
    public void sendData(String data) {
        if (!isConnected.get() || output == null) {
            System.err.println("❌ Not connected - cannot send data");
            return;
        }

        try {
            output.println(data);
            output.flush();
            System.out.println("📤 Sent to Android (" + data.length() + " bytes): "
                    + data.substring(0, Math.min(100, data.length())) + (data.length() > 100 ? "..." : ""));
        } catch (Exception e) {
            System.err.println("❌ Failed to send data: " + e.getMessage());
            e.printStackTrace();
            closeConnection();
        }
    }

    /**
     * Close current connection
     */
    public void closeConnection() {
        isConnected.set(false);

        try {
            if (input != null)
                input.close();
        } catch (IOException e) {
            System.err.println("Error closing input: " + e.getMessage());
        }

        try {
            if (output != null)
                output.close();
        } catch (Exception e) {
            System.err.println("Error closing output: " + e.getMessage());
        }

        try {
            if (clientSocket != null)
                clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }

        input = null;
        output = null;
        clientSocket = null;

        System.out.println("👋 Android device disconnected");
    }

    /**
     * Stop server and cleanup
     */
    public void stopListening() {
        isRunning.set(false);
        closeConnection();

        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverThread = null;
        }

        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }

        System.out.println("🛑 WiFi server stopped");
    }

    /**
     * Full cleanup (call on app shutdown)
     */
    public void cleanup() {
        stopListening();

        // Extra safety: Force-close server socket if still open
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("⚠️ Failed to close server socket: " + e.getMessage());
        }

        // Reset state
        actualPort = -1;
        isRunning.set(false);
        isConnected.set(false);
        onScanReceived = null;
        onProductReceived = null;

        System.out.println("🧹 WiFiHandler fully cleaned up");
    }

    /**
     * Check if client is connected
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    /**
     * Check if server is running
     */
    public boolean isServerRunning() {
        return isRunning.get();
    }

    /**
     * Get connection status
     */
    public String getStatus() {
        if (isConnected.get()) {
            return "Connected";
        } else if (isRunning.get()) {
            return "Waiting for connection";
        } else {
            return "Stopped";
        }
    }

    /**
     * Register product callback without restarting the server.
     * Call this when InventoryView is opened and server is already running.
     */
    public void setProductCallback(Consumer<Product> onProductReceived) {
        this.onProductReceived = onProductReceived;
        System.out.println("✅ Product callback registered: " + (onProductReceived != null ? "SET" : "CLEARED"));
    }

    /**
     * Register scan callback without restarting the server.
     */
    public void setScanCallback(Consumer<String> onScanReceived) {
        this.onScanReceived = onScanReceived;
        System.out.println("✅ Scan callback registered: " + (onScanReceived != null ? "SET" : "CLEARED"));
    }

    /**
     * The string encoded in the "connect" QR code shown on the desktop.
     * The scanner app strips the {@code POS:} prefix and connects to the rest.
     */
    public String getConnectionPayload() {
        int port = actualPort > 0 ? actualPort : BASE_PORT;
        return "POS:" + getLocalIP() + ":" + port;
    }

    /**
     * Get local IP address
     */
    public String getLocalIP() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isLoopback() && ni.isUp()) {
                    for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                        InetAddress inetAddr = addr.getAddress();
                        if (inetAddr instanceof Inet4Address) {
                            String ip = inetAddr.getHostAddress();
                            if (ip.startsWith("192.168.")) {
                                return ip;
                            }
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
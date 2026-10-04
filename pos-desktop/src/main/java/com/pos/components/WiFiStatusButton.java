package com.pos.components;

import com.pos.services.WiFiHandler;
import com.pos.utils.Icons;
import com.pos.utils.QRCodeUtil;
import com.pos.utils.Theme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Optional;

public class WiFiStatusButton extends Button {
    private WiFiHandler wifiHandler;
    private boolean isServerRunning = false;
    private boolean isConnected = false;
    
    public WiFiStatusButton() {
        this.wifiHandler = WiFiHandler.getInstance();
        
        setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
        setText("WiFi: Offline");
        
        setOnAction(e -> showWiFiDialog());
        
        // Check initial status
        updateStatus();
        
        // Update status periodically
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(this::updateStatus);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }, "WiFi-Status-Monitor").start();
    }
    
    private void updateStatus() {
        boolean serverRunning = wifiHandler.isServerRunning();
        boolean connected = wifiHandler.isConnected();
        
        if (serverRunning != isServerRunning || connected != isConnected) {
            isServerRunning = serverRunning;
            isConnected = connected;
            
            if (!serverRunning) {
                // Red: Not connected to any WiFi (server offline)
                setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
                setText("WiFi: Offline");
            } else if (!connected) {
                // Orange: System ready to connect
                setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
                setText("WiFi: Ready");
            } else {
                // Green: Connected to a device
                setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
                setText("WiFi: Connected");
            }
        }
    }
    
    private void showWiFiDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("WiFi Connection Status");
        dialog.setHeaderText("POS WiFi Server");

        String serverIP = wifiHandler.getLocalIP();
        int serverPort = wifiHandler.getPort() > 0 ? wifiHandler.getPort() : 8888;

        // ── Left column: status + scan-to-connect QR — the "glance at it" half ──
        VBox left = new VBox(14);
        left.setAlignment(Pos.TOP_CENTER);
        left.setPrefWidth(220);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER);
        Node statusIcon;
        Label statusText;
        if (!isServerRunning) {
            statusIcon = Icons.tinted(Icons.dot(16), Theme.DANGER);
            statusText = new Label("Offline");
            statusText.setTextFill(Color.web(Theme.DANGER));
        } else if (!isConnected) {
            statusIcon = Icons.tinted(Icons.dot(16), Theme.WARNING);
            statusText = new Label("Ready");
            statusText.setTextFill(Color.web(Theme.WARNING));
        } else {
            statusIcon = Icons.tinted(Icons.dot(16), Theme.SUCCESS);
            statusText = new Label("Connected");
            statusText.setTextFill(Color.web(Theme.SUCCESS));
        }
        statusText.setFont(Font.font("System", FontWeight.BOLD, 16));
        statusBox.getChildren().addAll(statusIcon, statusText);
        left.getChildren().add(statusBox);

        if (isServerRunning) {
            String payload = wifiHandler.getConnectionPayload();
            String base64 = QRCodeUtil.generateQRCode(payload);
            Image qrImg = base64 != null ? QRCodeUtil.getQRCodeImage(base64) : null;
            if (qrImg != null) {
                ImageView qr = new ImageView(qrImg);
                qr.setFitWidth(150);
                qr.setFitHeight(150);
                qr.setPreserveRatio(true);

                Label qrCaption = new Label("Scan in the POS Scanner app");
                qrCaption.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
                qrCaption.setTextFill(Color.web(Theme.BRAND));
                qrCaption.setWrapText(true);
                qrCaption.setAlignment(Pos.CENTER);
                qrCaption.setStyle("-fx-text-alignment: center;");

                VBox qrBox = new VBox(6, qr, qrCaption);
                qrBox.setAlignment(Pos.CENTER);
                left.getChildren().add(qrBox);
            }
        }

        // ── Right column: everything textual ────────────────────────────────────
        VBox right = new VBox(10);
        right.setPrefWidth(300);

        Label urlLabel = new Label(serverIP + ":" + serverPort);
        urlLabel.setFont(Font.font("Courier New", FontWeight.BOLD, 14));
        urlLabel.setStyle(Theme.chip(Theme.BRAND_TINT, Theme.BRAND_DARK));
        right.getChildren().add(urlLabel);

        String devicesText = !isServerRunning ? "Server offline"
                : isConnected ? "1 device connected" : "No devices connected";
        Label devicesLabel = new Label(devicesText);
        devicesLabel.setFont(Theme.body());
        devicesLabel.setTextFill(Color.web(Theme.TEXT_MUTED));
        right.getChildren().add(devicesLabel);

        Label instructions = new Label(
            "1. Make sure the phone is on the same WiFi as this PC\n" +
            "2. Open the POS Scanner app and tap 'Connect'\n" +
            "3. Tap 'Scan QR' and point it at the code\n" +
            "   (or enter " + serverIP + ":" + serverPort + " manually)\n" +
            "4. Start scanning products"
        );
        instructions.setWrapText(true);
        instructions.setFont(Theme.body());
        instructions.setTextFill(Color.web(Theme.TEXT_MUTED));
        right.getChildren().add(instructions);

        HBox columns = new HBox(24, left, right);
        columns.setPadding(new Insets(16, 20, 8, 20));

        // ── Buttons — always visible, below the two columns ─────────────────────
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(4, 20, 16, 20));

        Button serverBtn;
        if (!isServerRunning) {
            serverBtn = new Button("Start Server");
            Theme.hover(serverBtn, Theme.successButton(), Theme.successHover());
            serverBtn.setOnAction(e -> {
                wifiHandler.startListening(null, null);
                updateStatus();
                showAlert("Server Started", "WiFi server has been started", Alert.AlertType.INFORMATION);
            });
        } else {
            serverBtn = new Button("Restart Server");
            Theme.hover(serverBtn, Theme.secondaryButton(), Theme.secondaryHover());
            serverBtn.setOnAction(e -> {
                wifiHandler.stopListening();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {}
                wifiHandler.startListening(null, null);
                updateStatus();
                showAlert("Server Restarted", "WiFi server has been restarted", Alert.AlertType.INFORMATION);
            });
        }

        Button copyBtn = new Button("Copy Address");
        Theme.hover(copyBtn, Theme.primaryButton(), Theme.primaryHover());
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
            clipContent.putString(serverIP + ":" + serverPort);
            clipboard.setContent(clipContent);
            showAlert("Copied", "Server address copied to clipboard", Alert.AlertType.INFORMATION);
        });

        buttonBox.getChildren().addAll(serverBtn, copyBtn);

        VBox content = new VBox(columns, new Separator(), buttonBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(580);
        dialog.showAndWait();
    }
    
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
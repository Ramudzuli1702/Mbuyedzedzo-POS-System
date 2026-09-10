package com.pos.components;

import com.pos.services.WiFiHandler;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
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
        
        setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
        setText("📡 WiFi: Offline");
        
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
                setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
                setText("📡 WiFi: Offline");
            } else if (!connected) {
                // Orange: System ready to connect
                setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
                setText("📡 WiFi: Ready");
            } else {
                // Green: Connected to a device
                setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
                setText("📡 WiFi: Connected");
            }
        }
    }
    
    private void showWiFiDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("WiFi Connection Status");
        dialog.setHeaderText("POS WiFi Server");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);
        
        // Status indicator
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Label statusIcon;
        Label statusText;
        if (!isServerRunning) {
            statusIcon = new Label("🔴");
            statusText = new Label("Offline");
            statusText.setTextFill(Color.web("#e74c3c"));
        } else if (!isConnected) {
            statusIcon = new Label("🟠");
            statusText = new Label("Ready");
            statusText.setTextFill(Color.web("#f39c12"));
        } else {
            statusIcon = new Label("🟢");
            statusText = new Label("Connected");
            statusText.setTextFill(Color.web("#27ae60"));
        }
        statusIcon.setFont(Font.font(24));
        statusText.setFont(Font.font("System", FontWeight.BOLD, 16));
        statusBox.getChildren().addAll(statusIcon, statusText);
        
        content.getChildren().add(statusBox);
        content.getChildren().add(new Separator());
        
        // Server information
        Label serverTitle = new Label("Server Information:");
        serverTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        content.getChildren().add(serverTitle);
        
        VBox serverInfo = new VBox(8);
        serverInfo.setPadding(new Insets(0, 0, 0, 20));
        
        String serverIP = wifiHandler.getLocalIP();
        int serverPort = wifiHandler.getPort() > 0 ? wifiHandler.getPort() : 8888;
        
        Label ipLabel = new Label("IP Address: " + serverIP);
        Label portLabel = new Label("Port: " + serverPort);
        Label urlLabel = new Label("Server: " + serverIP + ":" + serverPort);
        urlLabel.setFont(Font.font("Courier New", 14));
        urlLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 8; -fx-background-radius: 5;");
        
        serverInfo.getChildren().addAll(ipLabel, portLabel, urlLabel);
        content.getChildren().add(serverInfo);
        
        content.getChildren().add(new Separator());
        
        // Connected devices
        Label devicesTitle = new Label("Connected Devices:");
        devicesTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        content.getChildren().add(devicesTitle);
        
        String devicesText;
        if (!isServerRunning) {
            devicesText = "Server offline";
        } else if (isConnected) {
            devicesText = "1 device connected";
        } else {
            devicesText = "No devices connected";
        }
        Label devicesLabel = new Label(devicesText);
        devicesLabel.setPadding(new Insets(0, 0, 0, 20));
        content.getChildren().add(devicesLabel);
        
        content.getChildren().add(new Separator());
        
        // Instructions
        Label instructionsTitle = new Label("Instructions:");
        instructionsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        content.getChildren().add(instructionsTitle);
        
        Label instructions = new Label(
            "1. Open the Android POS Scanner app\n" +
            "2. Go to Settings\n" +
            "3. Enter the server address: " + serverIP + ":" + serverPort + "\n" +
            "4. Tap 'Connect'\n" +
            "5. Start scanning products"
        );
        instructions.setWrapText(true);
        instructions.setPadding(new Insets(0, 0, 0, 20));
        content.getChildren().add(instructions);
        
        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button serverBtn;
        if (!isServerRunning) {
            serverBtn = new Button("▶️ Start Server");
            serverBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 8 15;");
            serverBtn.setOnAction(e -> {
                wifiHandler.startListening(null, null);
                updateStatus();
                showAlert("Server Started", "WiFi server has been started", Alert.AlertType.INFORMATION);
            });
        } else {
            serverBtn = new Button("🔄 Restart Server");
            serverBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 15;");
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
        
        Button copyBtn = new Button("📋 Copy Address");
        copyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 8 15;");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
            clipContent.putString(serverIP + ":" + serverPort);
            clipboard.setContent(clipContent);
            showAlert("Copied", "Server address copied to clipboard", Alert.AlertType.INFORMATION);
        });
        
        buttonBox.getChildren().addAll(serverBtn, copyBtn);
        content.getChildren().add(buttonBox);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
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
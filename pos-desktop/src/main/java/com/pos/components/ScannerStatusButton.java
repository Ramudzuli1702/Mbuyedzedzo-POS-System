package com.pos.components;

import com.pos.utils.ScannerDetection;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;

/**
 * Mirrors WiFiStatusButton's look and polling pattern, but for a USB barcode
 * scanner. There's no reliable "yes/no" hardware answer (see
 * ScannerDetection's own notes), so this shows "Detected" the moment either
 * signal fires and otherwise reads as a neutral "Not detected" rather than
 * an alarming red — not seeing one yet doesn't mean something is broken,
 * typing/the phone scanner both still work fine.
 */
public class ScannerStatusButton extends Button {
    private boolean detected = false;
    private boolean firstCheck = true;

    public ScannerStatusButton() {
        setStyle(neutralStyle());
        setText("Scanner: Checking...");
        setOnAction(e -> showInfo());

        new Thread(() -> {
            while (true) {
                boolean isDetected = ScannerDetection.isConnected();
                Platform.runLater(() -> updateStatus(isDetected));
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }, "Scanner-Status-Monitor").start();
    }

    private void updateStatus(boolean isDetected) {
        if (isDetected == detected && !firstCheck) return;
        detected = isDetected;
        firstCheck = false;

        if (detected) {
            setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;"
                    + "-fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;");
            setText("Scanner: Detected");
        } else {
            setStyle(neutralStyle());
            setText("Scanner: Not detected");
        }
    }

    private String neutralStyle() {
        return "-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 8 15; -fx-background-radius: 20; -fx-cursor: hand;";
    }

    private void showInfo() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("USB Barcode Scanner");
        alert.setHeaderText(detected ? "A scanner is detected" : "No scanner detected yet");
        alert.setContentText(detected
                ? "A USB barcode scanner looks to be connected, or one has been used "
                  + "successfully this session. It works anywhere in the app without needing "
                  + "a particular field focused — just scan."
                : "No USB barcode scanner has been detected yet. This isn't necessarily a "
                  + "problem: it updates automatically once a scanner is plugged in or first "
                  + "used, and you can always type a barcode in by hand or use the Android "
                  + "phone scanner in the meantime.");
        alert.showAndWait();
    }
}

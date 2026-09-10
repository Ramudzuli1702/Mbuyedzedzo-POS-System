package com.pos.dialogs;

import com.pos.services.CommunicationsService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class MarketingDialog {

    public static void show(CommunicationsService commService) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Send Marketing Email");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(520);

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        Label title = new Label("📣 Send Marketing Email to All Opted-In Customers");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setWrapText(true);

        TextField subjectField = new TextField();
        subjectField.setPromptText("Subject line...");
        subjectField.setStyle("-fx-padding: 8; -fx-font-size: 13;");

        Label bodyLabel = new Label("Email body (basic HTML supported):");
        bodyLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText(
            "<p>We have an exciting offer for you!</p>\n" +
            "<p><strong>20% off</strong> all items this weekend.</p>"
        );
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(8);
        bodyArea.setStyle("-fx-font-size: 12;");

        Label statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 12;");

        Button sendBtn = new Button("🚀 Send to All Opted-In Customers");
        sendBtn.setStyle(
            "-fx-background-color: #667eea; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;"
        );

        sendBtn.setOnAction(e -> {
            String subject = subjectField.getText().trim();
            String body    = bodyArea.getText().trim();

            if (subject.isEmpty() || body.isEmpty()) {
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");
                statusLabel.setText("⚠️ Please fill in both subject and body.");
                return;
            }

            // Confirm before blasting
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Email Blast");
            confirm.setHeaderText("Send marketing email to all opted-in customers?");
            confirm.setContentText("Subject: " + subject + "\n\nThis cannot be undone.");

            confirm.showAndWait().ifPresent(response -> {
                if (response != ButtonType.OK) return;

                sendBtn.setDisable(true);
                statusLabel.setStyle("-fx-text-fill: #2980b9; -fx-font-size: 12;");
                statusLabel.setText("📤 Sending... please wait.");

                // Run on background thread — never block JavaFX thread
                new Thread(() -> {
                    int count = commService.sendMarketingBlast(subject, body, 1);
                    Platform.runLater(() -> {
                        sendBtn.setDisable(false);
                        if (count > 0) {
                            statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12;");
                            statusLabel.setText("✅ Successfully sent to " + count + " customer(s).");
                        } else {
                            statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");
                            statusLabel.setText("❌ No emails sent. Check console for errors.");
                        }
                    });
                }, "Marketing-Blast").start();
            });
        });

        content.getChildren().addAll(
            title,
            new Label("Subject:"), subjectField,
            bodyLabel, bodyArea,
            sendBtn, statusLabel
        );

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
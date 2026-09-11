package com.pos.views;

import com.pos.Branding;
import com.pos.Edition;
import com.pos.license.LicenseClient.LicenseServerException;
import com.pos.license.LicenseManager;
import com.pos.utils.BrandAssets;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Shown before login when this copy isn't activated. The user enters a licence
 * key or starts a trial; on success {@code onActivated} runs (→ LoginView).
 */
public class ActivationView {

    private final Stage stage;
    private final LicenseManager manager;
    private final Runnable onActivated;
    private final String reason;

    public ActivationView(Stage stage, LicenseManager manager, String reason, Runnable onActivated) {
        this.stage = stage;
        this.manager = manager;
        this.reason = reason;
        this.onActivated = onActivated;
    }

    public void show() {
        // logoMark is the truly transparent (RGBA) asset — logo() is flat RGB
        // with a baked-in white background.
        var logo = BrandAssets.logoMark(96);

        Label title = new Label("Activate " + Branding.APP_NAME);
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#0f766e"));

        Label edition = new Label(Edition.current().isRetail() ? "Retail edition" : "Standard edition");
        edition.setTextFill(Color.web("#64748b"));

        Label why = new Label(reason);
        why.setWrapText(true);
        why.setTextFill(Color.web("#475569"));

        Label keyLbl = new Label("Licence key");
        keyLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        TextField keyField = new TextField();
        keyField.setPromptText("POS-2026-XXXX-XXXX-XXXX");
        keyField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 14; -fx-padding: 10;");

        Label status = new Label();
        status.setWrapText(true);
        status.setMinHeight(36);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(22, 22);
        spinner.setVisible(false);

        Button activateBtn = primary("Activate");
        Button trialBtn = secondary("Start 30-day trial");

        // Advanced: server URL
        TextField serverField = new TextField(manager.serverUrl());
        serverField.setStyle("-fx-font-size: 12; -fx-padding: 6;");
        TitledPane advanced = new TitledPane("Advanced", new VBox(6,
                new Label("Licensing server"), serverField));
        advanced.setExpanded(false);

        Runnable syncServer = () -> {
            String u = serverField.getText().trim();
            if (!u.isBlank() && !u.equals(manager.serverUrl())) manager.setServerUrl(u);
        };

        activateBtn.setOnAction(e -> {
            String key = keyField.getText().trim();
            if (key.isEmpty()) { status.setText("Enter your licence key."); return; }
            syncServer.run();
            run(spinner, activateBtn, trialBtn, status, () -> manager.activate(key));
        });

        trialBtn.setOnAction(e -> {
            syncServer.run();
            run(spinner, activateBtn, trialBtn, status, manager::startTrial);
        });

        HBox actions = new HBox(10, activateBtn, trialBtn, spinner);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, title, edition, new Separator(), why,
                keyLbl, keyField, actions, status, advanced);
        if (logo != null) {
            // A raw ImageView gets stretched to the VBox's full width, and a
            // StackPane wrapper alone doesn't stop that — it just passes the
            // stretch on to its child. Capping the wrapper's own max width to
            // its content size is what actually stops it, so VBox has only a
            // logo-sized box to center.
            StackPane logoBox = new StackPane(logo);
            logoBox.setAlignment(Pos.CENTER);
            logoBox.setMaxWidth(Region.USE_PREF_SIZE);
            card.getChildren().add(0, logoBox);
        }
        card.setPadding(new Insets(32));
        card.setMaxWidth(460);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 12;");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: #f1f5f9;");
        root.setPadding(new Insets(40));

        stage.setScene(new Scene(root, 900, 620));
        stage.centerOnScreen();
        stage.show();
    }

    private void run(ProgressIndicator spinner, Button a, Button b, Label status, ThrowingRunnable op) {
        spinner.setVisible(true);
        a.setDisable(true); b.setDisable(true);
        status.setTextFill(Color.web("#475569"));
        status.setText("Contacting the licensing server…");

        Thread t = new Thread(() -> {
            String error = null;
            try {
                op.run();
            } catch (LicenseServerException ex) {
                error = ex.getMessage();
            } catch (RuntimeException ex) {
                error = ex.getMessage() != null ? ex.getMessage() : "Activation failed.";
            }
            final String fError = error;
            Platform.runLater(() -> {
                spinner.setVisible(false);
                a.setDisable(false); b.setDisable(false);
                if (fError == null) {
                    status.setTextFill(Color.web("#16a34a"));
                    status.setText("Activated. Starting…");
                    onActivated.run();
                } else {
                    status.setTextFill(Color.web("#dc2626"));
                    status.setText(fError);
                }
            });
        }, "License-Activate");
        t.setDaemon(true);
        t.start();
    }

    private static Button primary(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 10 22; -fx-background-radius: 8; -fx-cursor: hand;");
        return b;
    }

    private static Button secondary(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #0f172a; -fx-font-weight: bold;"
                + "-fx-padding: 10 22; -fx-background-radius: 8; -fx-cursor: hand;");
        return b;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run(); }
}

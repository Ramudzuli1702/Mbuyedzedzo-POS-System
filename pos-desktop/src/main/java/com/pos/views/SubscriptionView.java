package com.pos.views;

import com.pos.license.LicenseManager;
import com.pos.license.LicenseToken;
import com.pos.models.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZoneId;

/**
 * Subscription / licence management — its own nav item rather than a card
 * buried in Settings, since this is what a shop owner actually opens when
 * they want to know "is my licence still good" or "where do I renew".
 * Purchasing/renewing itself happens on the licensing website
 * (Branding/LicenseManager.serverUrl() is that same host) — this view's
 * job is status, and getting there in one click.
 */
public class SubscriptionView {

    private final User currentUser;
    private final LicenseManager manager = new LicenseManager();

    public SubscriptionView(User user) {
        this.currentUser = user;
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());
        layout.setCenter(buildContent());
        return layout;
    }

    private HBox createTopBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(20));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        Label title = new Label("🔑  Subscription");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));
        bar.getChildren().add(title);
        return bar;
    }

    private ScrollPane buildContent() {
        LicenseManager.Status st = manager.check();
        LicenseToken tok = st.token();

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setMaxWidth(640);

        content.getChildren().add(statusBanner(st, tok));
        content.getChildren().add(detailsCard(tok));

        if (tok != null && "SUBSCRIPTION".equalsIgnoreCase(tok.licenseType)) {
            content.getChildren().add(renewalCard(tok));
        }

        content.getChildren().add(actionsCard(tok));

        VBox centered = new VBox(content);
        centered.setAlignment(Pos.TOP_CENTER);
        centered.setPadding(new Insets(0, 24, 24, 24));

        ScrollPane scroll = new ScrollPane(centered);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f5f7fa; -fx-background-color: #f5f7fa;");
        return scroll;
    }

    // ── Status banner ────────────────────────────────────────────────────────

    private VBox statusBanner(LicenseManager.Status st, LicenseToken tok) {
        String bg, fg, headline, sub;
        boolean expiringSoon = tok != null && "SUBSCRIPTION".equalsIgnoreCase(tok.licenseType)
                && tok.licenseExpiry != null && daysUntil(tok.licenseExpiry) <= 7;

        switch (st.state()) {
            case NEEDS_ACTIVATION -> {
                bg = "#fee2e2"; fg = "#991b1b";
                headline = "Not activated";
                // LicenseManager already worked out exactly why (expired, wrong
                // machine, wrong product, stale too long, never activated) —
                // show that instead of a generic line that'd hide the real reason.
                sub = st.message();
            }
            case GRACE -> {
                bg = "#fef3c7"; fg = "#92400e";
                headline = "Active — reconnect soon";
                sub = st.message();
            }
            default -> {
                if (expiringSoon) {
                    bg = "#fef3c7"; fg = "#92400e";
                    headline = "Active — renews in " + daysUntil(tok.licenseExpiry) + " day(s)";
                    sub = "Renew before it expires to avoid any interruption.";
                } else {
                    bg = "#dcfce7"; fg = "#166534";
                    headline = "Active";
                    sub = tok != null && tok.licenseExpiry == null
                            ? "Perpetual licence — no expiry." : "Your subscription is in good standing.";
                }
            }
        }

        VBox card = new VBox(4);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 10;");
        Label h = new Label(headline);
        h.setFont(Font.font("System", FontWeight.BOLD, 18));
        h.setTextFill(Color.web(fg));
        Label s = new Label(sub);
        s.setFont(Font.font("System", 13));
        s.setTextFill(Color.web(fg));
        s.setWrapText(true);
        card.getChildren().addAll(h, s);
        return card;
    }

    // ── Details ──────────────────────────────────────────────────────────────

    private VBox detailsCard(LicenseToken tok) {
        VBox card = card();
        card.getChildren().add(heading("Licence details"));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        row(grid, 0, "Product", com.pos.Edition.current().isRetail() ? "Mbuyedzedzo Retail POS" : "Mbuyedzedzo POS System");
        row(grid, 1, "Plan", tok != null && tok.licenseType != null ? planLabel(tok.licenseType) : "—");
        row(grid, 2, "Licence key", tok != null && tok.key != null ? tok.key : "—");
        row(grid, 3, "Expires", expiryLabel(tok));
        row(grid, 4, "Licensing server", manager.serverUrl());

        card.getChildren().add(grid);
        return card;
    }

    private String expiryLabel(LicenseToken tok) {
        if (tok == null) return "—"; // no token at all yet — "perpetual" would be a guess, not a fact
        return tok.licenseExpiry == null ? "Never (perpetual)" : formatDate(tok.licenseExpiry);
    }

    private String planLabel(String type) {
        return switch (type.toUpperCase()) {
            case "SUBSCRIPTION" -> "Monthly subscription";
            case "TRIAL" -> "30-day trial";
            default -> "Perpetual";
        };
    }

    // ── Renewal (subscriptions only) ─────────────────────────────────────────

    private VBox renewalCard(LicenseToken tok) {
        VBox card = card();
        card.setStyle(card.getStyle() + "-fx-border-color: #0f766e; -fx-border-width: 1; -fx-border-radius: 10;");
        card.getChildren().add(heading("Renew your subscription"));

        Label info = new Label("Subscriptions are billed monthly on the website — buying another month "
                + "there extends this licence automatically once payment clears.");
        info.setWrapText(true);
        info.setFont(Font.font("System", 13));
        info.setTextFill(Color.web("#64748b"));

        Button renewBtn = new Button("Renew on the website");
        renewBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        renewBtn.setOnAction(e -> openInBrowser(manager.serverUrl() + "/pricing"));

        card.getChildren().addAll(info, renewBtn);
        return card;
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private VBox actionsCard(LicenseToken tok) {
        VBox card = card();
        card.getChildren().add(heading("Manage"));

        Label statusMsg = new Label();
        statusMsg.setFont(Font.font("System", 12));
        statusMsg.setWrapText(true);

        Button manageOnline = new Button("Manage my account online");
        manageOnline.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-font-weight: bold;"
                + "-fx-padding: 10 16; -fx-background-radius: 6; -fx-cursor: hand;");
        manageOnline.setOnAction(e -> openInBrowser(manager.serverUrl() + "/account/login"));

        Button recheck = new Button("Re-check now");
        recheck.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-font-weight: bold;"
                + "-fx-padding: 10 16; -fx-background-radius: 6; -fx-cursor: hand;");
        recheck.setOnAction(e -> {
            manager.revalidateInBackground();
            statusMsg.setText("Re-checking with the licensing server in the background…");
            statusMsg.setTextFill(Color.web("#0f766e"));
        });

        Button deactivate = new Button("Deactivate this machine");
        deactivate.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-padding: 10 16; -fx-background-radius: 6; -fx-cursor: hand;");
        deactivate.setDisable(!currentUser.hasFullAccess() || tok == null);
        deactivate.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Release this licence from this computer? It frees a machine slot, "
                    + "and this copy will need to be activated again on next launch.",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(r -> {
                if (r != ButtonType.YES) return;
                new Thread(() -> {
                    manager.deactivateThisMachine();
                    Platform.runLater(() -> {
                        statusMsg.setTextFill(Color.web("#0f766e"));
                        statusMsg.setText("Deactivated. Close and re-open the app to activate again.");
                    });
                }, "License-Deactivate").start();
            });
        });

        HBox actions = new HBox(10, manageOnline, recheck, deactivate);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(actions, statusMsg);
        return card;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception ignored) {
            // No default browser available (e.g. a locked-down till) — nothing more we can do here.
        }
    }

    private static long daysUntil(Instant when) {
        return Math.max(0, Duration.between(Instant.now(), when).toDays());
    }

    private static String formatDate(Instant when) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
                .format(when);
    }

    private VBox card() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);");
        return card;
    }

    private Label heading(String text) {
        Label h = new Label(text);
        h.setFont(Font.font("System", FontWeight.BOLD, 16));
        h.setTextFill(Color.web("#2c3e50"));
        return h;
    }

    private void row(GridPane grid, int r, String label, String value) {
        Label l = new Label(label);
        l.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        l.setTextFill(Color.web("#7f8c8d"));
        Label v = new Label(value);
        v.setFont(Font.font("System", 13));
        v.setTextFill(Color.web("#2c3e50"));
        v.setWrapText(true);
        grid.add(l, 0, r);
        grid.add(v, 1, r);
    }
}

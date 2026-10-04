package com.pos.views;

import com.pos.models.User;
import com.pos.services.CommunicationsService;
import com.pos.services.CommunicationsService.MarketingCampaign;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class MarketingView {

    /** Same shadow-based card recipe as com.pos.utils.Theme.card() — every
     *  card in the app should read as the one component, not several. */
    private static final String CARD_STYLE =
        "-fx-background-color: white; -fx-background-radius: 14;"
        + "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);";

    private final User currentUser;
    private final CommunicationsService commService;
    private TableView<MarketingCampaign> historyTable;
    private TableView<String[]> subscribersTable;
    private TableView<String[]> optOutTable;
    private Label historyCountLabel;
    private Label subscriberCountLabel;
    private Label statusMessage;

    public MarketingView(User user) {
        this.currentUser = user;
        this.commService = new CommunicationsService();
    }

    public BorderPane getView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #f5f7fa;");
        layout.setTop(createTopBar());

        VBox leftCol = new VBox(18,
            createComposeCard(), createUnsubscribeCard(), createSubscribersCard());
        VBox rightCol = new VBox(18,
            createHistoryCard(), createOptOutLogCard());

        leftCol.setPrefWidth(460);
        rightCol.setPrefWidth(680);
        leftCol.setMaxWidth(Double.MAX_VALUE);
        rightCol.setMaxWidth(Double.MAX_VALUE);

        HBox columns = new HBox(20, leftCol, rightCol);
        HBox.setHgrow(leftCol, Priority.ALWAYS);
        HBox.setHgrow(rightCol, Priority.ALWAYS);

        VBox content = new VBox(18, createStatsRow(), columns);
        content.setPadding(new Insets(20));

        // Vertical scroll for small windows. The background MUST be opaque —
        // a transparent ScrollPane background makes the labels inside render
        // blank on some Windows GPUs.
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #f5f7fa; -fx-background-color: #f5f7fa;");
        layout.setCenter(scroll);
        return layout;
    }

    private HBox createTopBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(20));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Marketing");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        bar.getChildren().add(title);
        return bar;
    }

    private HBox createStatsRow() {
        List<MarketingCampaign> history = commService.getCampaignHistory();
        List<String[]> subscribers      = commService.getSubscribedCustomers();

        int totalCampaigns   = history.size();
        int totalSent        = history.stream().mapToInt(MarketingCampaign::getSuccessCount).sum();
        int totalSubscribers = subscribers.size();

        // Same shared stat-card component Inventory/Customers/Users use — not a
        // separate one-off implementation, so this reads as the same product.
        return com.pos.components.SummaryCards.row(
            new com.pos.components.SummaryCards.Card("Campaigns",   String.valueOf(totalCampaigns),   "#0f766e"),
            new com.pos.components.SummaryCards.Card("Emails Sent", String.valueOf(totalSent),        "#16a34a"),
            new com.pos.components.SummaryCards.Card("Subscribers", String.valueOf(totalSubscribers), "#d97706")
        );
    }

    private VBox createComposeCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle(CARD_STYLE);
        card.setMaxHeight(Double.MAX_VALUE);

        Label title = new Label("Compose & Send");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        Label subjectLbl = new Label("Subject");
        subjectLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        TextField subjectField = new TextField();
        subjectField.setPromptText("e.g. Weekend special — 20% off everything!");
        subjectField.setStyle("-fx-padding: 9; -fx-font-size: 13;");

        Label bodyLbl = new Label("Email body (HTML supported)");
        bodyLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText(
            "<p>Hi there,</p>\n" +
            "<p>We have an <strong>exciting offer</strong> just for you!</p>\n" +
            "<p>Visit us this weekend for 20% off all items.</p>"
        );
        bodyArea.setPrefRowCount(5);
        bodyArea.setWrapText(true);
        bodyArea.setStyle("-fx-font-size: 12;");

        Label statusLabel = new Label("");
        statusLabel.setWrapText(true);

        Button sendBtn = new Button("Send to All Opted-In Customers");
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.setStyle(
            "-fx-background-color: #0f766e;" +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;" +
            "-fx-padding: 11 24; -fx-background-radius: 8; -fx-cursor: hand;"
        );

        sendBtn.setOnAction(e -> {
            String subject = subjectField.getText().trim();
            String body    = bodyArea.getText().trim();

            if (subject.isEmpty() || body.isEmpty()) {
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
                statusLabel.setText("Please fill in both subject and body.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Blast");
            confirm.setHeaderText("Send to all opted-in customers?");
            confirm.setContentText("Subject: " + subject);

            confirm.showAndWait().ifPresent(res -> {
                if (res != ButtonType.OK) return;

                sendBtn.setDisable(true);
                statusLabel.setStyle("-fx-text-fill: #1d4ed8;");
                statusLabel.setText("Sending… please wait.");

                new Thread(() -> {
                    int count = commService.sendMarketingBlast(
                        subject, body, currentUser.getStaffID()
                    );
                    Platform.runLater(() -> {
                        sendBtn.setDisable(false);
                        subjectField.clear();
                        bodyArea.clear();

                        if (count > 0) {
                            statusLabel.setStyle("-fx-text-fill: #16a34a;");
                            statusLabel.setText("  Sent to " + count + " customer(s) successfully.");
                        } else {
                            statusLabel.setStyle("-fx-text-fill: #dc2626;");
                            statusLabel.setText(" No emails sent. Check console for errors.");
                        }

                        loadHistory();
                        loadSubscribers();
                    });
                }, "Marketing-Blast").start();
            });
        });

        card.getChildren().addAll(title, subjectLbl, subjectField, bodyLbl, bodyArea, sendBtn, statusLabel);
        return card;
    }

    private VBox createSubscribersCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle(CARD_STYLE);
        card.setMaxHeight(Double.MAX_VALUE);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Subscribed Customers");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        subscriberCountLabel = new Label("");
        subscriberCountLabel.setFont(Font.font("System", 11));
        subscriberCountLabel.setTextFill(Color.web("#64748b"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(title, spacer, subscriberCountLabel);

        subscribersTable = new TableView<>();
        subscribersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subscribersTable.setPrefHeight(170);
        subscribersTable.setMinHeight(90);
        VBox.setVgrow(subscribersTable, Priority.ALWAYS);
        subscribersTable.setPlaceholder(new Label("No subscribed customers found."));

        TableColumn<String[], String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue()[0]));

        TableColumn<String[], String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue()[1]));

        TableColumn<String[], String> smsCol = new TableColumn<>("SMS");
        smsCol.setPrefWidth(55);
        smsCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue()[2]));
        smsCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setAlignment(Pos.CENTER);
                setStyle(item.equals("Yes")
                    ? "-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 13;"
                    : "-fx-text-fill: #cbd5e1; -fx-font-size: 13;");
            }
        });

        TableColumn<String[], Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(110);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Unsubscribe");
            {
                btn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white;"
                    + "-fx-font-size: 10; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    int accountID = Integer.parseInt(row[3]);
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Unsubscribe " + row[0] + " from marketing emails?", ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(r -> {
                        if (r != ButtonType.YES) return;
                        commService.setMarketingOptIn(accountID, false, "Staff",
                            "Unsubscribed by staff from Marketing view", currentUser.getStaffID());
                        loadSubscribers();
                        loadOptOutLog();
                    });
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        subscribersTable.getColumns().addAll(nameCol, emailCol, smsCol, actionCol);
        loadSubscribers();

        card.getChildren().addAll(header, subscribersTable);
        return card;
    }

    private void loadSubscribers() {
        List<String[]> subs = commService.getSubscribedCustomers();
        subscribersTable.setItems(FXCollections.observableArrayList(subs));
        subscriberCountLabel.setText(subs.size() + " opted in  ");
    }

    /** Card for processing a customer's emailed unsubscribe request. */
    private VBox createUnsubscribeCard() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setStyle(CARD_STYLE);
        card.setMaxHeight(Double.MAX_VALUE);

        Label title = new Label("Process Unsubscribe Request");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        Label help = new Label(
            "When a customer emails asking to unsubscribe, paste their email address "
            + "or the unsubscribe code from their message subject line here.");
        help.setWrapText(true);
        help.setFont(Font.font("System", 11));
        help.setTextFill(Color.web("#64748b"));

        TextField input = new TextField();
        input.setPromptText("customer@example.com  or  Unsubscribe <code>");
        input.setStyle("-fx-padding: 9; -fx-font-size: 13;");

        statusMessage = new Label("");
        statusMessage.setWrapText(true);
        statusMessage.setFont(Font.font("System", 12));

        Button processBtn = new Button("Process");
        processBtn.setStyle(
            "-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold;"
            + "-fx-padding: 9 20; -fx-background-radius: 8; -fx-cursor: hand;");
        processBtn.setOnAction(e -> {
            CommunicationsService.UnsubResult res =
                commService.processUnsubscribeRequest(input.getText(), currentUser.getStaffID());
            statusMessage.setTextFill(Color.web(res.matched() ? "#16a34a" : "#dc2626"));
            statusMessage.setText((res.matched() ? "" : "") + res.message());
            if (res.matched()) {
                input.clear();
                loadSubscribers();
                loadOptOutLog();
            }
        });

        HBox row = new HBox(10, input, processBtn);
        HBox.setHgrow(input, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, help, row, statusMessage);
        return card;
    }

    /** Recent opt-out / opt-in events. */
    private VBox createOptOutLogCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle(CARD_STYLE);
        card.setMaxHeight(Double.MAX_VALUE);

        Label title = new Label("Recent Opt-Outs");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        optOutTable = new TableView<>();
        optOutTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        optOutTable.setPrefHeight(200);
        optOutTable.setMinHeight(120);
        VBox.setVgrow(optOutTable, Priority.ALWAYS);
        optOutTable.setPlaceholder(new Label("No opt-out activity yet."));

        String[][] cols = {
            {"When", "0", "90"}, {"Customer", "1", "0"}, {"Email", "2", "0"},
            {"Action", "3", "110"}, {"Via", "4", "90"}, {"By", "5", "120"}
        };
        for (String[] c : cols) {
            int idx = Integer.parseInt(c[1]);
            TableColumn<String[], String> tc = new TableColumn<>(c[0]);
            if (!"0".equals(c[2])) tc.setPrefWidth(Double.parseDouble(c[2]));
            tc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                idx < d.getValue().length ? d.getValue()[idx] : ""));
            if (idx == 3) tc.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); setStyle(""); return; }
                    setText(item);
                    setStyle("Unsubscribed".equals(item)
                        ? "-fx-text-fill: #dc2626; -fx-font-weight: bold;"
                        : "-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                }
            });
            optOutTable.getColumns().add(tc);
        }
        loadOptOutLog();

        card.getChildren().addAll(title, optOutTable);
        return card;
    }

    private void loadOptOutLog() {
        if (optOutTable == null) return;
        optOutTable.setItems(FXCollections.observableArrayList(commService.getSuppressionLog(25)));
    }

    private VBox createHistoryCard() {
        VBox card = new VBox(14);
        card.setPadding(new Insets(20));
        card.setStyle(CARD_STYLE);
        card.setMaxHeight(Double.MAX_VALUE);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Campaign History");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#0f766e"));

        historyCountLabel = new Label("");
        historyCountLabel.setFont(Font.font("System", 12));
        historyCountLabel.setTextFill(Color.web("#64748b"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle(
            "-fx-background-color: #f1f5f9; -fx-text-fill: #1e293b;" +
            "-fx-font-weight: bold; -fx-padding: 7 14; -fx-background-radius: 6; -fx-cursor: hand;"
        );
        refreshBtn.setOnAction(e -> loadHistory());

        header.getChildren().addAll(title, spacer, historyCountLabel, refreshBtn);

        historyTable = new TableView<>();
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        historyTable.setPrefHeight(360);
        historyTable.setMinHeight(160);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        historyTable.setPlaceholder(new Label("No campaigns sent yet."));

        TableColumn<MarketingCampaign, String> dateCol = new TableColumn<>("Date Sent");
        dateCol.setPrefWidth(155);
        dateCol.setCellValueFactory(new PropertyValueFactory<>("sentAt"));

        TableColumn<MarketingCampaign, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(new PropertyValueFactory<>("subject"));

        TableColumn<MarketingCampaign, String> sentByCol = new TableColumn<>("Sent By");
        sentByCol.setPrefWidth(130);
        sentByCol.setCellValueFactory(new PropertyValueFactory<>("sentByName"));

        TableColumn<MarketingCampaign, Integer> totalCol = new TableColumn<>("Recipients");
        totalCol.setPrefWidth(90);
        totalCol.setCellValueFactory(new PropertyValueFactory<>("recipientsCount"));
        totalCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<MarketingCampaign, Integer> successCol = new TableColumn<>("Delivered");
        successCol.setPrefWidth(90);
        successCol.setCellValueFactory(new PropertyValueFactory<>("successCount"));
        successCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item.toString());
                setAlignment(Pos.CENTER);
                MarketingCampaign row = getTableView().getItems().get(getIndex());
                if (item == 0)
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                else if (item < row.getRecipientsCount())
                    setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                else
                    setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
            }
        });

        TableColumn<MarketingCampaign, Void> previewCol = new TableColumn<>("Preview");
        previewCol.setPrefWidth(80);
        previewCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = com.pos.components.Ui.viewButton();
            {
                btn.setOnAction(e -> showPreviewDialog(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        historyTable.getColumns().addAll(dateCol, subjectCol, sentByCol, totalCol, successCol, previewCol);
        loadHistory();

        card.getChildren().addAll(header, historyTable);
        return card;
    }

    private void loadHistory() {
        List<MarketingCampaign> history = commService.getCampaignHistory();
        historyTable.setItems(FXCollections.observableArrayList(history));
        int total = history.stream().mapToInt(MarketingCampaign::getSuccessCount).sum();
        historyCountLabel.setText(history.size() + " campaigns  •  " + total + " emails sent  ");
    }

    private void showPreviewDialog(MarketingCampaign campaign) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Campaign Preview");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(520);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label subjectLbl = new Label("Subject: " + campaign.getSubject());
        subjectLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        subjectLbl.setWrapText(true);

        Label metaLbl = new Label(
            "Sent: "       + campaign.getSentAt() +
            "   |   By: " + campaign.getSentByName() +
            "   |   Delivered: " + campaign.getSuccessCount() +
            "/" + campaign.getRecipientsCount()
        );
        metaLbl.setFont(Font.font("System", 11));
        metaLbl.setTextFill(Color.web("#64748b"));
        metaLbl.setWrapText(true);

        Label bodyTitle = new Label("Email body (HTML source):");
        bodyTitle.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));

        TextArea bodyArea = new TextArea(campaign.getBodyHtml());
        bodyArea.setEditable(false);
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(12);
        bodyArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        content.getChildren().addAll(subjectLbl, metaLbl, new Separator(), bodyTitle, bodyArea);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }
}
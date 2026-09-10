package com.pos.dialogs;

import com.pos.services.CommunicationsService;
import com.pos.services.CommunicationsService.CommPreferences;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Optional;

public class CommunicationsDialog {

    public static Optional<CommPreferences> showDialog(int accountID, CommunicationsService commService) {
        Dialog<CommPreferences> dialog = new Dialog<>();
        dialog.setTitle("Communication Preferences");
        dialog.setHeaderText("How would you like us to contact you?");
        dialog.setWidth(500);

        ButtonType acceptButtonType = new ButtonType("Accept & Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(acceptButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Terms and Conditions
        Label termsTitle = new Label("Terms and Conditions");
        termsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextArea termsArea = new TextArea(CommunicationsService.getTermsAndConditions());
        termsArea.setEditable(false);
        termsArea.setWrapText(true);
        termsArea.setPrefRowCount(10);
        termsArea.setStyle("-fx-font-size: 11;");

        CheckBox termsCheckBox = new CheckBox("I have read and accept the Terms and Conditions");
        termsCheckBox.setStyle("-fx-font-weight: bold;");

        Separator separator1 = new Separator();

        // Communication preferences
        Label prefsTitle = new Label("Communication Preferences");
        prefsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        CheckBox receiptEmailCheck = new CheckBox("Send receipt to my email (Required)");
        receiptEmailCheck.setSelected(true);
        receiptEmailCheck.setDisable(true); // Always required

        CheckBox marketingCheck = new CheckBox("Send me marketing emails and special offers");
        CheckBox smsCheck = new CheckBox("Send me SMS notifications");

        Separator separator2 = new Separator();

        Label privacyNote = new Label("Your privacy is important to us. You can change these preferences at any time.");
        privacyNote.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
        privacyNote.setWrapText(true);

        content.getChildren().addAll(
            termsTitle,
            termsArea,
            termsCheckBox,
            separator1,
            prefsTitle,
            receiptEmailCheck,
            marketingCheck,
            smsCheck,
            separator2,
            privacyNote
        );

        dialog.getDialogPane().setContent(content);

        // Disable accept button until terms are accepted
        Button acceptButton = (Button) dialog.getDialogPane().lookupButton(acceptButtonType);
        acceptButton.setDisable(true);

        termsCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            acceptButton.setDisable(!newVal);
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == acceptButtonType) {
                CommPreferences prefs = new CommPreferences();
                prefs.setAccountID(accountID);
                prefs.setTermsAccepted(termsCheckBox.isSelected());
                prefs.setReceiptByEmail(receiptEmailCheck.isSelected());
                prefs.setMarketingEmails(marketingCheck.isSelected());
                prefs.setSmsNotifications(smsCheck.isSelected());
                return prefs;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
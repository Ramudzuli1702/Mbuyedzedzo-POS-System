package com.pos.components;

import com.pos.models.User;
import com.pos.services.SessionService;
import com.pos.services.UserService;
import com.pos.utils.Theme;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class AuthorizationKeyDialog {
    private AuthorizationKeyDialog() {}
    public static void show(User current,User supervisor,UserService users,SessionService sessions) {
        PasswordField password=new PasswordField();password.setPromptText("Your login password");
        var grid=Ui.formGrid();Ui.formField(grid,0,"Confirm password",password);
        Label key=new Label("Generate a key when you are ready to approve.");key.setWrapText(true);key.setStyle(Theme.chip(Theme.BRAND_TINT,Theme.BRAND_DARK));
        Label feedback=new Label();feedback.setWrapText(true);feedback.setStyle("-fx-text-fill: "+Theme.DANGER+";");
        Button generate=new Button("Generate key");generate.setStyle(Theme.primaryButton());
        generate.setOnAction(e->{
            User verified=users.login(current.getEmailAddress(),password.getText());password.clear();
            if(verified==null || !verified.hasFullAccess()){feedback.setText("Enter your current Admin or Manager password.");return;}
            String code=sessions.getOrCreateAuthKey(supervisor.getStaffID());
            if(code==null){feedback.setText("A key could not be generated. Check that the supervisor is active and retry.");return;}
            key.setText(code);key.setStyle("-fx-font-family: monospace; -fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: "+Theme.BRAND+";");
            feedback.setText("");password.setDisable(true);generate.setDisable(true);generate.setText("Key generated");
        });
        Label instructions=new Label("Valid for 15 minutes and one successful approval or session sign-off. Generating another key replaces the previous one. Use it for stocktakes, returns, exchanges or session reviews.");instructions.setWrapText(true);
        Ui.editor("Authorization key",supervisor.getFullNames(),new VBox(16,
                Ui.section("Confirm your identity",Ui.detailRow("Signed in as",current.getFullNames()),grid,generate,feedback),
                Ui.section("Single-use key",key,instructions)),null).showAndWait();
    }
}

package com.pos.components;

import com.pos.utils.HelpContent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.util.List;

/**
 * "Get to know the system" walkthrough — a short, dismissable series of
 * dialogs, one per screen, shown automatically on first launch and
 * re-runnable at any time from the info button in the sidebar.
 */
public final class TutorialOverlay {

    private TutorialOverlay() {}

    /** Shows the whole walkthrough, in order, starting from the first section. */
    public static void showWalkthrough(Window owner, List<String> sections) {
        showStep(owner, sections, 0);
    }

    /** Shows the help for a single section (what the info button opens). */
    public static void showSingle(Window owner, String section) {
        HelpContent.Entry entry = HelpContent.get(section);
        if (entry == null) return;

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("About this screen");
        dialog.setHeaderText(entry.title());
        dialog.getDialogPane().setContent(bodyLabel(entry.body()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static void showStep(Window owner, List<String> sections, int index) {
        if (index >= sections.size()) return;

        String section = sections.get(index);
        HelpContent.Entry entry = HelpContent.get(section);
        if (entry == null) { showStep(owner, sections, index + 1); return; }

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Get to know " + com.pos.Branding.APP_SHORT + " (" + (index + 1) + " of " + sections.size() + ")");
        dialog.setHeaderText(entry.title());
        dialog.getDialogPane().setContent(bodyLabel(entry.body()));

        ButtonType skipType = new ButtonType("Skip tutorial", ButtonBar.ButtonData.CANCEL_CLOSE);
        boolean isLast = index == sections.size() - 1;
        ButtonType nextType = new ButtonType(isLast ? "Done" : "Next", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(skipType, nextType);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == nextType && !isLast) {
                // Defer so this dialog fully closes before the next one opens.
                javafx.application.Platform.runLater(() -> showStep(owner, sections, index + 1));
            }
            return null;
        });

        dialog.showAndWait();
    }

    private static VBox bodyLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(420);
        label.setFont(Font.font("System", FontWeight.NORMAL, 13));

        VBox box = new VBox(label);
        box.setPadding(new Insets(10, 5, 10, 5));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}

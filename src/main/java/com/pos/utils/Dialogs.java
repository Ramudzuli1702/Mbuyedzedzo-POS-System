package com.pos.utils;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Consistent, user-facing error reporting.
 *
 * {@code Dialogs.error(...)} both <em>logs</em> the failure (with stack trace, to
 * the rolling log file) and shows the user a dialog that explains what happened,
 * offers the technical detail behind a disclosure, and points at the log folder.
 * Safe to call from any thread.
 */
public final class Dialogs {

    private static final Logger log = LoggerFactory.getLogger("app.error");

    private Dialogs() {}

    public static void error(String title, String message) {
        error(null, title, message, null);
    }

    public static void error(String title, String message, Throwable cause) {
        error(null, title, message, cause);
    }

    public static void error(Window owner, String title, String message, Throwable cause) {
        if (cause != null) log.error("{} — {}", title, message, cause);
        else               log.error("{} — {}", title, message);

        Runnable show = () -> buildAndShow(owner, title, message, cause);
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
    }

    private static void buildAndShow(Window owner, String title, String message, Throwable cause) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) alert.initOwner(owner);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message
                    + "\n\nThis has been recorded in the log file.");

            if (cause != null) {
                StringWriter sw = new StringWriter();
                cause.printStackTrace(new PrintWriter(sw));

                TextArea trace = new TextArea(sw.toString());
                trace.setEditable(false);
                trace.setWrapText(false);
                trace.setMaxWidth(Double.MAX_VALUE);
                trace.setMaxHeight(Double.MAX_VALUE);
                trace.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11;");

                GridPane content = new GridPane();
                content.setMaxWidth(Double.MAX_VALUE);
                content.add(new Label("Technical details:"), 0, 0);
                content.add(trace, 0, 1);
                GridPane.setVgrow(trace, Priority.ALWAYS);
                GridPane.setHgrow(trace, Priority.ALWAYS);

                alert.getDialogPane().setExpandableContent(content);
            }
            alert.showAndWait();
        } catch (Exception e) {
            // A dialog failure must never mask the original error.
            log.error("Could not display the error dialog for: {}", title, e);
        }
    }
}

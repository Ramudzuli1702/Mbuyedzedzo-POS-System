package com.pos;

import com.pos.database.DatabaseConnection;
import com.pos.database.DatabaseSetup;
import com.pos.setup.FirstRunSetup;
import com.pos.utils.Dialogs;
import com.pos.utils.LogSetup;
import com.pos.views.LoginView;
import com.pos.views.RegisterView;
import com.pos.views.SetupView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * POSApplication — JavaFX entry point.
 *
 * Launch sequence
 * ──────────────
 * start()
 *   │
 *   ├─ db.properties exists?
 *   │     NO  → show SetupView
 *   │              → background thread: FirstRunSetup.run()
 *   │                    1. Detect / install MySQL
 *   │                    2. Generate & set root password
 *   │                    3. Write db.properties
 *   │              → on success: continue to DatabaseSetup ↓
 *   │              → on failure: show error in SetupView, stay open
 *   │
 *   │     YES → DatabaseConnection.loadConfig() already ran in static init
 *   │
 *   ├─ DatabaseSetup.run()   — create schema + tables (idempotent)
 *   │
 *   └─ Staff table empty?
 *         YES → RegisterView  (first admin registration)
 *         NO  → LoginView
 */
public class POSApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(POSApplication.class);

    @Override
    public void start(Stage primaryStage) {
        // Any exception that escapes an FX event handler lands here.
        Thread.currentThread().setUncaughtExceptionHandler((t, ex) -> {
            log.error("Uncaught exception on the JavaFX thread", ex);
            Dialogs.error(primaryStage, "Something went wrong",
                    "The last action could not be completed.", ex);
        });

        primaryStage.setTitle(Branding.APP_NAME);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setMaximized(true);

        // Handle window close — log out and release the DB connection cleanly
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("🛑 Shutting down...");
            DatabaseConnection.closeAll();
            Platform.exit();
            System.exit(0);
        });

        if (FirstRunSetup.isAlreadyConfigured()) {
            // ── Normal launch — config exists ──────────────────────────────────
            // DatabaseConnection already loaded it in its static initialiser.
            continueToApp(primaryStage, null);
        } else {
            // ── First launch — run setup ────────────────────────────────────────
            SetupView setupView = new SetupView(primaryStage);
            setupView.show();

            Thread setupThread = new Thread(() -> {
                try {
                    FirstRunSetup.run(message -> setupView.setStatus(message), setupView.asPasswordPrompter());

                    // Setup done — hand off to the app on the FX thread
                    Platform.runLater(() -> continueToApp(primaryStage, setupView));

                } catch (FirstRunSetup.SetupException e) {
                    System.err.println("❌ First-run setup failed: " + e.getMessage());
                    Platform.runLater(() -> setupView.showError(e.getMessage()));
                    // Leave the SetupView open so the user can read the error.
                    // They can re-launch after fixing the issue (e.g. running as admin).
                }
            });
            setupThread.setName("pos-first-run-setup");
            setupThread.setDaemon(true);
            setupThread.start();
        }
    }

    /**
     * Called after first-run setup completes (or immediately on normal launch).
     * Must be called on the JavaFX Application Thread.
     *
     * @param setupView the SetupView if we just ran first-run setup, or null
     */
    private void continueToApp(Stage primaryStage, SetupView setupView) {
        // ── 1. Create/verify database schema ──────────────────────────────────
        if (setupView != null) setupView.setStatus("Creating database schema...");

        boolean dbReady = DatabaseSetup.run();

        if (!dbReady) {
            if (setupView != null) {
                setupView.showError(
                    "Failed to initialise the database.\n\n" +
                    "Please ensure MySQL is running and that the application has " +
                    "sufficient permissions, then re-launch."
                );
            } else {
                showFatalError(
                    "Database Error",
                    "Could not connect to the database.\n\n" +
                    "Please ensure MySQL is running and re-launch the application."
                );
            }
            return;
        }

        // ── 2. Decide: registration or login ──────────────────────────────────
        if (isFirstUser()) {
            RegisterView registerView = new RegisterView(primaryStage);
            registerView.show();
        } else {
            LoginView loginView = new LoginView(primaryStage);
            loginView.show();
        }
    }

    /**
     * Returns true if the Staff table has no rows — i.e. this is the very first
     * time the app has been fully set up.
     */
    private boolean isFirstUser() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Staff")) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️  Could not check Staff table: " + e.getMessage());
        }
        return false;
    }

    /**
     * Shows a blocking error alert. Used when there is no SetupView to display
     * the error in (i.e. a normal launch that fails at the DB step).
     */
    private void showFatalError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
        Platform.exit();
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        LogSetup.init();   // must run before anything touches a logger
        launch(args);
    }
}

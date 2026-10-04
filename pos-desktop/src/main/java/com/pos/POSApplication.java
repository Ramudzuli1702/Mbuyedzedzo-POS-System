package com.pos;

import com.pos.database.DatabaseConnection;
import com.pos.database.DatabaseSetup;
import com.pos.setup.FirstRunSetup;
import com.pos.setup.TermsAcceptance;
import com.pos.utils.Dialogs;
import com.pos.utils.LogSetup;
import com.pos.views.LoginView;
import com.pos.views.RegisterView;
import com.pos.views.SetupView;
import com.pos.views.SplashView;
import com.pos.views.TermsView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;
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
 *   ├─ SplashView — brand moment shown on every launch
 *   │
 *   ├─ terms-accepted.flag exists?
 *   │     NO  → TermsView — must Accept to continue (Decline exits)
 *   │     YES → skip straight through
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
        com.pos.utils.BrandAssets.applyAppIcons(primaryStage);

        // "Full screen" here means maximized — fills the screen but keeps the
        // Windows taskbar and this window's own title bar (with its close X)
        // visible. NOT Stage.setFullScreen(true), which hides both of those;
        // that was tried and was wrong.
        //
        // Every screen (splash, terms, login, dashboard, ...) sets its own new
        // Scene. Assigning a new Scene recomputes the window's size from that
        // scene's (root's) preferred size — if the stage's maximized property
        // already reads true, calling setMaximized(true) again is a no-op (the
        // value didn't change, so nothing re-applies), which is why the window
        // kept shrinking to a small content-sized footprint on every screen
        // change despite this call being here. Toggling false→true forces
        // JavaFX to actually re-apply the maximized bounds every time.
        primaryStage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    primaryStage.setMaximized(false);
                    primaryStage.setMaximized(true);
                });
            }
        });

        // Every Dialog/Alert opens in its OWN window with its OWN Scene — it is
        // NOT a child of primaryStage's scene, so attaching the stylesheet only
        // to primaryStage's scenes (as an earlier version of this did) left
        // every popup form (Add/Edit dialogs, "View" detail dialogs, Alerts)
        // completely unstyled. Window.getWindows() is the one list that covers
        // every window the whole app ever opens, so hooking it here — once —
        // is what actually makes the stylesheet reach every dialog, not just
        // the main screens.
        String css = getClass().getResource("/css/app-theme.css").toExternalForm();
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (Window w : change.getAddedSubList()) {
                    attachStylesheet(w, css);
                }
            }
        });

        // Handle window close — log out and release the DB connection cleanly
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("🛑 Shutting down...");
            DatabaseConnection.closeAll();
            Platform.exit();
            System.exit(0);
        });

        SplashView.show(primaryStage, () -> {
            if (TermsAcceptance.isAccepted()) {
                proceedPastTerms(primaryStage);
            } else {
                new TermsView(primaryStage,
                        () -> proceedPastTerms(primaryStage),
                        () -> { Platform.exit(); System.exit(0); }
                ).show();
            }
        });
    }

    /** Called once the licence-agreement gate is satisfied (or was already, on past launches). */
    private void proceedPastTerms(Stage primaryStage) {
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
                    System.err.println("First-run setup failed: " + e.getMessage());
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

        // ── 2. Licence gate ──────────────────────────────────────────────────
        com.pos.license.LicenseManager licence = new com.pos.license.LicenseManager();
        com.pos.license.LicenseManager.Status ls = licence.check();
        log.info("Licence check: {} — {}", ls.state(), ls.message());

        if (!ls.canRun()) {
            new com.pos.views.ActivationView(primaryStage, licence, ls.message(),
                    () -> proceedToLoginOrRegister(primaryStage)).show();
            return;
        }
        licence.revalidateInBackground();   // best-effort refresh on every launch

        // ── 3. Registration or login ─────────────────────────────────────────
        proceedToLoginOrRegister(primaryStage);
    }

    private void proceedToLoginOrRegister(Stage primaryStage) {
        if (isFirstUser()) {
            new RegisterView(primaryStage).show();
        } else {
            new LoginView(primaryStage).show();
        }
    }

    /** Adds the shared stylesheet to a window's scene — now, or as soon as it gets one. */
    private static void attachStylesheet(Window w, String css) {
        Scene existing = w.getScene();
        if (existing != null && !existing.getStylesheets().contains(css)) {
            existing.getStylesheets().add(css);
        }
        w.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !newScene.getStylesheets().contains(css)) {
                newScene.getStylesheets().add(css);
            }
        });
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
            System.err.println("  Could not check Staff table: " + e.getMessage());
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

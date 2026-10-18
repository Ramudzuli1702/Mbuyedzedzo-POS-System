package com.pos.setup;

import com.pos.Edition;
import com.pos.database.DatabaseConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.function.Consumer;

/** First-run orchestration. Existing servers keep their root password. */
public final class FirstRunSetup {
    public record PasswordRequest(boolean newPassword, boolean wrongPassword) {}

    @FunctionalInterface
    public interface PasswordPrompter {
        /** Return null to cancel. Called only on the setup worker thread. */
        String prompt(PasswordRequest request);
    }

    /** Boundary for testing recovery without installing a Windows service. */
    interface Operations {
        boolean serverExists() throws SetupException;
        void startExisting(Consumer<String> progress) throws SetupException;
        void install(String bootstrapPassword, Consumer<String> progress) throws SetupException;
        boolean accepts(String password) throws SetupException;
        String recoveryPassword() throws SetupException;
        void rememberRecoveryPassword(String password) throws SetupException;
        void clearRecoveryPassword() throws SetupException;
        void changePassword(String currentPassword, String newPassword) throws SetupException;
        void save(String password) throws SetupException;
    }

    public static boolean isAlreadyConfigured() {
        return Files.isRegularFile(Path.of(DatabaseConnection.CONFIG_PATH));
    }

    public static void run(Consumer<String> progress, PasswordPrompter prompter) throws SetupException {
        run(progress, prompter, new WindowsMySqlProvisioner());
    }

    static void run(Consumer<String> progress, PasswordPrompter prompter, Operations ops)
            throws SetupException {
        progress.accept("Checking your database server...");
        if (ops.serverExists()) {
            ops.startExisting(progress);
            String recovery = ops.recoveryPassword();
            if (recovery != null && ops.accepts(recovery)) {
                finishNewServer(progress, prompter, ops, recovery);
                return;
            }
            if (ops.accepts("")) {
                finishNewServer(progress, prompter, ops, "");
                return;
            }
            boolean wrong = false;
            while (true) {
                progress.accept("Enter your existing server password to connect.");
                String password = prompt(prompter, false, wrong);
                if (ops.accepts(password)) {
                    // Another edition or application may use the existing password.
                    ops.save(password);
                    ops.clearRecoveryPassword();
                    progress.accept("Database connection ready.");
                    return;
                }
                wrong = true;
            }
        }

        String password = prompt(prompter, true, false);
        // Reuse pending credentials: an earlier elevated installer may still be
        // completing when Retry is selected after a timeout or interruption.
        String bootstrap = ops.recoveryPassword();
        if (bootstrap == null) {
            byte[] bytes = new byte[14];
            new SecureRandom().nextBytes(bytes);
            // Strong temporary credential without delimiters from the installer's grammar.
            bootstrap = "Aa1" + java.util.HexFormat.of().formatHex(bytes) + "!";
        }
        ops.rememberRecoveryPassword(bootstrap);
        ops.install(bootstrap, progress);
        complete(progress, ops, bootstrap, password);
    }

    private static void finishNewServer(Consumer<String> progress, PasswordPrompter prompter,
                                        Operations ops, String current) throws SetupException {
        complete(progress, ops, current, prompt(prompter, true, false));
    }

    private static void complete(Consumer<String> progress, Operations ops, String current, String password)
            throws SetupException {
        progress.accept("Securing your database server...");
        if (!ops.accepts(current)) {
            throw new SetupException("The database server did not accept its setup credentials. "
                    + "Select Retry to reconnect. Your data has not been removed.");
        }
        ops.changePassword(current, password);
        if (!ops.accepts(password)) {
            throw new SetupException("The new server password could not be verified. Select Retry.");
        }
        ops.save(password);
        ops.clearRecoveryPassword();
        progress.accept("Database server ready.");
    }

    private static String prompt(PasswordPrompter prompter, boolean create, boolean wrong)
            throws SetupException {
        String password = prompter.prompt(new PasswordRequest(create, wrong));
        if (password == null) throw new SetupException("Setup cancelled. Select Retry when you are ready.");
        if (create && passwordProblem(password) != null) throw new SetupException(passwordProblem(password));
        return password;
    }

    public static String passwordProblem(String password) {
        if (password == null || password.length() < 8) return "Use at least 8 characters for your server password.";
        if (password.length() > 128) return "Use no more than 128 characters for your server password.";
        if (password.chars().anyMatch(Character::isISOControl)) return "The password cannot contain control characters.";
        return null;
    }

    static String databaseName() {
        return "pos_db_" + Edition.current().name().toLowerCase(java.util.Locale.ROOT);
    }

    public static class SetupException extends Exception {
        public SetupException(String message) { super(message); }
        public SetupException(String message, Throwable cause) { super(message, cause); }
    }
}

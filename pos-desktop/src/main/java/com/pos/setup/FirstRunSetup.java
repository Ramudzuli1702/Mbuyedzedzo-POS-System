package com.pos.setup;

import com.pos.database.DatabaseConnection;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FirstRunSetup {

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final String MYSQL8_REG_KEY =
        "HKLM\\SOFTWARE\\MySQL AB\\MySQL Server 8.4";
    private static final String MYSQL8_REG_KEY_WOW =
        "HKLM\\SOFTWARE\\WOW6432Node\\MySQL AB\\MySQL Server 8.4";
    private static final String MYSQL8_REG_KEY_GENERIC =
        "HKLM\\SOFTWARE\\MySQL AB";
    private static final String MYSQL_SERVICE = "MySQL84";
    private static final String PWD_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#%^&*";

    // ── Functional interface for password prompting ────────────────────────────

    /**
     * Called by the setup thread when MySQL is installed but no candidate
     * password works.  The implementation should show a UI prompt and return
     * the password the user entered, or {@code null} if they cancelled.
     *
     * <p>This will be called repeatedly (with {@code wrongPassword=true} on
     * retries) until the supplied password connects successfully or the user
     * cancels (returns {@code null}).
     */
    @FunctionalInterface
    public interface PasswordPrompter {
        /**
         * @param wrongPassword true if a previous attempt with a user-supplied
         *                      password was rejected — show a "Wrong password"
         *                      hint in the UI.
         * @return the password to try, or {@code null} to abort setup.
         */
        String prompt(boolean wrongPassword);
    }

    // ── Paths ──────────────────────────────────────────────────────────────────

    private static String getMysqlMsiPath() {
        String appDir = System.getProperty("jpackage.app-path");
        if (appDir != null) {
            File msi = new File(new File(appDir).getParentFile(), "mysql-installer.msi");
            if (msi.exists()) return msi.getAbsolutePath();
        }
        String jarDir = new File(
            FirstRunSetup.class.getProtectionDomain()
                               .getCodeSource()
                               .getLocation()
                               .getPath()
        ).getParent();
        return jarDir + File.separator + "mysql-installer.msi";
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public static boolean isAlreadyConfigured() {
        return new File(DatabaseConnection.CONFIG_PATH).exists();
    }

    /**
     * Runs the full first-run setup sequence.
     *
     * @param progress  callback for human-readable status messages
     * @param prompter  callback invoked when an existing MySQL installation has
     *                  an unknown root password — lets the UI ask the user
     * @throws SetupException if any unrecoverable step fails or the user cancels
     */
    public static void run(Consumer<String> progress, PasswordPrompter prompter)
            throws SetupException {

        progress.accept("Checking system requirements...");

        boolean mysqlPresent = isMysqlInstalled();
        String generatedPassword;

        if (!mysqlPresent) {
            progress.accept("MySQL not found. Installing MySQL 8.4 — this may take a few minutes...");
            installMysql(progress);
            progress.accept("Waiting for MySQL service to start...");
            waitForMysqlService(progress);
            generatedPassword = generatePassword();
            progress.accept("Securing database — setting root password...");
            setRootPassword("", generatedPassword, progress);

        } else {
            progress.accept("MySQL is already installed.");
            generatedPassword = generatePassword();
            progress.accept("Configuring database access...");

            String existingPassword = detectExistingRootPassword(progress, prompter);
            // existingPassword is the CURRENT working password.
            // We now change it to our generated one so the app owns its credentials.
            setRootPassword(existingPassword, generatedPassword, progress);
        }

        progress.accept("Saving configuration...");
        DatabaseConnection.saveConfig(
            "127.0.0.1", 3306, "pos_db", "root", generatedPassword
        );

        progress.accept("Setup complete!");
    }

    // ── MySQL Detection ────────────────────────────────────────────────────────

    private static boolean isMysqlInstalled() {
        if (registryKeyExists(MYSQL8_REG_KEY))     return true;
        if (registryKeyExists(MYSQL8_REG_KEY_WOW)) return true;
        if (isServiceRegistered(MYSQL_SERVICE))    return true;
        if (isServiceRegistered("MySQL80"))         return true;
        return commandExists("mysqld", "--version");
    }

    private static boolean registryKeyExists(String key) {
        try {
            Process p = new ProcessBuilder("reg", "query", key)
                .redirectErrorStream(true).start();
            String output = readOutput(p);
            p.waitFor(5, TimeUnit.SECONDS);
            return output.contains("MySQL") || p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    private static boolean isServiceRegistered(String serviceName) {
        try {
            Process p = new ProcessBuilder("sc", "query", serviceName)
                .redirectErrorStream(true).start();
            String output = readOutput(p);
            p.waitFor(5, TimeUnit.SECONDS);
            return output.contains("STATE") && !output.contains("FAILED");
        } catch (Exception e) { return false; }
    }

    private static boolean commandExists(String cmd, String... args) {
        try {
            String[] command = new String[1 + args.length];
            command[0] = cmd;
            System.arraycopy(args, 0, command, 1, args.length);
            Process p = new ProcessBuilder(command)
                .redirectErrorStream(true).start();
            p.waitFor(5, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    // ── MySQL Installation ─────────────────────────────────────────────────────

    private static void installMysql(Consumer<String> progress) throws SetupException {
        String msiPath = getMysqlMsiPath();
        File msiFile = new File(msiPath);

        if (!msiFile.exists()) {
            throw new SetupException(
                "MySQL installer not found at: " + msiPath + "\n\n" +
                "Please download MySQL 8.4 from https://dev.mysql.com/downloads/mysql/ " +
                "and place mysql-installer.msi next to the application."
            );
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "msiexec.exe", "/i", msiFile.getAbsolutePath(),
                "/qn", "/norestart", "ADDLOCAL=ALL"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null)
                        System.out.println("[MySQL MSI] " + line);
                } catch (IOException ignored) {}
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = p.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                p.destroyForcibly();
                throw new SetupException("MySQL installation timed out after 10 minutes.");
            }

            int exit = p.exitValue();
            if (exit != 0 && exit != 3010) {
                throw new SetupException(
                    "MySQL installation failed with exit code: " + exit +
                    "\nPlease install MySQL 8.4 manually from https://dev.mysql.com/downloads/mysql/"
                );
            }
            progress.accept("MySQL installed successfully.");

        } catch (SetupException e) {
            throw e;
        } catch (Exception e) {
            throw new SetupException("Failed to run MySQL installer: " + e.getMessage(), e);
        }
    }

    // ── Service Management ─────────────────────────────────────────────────────

    private static void waitForMysqlService(Consumer<String> progress) throws SetupException {
        try {
            new ProcessBuilder("net", "start", MYSQL_SERVICE)
                .redirectErrorStream(true).start().waitFor(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (canConnectToMysql("")) {
                progress.accept("MySQL service is running.");
                return;
            }
            try {
                Thread.sleep(2000);
                progress.accept("Waiting for MySQL to start...");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new SetupException(
            "MySQL service did not become ready within 60 seconds.\n" +
            "Please check that the MySQL service is running in Windows Services."
        );
    }

    private static boolean canConnectToMysql(String password) {
        String url = "jdbc:mysql://127.0.0.1:3306/" +
                     "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" +
                     "&connectTimeout=3000";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection c = DriverManager.getConnection(url, "root", password);
            c.close();
            return true;
        } catch (Exception e) { return false; }
    }

    // ── Password Management ────────────────────────────────────────────────────

    private static String generatePassword() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        sb.append(PWD_CHARS.charAt(rng.nextInt(26)));
        sb.append(PWD_CHARS.charAt(26 + rng.nextInt(26)));
        sb.append(PWD_CHARS.charAt(52 + rng.nextInt(10)));
        sb.append(PWD_CHARS.charAt(62 + rng.nextInt(PWD_CHARS.length() - 62)));
        for (int i = 4; i < 24; i++)
            sb.append(PWD_CHARS.charAt(rng.nextInt(PWD_CHARS.length())));
        for (int i = sb.length() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            char tmp = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, tmp);
        }
        return sb.toString();
    }

    /**
     * Tries blank + common defaults first (covers fresh installs).
     * If none work, falls through to prompting the user via {@code prompter}.
     * Loops until a correct password is supplied or the user cancels.
     */
    private static String detectExistingRootPassword(
            Consumer<String> progress, PasswordPrompter prompter) throws SetupException {

        // 1. Try silent candidates (blank, common defaults)
        String[] candidates = { "", "root", "mysql", "password" };
        for (String candidate : candidates) {
            if (canConnectToMysql(candidate)) {
                System.out.println("[Setup] Connected with silent candidate.");
                return candidate;
            }
        }

        // 2. None worked — ask the user
        progress.accept("Please enter your existing MySQL root password below.");
        boolean wrongPrevious = false;

        while (true) {
            String supplied = prompter.prompt(wrongPrevious);

            if (supplied == null) {
                // User hit Cancel
                throw new SetupException(
                    "Setup cancelled. The application cannot start without " +
                    "access to the MySQL database."
                );
            }

            if (canConnectToMysql(supplied)) {
                progress.accept("Connected successfully.");
                return supplied;
            }

            // Wrong — tell the UI and loop
            wrongPrevious = true;
            progress.accept("Incorrect password, please try again.");
        }
    }

    private static void setRootPassword(String currentPassword, String newPassword,
                                         Consumer<String> progress) throws SetupException {
        String url = "jdbc:mysql://127.0.0.1:3306/" +
                     "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" +
                     "&connectTimeout=10000";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, "root", currentPassword);
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate(
                    "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '"
                    + escapeSql(newPassword) + "'"
                );
                stmt.executeUpdate("FLUSH PRIVILEGES");
                progress.accept("Root password configured.");
            }
        } catch (Exception e) {
            throw new SetupException(
                "Failed to set MySQL root password: " + e.getMessage() +
                "\n\nPlease ensure MySQL is running and accessible.", e
            );
        }
    }

    private static String escapeSql(String input) {
        return input.replace("\\", "\\\\").replace("'", "\\'");
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static String readOutput(Process p) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // ── SetupException ─────────────────────────────────────────────────────────

    public static class SetupException extends Exception {
        public SetupException(String message) { super(message); }
        public SetupException(String message, Throwable cause) { super(message, cause); }
    }
}
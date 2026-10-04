package com.pos.services;

import com.pos.database.DatabaseConnection;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.Base64;
import java.util.Properties;

/**
 * SettingsService — manages all application settings.
 *
 * TWO STORAGE LAYERS:
 *
 *   1. DATABASE (BusinessSettings table)
 *      Business profile data — name, address, phone, VAT number, receipt footer.
 *      Shared across all terminals. Appears on receipts and reports.
 *
 *   2. ENCRYPTED LOCAL FILE (config/app.config)
 *      Sensitive machine-specific settings — email credentials, file save paths.
 *      AES-256 encrypted. Even if the file is accessed directly, it's unreadable.
 *      Never stored in the database.
 *
 * ENCRYPTION:
 *   AES-256-CBC with PBKDF2 key derivation.
 *   Key is derived from a machine-specific identifier + a fixed salt.
 *   This means the config file is tied to the machine it was created on —
 *   copying it to another PC won't work.
 */
public class SettingsService {

    private static final String CONFIG_DIR  = "config";
    private static final String CONFIG_FILE = CONFIG_DIR + "/app.config";
    private static final String SALT        = "AFMVFCC_POS_SALT_2025"; // fixed salt, not a secret

    // ── Business Profile (Database) ────────────────────────────────────────────

    public String getBusinessSetting(String key) {
        return getBusinessSetting(key, "");
    }

    public String getBusinessSetting(String key, String defaultValue) {
        String sql = "SELECT SettingValue FROM BusinessSettings WHERE SettingKey = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString("SettingValue");
                return val != null ? val : defaultValue;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public boolean saveBusinessSetting(String key, String value) {
        String sql = """
            INSERT INTO BusinessSettings (SettingKey, SettingValue)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE SettingValue = VALUES(SettingValue)
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Convenience getters for business profile
    public String getBusinessName()    { return getBusinessSetting("business.name",    "My Business"); }
    public String getBusinessAddress() { return getBusinessSetting("business.address", ""); }
    public String getBusinessPhone()   { return getBusinessSetting("business.phone",   ""); }
    public String getBusinessEmail()   { return getBusinessSetting("business.email",   ""); }
    public String getBusinessVatNo()   { return getBusinessSetting("business.vatNo",   ""); }
    public String getBusinessWebsite() { return getBusinessSetting("business.website", ""); }
    public String getReceiptFooter()   { return getBusinessSetting("receipt.footer",   "Thank you for your purchase!"); }
    public String getReceiptSavePath() { return getBusinessSetting("receipt.savePath", "receipts/"); }
    /** Whether a checkout should send the receipt straight to the OS printer.
     *  Off by default — with a "Print to PDF" default printer this pops a
     *  save-as dialog on every sale. The Sale Complete dialog has a Print button. */
    public boolean getReceiptAutoPrint() { return Boolean.parseBoolean(getBusinessSetting("receipt.autoPrint", "false")); }
    /**
     * Receipt column width in characters, matched to the shop's actual
     * printer roll: 58mm thermal prints about 32 columns, 80mm about 40,
     * 112mm about 56. Defaults to 40 (80mm) — the width this receipt format
     * was originally built for.
     */
    public int getReceiptColumns() {
        try {
            int cols = Integer.parseInt(getBusinessSetting("receipt.columns", "40"));
            return cols > 0 ? cols : 40;
        } catch (NumberFormatException e) {
            return 40;
        }
    }
    /**
     * Default save locations are an absolute folder on the Desktop, auto-created
     * on first use — never a relative path, which an installed build resolves
     * against its own install directory (e.g. Program Files), often not even
     * writable by a standard user. A path saved in Settings always wins if set.
     */
    public String getReportsSavePath() { return getBusinessSetting("reports.savePath", defaultDesktopFolder("Reports")); }
    public String getBackupSavePath()  { return getBusinessSetting("backup.savePath",  defaultDesktopFolder("Backups")); }

    private static String defaultDesktopFolder(String subfolder) {
        return System.getProperty("user.home") + File.separator + "Desktop" + File.separator
                + com.pos.Branding.APP_NAME + File.separator + subfolder + File.separator;
    }

    // ── Local Encrypted Config (Email credentials, paths) ─────────────────────

    private Properties loadLocalConfig() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (!file.exists()) return props;

        try {
            String encrypted = Files.readString(file.toPath());
            String decrypted = decrypt(encrypted);
            props.load(new StringReader(decrypted));
        } catch (Exception e) {
            System.err.println("Could not load local config: " + e.getMessage());
        }
        return props;
    }

    private boolean saveLocalConfig(Properties props) {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            StringWriter sw = new StringWriter();
            props.store(sw, "POS Local Config — do not edit manually");
            String encrypted = encrypt(sw.toString());
            Files.writeString(Paths.get(CONFIG_FILE), encrypted);
            return true;
        } catch (Exception e) {
            System.err.println("Could not save local config: " + e.getMessage());
            return false;
        }
    }

    public String getLocalSetting(String key) {
        return loadLocalConfig().getProperty(key, "");
    }

    public boolean saveLocalSetting(String key, String value) {
        Properties props = loadLocalConfig();
        props.setProperty(key, value);
        return saveLocalConfig(props);
    }

    // Email credential shortcuts
    public String getEmailUsername() { return getLocalSetting("email.username"); }
    public String getEmailPassword() { return getLocalSetting("email.password"); }
    public String getEmailFromName() {
        String name = getLocalSetting("email.fromName");
        return name.isBlank() ? getBusinessName() : name;
    }

    public boolean saveEmailCredentials(String username, String password, String fromName) {
        Properties props = loadLocalConfig();
        props.setProperty("email.username", username);
        props.setProperty("email.password", password);
        props.setProperty("email.fromName", fromName);
        return saveLocalConfig(props);
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    /**
     * Creates a full MySQL database backup as a SQL dump file.
     * Saved to the configured backup path with a timestamp filename.
     *
     * mysqldump is NOT reliably on the system PATH after the bundled silent
     * MySQL install — the official installer's "add to PATH" step frequently
     * doesn't take effect for a silent/unattended (/qn) install, and even when
     * it does, already-running processes don't see the updated PATH. So this
     * looks for mysqldump.exe at its actual install location first, via
     * {@link #locateMysqlExecutable}, and only falls back to bare PATH
     * resolution if that search comes up empty.
     */
    public BackupResult createBackup() {
        String backupPath = getBackupSavePath();
        try {
            Files.createDirectories(Paths.get(backupPath));
        } catch (IOException e) {
            return new BackupResult(false, "Could not create backup directory: " + e.getMessage(), null);
        }

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = backupPath + "pos_backup_" + timestamp + ".sql";

        String[] command = {
            locateMysqlExecutable("mysqldump"),
            "--host=" + DatabaseConnection.HOST,
            "--port=" + DatabaseConnection.PORT,
            "--user=" + DatabaseConnection.USERNAME,
            // Password is passed via the MYSQL_PWD environment variable below,
            // never on the command line where it would be visible to any process
            // listing (tasklist / ps / Process Explorer).
            "--single-transaction",     // consistent backup without locking
            "--routines",
            "--triggers",
            DatabaseConnection.DATABASE
        };

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("MYSQL_PWD", DatabaseConnection.PASSWORD == null ? "" : DatabaseConnection.PASSWORD);
            pb.redirectOutput(new File(filename));
            pb.redirectErrorStream(false);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                long fileSize = new File(filename).length();
                return new BackupResult(true, "Backup created: " + filename,
                        filename + " (" + (fileSize / 1024) + " KB)");
            } else {
                return new BackupResult(false, "mysqldump exited with code " + exitCode, null);
            }
        } catch (IOException e) {
            return new BackupResult(false,
                    "Could not find or run mysqldump (" + e.getMessage() + "). "
                    + "MySQL may not have added itself to the system PATH — check that "
                    + "MySQL Server is installed and try again after restarting this PC.", null);
        } catch (Exception e) {
            return new BackupResult(false, "Backup failed: " + e.getMessage(), null);
        }
    }

    public record BackupResult(boolean success, String message, String filePath) {}

    /**
     * Finds a MySQL CLI tool (mysqldump, mysql, ...) at its actual install
     * location rather than trusting the system PATH, which the silent MySQL
     * install frequently fails to update (or updates too late for an
     * already-running process to see). Falls back to the bare command name
     * — i.e. "trust PATH after all" — if no install location is found, so
     * this still works for anyone who installed MySQL some other way.
     */
    static String locateMysqlExecutable(String exeName) {
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        java.util.List<String> candidates = new java.util.ArrayList<>();
        String regLocation = queryMysqlRegistryLocation();
        if (regLocation != null) candidates.add(regLocation);
        for (String base : new String[]{programFiles, programFilesX86}) {
            if (base == null) continue;
            for (String version : new String[]{"MySQL Server 8.4", "MySQL Server 8.0", "MySQL Server 8.3"}) {
                candidates.add(base + File.separator + "MySQL" + File.separator + version);
            }
        }

        for (String dir : candidates) {
            File exe = new File(dir, "bin" + File.separator + exeName + ".exe");
            if (exe.isFile()) return exe.getAbsolutePath();
        }
        return exeName; // last resort: rely on PATH
    }

    /** Reads the install directory MySQL's own installer records in the registry, if present. */
    private static String queryMysqlRegistryLocation() {
        for (String version : new String[]{"MySQL Server 8.4", "MySQL Server 8.0", "MySQL Server 8.3"}) {
        for (String key : new String[]{
                "HKLM\\SOFTWARE\\MySQL AB\\" + version,
                "HKLM\\SOFTWARE\\WOW6432Node\\MySQL AB\\" + version}) {
            try {
                Process p = new ProcessBuilder("reg", "query", key, "/v", "Location")
                        .redirectErrorStream(true).start();
                String output = new String(p.getInputStream().readAllBytes());
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                for (String line : output.split("\\R")) {
                    int idx = line.indexOf("REG_SZ");
                    if (idx >= 0) {
                        String path = line.substring(idx + "REG_SZ".length()).trim();
                        if (!path.isBlank()) return path;
                    }
                }
            } catch (Exception ignored) {
                // Tried our best — the candidate-directory search below still runs.
            }
        }
        }
        return null;
    }

    // ── AES-256 Encryption ─────────────────────────────────────────────────────

    /**
     * Derives a machine-specific AES key using PBKDF2.
     * The machine identifier is the username + OS name — stable across reboots.
     */
    private SecretKey deriveKey() throws Exception {
        String machineId = System.getProperty("user.name") + System.getProperty("os.name");
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(machineId.toCharArray(), SALT.getBytes(), 65536, 256);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private String encrypt(String plaintext) throws Exception {
        SecretKey key = deriveKey();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes());

        // Prepend IV to the encrypted bytes so we can retrieve it for decryption
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    private String decrypt(String encryptedBase64) throws Exception {
        SecretKey key = deriveKey();
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);

        byte[] iv        = new byte[16];
        byte[] encrypted = new byte[combined.length - 16];
        System.arraycopy(combined, 0, iv, 0, 16);
        System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted));
    }
}

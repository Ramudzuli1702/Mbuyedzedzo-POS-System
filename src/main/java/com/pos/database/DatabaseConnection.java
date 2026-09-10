package com.pos.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DatabaseConnection — manages a single shared MySQL connection.
 *
 * On startup it reads connection details from:
 *   %PROGRAMDATA%\POS System\config\db.properties
 *
 * That file is written by FirstRunSetup on the very first launch.
 * If the file doesn't exist yet (e.g. during development) the hardcoded
 * defaults below are used instead.
 *
 * Constants are non-final so the static initialiser can overwrite them
 * from the config file before any other class reads them.
 */
public class DatabaseConnection {

    // ── Connection details ─────────────────────────────────────────────────────
    // These start as defaults and are overwritten from db.properties if it exists.
    // volatile: written once at startup (loadConfig / saveConfig), read from many
    // threads afterwards.
    public static volatile String HOST     = "127.0.0.1";
    public static volatile int    PORT     = 3306;
    public static volatile String DATABASE = "pos_db";
    public static volatile String USERNAME = "root";
    public static volatile String PASSWORD = "";  // overwritten from config on real installs

    /**
     * Timezone the JDBC connection operates in. Every DATETIME column in this
     * schema stores a wall-clock value with no zone; the app both writes
     * (Timestamp.valueOf(...)) and reads (rs.getTimestamp(...).toLocalDateTime())
     * assuming that value is local time. Pinning the connection here — and
     * forcing the MySQL session's time_zone to match, with preserveInstants=false
     * so the driver does not shift DATETIMEs — keeps NOW()/CURRENT_TIMESTAMP,
     * stored values, and the Java clock all in agreement.
     *
     * Default "+02:00" (South Africa Standard Time, no DST). Override with
     * db.timezone in db.properties — use a fixed offset ("+02:00") unless the
     * MySQL server has its named-timezone tables loaded.
     */
    public static volatile String TIMEZONE = "+02:00";

    // Path the installer / first-run setup writes the config to
    public static final String CONFIG_PATH =
        System.getenv("PROGRAMDATA") + File.separator +
        "POS System"                 + File.separator +
        "config"                     + File.separator +
        "db.properties";

    // ── Static init — load config file if present ──────────────────────────────
    static {
        loadConfig();
    }

    /**
     * Reads db.properties and overwrites the connection constants.
     * Safe to call multiple times (e.g. after FirstRunSetup writes the file).
     */
    public static void loadConfig() {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            System.out.println("ℹ️  No db.properties found — using development defaults.");
            return;
        }
        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);
            HOST     = props.getProperty("db.host",     HOST);
            PORT     = Integer.parseInt(props.getProperty("db.port", String.valueOf(PORT)));
            DATABASE = props.getProperty("db.name",     DATABASE);
            USERNAME = props.getProperty("db.username", USERNAME);
            PASSWORD = props.getProperty("db.password", PASSWORD);
            TIMEZONE = props.getProperty("db.timezone", TIMEZONE);
            System.out.println("✅ DB config loaded from " + CONFIG_PATH);
        } catch (Exception e) {
            System.err.println("⚠️  Could not read db.properties — using defaults. " + e.getMessage());
        }
    }

    /**
     * Writes the connection properties to the config file.
     * Called by FirstRunSetup after it sets the MySQL root password.
     */
    public static void saveConfig(String host, int port, String database,
                                   String username, String password) {
        try {
            File configFile = new File(CONFIG_PATH);
            configFile.getParentFile().mkdirs();            // create …/config/ if needed

            Properties props = new Properties();
            props.setProperty("db.host",     host);
            props.setProperty("db.port",     String.valueOf(port));
            props.setProperty("db.name",     database);
            props.setProperty("db.username", username);
            props.setProperty("db.password", password);
            props.setProperty("db.timezone", TIMEZONE);

            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "POS System — auto-generated database configuration");
            }

            // Immediately reload so subsequent getConnection() calls use the new values
            HOST     = host;
            PORT     = port;
            DATABASE = database;
            USERNAME = username;
            PASSWORD = password;

            System.out.println("✅ db.properties written to " + CONFIG_PATH);
        } catch (Exception e) {
            System.err.println("❌ Could not write db.properties: " + e.getMessage());
            throw new RuntimeException("Failed to save database configuration", e);
        }
    }

    // ── Connections ───────────────────────────────────────────────────────────
    //
    // Every call to getConnection() returns a NEW, independent connection that
    // the caller owns and must close (all call sites use try-with-resources).
    //
    // There is deliberately no shared/cached connection: services are called
    // from the JavaFX thread and several background threads (checkout worker,
    // inventory load, marketing send, the WiFi scanner). A single shared
    // java.sql.Connection is not thread-safe — one thread closing it, or
    // toggling auto-commit, would corrupt another thread's in-flight work.

    private static volatile boolean driverLoaded = false;

    private static void ensureDriver() throws SQLException {
        if (driverLoaded) return;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found", e);
        }
    }

    /**
     * Timezone query parameters shared by every connection URL.
     * See the TIMEZONE field for the rationale.
     *
     * The value is URL-encoded: an offset like "+02:00" contains a '+', which a
     * URL query string decodes to a space — the driver would then try
     * ZoneId.of(" 02:00") and fail. Encoding turns it into "%2B02%3A00".
     */
    private static String tzParams() {
        String tz;
        try {
            tz = java.net.URLEncoder.encode(TIMEZONE, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            tz = TIMEZONE;
        }
        return "connectionTimeZone=" + tz
             + "&forceConnectionTimeZoneToSession=true"
             + "&preserveInstants=false";
    }

    private static String buildUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
             + "?useSSL=false&allowPublicKeyRetrieval=true&" + tzParams()
             + "&connectTimeout=10000&socketTimeout=30000";
    }

    /**
     * Returns a NEW connection each call. The caller owns it and must close it
     * (every call site uses try-with-resources). Returns {@code null} if the
     * connection could not be opened — callers already null-check.
     */
    public static Connection getConnection() {
        try {
            ensureDriver();
            return DriverManager.getConnection(buildUrl(), USERNAME, PASSWORD);
        } catch (SQLException e) {
            System.err.println("❌ MySQL connection failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Opens a fresh connection to MySQL WITHOUT specifying a database.
     * Used by DatabaseSetup to issue CREATE DATABASE before the schema exists.
     */
    public static Connection getRootConnection() throws SQLException {
        String url = "jdbc:mysql://" + HOST + ":" + PORT
                   + "?useSSL=false&allowPublicKeyRetrieval=true&" + tzParams()
                   + "&connectTimeout=10000";
        ensureDriver();
        return DriverManager.getConnection(url, USERNAME, PASSWORD);
    }

    /**
     * No-op — connections are per-call and closed by their callers. Kept so the
     * shutdown paths (POSApplication, MainDashboard) still compile.
     */
    public static void closeConnection() { }

    public static void closeAll() { }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}

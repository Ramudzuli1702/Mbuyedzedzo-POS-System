package com.pos.it;

import com.pos.database.DatabaseConnection;
import com.pos.database.DatabaseSetup;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared setup for integration tests.
 *
 * ITs run against a real MySQL, configured with system properties (or the
 * matching {@code POS_IT_*} environment variables):
 *
 *   -Dpos.it.jdbcUrl=jdbc:mysql://localhost:3306/pos_db_test
 *   -Dpos.it.user=root
 *   -Dpos.it.password=secret
 *
 * If no URL is supplied, {@link #requireDatabase()} aborts the test via a
 * JUnit assumption, so `mvn verify` stays green on machines without a DB.
 *
 * <p><b>Use a dedicated database</b> (e.g. {@code pos_db_test}) — the schema is
 * (re)created and tables are cleared between tests.
 */
public final class ItSupport {

    private static final Pattern URL_DB =
            Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?]+)");

    private static boolean schemaReady = false;

    private ItSupport() {}

    static String prop(String key, String envKey) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = System.getenv(envKey);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Aborts (skips) the test unless an integration DB is configured, else points
     *  DatabaseConnection at it and ensures the schema exists. */
    public static void requireDatabase() {
        String url  = prop("pos.it.jdbcUrl", "POS_IT_JDBC_URL");
        String user = prop("pos.it.user",    "POS_IT_USER");
        String pass = prop("pos.it.password","POS_IT_PASSWORD");

        Assumptions.assumeTrue(url != null,
                "integration DB not configured (set -Dpos.it.jdbcUrl=...)");

        Matcher m = URL_DB.matcher(url);
        if (!m.find()) throw new IllegalArgumentException("Unparseable pos.it.jdbcUrl: " + url);

        DatabaseConnection.HOST     = m.group(1);
        DatabaseConnection.PORT     = m.group(2) != null ? Integer.parseInt(m.group(2)) : 3306;
        DatabaseConnection.DATABASE = m.group(3);
        DatabaseConnection.USERNAME = user != null ? user : "root";
        DatabaseConnection.PASSWORD = pass != null ? pass : "";
        DatabaseConnection.closeAll();  // drop any pool built with other settings

        if (!schemaReady) {
            Assumptions.assumeTrue(DatabaseSetup.run(),
                    "DatabaseSetup.run() failed — is MySQL reachable at " + url + " ?");
            schemaReady = true;
        }
    }

    /** Empties the given tables (children first). */
    public static void truncate(String... tables) {
        try (Connection c = DatabaseConnection.getConnection();
             Statement s = c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String t : tables) s.execute("DELETE FROM " + t);
            s.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (SQLException e) {
            throw new RuntimeException("truncate failed", e);
        }
    }
}

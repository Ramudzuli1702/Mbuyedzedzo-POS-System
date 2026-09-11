package com.pos.it;

import com.pos.database.DatabaseConnection;
import com.pos.database.DatabaseSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSetupIT {

    @BeforeAll
    static void setUp() {
        ItSupport.requireDatabase();
    }

    @Test
    void createsTheCoreSchema() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection c = DatabaseConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW TABLES")) {
            while (rs.next()) tables.add(rs.getString(1).toLowerCase());
        }
        for (String expected : new String[]{
                "staff", "account", "product", "transactions", "salesequence",
                "businesssettings", "businesssessions", "promo",
                "customercommunications", "marketingsuppression"}) {
            assertTrue(tables.contains(expected), "missing table: " + expected + " (have " + tables + ")");
        }
    }

    @Test
    void runIsIdempotent() {
        assertTrue(DatabaseSetup.run(), "second DatabaseSetup.run() should succeed");
        assertTrue(DatabaseSetup.run(), "third DatabaseSetup.run() should succeed");
    }
}

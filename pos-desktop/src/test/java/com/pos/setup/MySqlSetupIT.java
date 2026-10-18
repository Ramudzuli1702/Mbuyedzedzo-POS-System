package com.pos.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Run ONLY against a disposable, initially passwordless server, never the shop database. */
@EnabledIfSystemProperty(named = "pos.setup.it.port", matches = "[0-9]+")
class MySqlSetupIT {
    @Test void chosenPasswordWithPunctuationAndUnicodeRoundTripsThroughTheActualDriver() throws Exception {
        int port = Integer.parseInt(System.getProperty("pos.setup.it.port"));
        assertTrue(port >= 1024 && port <= 65535 && port != 3306,
                "A disposable server on a non-default port is required");
        WindowsMySqlProvisioner server = new WindowsMySqlProvisioner(port);
        String password = "Chosen 'pass;word:\\9! é";
        assertTrue(server.accepts(""));
        try {
            server.changePassword("", password);
            assertTrue(server.accepts(password));
            assertFalse(server.accepts(""));
            assertFalse(server.accepts("wrong password"));
            assertTrue(server.serverExists());
        } finally {
            if (server.accepts(password)) server.changePassword(password, "");
        }
        assertTrue(server.accepts(""));
    }
}

package com.pos.it;

import com.pos.database.DatabaseConnection;
import com.pos.models.User;
import com.pos.services.UserService;
import com.pos.utils.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceIT {

    private final UserService users = new UserService();

    @BeforeAll
    static void setUp() {
        ItSupport.requireDatabase();
    }

    @BeforeEach
    void clean() {
        ItSupport.truncate("CheckIn", "Staff");
    }

    private static User staff(String name, String email, String type) {
        User u = new User();
        u.setFullNames(name);
        u.setEmailAddress(email);
        u.setUserType(type);
        return u;
    }

    @Test
    void addThenLoginSucceedsWithCorrectPasswordOnly() {
        assertTrue(users.addUser(staff("Ada Lovelace", "ada@shop.test", "Cashier"), "analyticalengine"));

        assertNull(users.login("ada@shop.test", "wrong-password"));

        User in = users.login("ada@shop.test", "analyticalengine");
        assertNotNull(in);
        assertEquals("Ada Lovelace", in.getFullNames());
        assertEquals("Cashier", in.getUserType());
    }

    @Test
    void shortPasswordIsRejectedAtCreation() {
        assertFalse(users.addUser(staff("Grace Hopper", "grace@shop.test", "Manager"), "short7"));
        assertNull(users.login("grace@shop.test", "short7"));
    }

    @Test
    void legacyHashLoginSucceedsAndIsUpgradedToPbkdf2() throws Exception {
        // Seed a row with the OLD unsalted-SHA-256 hash directly.
        String legacy = "JAvlGPq9JyTdtvBO6x2llnRI1+gxwIyPqCKAn3THIKk="; // = base64(sha256("admin123"))
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status) "
                 + "VALUES ('Legacy Admin', 'legacy@shop.test', ?, 'Admin', 'Active')")) {
            ps.setString(1, legacy);
            ps.executeUpdate();
        }

        assertNotNull(users.login("legacy@shop.test", "admin123"), "legacy hash must still authenticate");

        String stored;
        try (Connection c = DatabaseConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT UserPassword FROM Staff WHERE EmailAddress = 'legacy@shop.test'")) {
            assertTrue(rs.next());
            stored = rs.getString(1);
        }
        assertTrue(stored.startsWith("pbkdf2:sha256:"), "hash should have been upgraded on login");
        assertFalse(PasswordUtil.needsRehash(stored));
        assertTrue(PasswordUtil.verifyPassword("admin123", stored));
    }
}

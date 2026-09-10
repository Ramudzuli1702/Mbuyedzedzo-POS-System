package com.pos.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void hashThenVerifyRoundTrips() {
        String hash = PasswordUtil.hashPassword("correct horse battery staple");
        assertTrue(PasswordUtil.verifyPassword("correct horse battery staple", hash));
    }

    @Test
    void wrongPasswordIsRejected() {
        String hash = PasswordUtil.hashPassword("s3cret-value");
        assertFalse(PasswordUtil.verifyPassword("s3cret-Value", hash));
        assertFalse(PasswordUtil.verifyPassword("", hash));
    }

    @Test
    void storedHashIsSaltedAndInCurrentFormat() {
        String a = PasswordUtil.hashPassword("same-password");
        String b = PasswordUtil.hashPassword("same-password");
        assertNotEquals(a, b, "each hash must use a fresh random salt");
        assertTrue(a.startsWith("pbkdf2:sha256:210000:"));
        assertEquals(5, a.split(":").length);
    }

    @Test
    void currentFormatDoesNotNeedRehash() {
        assertFalse(PasswordUtil.needsRehash(PasswordUtil.hashPassword("whatever")));
    }

    @Test
    void legacyUnsaltedSha256StillVerifiesButNeedsRehash() {
        // Base64(SHA-256("admin123")) — the format produced by the old class.
        String legacy = "JAvlGPq9JyTdtvBO6x2llnRI1+gxwIyPqCKAn3THIKk=";
        assertTrue(PasswordUtil.verifyPassword("admin123", legacy));
        assertFalse(PasswordUtil.verifyPassword("admin124", legacy));
        assertTrue(PasswordUtil.needsRehash(legacy));
    }

    @Test
    void nullsAreHandled() {
        assertFalse(PasswordUtil.verifyPassword(null, "x"));
        assertFalse(PasswordUtil.verifyPassword("x", null));
        assertTrue(PasswordUtil.needsRehash(null));
    }

    @Test
    void passwordPolicyRequiresEightChars() {
        assertFalse(PasswordUtil.isValidPassword("short7"));
        assertTrue(PasswordUtil.isValidPassword("longenough"));
        assertFalse(PasswordUtil.isValidPassword(null));
    }
}

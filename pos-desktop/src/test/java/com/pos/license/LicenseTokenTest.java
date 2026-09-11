package com.pos.license;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class LicenseTokenTest {

    private static String token(Instant tokenExp, Instant licExp, String product) {
        return TestTokens.signed(TestTokens.payload(
                "POS-2026-XK29-MNQT-7R4B", product, "fp-123", tokenExp, licExp));
    }

    @Test
    void verifiesAWellFormedTokenSignedByTheDevKey() {
        var t = LicenseToken.verify(token(
                Instant.now().plus(7, ChronoUnit.DAYS), null, "POS_STANDARD"));
        assertEquals("POS-2026-XK29-MNQT-7R4B", t.key);
        assertEquals("POS_STANDARD", t.product);
        assertEquals("fp-123", t.fingerprint);
        assertTrue(t.tokenFresh());
        assertFalse(t.licenseExpired());
        assertNull(t.licenseExpiry);
    }

    @Test
    void detectsAnExpiredTokenWindowButStillParses() {
        var t = LicenseToken.verify(token(
                Instant.now().minus(1, ChronoUnit.DAYS), null, "POS_STANDARD"));
        assertFalse(t.tokenFresh());
    }

    @Test
    void detectsAnExpiredLicense() {
        var t = LicenseToken.verify(token(
                Instant.now().plus(7, ChronoUnit.DAYS),
                Instant.now().minus(2, ChronoUnit.DAYS), "POS_STANDARD"));
        assertTrue(t.licenseExpired());
    }

    @Test
    void rejectsATamperedPayload() {
        String jwt = token(Instant.now().plus(7, ChronoUnit.DAYS), null, "POS_STANDARD");
        String[] p = jwt.split("\\.");
        // swap in a different (unsigned) payload
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                TestTokens.payload("POS-9999-AAAA-AAAA-AAAA", "POS_STANDARD", "fp-123",
                        Instant.now().plus(999, ChronoUnit.DAYS), null).getBytes());
        String tampered = p[0] + "." + forged + "." + p[2];
        assertThrows(LicenseToken.LicenseException.class, () -> LicenseToken.verify(tampered));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(LicenseToken.LicenseException.class, () -> LicenseToken.verify("not.a.jwt"));
        assertThrows(LicenseToken.LicenseException.class, () -> LicenseToken.verify("only-one-part"));
    }
}

package com.mbuyedzedzo.licensing.license;

import com.mbuyedzedzo.licensing.config.LicensingProperties;
import com.mbuyedzedzo.licensing.domain.License;
import com.mbuyedzedzo.licensing.domain.LicenseStatus;
import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Product;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LicenseTokenServiceTest {

    private LicenseTokenService tokens;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        SigningKeys keys = SigningKeys.withKeyPair(g.generateKeyPair());
        var props = new LicensingProperties(
                new LicensingProperties.Token("test-issuer", 7, null, null),
                new LicensingProperties.Trial(30, 1),
                new LicensingProperties.BootstrapAdmin("a@b.c", "x"));
        tokens = new LicenseTokenService(keys, props);
    }

    private static License license() {
        License l = new License();
        l.setLicenseKey("POS-2026-XK29-MNQT-7R4B");
        l.setProduct(Product.POS_STANDARD);
        l.setType(LicenseType.SUBSCRIPTION);
        l.setStatus(LicenseStatus.ACTIVE);
        l.setMaxMachines(2);
        l.setExpiresAt(Instant.now().plusSeconds(86_400 * 365));
        return l;
    }

    @Test
    void issuesAVerifiableTokenWithTheExpectedClaims() throws Exception {
        var issued = tokens.issue(license(), "fp-abc123");

        JWTClaimsSet c = tokens.verify(issued.token());
        assertEquals("test-issuer", c.getIssuer());
        assertEquals("POS-2026-XK29-MNQT-7R4B", c.getSubject());
        assertEquals("POS_STANDARD", c.getStringClaim("product"));
        assertEquals("fp-abc123", c.getStringClaim("fingerprint"));
        assertEquals("SUBSCRIPTION", c.getStringClaim("licenseType"));
        assertEquals(2, c.getIntegerClaim("maxMachines"));
        assertTrue(c.getExpirationTime().toInstant().isAfter(Instant.now()));
        assertTrue(c.getExpirationTime().toInstant().isBefore(Instant.now().plusSeconds(8L * 86_400)));
        assertEquals(issued.expiresAt().getEpochSecond(), c.getExpirationTime().toInstant().getEpochSecond());
    }

    @Test
    void aTokenSignedByAnotherKeyIsRejected() throws Exception {
        var issued = tokens.issue(license(), "fp");

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        var other = new LicenseTokenService(
                SigningKeys.withKeyPair(g.generateKeyPair()),
                new LicensingProperties(new LicensingProperties.Token("x", 7, null, null),
                        new LicensingProperties.Trial(30, 1),
                        new LicensingProperties.BootstrapAdmin("a", "b")));

        assertThrows(IllegalArgumentException.class, () -> other.verify(issued.token()));
    }

    @Test
    void perpetualLicenseHasNullExpiryClaim() throws Exception {
        License l = license();
        l.setType(LicenseType.PERPETUAL);
        l.setExpiresAt(null);
        JWTClaimsSet c = tokens.verify(tokens.issue(l, "fp").token());
        assertNull(c.getClaim("licenseExpiresAt"));
    }
}

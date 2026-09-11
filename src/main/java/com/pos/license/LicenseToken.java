package com.pos.license;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Parses and verifies the RS256 licence token issued by the licensing server,
 * fully offline, using the public key bundled at {@code /license-public.pem}.
 * No JWT library — a JWT is just {@code b64url(header).b64url(payload).b64url(sig)}.
 */
public final class LicenseToken {

    private static final Logger log = LoggerFactory.getLogger(LicenseToken.class);
    private static PublicKey publicKey;

    public final String key;               // sub
    public final String product;           // POS_STANDARD | POS_RETAIL
    public final String fingerprint;
    public final String licenseType;
    public final String licenseStatus;
    public final Instant tokenExpiry;      // exp
    public final Instant licenseExpiry;    // null = perpetual

    private LicenseToken(JsonObject p) {
        this.key = str(p, "sub");
        this.product = str(p, "product");
        this.fingerprint = str(p, "fingerprint");
        this.licenseType = str(p, "licenseType");
        this.licenseStatus = str(p, "licenseStatus");
        this.tokenExpiry = p.has("exp") ? Instant.ofEpochSecond(p.get("exp").getAsLong()) : Instant.EPOCH;
        this.licenseExpiry = p.has("licenseExpiresAt") && !p.get("licenseExpiresAt").isJsonNull()
                ? Instant.parse(p.get("licenseExpiresAt").getAsString()) : null;
    }

    /** @throws LicenseException if the signature is invalid or the token is malformed. */
    public static LicenseToken verify(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) throw new LicenseException("Malformed token");

            byte[] signed = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] sig = Base64.getUrlDecoder().decode(parts[2]);

            Signature v = Signature.getInstance("SHA256withRSA");
            v.initVerify(publicKey());
            v.update(signed);
            if (!v.verify(sig)) throw new LicenseException("Bad token signature");

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return new LicenseToken(JsonParser.parseString(payload).getAsJsonObject());
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseException("Could not read token: " + e.getMessage());
        }
    }

    public boolean tokenFresh()    { return tokenExpiry.isAfter(Instant.now()); }
    public boolean licenseExpired() { return licenseExpiry != null && licenseExpiry.isBefore(Instant.now()); }

    private static synchronized PublicKey publicKey() throws Exception {
        if (publicKey == null) {
            try (InputStream in = LicenseToken.class.getResourceAsStream("/license-public.pem")) {
                if (in == null) throw new IllegalStateException("license-public.pem missing from the build");
                String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                        .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                byte[] der = Base64.getDecoder().decode(pem);
                publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            }
        }
        return publicKey;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    /** Verification / parsing failure. */
    public static final class LicenseException extends RuntimeException {
        public LicenseException(String m) { super(m); }
    }
}

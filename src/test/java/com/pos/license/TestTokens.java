package com.pos.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Signs licence tokens in tests with the committed DEV private key, which pairs
 * with the {@code /license-public.pem} bundled in the app.
 */
final class TestTokens {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    static String signed(String payloadJson) {
        try {
            PrivateKey pk = devPrivateKey();
            String header = B64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
            String body   = B64.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(pk);
            s.update((header + "." + body).getBytes(StandardCharsets.UTF_8));
            String sig = B64.encodeToString(s.sign());
            return header + "." + body + "." + sig;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Convenience: a well-formed payload for the given fields. */
    static String payload(String key, String product, String fingerprint,
                          Instant tokenExp, Instant licenseExp) {
        return "{"
                + "\"sub\":\"" + key + "\","
                + "\"product\":\"" + product + "\","
                + "\"fingerprint\":\"" + fingerprint + "\","
                + "\"licenseType\":\"SUBSCRIPTION\","
                + "\"licenseStatus\":\"ACTIVE\","
                + "\"exp\":" + tokenExp.getEpochSecond() + ","
                + "\"licenseExpiresAt\":" + (licenseExp == null ? "null" : "\"" + licenseExp + "\"")
                + "}";
    }

    private static PrivateKey devPrivateKey() throws Exception {
        try (var in = TestTokens.class.getResourceAsStream("/license-private-dev.pem")) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }
}

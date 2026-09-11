package com.mbuyedzedzo.licensing.license;

import com.mbuyedzedzo.licensing.domain.Product;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Year;
import java.util.function.Predicate;

/**
 * License keys look like {@code POS-2026-XK29-MNQT-7R4B}:
 *
 *   segment 1 — product prefix (POS / RET)
 *   segment 2 — issue year
 *   segments 3-4 — random, from an unambiguous alphabet (no O/0, I/1, etc.)
 *   segment 5 — checksum of the preceding text, so typos are caught before
 *               the server is even contacted
 *
 * {@link #hasValidChecksum(String)} is duplicated in the desktop app.
 */
@Component
public class LicenseKeyGenerator {

    /** Crockford-ish: 32 chars, no I L O U, no 0 1. */
    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int BLOCK = 4;

    private final SecureRandom random = new SecureRandom();

    public String generate(Product product) {
        String prefix = product.keyPrefix();
        String year = String.valueOf(Year.now().getValue());
        String b1 = randomBlock();
        String b2 = randomBlock();
        String body = prefix + "-" + year + "-" + b1 + "-" + b2;
        return body + "-" + checksum(body);
    }

    /** Generate a key that {@code alreadyExists} says isn't taken. */
    public String generateUnique(Product product, Predicate<String> alreadyExists) {
        for (int i = 0; i < 20; i++) {
            String k = generate(product);
            if (!alreadyExists.test(k)) return k;
        }
        throw new IllegalStateException("Could not generate a unique license key");
    }

    public boolean hasValidChecksum(String key) {
        if (key == null) return false;
        String k = key.trim().toUpperCase();
        int last = k.lastIndexOf('-');
        if (last < 0) return false;
        String body = k.substring(0, last);
        String chk = k.substring(last + 1);
        return chk.equals(checksum(body));
    }

    private String randomBlock() {
        StringBuilder sb = new StringBuilder(BLOCK);
        for (int i = 0; i < BLOCK; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    static String checksum(String body) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(body.toUpperCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(BLOCK);
            for (int i = 0; i < BLOCK; i++) {
                sb.append(ALPHABET.charAt((h[i] & 0xFF) % ALPHABET.length()));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.pos.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PasswordUtil — password hashing and verification.
 *
 * STORAGE FORMAT (current):
 *   pbkdf2:sha256:{iterations}:{base64-salt}:{base64-hash}
 *
 *   PBKDF2-HMAC-SHA256, 16-byte random salt per password, 210 000 iterations,
 *   256-bit derived key. No external dependency — uses the JDK's JCE provider.
 *
 * LEGACY FORMAT (still verifiable, auto-upgraded on next login):
 *   A bare Base64 string with no ":" — an unsalted single-round SHA-256 digest
 *   as produced by earlier versions of this class. verifyPassword() still
 *   accepts these so existing accounts keep working; UserService.login()
 *   calls needsRehash() and rewrites the stored hash in the new format the
 *   first time the user authenticates successfully.
 */
public class PasswordUtil {

    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";
    private static final String PREFIX      = "pbkdf2:sha256:";
    private static final int    ITERATIONS  = 210_000;
    private static final int    SALT_BYTES  = 16;
    private static final int    KEY_BITS    = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Hashing ───────────────────────────────────────────────────────────────

    /** Hashes a password in the current PBKDF2 format. */
    public static String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return PREFIX + ITERATIONS + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    // ── Verification ──────────────────────────────────────────────────────────

    /**
     * Verifies a plaintext password against a stored hash.
     * Handles both the current PBKDF2 format and the legacy unsalted SHA-256 format.
     */
    public static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null || stored.isBlank()) {
            return false;
        }

        if (stored.startsWith(PREFIX)) {
            try {
                String[] parts = stored.substring(PREFIX.length()).split(":");
                if (parts.length != 3) return false;
                int    iterations = Integer.parseInt(parts[0]);
                byte[] salt       = Base64.getDecoder().decode(parts[1]);
                byte[] expected   = Base64.getDecoder().decode(parts[2]);
                byte[] actual     = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
                return MessageDigest.isEqual(expected, actual);
            } catch (RuntimeException e) {
                return false;
            }
        }

        // Legacy: unsalted, single-round SHA-256, Base64-encoded.
        return MessageDigest.isEqual(
                legacySha256(password).getBytes(),
                stored.getBytes());
    }

    /**
     * Returns true if the stored hash is not in the current format and should be
     * re-hashed. Call after a successful verifyPassword() and rewrite the column.
     */
    public static boolean needsRehash(String stored) {
        return stored == null || !stored.startsWith(PREFIX);
    }

    // ── Policy / helpers ──────────────────────────────────────────────────────

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    public static String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return password.toString();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGO);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    private static String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(password.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}

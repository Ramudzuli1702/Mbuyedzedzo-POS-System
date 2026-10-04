package com.mbuyedzedzo.licensing.admin;

import com.mbuyedzedzo.licensing.domain.EmailSettings;
import com.mbuyedzedzo.licensing.repo.EmailSettingsRepo;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Properties;

/**
 * SMTP settings entered in the admin portal (Settings → Email), stored in
 * the database instead of Azure App Service environment variables — a super
 * admin can change the sending account without touching the Azure portal.
 *
 * The password is encrypted at rest with a key derived from a fixed,
 * application-embedded passphrase (AES-256-CBC + PBKDF2) — the same
 * discipline as the desktop app's local settings encryption, adapted for a
 * server with no single stable "machine identity" to derive from. This is
 * obfuscation against a casual database browse, not a defence against a full
 * server compromise — same risk tolerance this project already accepts for
 * the committed dev-keys signing pair.
 */
@Service
public class MailSettingsService {

    private static final String PASSPHRASE = "mbuyedzedzo-licensing-email-settings-v1";
    private static final String SALT = "MBZ_EMAIL_SALT_2026";

    private final EmailSettingsRepo repo;

    public MailSettingsService(EmailSettingsRepo repo) {
        this.repo = repo;
    }

    public record Settings(String host, int port, String username, String password,
                            String fromName, boolean useStartTls) {
        boolean isConfigured() {
            return host != null && !host.isBlank() && username != null && !username.isBlank();
        }
    }

    public Settings current() {
        EmailSettings e = repo.findById(1L).orElse(null);
        if (e == null) return new Settings(null, 587, null, null, null, true);
        String password = null;
        if (e.getSmtpPasswordEncrypted() != null && !e.getSmtpPasswordEncrypted().isBlank()) {
            try {
                password = decrypt(e.getSmtpPasswordEncrypted());
            } catch (Exception ex) {
                password = null; // corrupt/legacy value — treat as unset rather than fail sending outright
            }
        }
        return new Settings(e.getSmtpHost(), e.getSmtpPort() == null ? 587 : e.getSmtpPort(),
                e.getSmtpUsername(), password, e.getFromName(), e.isUseStartTls());
    }

    /** The password field: blank/unchanged means "keep the existing one" — never shown back to the browser. */
    public void save(String host, int port, String username, String newPasswordOrBlank,
                     String fromName, boolean useStartTls) {
        EmailSettings e = repo.findById(1L).orElseGet(() -> {
            EmailSettings fresh = new EmailSettings();
            fresh.setId(1L);
            return fresh;
        });
        e.setSmtpHost(host);
        e.setSmtpPort(port);
        e.setSmtpUsername(username);
        e.setFromName(fromName);
        e.setUseStartTls(useStartTls);
        if (newPasswordOrBlank != null && !newPasswordOrBlank.isBlank()) {
            try {
                e.setSmtpPasswordEncrypted(encrypt(newPasswordOrBlank));
            } catch (Exception ex) {
                throw new IllegalStateException("Could not encrypt the password", ex);
            }
        }
        repo.save(e);
    }

    public boolean hasPasswordSet() {
        EmailSettings e = repo.findById(1L).orElse(null);
        return e != null && e.getSmtpPasswordEncrypted() != null && !e.getSmtpPasswordEncrypted().isBlank();
    }

    /** Builds a fresh JavaMailSenderImpl from the current settings, or null if not configured yet. */
    public JavaMailSenderImpl buildSender() {
        Settings s = current();
        if (!s.isConfigured()) return null;

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(s.host());
        sender.setPort(s.port());
        sender.setUsername(s.username());
        sender.setPassword(s.password());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(s.useStartTls()));
        return sender;
    }

    // ── AES-256 encryption, mirroring pos-desktop's SettingsService ────────

    private static SecretKey deriveKey() throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(PASSPHRASE.toCharArray(), SALT.getBytes(), 65536, 256);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private static String encrypt(String plaintext) throws Exception {
        SecretKey key = deriveKey();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes());

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private static String decrypt(String encryptedBase64) throws Exception {
        SecretKey key = deriveKey();
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);

        byte[] iv = new byte[16];
        byte[] encrypted = new byte[combined.length - 16];
        System.arraycopy(combined, 0, iv, 0, 16);
        System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted));
    }
}

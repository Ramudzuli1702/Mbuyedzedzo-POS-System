package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.config.EmailProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Delivers the purchased licence key by email. If {@code spring.mail.username}
 * isn't set (the dev default), it logs the email instead of attempting to
 * send — so the purchase flow is fully testable without real SMTP creds.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailProperties props;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailService(JavaMailSender mailSender, EmailProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    /**
     * @param setPasswordUrl null for a repeat customer who already has an
     *                       account; otherwise shown as a "set up your
     *                       account" link (one-time token, expires in 7 days).
     */
    public void sendLicenseEmail(String toEmail, String buyerName, String productName,
                                  String licenseKey, String licenseType, String setPasswordUrl) {
        // buyerName is free text the buyer typed at checkout — escape before it goes into
        // HTML. (productName/licenseType/licenseKey are all server-generated, not user input.)
        String safeBuyerName = escapeHtml(buyerName);
        String subject = "Your " + productName + " licence key";
        String accountBlock = setPasswordUrl == null ? "" : """
                <p style="color:#1f2937;">Set up your account to manage this licence online
                   (view it, and request a transfer if you ever change computers):</p>
                <p style="text-align:center;margin:20px 0;">
                  <a href="%s" style="background:#0f766e;color:#ffffff;text-decoration:none;
                     padding:10px 24px;border-radius:8px;font-weight:bold;display:inline-block;">
                     Set up my account</a>
                </p>
                <p style="color:#94a3b8;font-size:12px;">This link expires in 7 days.</p>
                """.formatted(setPasswordUrl);

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;">
                  <div style="max-width:480px;margin:24px auto;background:#ffffff;border-radius:12px;
                              overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 2px 10px rgba(15,23,42,0.08);">
                    <div style="background:#0f766e;padding:28px 24px;text-align:center;">
                      <div style="color:#ffffff;font-size:18px;font-weight:bold;">%s</div>
                      <div style="color:#d3ece9;font-size:12px;margin-top:4px;">Thanks for your purchase</div>
                    </div>
                    <div style="padding:24px;">
                      <p style="color:#1f2937;">Hi %s,</p>
                      <p style="color:#1f2937;">Here's your licence key for <strong>%s</strong> (%s):</p>
                      <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;
                                  padding:16px;text-align:center;font-family:'Courier New',Courier,monospace;
                                  font-size:16px;letter-spacing:1px;color:#0f766e;font-weight:bold;">
                        %s
                      </div>
                      <p style="color:#475569;font-size:13px;">
                        Enter this on the Activation screen the first time you launch the app.
                        It activates on the first computer you enter it on.
                      </p>
                      %s
                    </div>
                    <div style="background:#f8fafc;padding:16px 20px;text-align:center;
                                font-size:11px;color:#94a3b8;border-top:1px solid #e2e8f0;">
                      Mbuyedzedzo
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(productName, safeBuyerName, productName, licenseType, licenseKey, accountBlock);

        if (mailUsername == null || mailUsername.isBlank()) {
            log.info("[dev mode — no SMTP configured] Would email {} <{}>: {}\n{}",
                    buyerName, toEmail, subject, "licence key " + licenseKey);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
            helper.setTo(toEmail);
            helper.setFrom(mailUsername, props.fromName());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send licence email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

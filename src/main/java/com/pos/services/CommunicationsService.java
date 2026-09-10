package com.pos.services;

import com.pos.database.DatabaseConnection;

import javax.mail.*;
import javax.mail.internet.*;
import java.sql.*;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

public class CommunicationsService {

    // ─────────────────────────────────────────────
    // Inner class: CommPreferences (unchanged)
    // ─────────────────────────────────────────────
    public static class CommPreferences {
        private int accountID;
        private boolean marketingEmails;
        private boolean receiptByEmail = true;
        private boolean smsNotifications;
        private boolean termsAccepted;

        public int getAccountID() { return accountID; }
        public void setAccountID(int accountID) { this.accountID = accountID; }

        public boolean isMarketingEmails() { return marketingEmails; }
        public void setMarketingEmails(boolean v) { this.marketingEmails = v; }

        public boolean isReceiptByEmail() { return receiptByEmail; }
        public void setReceiptByEmail(boolean v) { this.receiptByEmail = v; }

        public boolean isSmsNotifications() { return smsNotifications; }
        public void setSmsNotifications(boolean v) { this.smsNotifications = v; }

        public boolean isTermsAccepted() { return termsAccepted; }
        public void setTermsAccepted(boolean v) { this.termsAccepted = v; }
    }

    // ─────────────────────────────────────────────
    // Marketing history model (inner class)
    // ─────────────────────────────────────────────
    public static class MarketingCampaign {
        private int campaignID;
        private String subject;
        private String bodyHtml;
        private String sentByName;
        private String sentAt;
        private int recipientsCount;
        private int successCount;

        public int getCampaignID() { return campaignID; }
        public void setCampaignID(int v) { this.campaignID = v; }

        public String getSubject() { return subject; }
        public void setSubject(String v) { this.subject = v; }

        public String getBodyHtml() { return bodyHtml; }
        public void setBodyHtml(String v) { this.bodyHtml = v; }

        public String getSentByName() { return sentByName; }
        public void setSentByName(String v) { this.sentByName = v; }

        public String getSentAt() { return sentAt; }
        public void setSentAt(String v) { this.sentAt = v; }

        public int getRecipientsCount() { return recipientsCount; }
        public void setRecipientsCount(int v) { this.recipientsCount = v; }

        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int v) { this.successCount = v; }
    }

    // ─────────────────────────────────────────────
    // SettingsService — replaces EmailConfig
    // ─────────────────────────────────────────────
    private final SettingsService settings = new SettingsService();

    /** Display name for the business in email subjects, headers and footers. */
    private String bizName() {
        String n = settings.getBusinessName();
        return (n == null || n.isBlank()) ? "Our Store" : n;
    }

    /** Contact address shown in email footers — the business email, or the sending account. */
    private String bizEmail() {
        String e = settings.getBusinessEmail();
        if (e == null || e.isBlank()) e = settings.getEmailUsername();
        return (e == null) ? "" : e;
    }

    // ─────────────────────────────────────────────
    // Gmail SMTP session — credentials from SettingsService
    // ─────────────────────────────────────────────
    private Session getMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        settings.getEmailUsername(),
                        settings.getEmailPassword());
            }
        });
    }

    // ─────────────────────────────────────────────
    // Send receipt email (plain text + HTML)
    // ─────────────────────────────────────────────
    public boolean sendReceiptByEmail(String toEmail, String receiptText) {
        try {
            Session session = getMailSession();

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(
                    settings.getEmailUsername(),
                    settings.getEmailFromName()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("Your Receipt from " + bizName());

            String htmlBody = buildReceiptHtml(receiptText);

            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(receiptText, "utf-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=utf-8");

            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);
            message.setContent(multipart);

            Transport.send(message);
            System.out.println("✅ Receipt emailed to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Failed to send receipt email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ─────────────────────────────────────────────
    // Send marketing email
    // ─────────────────────────────────────────────
    public boolean sendMarketingEmail(String toEmail, String customerName,
            String subject, String bodyHtml) {
        return sendMarketingEmail(toEmail, customerName, subject, bodyHtml, null);
    }

    /**
     * @param unsubToken per-customer unsubscribe token; enables the footer
     *                   unsubscribe link and the List-Unsubscribe header so the
     *                   recipient can opt out with one click.
     */
    public boolean sendMarketingEmail(String toEmail, String customerName,
            String subject, String bodyHtml, String unsubToken) {
        try {
            Session session = getMailSession();

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(
                    settings.getEmailUsername(),
                    settings.getEmailFromName()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject(subject);

            if (unsubToken != null && !unsubToken.isBlank() && !bizEmail().isBlank()) {
                message.setHeader("List-Unsubscribe",
                        "<mailto:" + bizEmail() + "?subject=Unsubscribe%20" + unsubToken + ">");
            }

            String fullHtml = buildMarketingHtml(customerName, bodyHtml, unsubToken);
            message.setContent(fullHtml, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("✅ Marketing email sent to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Failed to send marketing email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Blast marketing email to ALL opted-in customers.
     * Call this from a background thread — never on the JavaFX thread.
     *
     * @return number of emails successfully sent
     */
    public int sendMarketingBlast(String subject, String bodyHtml, int staffID) {
        String query = """
                    SELECT a.AccountID, a.EmailAddress, a.FullNames
                    FROM Account a
                    JOIN CustomerCommunications cc ON a.AccountID = cc.AccountID
                    WHERE cc.MarketingEmails = TRUE
                """;

        int successCount = 0;
        int totalCount = 0;

        // Collect first, then send — so we're not holding the shared connection
        // open while getOrCreateUnsubToken() needs it per recipient.
        java.util.List<int[]> ids = new ArrayList<>();
        java.util.List<String[]> recipients = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                ids.add(new int[]{ rs.getInt("AccountID") });
                recipients.add(new String[]{ rs.getString("EmailAddress"), rs.getString("FullNames") });
            }
        } catch (SQLException e) {
            System.err.println("❌ Marketing blast query error: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            for (int i = 0; i < recipients.size(); i++) {
                totalCount++;
                String email = recipients.get(i)[0];
                String name  = recipients.get(i)[1];
                String token = getOrCreateUnsubToken(ids.get(i)[0]);
                if (sendMarketingEmail(email, name, subject, bodyHtml, token))
                    successCount++;
                Thread.sleep(200);
            }

        } catch (Exception e) {
            System.err.println("❌ Marketing blast error: " + e.getMessage());
            e.printStackTrace();
        }

        saveCampaignHistory(subject, bodyHtml, staffID, totalCount, successCount);
        System.out.println("📊 Marketing blast complete: " + successCount + "/" + totalCount + " sent");
        return successCount;
    }

    private void saveCampaignHistory(String subject, String bodyHtml,
            int staffID, int total, int success) {
        String sql = """
                    INSERT INTO MarketingHistory (Subject, BodyHtml, SentBy, RecipientsCount, SuccessCount)
                    VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, subject);
            pstmt.setString(2, bodyHtml);
            pstmt.setInt(3, staffID);
            pstmt.setInt(4, total);
            pstmt.setInt(5, success);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Failed to save campaign history: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<MarketingCampaign> getCampaignHistory() {
        List<MarketingCampaign> history = new ArrayList<>();
        String sql = """
                    SELECT mh.CampaignID, mh.Subject, mh.BodyHtml, mh.RecipientsCount, mh.SuccessCount,
                           DATE_FORMAT(mh.SentAt, '%d %b %Y  %H:%i') AS SentAtFormatted,
                           s.FullNames AS SentByName
                    FROM MarketingHistory mh
                    LEFT JOIN Staff s ON mh.SentBy = s.StaffID
                    ORDER BY mh.SentAt DESC
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                MarketingCampaign c = new MarketingCampaign();
                c.setCampaignID(rs.getInt("CampaignID"));
                c.setSubject(rs.getString("Subject"));
                c.setBodyHtml(rs.getString("BodyHtml"));
                c.setSentByName(rs.getString("SentByName"));
                c.setSentAt(rs.getString("SentAtFormatted"));
                c.setRecipientsCount(rs.getInt("RecipientsCount"));
                c.setSuccessCount(rs.getInt("SuccessCount"));
                history.add(c);
            }

        } catch (SQLException e) {
            System.err.println("❌ Failed to load campaign history: " + e.getMessage());
            e.printStackTrace();
        }
        return history;
    }

    // ─────────────────────────────────────────────
    // SMS via email-to-SMS gateway
    // ─────────────────────────────────────────────
    public boolean sendSMS(String phoneNumber, String carrier, String message) {
        String gateway = getSmsGateway(carrier);
        if (gateway == null) {
            System.err.println("⚠️ Unknown carrier for SMS gateway: " + carrier);
            return false;
        }

        String digits = phoneNumber.replaceAll("[^0-9]", "");
        String smsEmail = digits + "@" + gateway;

        try {
            Session session = getMailSession();

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(settings.getEmailUsername()));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(smsEmail));
            msg.setSubject("");
            msg.setText(message);

            Transport.send(msg);
            System.out.println("✅ SMS sent to " + phoneNumber + " via " + gateway);
            return true;

        } catch (Exception e) {
            System.err.println("❌ SMS send failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String getSmsGateway(String carrier) {
        if (carrier == null) return null;
        return switch (carrier.toLowerCase().trim()) {
            case "vodacom"         -> "voda.co.za";
            case "mtn"             -> "mtn.co.za";
            case "cellc", "cell c" -> "cellc.co.za";
            case "telkom"          -> "telkomsa.net";
            default                -> null;
        };
    }

    // ─────────────────────────────────────────────
    // DB: preferences
    // ─────────────────────────────────────────────
    public boolean savePreferences(CommPreferences prefs) {
        String query = """
                    INSERT INTO CustomerCommunications
                    (AccountID, MarketingEmails, ReceiptByEmail, SMSNotifications, TermsAccepted, AcceptanceDate)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        MarketingEmails  = VALUES(MarketingEmails),
                        ReceiptByEmail   = VALUES(ReceiptByEmail),
                        SMSNotifications = VALUES(SMSNotifications),
                        TermsAccepted    = VALUES(TermsAccepted),
                        AcceptanceDate   = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, prefs.getAccountID());
            pstmt.setBoolean(2, prefs.isMarketingEmails());
            pstmt.setBoolean(3, prefs.isReceiptByEmail());
            pstmt.setBoolean(4, prefs.isSmsNotifications());
            pstmt.setBoolean(5, prefs.isTermsAccepted());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public CommPreferences getPreferences(int accountID) {
        String query = "SELECT * FROM CustomerCommunications WHERE AccountID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, accountID);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                CommPreferences prefs = new CommPreferences();
                prefs.setAccountID(rs.getInt("AccountID"));
                prefs.setMarketingEmails(rs.getBoolean("MarketingEmails"));
                prefs.setReceiptByEmail(rs.getBoolean("ReceiptByEmail"));
                prefs.setSmsNotifications(rs.getBoolean("SMSNotifications"));
                prefs.setTermsAccepted(rs.getBoolean("TermsAccepted"));
                return prefs;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new CommPreferences();
    }

    public boolean hasAcceptedTerms(int accountID) {
        return getPreferences(accountID).isTermsAccepted();
    }

    // ─────────────────────────────────────────────
    // Terms text
    // ─────────────────────────────────────────────
    public static String getTermsAndConditions() {
        return """
                TERMS AND CONDITIONS

                1. RECEIPT DELIVERY
                We will send your receipt to the email address provided.
                You can opt out of email receipts at any time.

                2. MARKETING COMMUNICATIONS
                By opting in, you agree to receive marketing emails about:
                - Special offers and promotions
                - New product announcements
                - Exclusive deals for members
                You can unsubscribe at any time.

                3. SMS NOTIFICATIONS
                If you opt in for SMS notifications, you may receive:
                - Order confirmations
                - Special promotional offers
                - Important account updates
                Standard messaging rates may apply.

                4. DATA PRIVACY
                We respect your privacy and will never sell your personal
                information to third parties.

                5. CONTACT PREFERENCES
                You can update your communication preferences at any time.

                By accepting these terms, you acknowledge that you have read
                and understood these conditions.
                """;
    }

    // ─────────────────────────────────────────────
    // HTML builders
    // ─────────────────────────────────────────────
    private String buildReceiptHtml(String receiptText) {
        String escaped = receiptText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");

        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f5f7fa; padding: 20px;">
                  <div style="max-width: 480px; margin: auto; background: white;
                              border-radius: 10px; overflow: hidden;
                              box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <div style="background: #667eea; padding: 20px; text-align: center;">
                      <h2 style="color: white; margin: 0;">🧾 Your Receipt</h2>
                      <p style="color: #e0e6ff; margin: 4px 0 0;">Thank you for shopping with us!</p>
                    </div>
                    <div style="padding: 24px; font-family: monospace; font-size: 13px;
                                line-height: 1.6; color: #2c3e50; white-space: pre-wrap;">
                      %s
                    </div>
                    <div style="background: #f5f7fa; padding: 16px; text-align: center;
                                font-size: 11px; color: #7f8c8d;">
                      %s &nbsp;|&nbsp; %s<br>
                      <a href="mailto:%s?subject=Unsubscribe%%20from%%20receipts"
                         style="color: #95a5a6;">Unsubscribe from email receipts</a>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escaped, bizName(), bizEmail(), bizEmail());
    }

    private String buildMarketingHtml(String customerName, String bodyHtml, String unsubToken) {
        String subject = (unsubToken != null && !unsubToken.isBlank())
                ? "Unsubscribe%20" + unsubToken
                : "Unsubscribe";
        String unsubLink = "mailto:" + bizEmail() + "?subject=" + subject;
        String unsubNote = (unsubToken != null && !unsubToken.isBlank())
                ? "Click Unsubscribe and send the message — we'll remove you from our mailing list."
                : "Reply to this email with \"Unsubscribe\" to opt out.";

        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background: #f5f7fa; padding: 20px;">
                  <div style="max-width: 560px; margin: auto; background: white;
                              border-radius: 10px; overflow: hidden;
                              box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <div style="background: linear-gradient(135deg, #667eea, #764ba2);
                                padding: 28px; text-align: center;">
                      <h1 style="color: white; margin: 0; font-size: 24px;">%s</h1>
                      <p style="color: #e0e6ff; margin: 6px 0 0; font-size: 13px;">Special offer just for you</p>
                    </div>
                    <div style="padding: 28px; color: #2c3e50;">
                      <p style="font-size: 15px;">Hi <strong>%s</strong>,</p>
                      %s
                    </div>
                    <div style="background: #f5f7fa; padding: 16px; text-align: center;
                                font-size: 11px; color: #7f8c8d; border-top: 1px solid #e0e0e0;">
                      %s &nbsp;|&nbsp; %s<br>
                      <a href="%s" style="color: #95a5a6;">Unsubscribe</a><br>
                      <span style="color: #b0b8c0;">%s</span>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(bizName(), customerName, bodyHtml, bizName(), bizEmail(), unsubLink, unsubNote);
    }

    public List<String[]> getSubscribedCustomers() {
        List<String[]> customers = new ArrayList<>();
        String sql = """
                    SELECT a.AccountID, a.FullNames, a.EmailAddress, cc.SMSNotifications
                    FROM Account a
                    JOIN CustomerCommunications cc ON a.AccountID = cc.AccountID
                    WHERE cc.MarketingEmails = TRUE
                    ORDER BY a.FullNames
                """;
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                customers.add(new String[] {
                        rs.getString("FullNames"),
                        rs.getString("EmailAddress"),
                        rs.getBoolean("SMSNotifications") ? "✅" : "—",
                        String.valueOf(rs.getInt("AccountID"))   // index 3 — for the Unsubscribe action
                });
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load subscribers: " + e.getMessage());
            e.printStackTrace();
        }
        return customers;
    }

    // ─────────────────────────────────────────────
    // Marketing opt-in / unsubscribe
    // ─────────────────────────────────────────────

    public record UnsubResult(boolean matched, String customerName, String email, String message) {}

    /** The customer's unsubscribe token, generated and stored on first use. */
    public String getOrCreateUnsubToken(int accountID) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT UnsubToken FROM CustomerCommunications WHERE AccountID = ?")) {
                ps.setInt(1, accountID);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String tok = rs.getString("UnsubToken");
                    if (tok != null && !tok.isBlank()) return tok;
                }
            }
            String token = randomToken();
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO CustomerCommunications (AccountID, UnsubToken)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE UnsubToken = VALUES(UnsubToken)
                    """)) {
                ps.setInt(1, accountID);
                ps.setString(2, token);
                ps.executeUpdate();
            }
            return token;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String randomToken() {
        byte[] b = new byte[18];
        new java.security.SecureRandom().nextBytes(b);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /**
     * Sets a customer's marketing-email opt-in state and records the change in
     * MarketingSuppression.
     *
     * @param method  how it was actioned: "Staff", "EmailRequest", "Signup", ...
     * @param staffID staff member who actioned it, or null
     */
    public boolean setMarketingOptIn(int accountID, boolean optIn, String method,
                                     String note, Integer staffID) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO CustomerCommunications (AccountID, MarketingEmails)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE MarketingEmails = VALUES(MarketingEmails)
                    """)) {
                ps.setInt(1, accountID);
                ps.setBoolean(2, optIn);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO MarketingSuppression (AccountID, Email, OptedIn, Method, Note, ActionedBy)
                    SELECT a.AccountID, a.EmailAddress, ?, ?, ?, ?
                    FROM Account a WHERE a.AccountID = ?
                    """)) {
                ps.setBoolean(1, optIn);
                ps.setString(2, method);
                ps.setString(3, note);
                if (staffID != null) ps.setInt(4, staffID); else ps.setNull(4, Types.INTEGER);
                ps.setInt(5, accountID);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Handles a pasted unsubscribe request from a customer's message — either an
     * email address or an unsubscribe token (which may arrive as
     * "Unsubscribe &lt;token&gt;"). Opts the customer out and logs it.
     */
    public UnsubResult processUnsubscribeRequest(String input, Integer staffID) {
        if (input == null || input.isBlank())
            return new UnsubResult(false, null, null, "Enter an email address or unsubscribe code.");
        String s = input.trim();

        String candidateToken = s;
        int sp = s.lastIndexOf(' ');
        if (sp >= 0 && sp < s.length() - 1) candidateToken = s.substring(sp + 1).trim();

        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer accountID = null;
            String name = null, email = null;

            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT a.AccountID, a.FullNames, a.EmailAddress
                    FROM CustomerCommunications cc JOIN Account a ON a.AccountID = cc.AccountID
                    WHERE cc.UnsubToken = ?
                    """)) {
                ps.setString(1, candidateToken);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) { accountID = rs.getInt(1); name = rs.getString(2); email = rs.getString(3); }
            }

            if (accountID == null && s.contains("@")) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT AccountID, FullNames, EmailAddress FROM Account WHERE EmailAddress = ?")) {
                    ps.setString(1, s);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) { accountID = rs.getInt(1); name = rs.getString(2); email = rs.getString(3); }
                }
            }

            if (accountID == null)
                return new UnsubResult(false, null, null,
                        "No customer matched \"" + s + "\". Check the email address or code.");

            setMarketingOptIn(accountID, false, "EmailRequest", "Processed from customer message", staffID);
            return new UnsubResult(true, name, email,
                    name + " has been unsubscribed from marketing emails.");
        } catch (SQLException e) {
            e.printStackTrace();
            return new UnsubResult(false, null, null, "Database error: " + e.getMessage());
        }
    }

    /** Recent opt-out / opt-in events: [when, name, email, action, method, actionedBy]. */
    public List<String[]> getSuppressionLog(int limit) {
        List<String[]> out = new ArrayList<>();
        String sql = """
            SELECT ms.EventAt, a.FullNames, ms.Email, ms.OptedIn, ms.Method,
                   COALESCE(s.FullNames, 'System') AS actionedBy
            FROM MarketingSuppression ms
            JOIN Account a ON a.AccountID = ms.AccountID
            LEFT JOIN Staff s ON s.StaffID = ms.ActionedBy
            ORDER BY ms.EventAt DESC
            LIMIT ?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp t = rs.getTimestamp("EventAt");
                out.add(new String[]{
                        t != null ? t.toLocalDateTime().format(
                                java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm")) : "",
                        rs.getString("FullNames"),
                        rs.getString("Email"),
                        rs.getBoolean("OptedIn") ? "Re-subscribed" : "Unsubscribed",
                        rs.getString("Method"),
                        rs.getString("actionedBy")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }
}
package com.mbuyedzedzo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SMTP configuration entered in the admin portal (Settings → Email) instead
 * of as Azure App Service environment variables — a super admin can change
 * the sending account without touching the Azure portal at all. Always a
 * single row (id=1); the password is encrypted at rest, never shown back to
 * the browser once saved — see {@link com.mbuyedzedzo.licensing.admin.MailSettingsService}.
 */
@Entity
@Table(name = "email_settings")
@Getter
@Setter
public class EmailSettings {

    @Id
    private Long id = 1L;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort = 587;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password_enc")
    private String smtpPasswordEncrypted;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "use_starttls")
    private boolean useStartTls = true;
}

-- SMTP configuration entered in the admin portal, instead of Azure App
-- Service environment variables. Always a single row (id=1).
CREATE TABLE email_settings (
    id                  BIGINT PRIMARY KEY,
    smtp_host           NVARCHAR(150),
    smtp_port           INT DEFAULT 587,
    smtp_username       NVARCHAR(150),
    smtp_password_enc   NVARCHAR(500),
    from_name           NVARCHAR(100),
    use_starttls        BIT NOT NULL DEFAULT 1
);

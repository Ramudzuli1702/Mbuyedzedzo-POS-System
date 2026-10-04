-- Mbuyedzedzo Licensing — initial schema (Azure SQL / SQL Server)
-- Ported from the original MySQL schema: AUTO_INCREMENT -> IDENTITY,
-- DATETIME -> DATETIMEOFFSET(6), BOOLEAN -> BIT, VARCHAR -> NVARCHAR (Unicode-safe —
-- customer/contact names aren't guaranteed ASCII), inline MySQL INDEX(...)
-- split into separate CREATE INDEX statements (T-SQL has no inline form),
-- ENGINE=InnoDB dropped (not a SQL Server concept).

CREATE TABLE admin_user (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    email         NVARCHAR(190) NOT NULL UNIQUE,
    password_hash NVARCHAR(100) NOT NULL,
    full_name     NVARCHAR(120) NOT NULL,
    role          NVARCHAR(20)  NOT NULL,          -- SUPER_ADMIN | SALES_AGENT
    active        BIT           NOT NULL DEFAULT 1,
    created_at    DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET)
);

CREATE TABLE customer (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_name        NVARCHAR(160) NOT NULL,
    contact_name    NVARCHAR(120) NOT NULL,
    email           NVARCHAR(190) NOT NULL,
    phone           NVARCHAR(40),
    notes           NVARCHAR(500),
    created_by      BIGINT,                         -- NULL = self-registered via the storefront
    created_at      DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    CONSTRAINT fk_customer_creator FOREIGN KEY (created_by) REFERENCES admin_user(id)
);
CREATE INDEX idx_customer_org   ON customer(org_name);
CREATE INDEX idx_customer_email ON customer(email);

CREATE TABLE license (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_key    NVARCHAR(40)  NOT NULL UNIQUE,  -- POS-2026-XK29-MNQT-7R4B
    product        NVARCHAR(20)  NOT NULL,         -- POS_STANDARD | POS_RETAIL
    type           NVARCHAR(20)  NOT NULL,         -- PERPETUAL | SUBSCRIPTION | TRIAL
    status         NVARCHAR(20)  NOT NULL,         -- ISSUED | ACTIVE | SUSPENDED | REVOKED | EXPIRED
    max_machines   INT           NOT NULL DEFAULT 1,
    customer_id    BIGINT,
    issued_by      BIGINT,                         -- NULL = issued by the API (e.g. a trial)
    issued_at      DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    expires_at     DATETIMEOFFSET(6),                      -- NULL = never (perpetual)
    revoked_at     DATETIMEOFFSET(6),
    revoke_reason  NVARCHAR(300),
    notes          NVARCHAR(500),
    CONSTRAINT fk_license_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_license_issuer   FOREIGN KEY (issued_by)   REFERENCES admin_user(id)
);
CREATE INDEX idx_license_status  ON license(status);
CREATE INDEX idx_license_expires ON license(expires_at);

CREATE TABLE activation (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id          BIGINT        NOT NULL,
    machine_fingerprint NVARCHAR(64)  NOT NULL,    -- SHA-256 hex
    machine_label       NVARCHAR(160),
    app_version         NVARCHAR(40),
    active              BIT           NOT NULL DEFAULT 1,
    activated_at        DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    last_seen_at        DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    CONSTRAINT fk_activation_license FOREIGN KEY (license_id) REFERENCES license(id) ON DELETE CASCADE,
    CONSTRAINT uq_activation_slot UNIQUE (license_id, machine_fingerprint)
);
CREATE INDEX idx_activation_fp ON activation(machine_fingerprint);

CREATE TABLE transfer_log (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id      BIGINT        NOT NULL,
    old_fingerprint NVARCHAR(64),
    reason          NVARCHAR(300),
    performed_by    BIGINT,                         -- NULL = self-service / system
    performed_at    DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    CONSTRAINT fk_transfer_license FOREIGN KEY (license_id)   REFERENCES license(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_actor   FOREIGN KEY (performed_by) REFERENCES admin_user(id)
);

CREATE TABLE audit_log (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    actor      NVARCHAR(190),                      -- email, or "api" / "system"
    action     NVARCHAR(60)  NOT NULL,
    target     NVARCHAR(120),
    detail     NVARCHAR(500),
    [at]       DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET)  -- AT is a T-SQL reserved word, must be bracketed
);
CREATE INDEX idx_audit_at ON audit_log([at]);

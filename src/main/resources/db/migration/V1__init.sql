-- Mvelelo Licensing — initial schema (MySQL 8)

CREATE TABLE admin_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,          -- SUPER_ADMIN | SALES_AGENT
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE customer (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    org_name        VARCHAR(160) NOT NULL,
    contact_name    VARCHAR(120) NOT NULL,
    email           VARCHAR(190) NOT NULL,
    phone           VARCHAR(40),
    notes           VARCHAR(500),
    created_by      BIGINT       NOT NULL,        -- admin_user.id (agent or admin)
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_creator FOREIGN KEY (created_by) REFERENCES admin_user(id),
    INDEX idx_customer_org (org_name),
    INDEX idx_customer_email (email)
) ENGINE=InnoDB;

CREATE TABLE license (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_key    VARCHAR(40)  NOT NULL UNIQUE,  -- POS-2026-XK29-MNQT-7R4B
    product        VARCHAR(20)  NOT NULL,         -- POS_STANDARD | POS_RETAIL
    type           VARCHAR(20)  NOT NULL,         -- PERPETUAL | SUBSCRIPTION | TRIAL
    status         VARCHAR(20)  NOT NULL,         -- ISSUED | ACTIVE | SUSPENDED | REVOKED | EXPIRED
    max_machines   INT          NOT NULL DEFAULT 1,
    customer_id    BIGINT,
    issued_by      BIGINT,                        -- NULL = issued by the API (e.g. a trial)
    issued_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     DATETIME,                      -- NULL = never (perpetual)
    revoked_at     DATETIME,
    revoke_reason  VARCHAR(300),
    notes          VARCHAR(500),
    CONSTRAINT fk_license_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_license_issuer   FOREIGN KEY (issued_by)   REFERENCES admin_user(id),
    INDEX idx_license_status (status),
    INDEX idx_license_expires (expires_at)
) ENGINE=InnoDB;

CREATE TABLE activation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_id          BIGINT       NOT NULL,
    machine_fingerprint VARCHAR(64)  NOT NULL,    -- SHA-256 hex
    machine_label       VARCHAR(160),
    app_version         VARCHAR(40),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    activated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activation_license FOREIGN KEY (license_id) REFERENCES license(id) ON DELETE CASCADE,
    UNIQUE KEY uq_activation_slot (license_id, machine_fingerprint),
    INDEX idx_activation_fp (machine_fingerprint)
) ENGINE=InnoDB;

CREATE TABLE transfer_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_id      BIGINT       NOT NULL,
    old_fingerprint VARCHAR(64),
    reason          VARCHAR(300),
    performed_by    BIGINT,                        -- NULL = self-service / system
    performed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_license FOREIGN KEY (license_id)   REFERENCES license(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_actor   FOREIGN KEY (performed_by) REFERENCES admin_user(id)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor      VARCHAR(190),                      -- email, or "api" / "system"
    action     VARCHAR(60)  NOT NULL,
    target     VARCHAR(120),
    detail     VARCHAR(500),
    at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_at (at)
) ENGINE=InnoDB;

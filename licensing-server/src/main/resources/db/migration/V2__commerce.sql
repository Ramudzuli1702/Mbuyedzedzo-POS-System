-- Commerce: storefront purchases, payment tracking, customer self-service login.

-- NULL = self-registered via the storefront (mirrors issued_by / performed_by's
-- existing "NULL = system/self-service" convention elsewhere in this schema).
-- password_hash stays NULL until the customer follows the "set your password"
-- link emailed after a confirmed purchase (set_password_token, single-use).
ALTER TABLE customer
    MODIFY COLUMN created_by BIGINT NULL,
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER email,
    ADD COLUMN set_password_token VARCHAR(64) NULL,
    ADD COLUMN set_password_token_expires_at DATETIME NULL,
    ADD UNIQUE KEY uq_customer_set_password_token (set_password_token);

CREATE TABLE orders (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference          VARCHAR(40)   NOT NULL UNIQUE,   -- our order ref; sent to PayFast as m_payment_id
    customer_id        BIGINT,                          -- set once the buyer's account is identified/created
    buyer_email        VARCHAR(190)  NOT NULL,
    buyer_name         VARCHAR(160)  NOT NULL,
    product            VARCHAR(20)   NOT NULL,          -- POS_STANDARD | POS_RETAIL
    license_type       VARCHAR(20)   NOT NULL,          -- PERPETUAL | SUBSCRIPTION
    amount             DECIMAL(10,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'ZAR',
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | PAID | FAILED | CANCELLED
    gateway            VARCHAR(20)   NOT NULL DEFAULT 'PAYFAST',
    gateway_payment_id VARCHAR(60),
    license_id         BIGINT,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at            DATETIME,
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_order_license  FOREIGN KEY (license_id)  REFERENCES license(id),
    INDEX idx_order_status (status),
    INDEX idx_order_reference (reference)
) ENGINE=InnoDB;

-- Self-service machine-transfer requests — an admin/agent approves or denies
-- from the existing license detail page; approval calls the existing
-- resetMachineBinding() (which writes to transfer_log as it already does).
CREATE TABLE transfer_request (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_id    BIGINT       NOT NULL,
    customer_id   BIGINT       NOT NULL,
    reason        VARCHAR(300),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | DENIED
    requested_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at   DATETIME,
    resolved_by   BIGINT,
    CONSTRAINT fk_transfer_request_license  FOREIGN KEY (license_id)  REFERENCES license(id)  ON DELETE CASCADE,
    CONSTRAINT fk_transfer_request_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_transfer_request_resolver FOREIGN KEY (resolved_by) REFERENCES admin_user(id),
    INDEX idx_transfer_request_status (status)
) ENGINE=InnoDB;

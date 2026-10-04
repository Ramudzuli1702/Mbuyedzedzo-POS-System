-- Commerce: storefront purchases, payment tracking, customer self-service login.
-- (Azure SQL / SQL Server — see V1's header for the general MySQL->T-SQL notes.)

-- NULL = self-registered via the storefront (mirrors issued_by / performed_by's
-- existing "NULL = system/self-service" convention elsewhere in this schema).
ALTER TABLE customer ALTER COLUMN created_by BIGINT NULL;

-- password_hash stays NULL until the customer follows the "set your password"
-- link emailed after a confirmed purchase (set_password_token, single-use).
ALTER TABLE customer ADD
    password_hash NVARCHAR(100) NULL,
    set_password_token NVARCHAR(64) NULL,
    set_password_token_expires_at DATETIMEOFFSET(6) NULL,
    CONSTRAINT uq_customer_set_password_token UNIQUE (set_password_token);

CREATE TABLE orders (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    reference          NVARCHAR(40)  NOT NULL UNIQUE,   -- our order ref; sent to PayFast as m_payment_id
    customer_id        BIGINT,                          -- set once the buyer's account is identified/created
    buyer_email        NVARCHAR(190) NOT NULL,
    buyer_name         NVARCHAR(160) NOT NULL,
    product            NVARCHAR(20)  NOT NULL,          -- POS_STANDARD | POS_RETAIL
    license_type       NVARCHAR(20)  NOT NULL,          -- PERPETUAL | SUBSCRIPTION
    amount             DECIMAL(10,2) NOT NULL,
    currency           NVARCHAR(3)   NOT NULL DEFAULT 'ZAR',
    status             NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PAID | FAILED | CANCELLED
    gateway            NVARCHAR(20)  NOT NULL DEFAULT 'PAYFAST',
    gateway_payment_id NVARCHAR(60),
    license_id         BIGINT,
    created_at         DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    paid_at            DATETIMEOFFSET(6),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_order_license  FOREIGN KEY (license_id)  REFERENCES license(id)
);
CREATE INDEX idx_order_status    ON orders(status);
CREATE INDEX idx_order_reference ON orders(reference);

-- Self-service machine-transfer requests — an admin/agent approves or denies
-- from the existing license detail page; approval calls the existing
-- resetMachineBinding() (which writes to transfer_log as it already does).
CREATE TABLE transfer_request (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    license_id    BIGINT        NOT NULL,
    customer_id   BIGINT        NOT NULL,
    reason        NVARCHAR(300),
    status        NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | DENIED
    requested_at  DATETIMEOFFSET(6)     NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    resolved_at   DATETIMEOFFSET(6),
    resolved_by   BIGINT,
    CONSTRAINT fk_transfer_request_license  FOREIGN KEY (license_id)  REFERENCES license(id)  ON DELETE CASCADE,
    CONSTRAINT fk_transfer_request_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_transfer_request_resolver FOREIGN KEY (resolved_by) REFERENCES admin_user(id)
);
CREATE INDEX idx_transfer_request_status ON transfer_request(status);

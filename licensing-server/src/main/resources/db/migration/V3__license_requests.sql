-- Self-service license requests from the public storefront — a stand-in for
-- the PayFast checkout flow while that's still in testing: a visitor submits
-- a request, an admin/agent reviews it in the portal and either approves it
-- (issuing a real license key + a login, both emailed) or rejects it.
CREATE TABLE license_request (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    business_name   NVARCHAR(150) NOT NULL,
    contact_name    NVARCHAR(150) NOT NULL,
    email           NVARCHAR(150) NOT NULL,
    phone           NVARCHAR(40),
    product         NVARCHAR(20)  NOT NULL,          -- POS_STANDARD | POS_RETAIL
    license_type    NVARCHAR(20)  NOT NULL,          -- PERPETUAL | SUBSCRIPTION
    notes           NVARCHAR(500),
    status          NVARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    requested_at    DATETIMEOFFSET(6) NOT NULL DEFAULT CAST(SYSUTCDATETIME() AS DATETIMEOFFSET),
    resolved_at     DATETIMEOFFSET(6),
    resolved_by     BIGINT,
    customer_id     BIGINT,                          -- set once approved
    license_id      BIGINT,                          -- set once approved
    reject_reason   NVARCHAR(300),
    CONSTRAINT fk_license_request_resolver FOREIGN KEY (resolved_by) REFERENCES admin_user(id),
    CONSTRAINT fk_license_request_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_license_request_license  FOREIGN KEY (license_id)  REFERENCES license(id)
);
CREATE INDEX idx_license_request_status ON license_request(status);

# Mvelelo Licensing

License issuance, machine-bound activation, and product management for the
**Mvelelo POS** desktop systems (Standard and Retail editions).

One Spring Boot app:

| Path | Audience | Purpose |
| --- | --- | --- |
| `/api/v1/**` | The desktop POS | Activate a key, validate/refresh a token, deactivate a machine |
| `/admin/**` | Admin & Sales Agent (browser) | Manage customers, generate & manage license keys, transfers, audit |

## How licensing works

1. An admin (or agent) creates a **customer** and issues a **license key** for a
   product (`POS_STANDARD` or `POS_RETAIL`), type (`PERPETUAL` / `SUBSCRIPTION` /
   `TRIAL`) and machine limit.
2. On first launch the desktop app shows an **activation screen**. The user
   enters the key; the app also sends a **machine fingerprint** (hashed disk +
   MAC + CPU id).
3. The server checks the key (valid, right product, not revoked/expired, machine
   slots available), records the activation, and returns a short-lived
   **RS256-signed token** bound to `{key, product, fingerprint, expiry}`.
4. Every launch the desktop verifies that token **offline** using a bundled
   public key. Periodically it calls `/validate` to refresh; if the server says
   `REVOKED` the app blocks on the next launch. A 14-day offline grace covers
   temporary loss of connectivity.
5. **Trials**: the activation screen can start a 30-day machine-bound trial
   without a purchased key.
6. **Transfers**: if a customer changes computers, an admin/agent resets the
   machine binding from the PMS (logged), or the customer self-services it up to
   a yearly limit (planned).

## Not built (portfolio scope)

Payment gateway (PayFast/Stripe), public marketing site / self-service
checkout, support ticketing, commission reports, automated renewal emails.
Keys are issued manually from the PMS. In a real deployment, a successful
payment webhook would call the same "issue license" service.

## Run locally

```bash
docker compose up -d db                      # MySQL on :3307
# generate a dev signing keypair (see application.yml for the commands) into ./config
mvn spring-boot:run
# admin UI:  http://localhost:8080/admin   (bootstrap admin from application.yml)
```

## Stack

Spring Boot 3.3 · Java 21 · Spring MVC + Thymeleaf + Spring Security · MySQL 8 ·
Flyway · Nimbus JOSE (RS256) · Testcontainers.

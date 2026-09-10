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

## API (desktop ↔ server)

| Endpoint | Body | On success | Errors |
| --- | --- | --- | --- |
| `POST /api/v1/activate` | key, fingerprint, machineLabel, product, appVersion | signed token + status | `BAD_CHECKSUM` `INVALID_KEY` `WRONG_PRODUCT` `MACHINE_LIMIT` `REVOKED` `SUSPENDED` `EXPIRED` |
| `POST /api/v1/validate` | key, fingerprint | fresh token | `INVALID_KEY` `NOT_ACTIVATED` … |
| `POST /api/v1/trial` | product, fingerprint, machineLabel, appVersion | signed token (30-day TRIAL) | `TRIAL_ALREADY_USED` |
| `POST /api/v1/deactivate` | key, fingerprint | frees the machine slot | `INVALID_KEY` |

Token = RS256 JWT, claims: `sub`=key, `product`, `fingerprint`, `licenseType`,
`licenseStatus`, `maxMachines`, `licenseExpiresAt`, short `exp` (7 days).

## Run locally

```bash
docker compose up -d db                      # MySQL on :3307
mvn spring-boot:run                          # signing keys auto-generated into ./config on first run
# admin UI:  http://localhost:8080/admin   (Phase 3)
```

Integration test against a real MySQL (own schema; skipped otherwise):

```bash
mvn -Dlicensing.it.jdbcUrl=jdbc:mysql://localhost:3306/mvelelo_licensing_test \
    -Dlicensing.it.user=root -Dlicensing.it.password=secret verify
```

## Stack

Spring Boot 3.3 · Java 21 · Spring MVC + Thymeleaf + Spring Security · MySQL 8 ·
Flyway · Nimbus JOSE (RS256).

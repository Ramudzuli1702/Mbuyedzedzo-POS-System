# Mbuyedzedzo Licensing

*Part of the [Mbuyedzedzo](../README.md) suite — see the root README for
how this fits with [`pos-desktop`](../pos-desktop) and [`mobile-scanner`](../mobile-scanner).*

License issuance, machine-bound activation, and product management for the
**Mbuyedzedzo POS** desktop systems (Standard and Retail editions).

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
   enters the key; the app also sends a **machine fingerprint** — SHA-256 of the
   physical MAC addresses + machine name + OS user.
3. The server checks the key (valid checksum, right product, not
   revoked/suspended/expired, machine slots available), records the activation,
   and returns a short-lived **RS256 token** bound to
   `{key, product, fingerprint, expiry}`.
4. Every launch the desktop verifies that token **offline** with the public key
   bundled at `pos-desktop/src/main/resources/license-public.pem`.
   It also calls `/validate` in the background; if the server says the licence
   is dead the app clears the activation and blocks on the next launch. A
   14-day offline grace covers temporary loss of connectivity.
5. **Trials**: the activation screen can start a 30-day machine-bound trial
   without a purchased key.
6. **Transfers**: if a customer changes computers, an admin/agent resets the
   machine binding from the portal (logged); customer self-service is planned.
7. **Expiry**: a scheduled task flips ISSUED/ACTIVE licences past their expiry
   date to `EXPIRED` hourly.

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
Signed with `dev-keys/license-private.pem` (a committed throwaway pair — pass
`LICENSE_PRIVATE_KEY` / `LICENSE_PUBLIC_KEY` in production and re-bundle the
public key in the POS build).

## Run locally

Defaults to SQL Server (see "Why Azure SQL, not MySQL" below) — point it at
a local instance, or use `docker-compose.yml`:

```bash
# 1. In your SQL Server instance:
#    CREATE DATABASE mbuyedzedzo_licensing;

# 2. Run it (SQL-auth login, e.g. "sa"):
DB_USER=sa DB_PASSWORD=yourpass mvn spring-boot:run
#   ...local instance with no TLS cert:    DB_ENCRYPT=false mvn spring-boot:run

# admin UI:  http://localhost:8080/admin
# first login: BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD (see application.yml)
```

`docker-compose.yml` is an **optional** alternative if you'd rather not
install SQL Server — see the notes in that file. Flyway builds the schema
on startup.

### Admin portal

| Screen | Who | Does |
| --- | --- | --- |
| Dashboard | all | counts, activations (30d), licenses expiring soon |
| Customers | agent sees own · admin sees all | list/search, create, view a customer's licenses |
| Licenses | agent sees own · admin sees all | **generate key**, view activations & transfer history, **reset machine binding** (transfer), renew; **revoke / suspend** are super-admin only |
| Agents | super-admin only | create sales agents, deactivate, reset password |
| Audit | all | every issue / activate / revoke / transfer |

Integration test against a real MySQL (own schema; skipped otherwise):

```bash
mvn -Dlicensing.it.jdbcUrl="jdbc:sqlserver://localhost:1433;databaseName=mbuyedzedzo_licensing_test;encrypt=false" \
    -Dlicensing.it.user=sa -Dlicensing.it.password=secret verify
```

## Deployed

Live on Azure: **https://mbuyedzedzo-licensing.azurewebsites.net**

| Resource | Details |
| --- | --- |
| Resource group | `mbuyedzedzo-rg` |
| App Service | `mbuyedzedzo-licensing`, Linux, Java 21 (`JAVA:21-java21`), Free (F1) plan `mbuyedzedzo-asp`, South Africa North |
| Database | Azure SQL `mbuyedzedzo-sql`/`mbuyedzedzo_licensing`, Free tier, UAE North (the student subscription this runs on only has one free-tier DB slot per region, and South Africa North's was already taken by an unrelated project — see the database engine note below for why Azure SQL at all) |
| PayFast / email | Sandbox credentials / dev-mode logging (see the main PayFast section above) — swap in real ones via App Service application settings, no redeploy needed |

Redeploy after a change: `mvn clean package` then
`az webapp deploy --name mbuyedzedzo-licensing --resource-group mbuyedzedzo-rg --src-path target/mbuyedzedzo-licensing-1.0.0.jar --type jar`.

### Why Azure SQL, not MySQL

This runs on an Azure for Students subscription, which blocks
`Microsoft.DBforMySQL` entirely (every region) but allows `Microsoft.Sql`
(Azure SQL/SQL Server). `pos-desktop` and `mobile-scanner` are unaffected —
this is the only component with a database, and only this component's
Flyway migrations and JDBC driver changed. Elsewhere deploying (a normal
subscription, your own VPS), MySQL is the simpler default — the historical
MySQL schema is in this file's git history if you want it back, and the
port itself (`AUTO_INCREMENT`→`IDENTITY`, `VARCHAR`→`NVARCHAR`,
`DATETIME`→`DATETIMEOFFSET(6)` for Hibernate's `Instant` mapping,
inline `INDEX(...)`→separate `CREATE INDEX`) is documented in the V1/V2
migration file headers.

## Deploying elsewhere

Runnable jar: `mvn clean package` → `java -jar target/mbuyedzedzo-licensing-1.0.0.jar`.
Set `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`, `LICENSE_PRIVATE_KEY` /
`LICENSE_PUBLIC_KEY` (absolute paths to a real keypair), and
`BOOTSTRAP_ADMIN_EMAIL/PASSWORD`. Put it behind HTTPS (reverse proxy or
`server.ssl.*`). A small VPS (e.g. Hetzner) is plenty — swap the datasource
URL/driver back to MySQL (see above) if you're not on Azure SQL.

## Stack

Spring Boot 3.3 · Java 21 · Spring MVC + Thymeleaf + Spring Security ·
Azure SQL (SQL Server) · Flyway · Nimbus JOSE (RS256) · deployed on Azure
App Service (Linux).

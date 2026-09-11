# Mbuyedzedzo POS (desktop)

*Part of the [Mbuyedzedzo](../README.md) suite — see the root README for
how this fits with [`licensing-server`](../licensing-server) and
[`mobile-scanner`](../mobile-scanner).*

A JavaFX point-of-sale application: sales terminal, inventory, staff
accounts, customer/marketing tooling, and reporting, packaged as a
self-contained Windows installer with no separate Java or MySQL install
required.

## Editions — two products, one codebase

The build produces **two distinct installers**. A customer licenses one;
the other isn't in their build — it's not a setting they can flip.

| Edition | For | Difference | Build |
| --- | --- | --- | --- |
| **Standard POS** | Shops that keep customer accounts (loyalty, contracts, age-restricted goods) | Full Customer and Marketing screens, promo targeting, receipt-by-email, customer-insight reports | `mvn clean verify` → `target/dist/POS-*.exe` |
| **Retail POS** | Shops that just sell over the counter | No Customer or Marketing screens. Every sale is booked against a single hidden **"Walk-in"** account, so no schema change is needed. Reports drop the customer-centric views. | `mvn -P retail clean verify` → `target/dist/RetailPOS-*.exe` |

The edition is baked in at build time: the Maven `pos.edition` property is
filtered into `pos-edition.properties` inside the jar and read by
`com.pos.Edition`. The window title, receipts, app icon, and installer name
all follow it (e.g. *Mbuyedzedzo Retail POS*).

Both editions are built by the same `jlink` (custom minimal JRE) →
`jpackage` (Windows `.exe`, via WiX) pipeline — see `pom.xml`'s `exec-maven-plugin`
section for the exact steps. **Building both back-to-back:** each profile's
`mvn clean` wipes `target/`, which is where the other edition's installer
also lives — copy each `.exe` out before building the next one.

## First run

1. **Splash** — the brand mark, shown briefly on every launch.
2. **Licence agreement** — shown once per machine (a flag file next to
   `db.properties`); declining exits the app.
3. **MySQL setup** — detects an existing MySQL install or silently installs
   the bundled one, generates a root password, and writes
   `%PROGRAMDATA%\POS System\config\db.properties`. Fully automatic; the
   installer requires admin rights specifically so this step can run.
4. **Licence activation** — see below.
5. **Registration** — if the `Staff` table is empty, the first account you
   create becomes the Admin.

Every step after the first is skipped on subsequent launches once its
one-time condition is satisfied.

## Licensing

Without a valid licence this build shows an **Activation screen** instead of
the login screen. On activation the app sends a licence key + a machine
fingerprint (hashed MACs + machine name) to the licensing server and stores
a short-lived **RS256 token**, which it then verifies **offline** on every
launch using the bundled public key (`src/main/resources/license-public.pem`).
It re-checks with the server in the background each launch, with a 14-day
offline grace window. A **30-day trial** can be started from the same
screen.

Keys are product-specific — a Retail key won't activate a Standard build.

- Server + admin portal: **[`../licensing-server`](../licensing-server)**
  (Spring Boot; issue / revoke / renew / transfer keys, sales-agent roles).
- Licence status and a "Deactivate this machine" button live in **Settings**.
- Payment gateway integration (PayFast) is out of scope for this portfolio
  build — keys are issued by hand from the admin portal. In a real deployment a
  payment webhook would call the same "issue licence" service.

## Mobile scanner companion

The desktop runs a small TCP/JSON server (`WiFiHandler`, port 8888) so the
**[`../mobile-scanner`](../mobile-scanner)** Android app can push barcode
scans and new products into the running sale over the local network —
useful as a wireless alternative to a USB scanner, or for adding stock from
the shelf. Pairing is a QR code shown on the desktop; see that project's
README for the wire protocol.

## Features

### Core functionality
- **Sales terminal** — barcode/QR scanning (webcam or the mobile companion), cart management, customer selection, promo codes
- **Inventory** — product CRUD, stock tracking, auto-generated QR codes, low-stock alerts
- **Staff accounts** — role-based access (Admin, Manager, Cashier), salted PBKDF2-HMAC-SHA256 password hashing
- **Customers & marketing** (Standard edition only) — customer database, purchase history, opt-in marketing email campaigns, unsubscribe handling
- **Reports** — sales trends, top products, category performance, Excel export
- **Receipts** — 40-column thermal-format text receipts; printed, saved to disk, and/or emailed (branded HTML with the logo)

### Security
- Salted PBKDF2-HMAC-SHA256 password hashing (legacy SHA-256 hashes upgrade transparently on next login)
- Role-based access control
- Session/login tracking
- Offline-verifiable, machine-bound software licensing (RS256)

## Technology stack

- **Language:** Java 22
- **UI:** JavaFX 22
- **Database:** MySQL (bundled installer, provisioned automatically on first run)
- **Build:** Maven
- **Packaging:** Shade fat-JAR → `jlink` custom runtime → `jpackage` Windows `.exe` (WiX)
- **Libraries:** MySQL Connector/J · ZXing (QR generation) · Apache POI (Excel export) · iText 5 (PDF reports) · Jakarta Mail (email receipts & marketing) · Gson (JSON — scanner protocol & licensing) · HikariCP (connection pooling) · Nimbus JOSE (RS256 licence tokens)

## Project structure

```
pos-desktop/
├── src/main/java/com/pos/
│   ├── POSApplication.java      # entry point: splash → terms → setup → licence → login
│   ├── Branding.java, Edition.java, Terms.java
│   ├── database/                # connection pool, schema setup/migrations
│   ├── license/                 # activation, offline token verification, fingerprinting
│   ├── setup/                   # first-run MySQL provisioning, terms acceptance
│   ├── models/                  # User, Product, Customer, ...
│   ├── services/                # business logic — sales, inventory, reports, comms, WiFi bridge
│   ├── utils/                   # password hashing, QR codes, receipts, brand assets
│   ├── components/, dialogs/    # reusable JavaFX widgets
│   └── views/                   # one class per screen (Login, Sales, Inventory, Reports, ...)
├── src/main/resources/          # FXML-free — views are built in code; brand assets, license-public.pem
├── MySQL_Scripts/                # reference schema (the app creates it itself; this is a readable copy)
├── installer/                   # legacy Inno Setup script (superseded by the jlink/jpackage pipeline below)
└── pom.xml
```

## Database setup

**No manual setup is required.** On first launch the application:

1. Detects or silently installs MySQL (`installer/mysql-installer.msi`, bundled by `jpackage`), generating a random root password.
2. Writes the connection details to `%PROGRAMDATA%\POS System\config\db.properties`.
3. Creates the `pos_db` schema and every table (`com.pos.database.DatabaseSetup`, idempotent — safe to re-run on every launch).
4. Runs any pending column migrations.

For development against an existing local MySQL, create `%PROGRAMDATA%\POS System\config\db.properties` yourself:

```properties
db.host=127.0.0.1
db.port=3306
db.name=pos_db
db.username=root
db.password=your-password
# Optional — defaults to Africa/Johannesburg
db.timezone=Africa/Johannesburg
```

`MySQL_Scripts/Tables_creation_script.sql` is a **reference copy** of the schema; the app never reads it.

## Running it

Requires JDK 22 and Maven.

```bash
# Development — runs straight from source
mvn javafx:run

# Fat JAR
mvn clean package
java -jar target/point-of-sale-system-1.0.0.jar

# Windows installer (.exe) — see pom.xml's exec-maven-plugin section for
# the jlink/jpackage/WiX prerequisites
mvn clean verify              # Standard edition
mvn -P retail clean verify    # Retail edition
```

## First login

There is **no default account**. On first run — when the `Staff` table is
empty — the app opens the registration screen, and the first account you
create becomes the Admin.

## Roles

| Role | Access |
| --- | --- |
| Admin / Manager | Everything — sales, inventory, staff, reports, settings |
| Cashier | Sales terminal only |

## Troubleshooting

- **Database connection issues** — confirm the MySQL service is running and check `%PROGRAMDATA%\POS System\config\db.properties`.
- **JavaFX runtime errors in dev** — `mvn clean javafx:run`.
- **Port 8888 in use** — the mobile-scanner bridge auto-increments up to 10 times; check nothing else is bound to that range.

## License

Portfolio/personal project — not licensed for redistribution.

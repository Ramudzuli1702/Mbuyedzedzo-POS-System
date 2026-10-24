# Mbuyedzedzo
POS System — desktop

Windows JavaFX app for sales, inventory, staff, sessions, stocktaking, promotions, reports and receipts. This README covers development and packaging. For shop operations, use the [user guide](docs/user-guide.md) and [documentation index](docs/README.md).

The current Maven project version is **1.0.9**. This source version does not mean the published installers have been updated.

## Editions

| Edition | Behaviour | Installer build |
| --- | --- | --- |
| Standard | Customer accounts, purchase history, customer reports and marketing | `mvn clean verify` |
| Retail | Walk-in sales; customer/marketing screens hidden | `mvn -P retail clean verify` |

Both use the same Java code. Maven filters `pos.edition` into `pos-edition.properties`; `Edition` selects the behaviour. The edition is fixed for a build. Default schemas are `pos_db_standard` and `pos_db_retail`.

## Development

Requires JDK 22 and Maven. Run from `pos-desktop`:

```powershell
mvn test
mvn javafx:run
```

The app starts its normal setup and licensing flow. Use a separate development database; startup performs schema migrations. There is no default staff password. The first account registered in an empty staff table becomes Admin.

To build the shaded JAR without creating a Windows installer:

```powershell
mvn clean package
java -jar target/point-of-sale-system-1.0.9.jar
```

## Windows packaging

Requires JDK 22, JavaFX 22 jmods and WiX 3 tools available to `jpackage`. `javafx.jmods.path` in `pom.xml` defaults to `C:\javafx-jmods-22.0.2`; override it for your machine:

```powershell
mvn clean verify '-Djavafx.jmods.path=C:\tools\javafx-jmods-22.0.2'
```

Maven creates the shaded JAR, stages edition/version-specific input, builds a Java runtime with `jlink`, then creates the `.exe` with `jpackage`. Output goes to `target/dist`. Copy the installer out before running `clean` for the other edition; both builds use `target`.

The optional MySQL web bootstrapper belongs at `installer/mysql-installer-web-community-8.0.46.0.msi`, with its original filename. It is excluded from Git. If absent, setup downloads it. A new installation needs internet even with the bootstrapper bundled. Do not substitute a renamed server MSI.

Building does not upload installers, change release assets or update the website.

## Database setup and credentials

First-run setup installs/configures a local MySQL server when needed, or connects to an existing server using its current password. Only the Windows setup helper requests UAC elevation. Existing server passwords and databases are preserved. Use Retry setup after cancellation or failure.

Startup creates tables and applies migrations. Local root connections are migrated to a generated account restricted to that edition's schema. It also has schema-local DDL privileges for migrations; it is not a read/write-only account. Remote root connections require a schema-scoped account before use.

Connection settings are saved per edition:

```text
%PROGRAMDATA%\POS System\config\db-standard.properties
%PROGRAMDATA%\POS System\config\db-retail.properties
```

Saved database passwords use Windows secret protection. Do not move protected values between Windows identities or publish connection files. Legacy shared `db.properties` files remain migration inputs.

For development against an existing server, recognized properties are:

```properties
db.host=127.0.0.1
db.port=3306
db.name=pos_db_standard
db.username=YOUR_DEVELOPMENT_ACCOUNT
db.password=YOUR_DEVELOPMENT_PASSWORD
db.timezone=+02:00
```

These are placeholders. Prefer the setup flow for saved protected credentials. `MySQL_Scripts/Tables_creation_script.sql` is reference material; startup uses the Java schema classes, which include newer migrations.

## Implementation map

See [code architecture](docs/code-architecture.md) for call flows and extension rules,
and [database architecture](docs/database.md) / [table dictionary](docs/database-tables.md)
for schema relationships, migrations and field meanings.

| Area | Main classes | Guide |
| --- | --- | --- |
| Sales/payments | `SalesView`, `TransactionService`, `SessionMoney` | [User guide](docs/user-guide.md) |
| Inventory/barcodes | `InventoryView`, `ProductService`, `InventoryExcelService` | [Inventory](docs/user-guide.md#inventory-and-barcode-labels) |
| Stocktaking | `StocktakingView`, `StocktakeService`, `StocktakeExcelService`, `StocktakeReviewView` | [Stocktaking](docs/stocktaking.md) |
| Promotions | `PromotionView`, `PromotionService`, `PromotionEngine`, `PromotionExcelService` | [Promotions](docs/promotions.md) |
| Supervisor keys | `AuthorizationKeyDialog`, `SupervisorAuthorization`, `SessionReview`, `FinancialApproval` | [Authorization keys](docs/user-guide.md#authorization-keys) |
| Phone connection | `WiFiHandler`, `ScannerTls`, `StocktakeProtocol`, `MobileReceiptService` | [Android guide](../mobile-scanner/README.md) |
| Schema/credentials | `DatabaseSetup`, `LedgerSchema`, `CompanionSchema`, `PromotionSchema`, `RuntimeDatabaseAccount` | This README |
| UI/branding | `Theme`, `Ui`, `Icons`, `BrandAssets`, `SidebarBrand` | [UI behaviour](docs/ui-and-companion-login.md) |

Desktop labels use Code 128 and save as PNG. Barcode printing has been removed. Connection-screen QR codes carry the phone pairing identity.

Phone traffic uses newline-delimited JSON over TLS, certificate-fingerprint pairing and desktop staff authentication. The default listener is port 8888, with up to nine alternative ports. A bare IP is insufficient for secure pairing. See the [companion README](../mobile-scanner/README.md) for protocol/session behaviour.

## Licensing

Activation requires a key for the installed edition, or an eligible trial. The desktop verifies a signed RS256 token against its bundled public key and machine fingerprint. Background validation refreshes the token or handles revocation. Offline grace is bounded by the signed issue time and does not override licence expiry. See [licensing-server](../licensing-server/README.md) for server setup and API details.

## Verification

Run unit tests with `mvn test`. Database integration fixtures clear tables: use disposable data, never a shop or shared development database.

For financial/promotion checks, set `POS_IT_JDBC_URL`, `POS_IT_USER` and `POS_IT_PASSWORD` to a dedicated MySQL test database, then run:

```powershell
mvn '-Dtest=FinancialIntegrityIT,PromotionIT' test
```

`ItSupport` configures the connection from those variables and creates the schema. Without a test URL these checks skip. `PromotionUiIT` also needs a working Windows JavaFX environment. Run JavaFX test classes separately when they manage the toolkit lifecycle.

`CompanionIT` uses a different fixture: `pos.companion.it.port` must identify a disposable loopback MySQL server on a non-default port, using test-only root credentials. It creates/drops a random schema. Optional device/UI checks have additional requirements. Read the fixture before enabling it; never use a business server.

`mvn verify` also runs Windows packaging. Use targeted Maven `test` commands for integration behaviour alone.

Automated tests do not replace clean-Windows installation, physical camera, printer and real-phone acceptance checks. Test counts in historical notes describe those changes, not a fresh run of the current tree.

## Local files and support

Preferences: `%LOCALAPPDATA%\Mbuyedzedzo\<edition>\app.config`. The protected scanner identity is alongside them as `scanner-identity.dat`. Logs normally live under `%PROGRAMDATA%\POS System\logs`. Export folders are configurable in Settings.

See [troubleshooting](docs/troubleshooting.md) for recovery steps and support details.

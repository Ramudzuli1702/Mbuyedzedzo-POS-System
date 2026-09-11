package com.pos.database;

import java.sql.*;

/**
 * DatabaseSetup — runs automatically on first launch (and every launch — it's idempotent).
 *
 * WHAT IT DOES:
 *   1. Connects to MySQL using root credentials from DatabaseConnection
 *   2. Creates the database if it doesn't exist
 *   3. Creates all tables if they don't exist (safe to re-run)
 *   4. Inserts seed data (SaleSequence, default BusinessSettings)
 *   5. Runs any pending column migrations
 *
 * CALLED FROM: POSApplication.continueToApp() after FirstRunSetup completes.
 *
 * The user never sees this — it runs silently in ~1 second on first run
 * and is skipped almost instantly on subsequent runs (all tables already exist).
 */
public class DatabaseSetup {

    /**
     * Entry point called from POSApplication.
     * Returns true if setup completed successfully.
     */
    public static boolean run() {
        System.out.println("Running database setup...");
        try {
            createDatabaseIfNotExists();
            // Must run BEFORE createAllTables(): if SaleSequence already
            // exists from an older schema version (nextID as PRIMARY KEY,
            // no `id` column), "CREATE TABLE IF NOT EXISTS" inside
            // createAllTables() is a no-op against the existing table, so
            // the later "INSERT IGNORE INTO SaleSequence (id, nextID)..."
            // would fail with "Unknown column 'id'". Repairing the schema
            // first guarantees the `id` column exists before anything
            // else tries to reference it.
            fixSaleSequenceSchema();
            createAllTables();
            runMigrations();
            System.out.println("Database setup complete.");
            return true;
        } catch (Exception e) {
            System.err.println("Database setup failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ── Step 1: Create database ────────────────────────────────────────────────

    private static void createDatabaseIfNotExists() throws SQLException {
        // getRootConnection() connects without specifying a schema —
        // required because the database may not exist yet.
        try (Connection conn = DatabaseConnection.getRootConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE DATABASE IF NOT EXISTS `" + DatabaseConnection.DATABASE + "` " +
                "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
            System.out.println("Database '" + DatabaseConnection.DATABASE + "' ready.");
        }
    }

    // ── Step 2: Create all tables ──────────────────────────────────────────────

    private static void createAllTables() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Staff
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Staff (
                    StaffID      INT AUTO_INCREMENT PRIMARY KEY,
                    FullNames    VARCHAR(100) NOT NULL,
                    EmailAddress VARCHAR(100) NOT NULL UNIQUE,
                    UserPassword VARCHAR(255) NOT NULL,
                    UserType     VARCHAR(10)  NOT NULL,
                    Status       VARCHAR(10),
                    TimeStamp    DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
            """);

            // CheckIn
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS CheckIn (
                    LogID       INT AUTO_INCREMENT PRIMARY KEY,
                    StaffID     INT NOT NULL,
                    LoginStamp  DATETIME DEFAULT CURRENT_TIMESTAMP,
                    LogoutStamp DATETIME,
                    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // Account (customers)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Account (
                    AccountID    INT AUTO_INCREMENT PRIMARY KEY,
                    StaffID      INT NOT NULL,
                    FullNames    VARCHAR(100) NOT NULL,
                    EmailAddress VARCHAR(100) NOT NULL UNIQUE,
                    DateOfBirth  DATE NOT NULL,
                    ContactNo    VARCHAR(20) NOT NULL,
                    TimeStamp    DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // Category
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Category (
                    CategoryID   INT AUTO_INCREMENT PRIMARY KEY,
                    CategoryName VARCHAR(30) NOT NULL
                ) ENGINE=InnoDB
            """);

            // Promo
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Promo (
                    PromoID         INT AUTO_INCREMENT PRIMARY KEY,
                    PromoCode       VARCHAR(10)    NOT NULL UNIQUE,
                    PromoName       VARCHAR(100),
                    DiscountType    VARCHAR(10)    NOT NULL DEFAULT 'PERCENT',
                    DiscountValue   DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
                    MinimumPurchase DECIMAL(10,2)  DEFAULT 0.00,
                    ValidFrom       DATETIME,
                    ValidTill       DATETIME,
                    IsActive        BOOLEAN        DEFAULT TRUE,
                    UsageLimit      INT            DEFAULT NULL,
                    UsageCount      INT            DEFAULT 0,
                    PromoQR         BLOB
                ) ENGINE=InnoDB
            """);

            // Product
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Product (
                    ProductID   INT AUTO_INCREMENT PRIMARY KEY,
                    StaffID     INT NOT NULL,
                    CategoryID  INT NOT NULL,
                    ProductName VARCHAR(50)   NOT NULL,
                    QRCode      LONGBLOB,
                    BarCode     LONGBLOB,
                    Quantity    INT           DEFAULT 0,
                    NoSold      INT           NOT NULL DEFAULT 0,
                    Price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                    SalePrice   DECIMAL(10,2) DEFAULT NULL,
                    FOREIGN KEY (StaffID)    REFERENCES Staff(StaffID)    ON DELETE CASCADE,
                    FOREIGN KEY (CategoryID) REFERENCES Category(CategoryID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // Transactions
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Transactions (
                    TransactionID   INT AUTO_INCREMENT PRIMARY KEY,
                    SaleID          INT           NOT NULL DEFAULT 0,
                    StaffID         INT           NOT NULL,
                    AccountID       INT           NOT NULL,
                    ProductID       INT           NOT NULL,
                    PromoID         INT,
                    Quantity        INT           NOT NULL DEFAULT 1,
                    PromoCode       VARCHAR(10),
                    SalePrice       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                    PaymentMethod   VARCHAR(20)   DEFAULT 'Cash',
                    AmountPaid      DECIMAL(10,2) DEFAULT 0.00,
                    ChangeGiven     DECIMAL(10,2) DEFAULT 0.00,
                    TransactionDate DATETIME      DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_saleid (SaleID),
                    INDEX idx_date   (TransactionDate),
                    FOREIGN KEY (StaffID)   REFERENCES Staff(StaffID)           ON DELETE CASCADE,
                    FOREIGN KEY (AccountID) REFERENCES Account(AccountID)        ON DELETE CASCADE,
                    FOREIGN KEY (ProductID) REFERENCES Product(ProductID)        ON DELETE CASCADE,
                    FOREIGN KEY (PromoID)   REFERENCES Promo(PromoID)            ON DELETE SET NULL
                ) ENGINE=InnoDB
            """);

            // Returns
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Returns (
                    ReturnID       INT AUTO_INCREMENT PRIMARY KEY,
                    TransactionID  INT           NOT NULL,
                    ReturnQuantity INT           NOT NULL DEFAULT 1,
                    StaffID        INT           NOT NULL,
                    SupervisorID   INT,
                    ReturnDate     DATETIME      DEFAULT CURRENT_TIMESTAMP,
                    Reason         TEXT,
                    RefundAmount   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                    Status         VARCHAR(20)   DEFAULT 'Pending',
                    ApprovalCode   VARCHAR(50),
                    ApprovalDate   DATETIME,
                    INDEX idx_return_status (Status),
                    FOREIGN KEY (TransactionID) REFERENCES Transactions(TransactionID) ON DELETE CASCADE,
                    FOREIGN KEY (StaffID)       REFERENCES Staff(StaffID)              ON DELETE CASCADE,
                    FOREIGN KEY (SupervisorID)  REFERENCES Staff(StaffID)              ON DELETE SET NULL
                ) ENGINE=InnoDB
            """);

            // Exchanges
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Exchanges (
                    ExchangeID            INT AUTO_INCREMENT PRIMARY KEY,
                    OriginalTransactionID INT           NOT NULL,
                    NewTransactionID      INT,
                    StaffID               INT           NOT NULL,
                    SupervisorID          INT,
                    ExchangeDate          DATETIME      DEFAULT CURRENT_TIMESTAMP,
                    Status                VARCHAR(20)   DEFAULT 'Pending',
                    Reason                TEXT,
                    ApprovalCode          VARCHAR(50),
                    ApprovalDate          DATETIME,
                    NewProductID          INT,
                    NewPrice              DECIMAL(10,2),
                    INDEX idx_exchange_status (Status),
                    FOREIGN KEY (OriginalTransactionID) REFERENCES Transactions(TransactionID) ON DELETE CASCADE,
                    FOREIGN KEY (NewTransactionID)      REFERENCES Transactions(TransactionID) ON DELETE SET NULL,
                    FOREIGN KEY (StaffID)               REFERENCES Staff(StaffID)              ON DELETE CASCADE,
                    FOREIGN KEY (SupervisorID)          REFERENCES Staff(StaffID)              ON DELETE SET NULL
                ) ENGINE=InnoDB
            """);

            // AddInventory
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS AddInventory (
                    InventoryID   INT AUTO_INCREMENT PRIMARY KEY,
                    StaffID       INT NOT NULL,
                    ProductID     INT NOT NULL,
                    QuantityLeft  INT NOT NULL,
                    QuantityAdded INT DEFAULT 0,
                    InventoryDate DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (StaffID)   REFERENCES Staff(StaffID)   ON DELETE CASCADE,
                    FOREIGN KEY (ProductID) REFERENCES Product(ProductID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // CustomerCommunications
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS CustomerCommunications (
                    CommID           INT AUTO_INCREMENT PRIMARY KEY,
                    AccountID        INT     NOT NULL UNIQUE,
                    MarketingEmails  BOOLEAN DEFAULT FALSE,
                    ReceiptByEmail   BOOLEAN DEFAULT TRUE,
                    SMSNotifications BOOLEAN DEFAULT FALSE,
                    TermsAccepted    BOOLEAN DEFAULT FALSE,
                    UnsubToken       VARCHAR(64) DEFAULT NULL,
                    AcceptanceDate   DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (AccountID) REFERENCES Account(AccountID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // BusinessSessions
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS BusinessSessions (
                    SessionID         INT AUTO_INCREMENT PRIMARY KEY,
                    SupervisorID      INT           NOT NULL,
                    StartDate         DATETIME      NOT NULL,
                    EndDate           DATETIME,
                    Status            VARCHAR(20)   DEFAULT 'Active',
                    TotalCashSales    DECIMAL(10,2) DEFAULT 0.00,
                    TotalCardSales    DECIMAL(10,2) DEFAULT 0.00,
                    TotalSales        DECIMAL(10,2) DEFAULT 0.00,
                    DeclarationSigned BOOLEAN       DEFAULT FALSE,
                    AuthorisationCode VARCHAR(50),
                    Notes             TEXT,
                    INDEX idx_session_status (Status),
                    FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // SessionTransactions
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SessionTransactions (
                    ID        INT AUTO_INCREMENT PRIMARY KEY,
                    SessionID INT NOT NULL,
                    SaleID    INT NOT NULL,
                    FOREIGN KEY (SessionID) REFERENCES BusinessSessions(SessionID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // SupervisorCodes
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SupervisorCodes (
                    CodeID      INT AUTO_INCREMENT PRIMARY KEY,
                    StaffID     INT NOT NULL,
                    AuthCode    VARCHAR(6) NOT NULL,
                    CreatedDate DATETIME DEFAULT CURRENT_TIMESTAMP,
                    ExpiryDate  DATETIME,
                    IsActive    BOOLEAN DEFAULT TRUE,
                    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
                ) ENGINE=InnoDB
            """);

            // MarketingSuppression — audit log of marketing opt-out / opt-in events.
            // The live opt-in state is CustomerCommunications.MarketingEmails; this
            // table records who changed it, when, how, and why.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS MarketingSuppression (
                    EventID     INT AUTO_INCREMENT PRIMARY KEY,
                    AccountID   INT NOT NULL,
                    Email       VARCHAR(100),
                    OptedIn     BOOLEAN NOT NULL,
                    Method      VARCHAR(20)  NOT NULL DEFAULT 'Staff',
                    Note        VARCHAR(255),
                    ActionedBy  INT,
                    EventAt     DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_ms_account (AccountID),
                    FOREIGN KEY (AccountID)  REFERENCES Account(AccountID) ON DELETE CASCADE,
                    FOREIGN KEY (ActionedBy) REFERENCES Staff(StaffID)     ON DELETE SET NULL
                ) ENGINE=InnoDB
            """);

            // MarketingHistory
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS MarketingHistory (
                    CampaignID      INT AUTO_INCREMENT PRIMARY KEY,
                    Subject         TEXT NOT NULL,
                    BodyHtml        TEXT NOT NULL,
                    SentBy          INT  NOT NULL,
                    SentAt          DATETIME DEFAULT CURRENT_TIMESTAMP,
                    RecipientsCount INT  DEFAULT 0,
                    SuccessCount    INT  DEFAULT 0,
                    FOREIGN KEY (SentBy) REFERENCES Staff(StaffID)
                ) ENGINE=InnoDB
            """);

            // SaleSequence — single-row counter for grouping sale line items.
            //
            // IMPORTANT: `id` is a fixed pin column (always 1) and is the
            // PRIMARY KEY. `nextID` is a plain counter column — it must
            // NEVER be the primary key.
            //
            // Why this matters: if nextID were the PK (as in a previous
            // version of this schema), every increment of nextID rewrites
            // the row's identity. Because this setup routine re-runs its
            // seed INSERT on every app launch, once nextID had moved past
            // its original seed value the "INSERT IGNORE" would no longer
            // collide with the existing row (different PK value) and would
            // silently create a SECOND row. From that point on, the
            // un-scoped "UPDATE SaleSequence SET nextID = nextID + 1" in
            // TransactionService increments BOTH rows on every sale, and
            // it's only a matter of time before their values collide —
            // producing "Duplicate entry for key salesequence.PRIMARY".
            //
            // Pinning a separate `id` column as the PK means the seed
            // INSERT IGNORE is genuinely idempotent (always targets id=1)
            // and nextID can be freely incremented without ever touching
            // the table's key structure.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS SaleSequence (
                    id     INT PRIMARY KEY DEFAULT 1,
                    nextID INT NOT NULL DEFAULT 1
                ) ENGINE=InnoDB
            """);

            // BusinessSettings — key/value store for app configuration
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS BusinessSettings (
                    SettingKey   VARCHAR(100) PRIMARY KEY,
                    SettingValue TEXT,
                    UpdatedAt    DATETIME DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
            """);

            // ── Seed data ──────────────────────────────────────────────────────
            // id is pinned to 1, so this is safely idempotent across every
            // launch regardless of how high nextID has climbed.
            stmt.executeUpdate(
                "INSERT IGNORE INTO SaleSequence (id, nextID) VALUES (1, 1)"
            );

            // Default business settings — INSERT IGNORE skips if already set
            insertDefaultSetting(conn, "business.name",    "My Business");
            insertDefaultSetting(conn, "business.address", "");
            insertDefaultSetting(conn, "business.phone",   "");
            insertDefaultSetting(conn, "business.email",   "");
            insertDefaultSetting(conn, "business.vatNo",   "");
            insertDefaultSetting(conn, "business.website", "");
            insertDefaultSetting(conn, "receipt.footer",   "Thank you for your purchase!");
            insertDefaultSetting(conn, "receipt.savePath", "receipts/");
            insertDefaultSetting(conn, "reports.savePath", "reports/");
            insertDefaultSetting(conn, "backup.savePath",  "backups/");

            System.out.println("✅ All tables verified/created.");
        }
    }

    private static void insertDefaultSetting(Connection conn, String key, String value)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO BusinessSettings (SettingKey, SettingValue) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    // ── Step 3: Migrations ─────────────────────────────────────────────────────

    /**
     * Safe column additions using information_schema.
     * Each migration is idempotent — running twice causes no harm.
     */
    private static void runMigrations() throws SQLException {
        // Note: SaleSequence repair (fixSaleSequenceSchema) now runs earlier,
        // in run(), before createAllTables() — see run() for why.

        try (Connection conn = DatabaseConnection.getConnection()) {
            addColumnIfMissing(conn, "Product", "SalePrice",
                "DECIMAL(10,2) DEFAULT NULL AFTER Price");
            addColumnIfMissing(conn, "Promo", "PromoName",
                "VARCHAR(100) AFTER PromoCode");
            addColumnIfMissing(conn, "Promo", "DiscountType",
                "VARCHAR(10) NOT NULL DEFAULT 'PERCENT' AFTER PromoName");
            addColumnIfMissing(conn, "Promo", "DiscountValue",
                "DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER DiscountType");
            addColumnIfMissing(conn, "Promo", "MinimumPurchase",
                "DECIMAL(10,2) DEFAULT 0.00 AFTER DiscountValue");
            addColumnIfMissing(conn, "Promo", "IsActive",
                "BOOLEAN DEFAULT TRUE AFTER ValidTill");
            addColumnIfMissing(conn, "Promo", "UsageLimit",
                "INT DEFAULT NULL AFTER IsActive");
            addColumnIfMissing(conn, "Promo", "UsageCount",
                "INT DEFAULT 0 AFTER UsageLimit");
            addColumnIfMissing(conn, "CustomerCommunications", "UnsubToken",
                "VARCHAR(64) DEFAULT NULL AFTER TermsAccepted");
            System.out.println("✅ Migrations complete.");
        }
    }

    /**
     * One-time repair for installs that were created with the original
     * broken SaleSequence schema (nextID INT PRIMARY KEY, no id column).
     *
     * Detects the old schema by checking whether the `id` column exists.
     * If missing, computes the highest nextID value currently present
     * (covering the case where duplicate rows already accumulated),
     * drops and recreates the table with the corrected schema, and
     * restores the counter so SaleIDs continue from where they left off
     * instead of resetting to 1.
     */
    private static void fixSaleSequenceSchema() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {

            // First make sure the table even exists yet (fresh installs
            // already get the correct schema from createAllTables()).
            try (PreparedStatement tableCheck = conn.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'SaleSequence'
                    """)) {
                tableCheck.setString(1, DatabaseConnection.DATABASE);
                try (ResultSet rs = tableCheck.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        return; // table doesn't exist yet — nothing to repair
                    }
                }
            }

            // If the `id` column already exists, this install already has
            // the corrected schema — nothing to do.
            try (PreparedStatement colCheck = conn.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'SaleSequence' AND COLUMN_NAME = 'id'
                    """)) {
                colCheck.setString(1, DatabaseConnection.DATABASE);
                try (ResultSet rs = colCheck.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return; // already migrated
                    }
                }
            }

            try (Statement stmt = conn.createStatement()) {
                // Preserve the counter's current value (taking the max in
                // case the bug already produced more than one row) so
                // SaleIDs keep counting up instead of resetting to 1.
                int preservedNextId = 1;
                try (ResultSet rs = stmt.executeQuery("SELECT MAX(nextID) FROM SaleSequence")) {
                    if (rs.next() && rs.getObject(1) != null) {
                        preservedNextId = rs.getInt(1);
                    }
                }

                stmt.executeUpdate("DROP TABLE SaleSequence");
                stmt.executeUpdate("""
                    CREATE TABLE SaleSequence (
                        id     INT PRIMARY KEY DEFAULT 1,
                        nextID INT NOT NULL DEFAULT 1
                    ) ENGINE=InnoDB
                """);

                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO SaleSequence (id, nextID) VALUES (1, ?)")) {
                    ins.setInt(1, preservedNextId);
                    ins.executeUpdate();
                }

                System.out.println(
                    "  ↳ Repaired SaleSequence schema (legacy nextID-as-PK detected), "
                    + "preserved nextID=" + preservedNextId);
            }
        }
    }

    private static void addColumnIfMissing(Connection conn, String table,
                                            String column, String definition)
            throws SQLException {
        String check = """
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setString(1, DatabaseConnection.DATABASE);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(
                            "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition
                        );
                        System.out.println("  ↳ Added column " + table + "." + column);
                    }
                }
            }
        }
    }
}
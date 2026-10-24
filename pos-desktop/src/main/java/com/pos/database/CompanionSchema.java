package com.pos.database;

import java.sql.*;

/** Idempotent schema for audited stock counts and acknowledged phone receipts. */
public final class CompanionSchema {
    private CompanionSchema() {}

    public static void ensure(Connection c) throws SQLException {
        try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='IsArchived'")) {
            if (!check.executeQuery().next()) try (Statement s = c.createStatement()) {
                s.executeUpdate("ALTER TABLE Product ADD COLUMN IsArchived BOOLEAN NOT NULL DEFAULT FALSE");
            }
        }
        try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='StockVersion'")) {
            if (!check.executeQuery().next()) try (Statement s = c.createStatement()) {
                s.executeUpdate("ALTER TABLE Product ADD COLUMN StockVersion BIGINT NOT NULL DEFAULT 0");
            }
        }
        try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME='pos_product_stock_version'")) {
            if (!check.executeQuery().next()) try (Statement s = c.createStatement()) {
                // Tracks every stock writer, including returns, Excel imports and manual edits.
                s.executeUpdate("CREATE TRIGGER pos_product_stock_version BEFORE UPDATE ON Product FOR EACH ROW SET NEW.StockVersion = OLD.StockVersion + IF(NEW.Quantity <> OLD.Quantity, 1, 0)");
            }
        }
        try (Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Stocktake (
                    StocktakeID VARCHAR(36) PRIMARY KEY, StaffID INT NOT NULL,
                    Status VARCHAR(16) NOT NULL DEFAULT 'Draft', Notes VARCHAR(1000) NOT NULL DEFAULT '',
                    CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, SubmittedAt DATETIME NULL,
                    ReviewedBy INT NULL, ReviewedAt DATETIME NULL, ReviewNotes VARCHAR(1000) NOT NULL DEFAULT '',
                    INDEX idx_stocktake_status (Status, CreatedAt)
                ) ENGINE=InnoDB
                """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS StocktakeLine (
                    StocktakeID VARCHAR(36) NOT NULL, ProductID INT NOT NULL,
                    ProductName VARCHAR(255) NOT NULL, BarCode VARCHAR(255), CategoryName VARCHAR(255),
                    ExpectedQuantity INT NOT NULL, ExpectedVersion BIGINT NOT NULL, CountedQuantity INT NULL,
                    PRIMARY KEY (StocktakeID, ProductID),
                    FOREIGN KEY (StocktakeID) REFERENCES Stocktake(StocktakeID) ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS MobileReceiptOutbox (
                    ReceiptID VARCHAR(36) PRIMARY KEY, ReceiptText LONGTEXT NOT NULL,
                    CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB
                """);
        }
    }
}

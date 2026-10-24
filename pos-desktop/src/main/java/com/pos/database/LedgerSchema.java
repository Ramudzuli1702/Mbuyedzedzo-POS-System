package com.pos.database;

import java.sql.*;
import java.util.*;

/** Non-destructive upgrades for catalog archiving and immutable accounting evidence. */
public final class LedgerSchema {
    private LedgerSchema() {}

    public static void ensure(Connection c) throws SQLException {
        PromotionSchema.ensure(c);
        column(c, "Product", "IsArchived", "BOOLEAN NOT NULL DEFAULT FALSE");
        column(c, "Account", "IsArchived", "BOOLEAN NOT NULL DEFAULT FALSE");
        // NULL means unknown: historical costs cannot be reconstructed from today's catalog.
        column(c, "Transactions", "UnitCostAtSale", "DECIMAL(10,2) NULL");
        column(c, "Transactions", "OperatorID", "INT NULL");
        column(c, "Returns", "SettlementSessionID", "INT NULL");
        column(c, "Returns", "RefundMethod", "VARCHAR(20) NULL");
        column(c, "Returns", "IsExchangeCredit", "BOOLEAN NOT NULL DEFAULT FALSE");
        try(PreparedStatement p=c.prepareStatement("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='SupervisorCodes' AND COLUMN_NAME='AuthCode'");ResultSet r=p.executeQuery()) {
            if(r.next() && r.getInt(1)<255)try(Statement s=c.createStatement()) {
                s.executeUpdate("ALTER TABLE SupervisorCodes MODIFY AuthCode VARCHAR(255) NOT NULL");
                s.executeUpdate("UPDATE SupervisorCodes SET IsActive=FALSE");
            }
        }
        // Replace cascades in one ALTER per table so there is no interval without constraints.
        Map<String, List<String>> changes = new LinkedHashMap<>();
        try (PreparedStatement p = c.prepareStatement("""
                SELECT k.TABLE_NAME,k.CONSTRAINT_NAME,k.COLUMN_NAME,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE k
                JOIN information_schema.REFERENTIAL_CONSTRAINTS r
                  ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME
                 AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME
                WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND r.DELETE_RULE='CASCADE'
                  AND k.TABLE_NAME IN ('Transactions','Returns','Exchanges','Product','Account','AddInventory','BusinessSessions','SessionTransactions','CheckIn')
                ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION
                """); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                String table = r.getString(1), name = quote(r.getString(2));
                // InnoDB cannot reuse a dropped FK name in the same ALTER on every supported version.
                String replacement=quote("pos_restrict_"+UUID.nameUUIDFromBytes((table+":"+r.getString(2)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""));
                changes.computeIfAbsent(table, key -> new ArrayList<>()).add(
                        "DROP FOREIGN KEY " + name + ", ADD CONSTRAINT " + replacement + " FOREIGN KEY ("
                        + quote(r.getString(3)) + ") REFERENCES " + quote(r.getString(4)) + " ("
                        + quote(r.getString(5)) + ") ON DELETE RESTRICT");
            }
        }
        try (Statement s = c.createStatement()) {
            for (var change : changes.entrySet())
                s.executeUpdate("ALTER TABLE " + quote(change.getKey()) + " " + String.join(", ", change.getValue()));
        }
    }

    private static String quote(String name) { return "`" + name.replace("`", "``") + "`"; }
    private static void column(Connection c, String table, String name, String definition) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            p.setString(1, table); p.setString(2, name);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) return;
            }
        }
        try (Statement s = c.createStatement()) {
            s.executeUpdate("ALTER TABLE " + quote(table) + " ADD COLUMN " + quote(name) + " " + definition);
        }
    }
}

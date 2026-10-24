package com.pos.database;

import java.sql.*;

/** Additive promotion evidence; existing coupon and transaction records are preserved. */
public final class PromotionSchema {
    private PromotionSchema() {}
    public static void ensure(Connection c) throws SQLException {
        try(Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS PromotionGuard (GuardID INT PRIMARY KEY,Revision BIGINT NOT NULL)");
            s.executeUpdate("INSERT IGNORE INTO PromotionGuard VALUES (1,0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS PromotionCampaign (PromotionID VARCHAR(40) PRIMARY KEY,Name VARCHAR(120) NOT NULL,Definition MEDIUMTEXT NOT NULL,PublishedDefinition MEDIUMTEXT NULL,Revision INT NOT NULL,State VARCHAR(20) NOT NULL DEFAULT 'DRAFT',CreatedBy INT NOT NULL,ReviewedBy INT NULL,ReviewedAt DATETIME NULL,UpdatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS PromotionRedemption (SaleID INT NOT NULL,PromotionID VARCHAR(40) NOT NULL,Revision INT NOT NULL,NameSnapshot VARCHAR(120) NOT NULL,Applications INT NOT NULL,Discount DECIMAL(14,2) NOT NULL,CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(SaleID,PromotionID))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS PromotionReview (ReviewID BIGINT AUTO_INCREMENT PRIMARY KEY,PromotionID VARCHAR(40) NOT NULL,Revision INT NOT NULL,ReviewerID INT NOT NULL,Action VARCHAR(20) NOT NULL,DefinitionSnapshot MEDIUMTEXT NOT NULL,ReviewedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
        column(c,"RegularUnitPrice","DECIMAL(10,2) NULL");column(c,"PromotionSnapshot","TEXT NULL");
    }
    private static void column(Connection c,String name,String type) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Transactions' AND COLUMN_NAME=?")) {
            p.setString(1,name);try(ResultSet r=p.executeQuery()){if(r.next())return;}
        }
        try(Statement s=c.createStatement()){s.executeUpdate("ALTER TABLE Transactions ADD COLUMN "+name+" "+type);}
    }
}

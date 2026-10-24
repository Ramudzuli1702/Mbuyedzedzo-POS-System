package com.pos.database;

import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Replaces provisioning root credentials with a local account confined to this edition's schema. */
public final class RuntimeDatabaseAccount {
    private RuntimeDatabaseAccount() {}
    public record Credentials(String username,String password) {}
    public static Credentials create(Connection admin,String schema) throws SQLException {
        if(schema==null || schema.isBlank())throw new SQLException("Database name is missing.");
        String user="pos_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        String password=Base64.getUrlEncoder().withoutPadding().encodeToString(random);Arrays.fill(random,(byte)0);
        try(PreparedStatement p=admin.prepareStatement("CREATE USER ?@'localhost' IDENTIFIED BY ?")) {
            p.setString(1,user);p.setString(2,password);p.executeUpdate();
        }
        boolean literalNames;
        try(Statement s=admin.createStatement();ResultSet r=s.executeQuery("SELECT @@GLOBAL.partial_revokes")){r.next();literalNames=r.getBoolean(1);}
        String grantSchema=literalNames?schema:schema.replace("\\","\\\\").replace("_","\\_").replace("%","\\%");
        try(Statement s=admin.createStatement()) {
            // Startup still needs schema-local DDL for migrations. No global privileges,
            // GRANT OPTION, account administration or filesystem access are granted.
            s.executeUpdate("GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,DROP,INDEX,REFERENCES,TRIGGER,SHOW VIEW,LOCK TABLES ON `"
                    +grantSchema.replace("`","``")+"`.* TO '"+user+"'@'localhost'");
        }
        return new Credentials(user,password);
    }
    public static void migrateIfRoot() throws SQLException {
        if(!"root".equalsIgnoreCase(DatabaseConnection.USERNAME))return;
        if(!DatabaseConnection.isLoopback())throw new SQLException("Configure a schema-scoped account for the remote database before using the POS.");
        Credentials credentials;
        try(Connection admin=DatabaseConnection.getConnection()) {
            if(admin==null)throw new SQLException("Database unavailable");
            credentials=create(admin,DatabaseConnection.DATABASE);
        }
        DatabaseConnection.saveConfig(DatabaseConnection.HOST,DatabaseConnection.PORT,DatabaseConnection.DATABASE,credentials.username(),credentials.password());
    }
}

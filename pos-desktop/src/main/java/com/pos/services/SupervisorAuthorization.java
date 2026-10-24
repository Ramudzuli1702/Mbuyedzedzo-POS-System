package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.utils.*;
import java.security.SecureRandom;
import java.sql.*;

/** Short-lived, single-use supervisor codes, stored as salted password hashes. */
final class SupervisorAuthorization {
    private SupervisorAuthorization() {}
    static String issue(int staff) {
        String code=String.format(java.util.Locale.ROOT,"%06d",new SecureRandom().nextInt(1_000_000));
        return create(staff,code)?code:null;
    }
    static boolean create(int staff,String code) {
        if(code==null || !code.matches("[0-9]{6}"))return false;
        try(Connection c=DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireManager(c,staff);
                try(PreparedStatement p=c.prepareStatement("UPDATE SupervisorCodes SET IsActive=FALSE WHERE StaffID=?")){p.setInt(1,staff);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO SupervisorCodes (StaffID,AuthCode,IsActive,ExpiryDate) VALUES (?,?,TRUE,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 15 MINUTE))")) {
                    p.setInt(1,staff);p.setString(2,PasswordUtil.hashPassword(code));p.executeUpdate();
                }
                c.commit();return true;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }catch(SQLException e){return false;}
    }
    private static void requireManager(Connection c,int staff) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT StaffID FROM Staff WHERE StaffID=? AND Status='Active' AND UserType IN ('Admin','Manager') FOR UPDATE")) {
            p.setInt(1,staff);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Active Admin or Manager required.");}
        }
    }
    /**
     * Locks and verifies the reviewer's active hash on the caller's transaction.
     * Does not consume the key: consume only after all business checks/writes succeed,
     * then commit both together. Failed attempts still affect the process rate limiter.
     */
    static int verify(Connection c,int staff,String code) throws SQLException {
        if(!LoginAttempts.SHARED.begin("approval:"+staff))throw new SQLException("Too many authorization attempts. Wait one minute.");
        requireManager(c,staff);
        try(PreparedStatement p=c.prepareStatement("SELECT CodeID,AuthCode FROM SupervisorCodes WHERE StaffID=? AND IsActive=TRUE AND ExpiryDate>CURRENT_TIMESTAMP ORDER BY CodeID DESC LIMIT 1 FOR UPDATE")) {
            p.setInt(1,staff);
            try(ResultSet r=p.executeQuery()) {
                if(r.next() && !PasswordUtil.needsRehash(r.getString(2)) && PasswordUtil.verifyPassword(code,r.getString(2))) {
                    LoginAttempts.SHARED.success("approval:"+staff);return r.getInt(1);
                }
            }
        }
        throw new SQLException("The authorization code is invalid, expired or already used. Generate a new code.");
    }
    static void consume(Connection c,int code) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("UPDATE SupervisorCodes SET IsActive=FALSE WHERE CodeID=? AND IsActive=TRUE")) {
            p.setInt(1,code);if(p.executeUpdate()!=1)throw new SQLException("Authorization code already used.");
        }
    }
}

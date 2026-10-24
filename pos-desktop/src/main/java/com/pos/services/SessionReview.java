package com.pos.services;

import com.pos.database.DatabaseConnection;
import java.sql.*;

final class SessionReview {
    private SessionReview() {}
    static boolean review(int session,int reviewer,String code,String notes,boolean countersign) {
        try(Connection c=DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int authorization=SupervisorAuthorization.verify(c,reviewer,code);
                try(PreparedStatement p=c.prepareStatement("SELECT Status,DeclarationSigned FROM BusinessSessions WHERE SessionID=? FOR UPDATE")) {
                    p.setInt(1,session);try(ResultSet r=p.executeQuery()) {
                        if(!r.next() || countersign && (!"Closed".equals(r.getString(1)) || r.getBoolean(2))
                                || !countersign && !"Active".equals(r.getString(1))) {c.rollback();return false;}
                    }
                }
                String sql=countersign
                        ?"UPDATE BusinessSessions SET DeclarationSigned=TRUE,AuthorisationCode=NULL,Notes=CONCAT(COALESCE(Notes,''),?) WHERE SessionID=?"
                        :"UPDATE BusinessSessions SET Status='Closed',EndDate=CURRENT_TIMESTAMP,DeclarationSigned=TRUE,AuthorisationCode=NULL,Notes=CONCAT(COALESCE(Notes,''),?) WHERE SessionID=?";
                SessionMoney.refresh(c,session);
                try(PreparedStatement p=c.prepareStatement(sql)) {
                    p.setString(1," | ["+(countersign?"COUNTERSIGNED":"CLOSED")+" BY STAFF #"+reviewer+"] "+(notes==null?"":notes));p.setInt(2,session);p.executeUpdate();
                }
                SupervisorAuthorization.consume(c,authorization);c.commit();return true;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }catch(SQLException e){return false;}
    }
}

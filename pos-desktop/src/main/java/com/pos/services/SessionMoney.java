package com.pos.services;

import java.sql.*;

/** Cash/card movement belongs to the session in which payment or refund actually occurs. */
final class SessionMoney {
    private SessionMoney() {}
    static void refresh(Connection c,int session) throws SQLException {
        String received="""
                SELECT COALESCE(SUM(CASE WHEN e.ExchangeID IS NOT NULL THEN 0
                  WHEN t.OperatorID IS NULL THEN t.SalePrice*t.Quantity
                  ELSE t.AmountPaid-t.ChangeGiven END),0)
                FROM SessionTransactions st JOIN Transactions t ON t.SaleID=st.SaleID
                LEFT JOIN Exchanges e ON e.NewTransactionID=t.TransactionID AND e.Status='Approved'
                WHERE st.SessionID=?
                """;
        java.math.BigDecimal cash=received(c,received+" AND t.PaymentMethod='Cash'",session),
                card=received(c,received+" AND t.PaymentMethod='Card'",session);
        try(PreparedStatement p=c.prepareStatement("SELECT RefundMethod,COALESCE(SUM(RefundAmount),0) FROM Returns WHERE Status='Approved' AND IsExchangeCredit=FALSE AND SettlementSessionID=? GROUP BY RefundMethod")) {
            p.setInt(1,session);try(ResultSet r=p.executeQuery()){while(r.next()) {
                if("Cash".equals(r.getString(1)))cash=cash.subtract(r.getBigDecimal(2));
                else if("Card".equals(r.getString(1)))card=card.subtract(r.getBigDecimal(2));
            }}
        }
        try(PreparedStatement p=c.prepareStatement("UPDATE BusinessSessions SET TotalCashSales=?,TotalCardSales=?,TotalSales=? WHERE SessionID=?")) {
            p.setBigDecimal(1,cash);p.setBigDecimal(2,card);p.setBigDecimal(3,cash.add(card));p.setInt(4,session);
            if(p.executeUpdate()!=1)throw new SQLException("Business session is missing.");
        }
    }
    private static java.math.BigDecimal received(Connection c,String sql,int session) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,session);try(ResultSet r=p.executeQuery()){r.next();return r.getBigDecimal(1);}}
    }
}

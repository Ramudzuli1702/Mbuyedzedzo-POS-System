package com.pos.services;

import com.pos.database.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.*;

/** Serializes review against both the request and original sale; all effects roll back together. */
final class FinancialApproval {
    private FinancialApproval() {}
    static boolean review(boolean exchange, boolean approve, int request, int reviewer, String code) {
        try (Connection c = DatabaseConnection.getConnection()) {
            if(c==null)return false;
            c.setAutoCommit(false);
            try {
                int authorization=SupervisorAuthorization.verify(c,reviewer,code);
                String table=exchange?"Exchanges":"Returns", id=exchange?"ExchangeID":"ReturnID";
                String link=exchange?"OriginalTransactionID":"TransactionID";
                int transaction,product,quantity,staff,account,sale,replacement=0;
                String status,method;BigDecimal refund,price,cost=null,newPrice=null;
                try(PreparedStatement p=c.prepareStatement("SELECT r.*,t.ProductID,t.Quantity AS SoldQuantity,t.SalePrice,t.AccountID,t.SaleID,t.PaymentMethod,t.UnitCostAtSale FROM "+table+" r JOIN Transactions t ON t.TransactionID=r."+link+" WHERE r."+id+"=? FOR UPDATE")) {
                    p.setInt(1,request);
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next()){c.rollback();return false;}
                        status=r.getString("Status");transaction=r.getInt(link);product=r.getInt("ProductID");
                        quantity=exchange?1:r.getInt("ReturnQuantity");staff=r.getInt("StaffID");account=r.getInt("AccountID");sale=r.getInt("SaleID");
                        price=r.getBigDecimal("SalePrice");cost=r.getBigDecimal("UnitCostAtSale");method=r.getString("PaymentMethod");
                        refund=exchange?price:r.getBigDecimal("RefundAmount");
                        if(exchange){replacement=r.getInt("NewProductID");newPrice=r.getBigDecimal("NewPrice");}
                        if(!"Pending".equals(status)) { c.rollback(); return false; }
                        if(approve) {
                            int returned;BigDecimal refunded;
                            try(PreparedStatement sums=c.prepareStatement("SELECT COALESCE(SUM(ReturnQuantity),0),COALESCE(SUM(RefundAmount),0) FROM Returns WHERE TransactionID=? AND Status='Approved'")) {
                                sums.setInt(1,transaction);try(ResultSet s=sums.executeQuery()){s.next();returned=s.getInt(1);refunded=s.getBigDecimal(2);}
                            }
                            int sold=r.getInt("SoldQuantity");
                            if(quantity<=0 || quantity>sold-returned || refund==null || refund.signum()<0
                                    || refund.compareTo(price.multiply(BigDecimal.valueOf(quantity)))>0
                                    || refunded.add(refund).compareTo(price.multiply(BigDecimal.valueOf(sold)))>0)
                                throw new SQLException("This request exceeds the remaining refundable quantity or value.");
                        }
                    }
                }
                int newTransaction=0;
                int settlementSession=0;
                if(approve) {
                    if(!exchange) {
                        if(!java.util.Set.of("Cash","Card").contains(method))throw new SQLException("Choose a supported refund payment method.");
                        try(PreparedStatement p=c.prepareStatement("SELECT SessionID FROM BusinessSessions WHERE Status='Active' ORDER BY SessionID DESC LIMIT 1 FOR UPDATE");ResultSet r=p.executeQuery()) {
                            if(!r.next())throw new SQLException("Start a business session before approving a refund.");settlementSession=r.getInt(1);
                        }
                    }
                    if(exchange) {
                        // Unequal-value exchanges need an explicit settlement, not a fictitious sale.
                        if(newPrice==null || newPrice.compareTo(price)!=0)
                            throw new SQLException("Use a return followed by a new sale for an exchange with a price difference.");
                        BigDecimal replacementCost;
                        try(PreparedStatement p=c.prepareStatement("SELECT Price,CostPrice FROM Product WHERE ProductID=? AND IsArchived=FALSE FOR UPDATE")) {
                            p.setInt(1,replacement);try(ResultSet r=p.executeQuery()) {
                                if(!r.next() || r.getBigDecimal(1).compareTo(newPrice)!=0)throw new SQLException("Replacement price changed. Submit a new request.");
                                replacementCost=r.getBigDecimal(2);
                            }
                        }
                        new ProductService().updateStock(c,replacement,1);
                        try(PreparedStatement p=c.prepareStatement("INSERT INTO Transactions (SaleID,ProductID,StaffID,OperatorID,AccountID,Quantity,SalePrice,UnitCostAtSale,PaymentMethod,AmountPaid,ChangeGiven,TransactionDate) VALUES (?,?,?,?,?,1,?,?,?,0,0,CURRENT_TIMESTAMP)",Statement.RETURN_GENERATED_KEYS)) {
                            p.setInt(1,sale);p.setInt(2,replacement);p.setInt(3,staff);p.setInt(4,reviewer);p.setInt(5,account);
                            p.setBigDecimal(6,newPrice);p.setBigDecimal(7,replacementCost);p.setString(8,method);p.executeUpdate();
                            try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Replacement was not recorded.");newTransaction=r.getInt(1);}
                        }
                        // Credit the original item exactly once. Existing net reports already deduct approved returns.
                        try(PreparedStatement p=c.prepareStatement("INSERT INTO Returns (TransactionID,ReturnQuantity,StaffID,SupervisorID,Reason,RefundAmount,Status,ApprovalDate,IsExchangeCredit) VALUES (?,1,?,?,'Exchange credit',?,'Approved',CURRENT_TIMESTAMP,TRUE)")) {
                            p.setInt(1,transaction);p.setInt(2,staff);p.setInt(3,reviewer);p.setBigDecimal(4,price);p.executeUpdate();
                        }
                    }
                    try(PreparedStatement p=c.prepareStatement("UPDATE Product SET Quantity=Quantity+?,NoSold=GREATEST(NoSold-?,0) WHERE ProductID=?")) {
                        p.setInt(1,quantity);p.setInt(2,quantity);p.setInt(3,product);
                        if(p.executeUpdate()!=1)throw new SQLException("Returned product is missing.");
                    }
                }
                String sql="UPDATE "+table+" SET Status=?,SupervisorID=?,ApprovalCode=NULL,ApprovalDate=CURRENT_TIMESTAMP"
                        +(exchange&&approve?",NewTransactionID=?":"")+" WHERE "+id+"=? AND Status='Pending'";
                try(PreparedStatement p=c.prepareStatement(sql)) {
                    p.setString(1,approve?"Approved":"Rejected");p.setInt(2,reviewer);int n=3;
                    if(exchange&&approve)p.setInt(n++,newTransaction);p.setInt(n,request);
                    if(p.executeUpdate()!=1)throw new SQLException("Request was already reviewed.");
                }
                if(approve && !exchange) {
                    try(PreparedStatement p=c.prepareStatement("UPDATE Returns SET SettlementSessionID=?,RefundMethod=? WHERE ReturnID=?")) {
                        p.setInt(1,settlementSession);p.setString(2,method);p.setInt(3,request);p.executeUpdate();
                    }
                    SessionMoney.refresh(c,settlementSession);
                }
                SupervisorAuthorization.consume(c,authorization);
                c.commit();return true;
            } catch(SQLException | RuntimeException e) {c.rollback();throw e;}
        } catch(SQLException e) {System.err.println("Review not saved: "+e.getMessage());return false;}
    }
}

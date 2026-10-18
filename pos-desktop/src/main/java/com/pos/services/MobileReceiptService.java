package com.pos.services;

import com.google.gson.JsonObject;
import com.pos.database.DatabaseConnection;
import java.sql.*;
import java.util.*;

/** Persist before transmission; delete only after the phone confirms durable storage. */
public final class MobileReceiptService {
    private final StocktakeService.Connections connections;
    public MobileReceiptService() { this(() -> { Connection c=DatabaseConnection.getConnection();if(c==null)throw new SQLException("Database unavailable");return c; }); }
    public MobileReceiptService(StocktakeService.Connections connections) { this.connections=connections; }
    public void queue(String text) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO MobileReceiptOutbox (ReceiptID,ReceiptText) VALUES (?,?)")) {
            p.setString(1,UUID.randomUUID().toString());p.setString(2,text);p.executeUpdate();
        }
    }
    public List<JsonObject> pending() throws SQLException {
        List<JsonObject> messages=new ArrayList<>();
        try(Connection c=open();Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT ReceiptID,ReceiptText FROM MobileReceiptOutbox ORDER BY CreatedAt,ReceiptID LIMIT 100")) {
            while(r.next()) { JsonObject j=new JsonObject();j.addProperty("type","receipt");j.addProperty("id",r.getString(1));j.addProperty("text",r.getString(2));messages.add(j); }
        }
        return messages;
    }
    public void acknowledge(String id) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("DELETE FROM MobileReceiptOutbox WHERE ReceiptID=?")) { p.setString(1,id);p.executeUpdate(); }
    }
    private Connection open() throws SQLException { return connections.open(); }
}

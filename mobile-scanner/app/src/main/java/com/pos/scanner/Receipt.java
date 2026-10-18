package com.pos.scanner;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class Receipt implements Serializable {
    private String receiptId;
    public String name="Walk-in",email="",cashier="",date="",fullReceiptText="";
    public double total;
    public List<ReceiptItem> items=new ArrayList<>();
    public long timestamp=System.currentTimeMillis();
    public Receipt() { receiptId=UUID.randomUUID().toString(); }
    public static class ReceiptItem implements Serializable {
        public String name; public int quantity; public double price,subtotal;
        public ReceiptItem(String name,int quantity,double price,double subtotal) { this.name=name;this.quantity=quantity;this.price=price;this.subtotal=subtotal; }
        public String getName(){return name;} public int getQuantity(){return quantity;}
        public double getPrice(){return price;} public double getSubtotal(){return subtotal;}
    }
    public String getReceiptId(){return receiptId;} public void setReceiptId(String id){receiptId=id;}
    public String getCustomerName(){return name;} public String getCustomerEmail(){return email;}
    public String getCashierName(){return cashier;} public String getDate(){return date;}
    public List<ReceiptItem> getItems(){return items==null?Collections.emptyList():items;} public double getTotal(){return total;}
    public String getFullReceiptText(){return fullReceiptText;} public void setFullReceiptText(String text){fullReceiptText=text;}
    private static final Pattern META=Pattern.compile("^(Date|Cashier|Customer|Email)\\s*:\\s*(.*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL=Pattern.compile("^(?:NET\\s+)?TOTAL\\s*:?\\s*R?\\s*([\\d.,]+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern DETAIL=Pattern.compile("^(\\d+)\\s*[x×]\\s*([\\d.,]+)\\s+R?\\s*([\\d.,]+)$");
    private static final Pattern LEGACY=Pattern.compile("^(.+?)\\s{2,}(\\d+)\\s{2,}R?\\s*([\\d.,]+)$");

    public static Receipt fromString(String text) {
        Receipt receipt=new Receipt();receipt.fullReceiptText=text==null?"":text;
        receipt.receiptId=UUID.nameUUIDFromBytes(receipt.fullReceiptText.getBytes(StandardCharsets.UTF_8)).toString();
        boolean inItems=false,amountColumn=true;StringBuilder name=new StringBuilder();
        for(String raw:receipt.fullReceiptText.split("\\r?\\n")) {
            String line=raw.trim();if(line.isEmpty()||line.matches("[-=]+"))continue;
            Matcher meta=META.matcher(line),total=TOTAL.matcher(line),detail=DETAIL.matcher(line);
            if(meta.matches()) {
                switch(meta.group(1).toLowerCase(Locale.ROOT)) {
                    case "date":receipt.date=meta.group(2);break;case "cashier":receipt.cashier=meta.group(2);break;
                    case "customer":receipt.name=meta.group(2);break;case "email":receipt.email=meta.group(2);break;
                }
            } else if(total.matches()) { receipt.total=money(total.group(1));inItems=false; }
            else if(line.startsWith("Item") && line.contains("Qty")) { inItems=true;amountColumn=line.contains("Amount");name.setLength(0); }
            else if(line.matches("(?i)^(Subtotal|Discount|Payment|Tendered|Change)\\b.*")) { inItems=false;name.setLength(0); }
            else if(inItems) {
                Matcher legacy=LEGACY.matcher(line);
                if(detail.matches()) {
                    receipt.items.add(new ReceiptItem(name.toString().trim(),Integer.parseInt(detail.group(1)),money(detail.group(2)),money(detail.group(3))));name.setLength(0);
                } else if(legacy.matches()) {
                    int qty=Integer.parseInt(legacy.group(2));double value=money(legacy.group(3));
                    receipt.items.add(new ReceiptItem(legacy.group(1).trim(),qty,amountColumn&&qty>0?value/qty:value,amountColumn?value:value*qty));name.setLength(0);
                } else { if(name.length()>0)name.append(' ');name.append(line); }
            }
        }
        return receipt;
    }
    static double money(String value) {
        value=value.replace(" ","");
        int comma=value.lastIndexOf(','),dot=value.lastIndexOf('.');
        if(comma>dot) value=value.replace(".","").replace(',','.');else value=value.replace(",","");
        try{return Double.parseDouble(value);}catch(NumberFormatException e){return 0;}
    }
}

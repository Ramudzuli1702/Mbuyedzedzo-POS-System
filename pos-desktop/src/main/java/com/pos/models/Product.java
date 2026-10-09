package com.pos.models;

import java.math.BigDecimal;

public class Product {
    private int productID;
    private int staffID;
    private int categoryID;
    private String productName;
    private String qrCode;
    private String barCode;
    private int quantity;
    private int noSold;
    private BigDecimal price;
    private BigDecimal costPrice;
    private String categoryName;
    
    public Product() {}
    
    public Product(int productID, String productName, String barCode, int quantity, BigDecimal price) {
        this.productID = productID;
        this.productName = productName;
        this.barCode = barCode;
        this.quantity = quantity;
        this.price = price;
    }
    
    // Getters and Setters
    public int getProductID() { return productID; }
    public void setProductID(int productID) { this.productID = productID; }
    
    public int getStaffID() { return staffID; }
    public void setStaffID(int staffID) { this.staffID = staffID; }
    
    public int getCategoryID() { return categoryID; }
    public void setCategoryID(int categoryID) { this.categoryID = categoryID; }
    
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    
    public String getBarCode() { return barCode; }
    public void setBarCode(String barCode) { this.barCode = barCode; }
    
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    
    public int getNoSold() { return noSold; }
    public void setNoSold(int noSold) { this.noSold = noSold; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    /** What the shop paid to acquire this unit — the basis for real profit-margin reporting. */
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    
    public boolean isLowStock() {
        return quantity < 10; // Alert when less than 10 items
    }
    
    public boolean isOutOfStock() {
        return quantity <= 0;
    }
}
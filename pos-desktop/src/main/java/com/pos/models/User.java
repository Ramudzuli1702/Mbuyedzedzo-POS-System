package com.pos.models;

import java.time.LocalDateTime;

public class User {
    private int staffID;
    private String fullNames;
    private String emailAddress;
    private String userPassword;
    private String userType;
    private String status;
    private LocalDateTime timeStamp;
    
    public User() {}
    
    public User(int staffID, String fullNames, String emailAddress, String userType, String status) {
        this.staffID = staffID;
        this.fullNames = fullNames;
        this.emailAddress = emailAddress;
        this.userType = userType;
        this.status = status;
    }
    
    // Getters and Setters
    public int getStaffID() { return staffID; }
    public void setStaffID(int staffID) { this.staffID = staffID; }
    
    public String getFullNames() { return fullNames; }
    public void setFullNames(String fullNames) { this.fullNames = fullNames; }
    
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    
    public String getUserPassword() { return userPassword; }
    public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
    
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getTimeStamp() { return timeStamp; }
    public void setTimeStamp(LocalDateTime timeStamp) { this.timeStamp = timeStamp; }
    
    public boolean isAdmin() {
        return "Admin".equalsIgnoreCase(userType);
    }
    
    public boolean isManager() {
        return "Manager".equalsIgnoreCase(userType);
    }
    
    public boolean isCashier() {
        return "Cashier".equalsIgnoreCase(userType);
    }
    
    public boolean hasFullAccess() {
        return isAdmin() || isManager();
    }
}
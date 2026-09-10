package com.pos.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Customer {
    private int accountID;
    private int staffID;
    private String fullNames;
    private String emailAddress;
    private LocalDate dateOfBirth;
    private String contactNo;
    private LocalDateTime timeStamp;
    
    public Customer() {}
    
    public Customer(int accountID, String fullNames, String emailAddress, String contactNo) {
        this.accountID = accountID;
        this.fullNames = fullNames;
        this.emailAddress = emailAddress;
        this.contactNo = contactNo;
    }
    
    // Getters and Setters
    public int getAccountID() { return accountID; }
    public void setAccountID(int accountID) { this.accountID = accountID; }
    
    public int getStaffID() { return staffID; }
    public void setStaffID(int staffID) { this.staffID = staffID; }
    
    public String getFullNames() { return fullNames; }
    public void setFullNames(String fullNames) { this.fullNames = fullNames; }
    
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    
    public String getContactNo() { return contactNo; }
    public void setContactNo(String contactNo) { this.contactNo = contactNo; }
    
    public LocalDateTime getTimeStamp() { return timeStamp; }
    public void setTimeStamp(LocalDateTime timeStamp) { this.timeStamp = timeStamp; }
}
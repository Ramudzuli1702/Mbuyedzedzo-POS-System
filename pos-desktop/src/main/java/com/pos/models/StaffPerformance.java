package com.pos.models;

import java.time.LocalDateTime;

public class StaffPerformance {
    private int staffID;
    private String fullNames;
    private double totalSales;
    private double totalHours;
    private double efficiency; // sales per hour
    private int rank;

    // Constructors
    public StaffPerformance() {}

    public StaffPerformance(int staffID, String fullNames, double totalSales, double totalHours) {
        this.staffID = staffID;
        this.fullNames = fullNames;
        this.totalSales = totalSales;
        this.totalHours = totalHours;
        this.efficiency = totalHours > 0 ? totalSales / totalHours : 0;
    }

    // Getters and Setters
    public int getStaffID() { return staffID; }
    public void setStaffID(int staffID) { this.staffID = staffID; }

    public String getFullNames() { return fullNames; }
    public void setFullNames(String fullNames) { this.fullNames = fullNames; }

    public double getTotalSales() { return totalSales; }
    public void setTotalSales(double totalSales) { this.totalSales = totalSales; }

    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }

    public double getEfficiency() { return efficiency; }
    public void setEfficiency(double efficiency) { this.efficiency = efficiency; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
}
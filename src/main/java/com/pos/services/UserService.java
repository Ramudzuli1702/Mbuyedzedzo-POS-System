package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.models.User;
import com.pos.models.StaffPerformance;
import com.pos.utils.PasswordUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.*;

public class UserService {

    public User login(String email, String password) {
        String query = "SELECT * FROM Staff WHERE EmailAddress = ? AND Status = 'Active'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String hashedPassword = rs.getString("UserPassword");
                if (PasswordUtil.verifyPassword(password, hashedPassword)) {
                    User user = new User();
                    user.setStaffID(rs.getInt("StaffID"));
                    user.setFullNames(rs.getString("FullNames"));
                    user.setEmailAddress(rs.getString("EmailAddress"));
                    user.setUserType(rs.getString("UserType"));
                    user.setStatus(rs.getString("Status"));
                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean logCheckIn(int staffID) {
        String query = "INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, staffID);
            pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean logCheckOut(int staffID) {
        String query = "UPDATE CheckIn SET LogoutStamp = ? WHERE StaffID = ? AND LogoutStamp IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setInt(2, staffID);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean addUser(User user, String password) {
        if (!PasswordUtil.isValidPassword(password)) {
            return false;
        }

        String query = "INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, user.getFullNames());
            pstmt.setString(2, user.getEmailAddress());
            pstmt.setString(3, PasswordUtil.hashPassword(password));
            pstmt.setString(4, user.getUserType());
            pstmt.setString(5, "Active");

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateUser(User user) {
        String query = "UPDATE Staff SET FullNames = ?, EmailAddress = ?, UserType = ?, Status = ? WHERE StaffID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, user.getFullNames());
            pstmt.setString(2, user.getEmailAddress());
            pstmt.setString(3, user.getUserType());
            pstmt.setString(4, user.getStatus());
            pstmt.setInt(5, user.getStaffID());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteUser(int staffID) {
        String query = "UPDATE Staff SET Status = 'Inactive' WHERE StaffID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, staffID);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean resetPassword(int staffID, String newPassword) {
        if (!PasswordUtil.isValidPassword(newPassword)) {
            return false;
        }

        String query = "UPDATE Staff SET UserPassword = ? WHERE StaffID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, PasswordUtil.hashPassword(newPassword));
            pstmt.setInt(2, staffID);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public ObservableList<User> getAllUsers() {
        ObservableList<User> users = FXCollections.observableArrayList();
        String query = "SELECT * FROM Staff ORDER BY TimeStamp DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                User user = new User();
                user.setStaffID(rs.getInt("StaffID"));
                user.setFullNames(rs.getString("FullNames"));
                user.setEmailAddress(rs.getString("EmailAddress"));
                user.setUserType(rs.getString("UserType"));
                user.setStatus(rs.getString("Status"));
                user.setTimeStamp(rs.getTimestamp("TimeStamp").toLocalDateTime());
                users.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    /**
     * Returns all active staff members for cashier selection in SalesView.
     * Results are sorted by name for easy scanning in the picker dialog.
     */
    public List<User> getAllActiveUsers() {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM Staff WHERE Status = 'Active' ORDER BY FullNames ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                User user = new User();
                user.setStaffID(rs.getInt("StaffID"));
                user.setFullNames(rs.getString("FullNames"));
                user.setEmailAddress(rs.getString("EmailAddress"));
                user.setUserType(rs.getString("UserType"));
                user.setStatus(rs.getString("Status"));
                users.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public ObservableList<User> searchUsers(String keyword) {
        ObservableList<User> users = FXCollections.observableArrayList();
        String query = "SELECT * FROM Staff WHERE FullNames LIKE ? OR EmailAddress LIKE ? OR UserType LIKE ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            String searchPattern = "%" + keyword + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                User user = new User();
                user.setStaffID(rs.getInt("StaffID"));
                user.setFullNames(rs.getString("FullNames"));
                user.setEmailAddress(rs.getString("EmailAddress"));
                user.setUserType(rs.getString("UserType"));
                user.setStatus(rs.getString("Status"));
                users.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    // New methods for performance
    public List<StaffPerformance> getStaffPerformance(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Map<Integer, Double> totalSalesMap = getTotalSalesPerStaff(ym);
        Map<Integer, Double> totalHoursMap = getMonthlyHoursPerStaff(ym);

        List<User> activeUsers = getAllActiveUsers();
        List<StaffPerformance> performances = new ArrayList<>();

        for (User user : activeUsers) {
            int id = user.getStaffID();
            double sales = totalSalesMap.getOrDefault(id, 0.0);
            double hours = totalHoursMap.getOrDefault(id, 0.0);
            StaffPerformance perf = new StaffPerformance(id, user.getFullNames(), sales, hours);
            performances.add(perf);
        }

        // Sort by totalSales descending, assign ranks
        performances.sort((a, b) -> Double.compare(b.getTotalSales(), a.getTotalSales()));
        for (int i = 0; i < performances.size(); i++) {
            performances.get(i).setRank(i + 1);
        }

        return performances;
    }

    private Map<Integer, Double> getTotalSalesPerStaff(YearMonth ym) {
        Map<Integer, Double> salesMap = new HashMap<>();
        String query = "SELECT StaffID, SUM(SalePrice * Quantity) as totalSales FROM Transactions WHERE YEAR(TransactionDate) = ? AND MONTH(TransactionDate) = ? GROUP BY StaffID";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, ym.getYear());
            pstmt.setInt(2, ym.getMonthValue());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                salesMap.put(rs.getInt("StaffID"), rs.getDouble("totalSales"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return salesMap;
    }

    private Map<Integer, Double> getMonthlyHoursPerStaff(YearMonth ym) {
        Map<Integer, Double> hoursMap = new HashMap<>();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        String query = "SELECT StaffID, SUM(TIMESTAMPDIFF(SECOND, LoginStamp, COALESCE(LogoutStamp, NOW()))) / 3600.0 as totalHours " +
                       "FROM CheckIn WHERE DATE(LoginStamp) >= ? AND DATE(LoginStamp) <= ? GROUP BY StaffID";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setDate(1, java.sql.Date.valueOf(start));
            pstmt.setDate(2, java.sql.Date.valueOf(end));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                hoursMap.put(rs.getInt("StaffID"), rs.getDouble("totalHours"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return hoursMap;
    }

    public Map<Integer, Map<LocalDate, Double>> getDailySalesAllStaff(YearMonth ym) {
        Map<Integer, Map<LocalDate, Double>> dailyMap = new HashMap<>();
        String query = "SELECT StaffID, DATE(TransactionDate) as saleDate, SUM(SalePrice * Quantity) as dailySales " +
                       "FROM Transactions WHERE YEAR(TransactionDate) = ? AND MONTH(TransactionDate) = ? GROUP BY StaffID, DATE(TransactionDate)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, ym.getYear());
            pstmt.setInt(2, ym.getMonthValue());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int staffID = rs.getInt("StaffID");
                LocalDate date = rs.getDate("saleDate").toLocalDate();
                double sales = rs.getDouble("dailySales");
                dailyMap.computeIfAbsent(staffID, k -> new HashMap<>()).put(date, sales);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dailyMap;
    }

    public Map<Integer, Map<LocalDate, Double>> getDailyHoursAllStaff(YearMonth ym) {
        Map<Integer, Map<LocalDate, Double>> dailyMap = new HashMap<>();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        String query = "SELECT StaffID, DATE(LoginStamp) as loginDate, SUM(TIMESTAMPDIFF(SECOND, LoginStamp, COALESCE(LogoutStamp, NOW()))) / 3600.0 as dailyHours " +
                       "FROM CheckIn WHERE DATE(LoginStamp) >= ? AND DATE(LoginStamp) <= ? GROUP BY StaffID, DATE(LoginStamp)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setDate(1, java.sql.Date.valueOf(start));
            pstmt.setDate(2, java.sql.Date.valueOf(end));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int staffID = rs.getInt("StaffID");
                LocalDate date = rs.getDate("loginDate").toLocalDate();
                double hours = rs.getDouble("dailyHours");
                dailyMap.computeIfAbsent(staffID, k -> new HashMap<>()).put(date, hours);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return dailyMap;
    }

    public String getStaffNameById(int staffID) {
        String query = "SELECT FullNames FROM Staff WHERE StaffID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, staffID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("FullNames");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
}
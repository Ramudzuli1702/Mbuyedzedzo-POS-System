package com.pos.services;

import com.pos.database.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

public class CategoryService {

    public ObservableList<String> getAllCategories() {
        ObservableList<String> categories = FXCollections.observableArrayList();
        String query = "SELECT CategoryName FROM Category ORDER BY CategoryName";

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                categories.add(rs.getString("CategoryName"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }

    public int getCategoryId(String categoryName) {
        String query = "SELECT CategoryID FROM Category WHERE CategoryName = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, categoryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("CategoryID");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean addCategory(String categoryName) {
        String query = "INSERT INTO Category (CategoryName) VALUES (?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, categoryName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateCategory(int categoryId, String newName) {
        String query = "UPDATE Category SET CategoryName = ? WHERE CategoryID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, newName);
            pstmt.setInt(2, categoryId);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteCategory(int categoryId) {
        // Check if any products use this category first
        String checkQuery = "SELECT COUNT(*) FROM Product WHERE CategoryID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {

            checkStmt.setInt(1, categoryId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                return false; // Cannot delete — products exist under this category
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        String query = "DELETE FROM Category WHERE CategoryID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, categoryId);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * The category every product is now silently filed under — category
     * picking was removed from the UI (it only ever existed for debugging),
     * but Product.CategoryID stays NOT NULL with an FK to Category, so every
     * insert/update still needs a valid id. Reuses whatever category already
     * has the lowest id on an existing install (so nothing has to migrate);
     * creates "General" on a fresh install where none exist yet.
     */
    public int getOrCreateDefaultCategoryId() {
        String query = "SELECT CategoryID FROM Category ORDER BY CategoryID LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) return rs.getInt("CategoryID");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO Category (CategoryName) VALUES ('General')",
                        Statement.RETURN_GENERATED_KEYS)) {

            if (pstmt.executeUpdate() > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Needed for the dialog to display ID alongside name
    public ObservableList<javafx.util.Pair<Integer, String>> getAllCategoriesWithId() {
        ObservableList<javafx.util.Pair<Integer, String>> categories = FXCollections.observableArrayList();
        String query = "SELECT CategoryID, CategoryName FROM Category ORDER BY CategoryName";

        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                categories.add(new javafx.util.Pair<>(
                        rs.getInt("CategoryID"),
                        rs.getString("CategoryName")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }
}
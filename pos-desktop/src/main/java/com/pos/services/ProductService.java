package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.models.Product;
import com.pos.utils.QRCodeUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

public class ProductService {

    /* =========================
       ADD PRODUCT
    ========================= */
    public boolean addProduct(Product product) {

        String qrData = "PRODUCT:" + product.getBarCode();
        String qrCode = QRCodeUtil.generateQRCode(qrData);

        String sql = """
            INSERT INTO Product
            (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, product.getStaffID());
            pstmt.setInt(2, product.getCategoryID());
            pstmt.setString(3, product.getProductName());
            pstmt.setString(4, qrCode);
            pstmt.setString(5, product.getBarCode());
            pstmt.setInt(6, product.getQuantity());
            pstmt.setBigDecimal(7, product.getPrice());

            if (pstmt.executeUpdate() == 0) return false;

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    product.setProductID(rs.getInt(1));
                    product.setQrCode(qrCode);
                }
            }
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       UPDATE PRODUCT
    ========================= */
    public boolean updateProduct(Product product) {

        String sql = """
            UPDATE Product
            SET ProductName = ?, CategoryID = ?, BarCode = ?, Quantity = ?, Price = ?
            WHERE ProductID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getProductName());
            pstmt.setInt(2, product.getCategoryID());
            pstmt.setString(3, product.getBarCode());
            pstmt.setInt(4, product.getQuantity());
            pstmt.setBigDecimal(5, product.getPrice());
            pstmt.setInt(6, product.getProductID());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       DELETE PRODUCT
    ========================= */
    public boolean deleteProduct(int productID) {

        String sql = "DELETE FROM Product WHERE ProductID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, productID);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       GET PRODUCT
    ========================= */
    public Product getProductById(int productID) {

        String sql = """
            SELECT p.*, c.CategoryName
            FROM Product p
            LEFT JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE p.ProductID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, productID);
            ResultSet rs = pstmt.executeQuery();

            return rs.next() ? extractProduct(rs) : null;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Product getProductByCode(String code) {

        String sql = """
            SELECT p.*, c.CategoryName
            FROM Product p
            LEFT JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE p.QRCode = ? OR p.BarCode = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, code);
            pstmt.setString(2, code);
            ResultSet rs = pstmt.executeQuery();

            return rs.next() ? extractProduct(rs) : null;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /* =========================
       LISTING METHODS
    ========================= */
    public ObservableList<Product> getAllProducts() {

        ObservableList<Product> products = FXCollections.observableArrayList();

        String sql = """
            SELECT p.*, c.CategoryName
            FROM Product p
            LEFT JOIN Category c ON p.CategoryID = c.CategoryID
            ORDER BY p.ProductName
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(extractProduct(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    public ObservableList<Product> searchProducts(String keyword) {

        ObservableList<Product> products = FXCollections.observableArrayList();
        String search = "%" + keyword + "%";

        String sql = """
            SELECT p.*, c.CategoryName
            FROM Product p
            LEFT JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE p.ProductName LIKE ? OR p.BarCode LIKE ? OR c.CategoryName LIKE ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, search);
            pstmt.setString(2, search);
            pstmt.setString(3, search);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                products.add(extractProduct(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    public ObservableList<Product> getLowStockProducts() {

        ObservableList<Product> products = FXCollections.observableArrayList();

        String sql = """
            SELECT p.*, c.CategoryName
            FROM Product p
            LEFT JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE p.Quantity < 10
            ORDER BY p.Quantity ASC
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(extractProduct(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    /* =========================
       STOCK UPDATES (CRITICAL)
    ========================= */

    // Used inside TRANSACTIONS (Sales)
    public void updateStock(Connection conn, int productID, int quantitySold) throws SQLException {

        String sql = """
            UPDATE Product
            SET Quantity = Quantity - ?, NoSold = NoSold + ?
            WHERE ProductID = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, quantitySold);
            pstmt.setInt(2, quantitySold);
            pstmt.setInt(3, productID);
            pstmt.executeUpdate();
        }
    }

    // Used OUTSIDE transactions
    public boolean updateStock(int productID, int quantitySold) {

        try (Connection conn = DatabaseConnection.getConnection()) {
            updateStock(conn, productID, quantitySold);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       HELPERS
    ========================= */
    private Product extractProduct(ResultSet rs) throws SQLException {

        Product product = new Product();
        product.setProductID(rs.getInt("ProductID"));
        product.setStaffID(rs.getInt("StaffID"));
        product.setCategoryID(rs.getInt("CategoryID"));
        product.setProductName(rs.getString("ProductName"));
        product.setQrCode(rs.getString("QRCode"));
        product.setBarCode(rs.getString("BarCode"));
        product.setQuantity(rs.getInt("Quantity"));
        product.setNoSold(rs.getInt("NoSold"));
        product.setPrice(rs.getBigDecimal("Price"));
        product.setCategoryName(rs.getString("CategoryName"));

        return product;
    }
}

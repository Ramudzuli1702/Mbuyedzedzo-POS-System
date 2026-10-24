package com.pos.services;

import com.pos.database.DatabaseConnection;
import com.pos.models.Product;
import com.pos.utils.BarcodeUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

public class ProductService {

    /* =========================
       ADD PRODUCT
    ========================= */
    public boolean addProduct(Product product) {

        validate(product);

        // The "QRCode" column holds whatever label image gets generated for
        // this product — a barcode image now, not a QR code (see BarcodeUtil).
        String barcodeImage = BarcodeUtil.generateBarcode(product.getBarCode());

        String sql = """
            INSERT INTO Product
            (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price, CostPrice)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            conn.setAutoCommit(false);
            try {
            lockBarcode(conn,product.getBarCode(),0);

            pstmt.setInt(1, product.getStaffID());
            pstmt.setInt(2, product.getCategoryID());
            pstmt.setString(3, product.getProductName());
            pstmt.setString(4, barcodeImage);
            pstmt.setString(5, product.getBarCode());
            pstmt.setInt(6, product.getQuantity());
            pstmt.setBigDecimal(7, product.getPrice());
            pstmt.setBigDecimal(8, product.getCostPrice());

            if (pstmt.executeUpdate() == 0) {conn.rollback();return false;}

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    product.setProductID(rs.getInt(1));
                    product.setQrCode(barcodeImage);
                }
            }
            conn.commit();return true;
            }catch(SQLException|RuntimeException e){conn.rollback();throw e;}

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       UPDATE PRODUCT
    ========================= */
    public boolean updateProduct(Product product) {

        validate(product);

        // The barcode VALUE may have changed, so the generated label image
        // is regenerated too — otherwise an edited barcode would print a
        // label that doesn't match the number underneath it.
        String barcodeImage = BarcodeUtil.generateBarcode(product.getBarCode());

        String sql = """
            UPDATE Product
            SET ProductName = ?, CategoryID = ?, BarCode = ?, QRCode = ?, Quantity = ?, Price = ?, CostPrice = ?
            WHERE ProductID = ? AND StockVersion = ? AND IsArchived = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            try {
            lockBarcode(conn,product.getBarCode(),product.getProductID());

            pstmt.setString(1, product.getProductName());
            pstmt.setInt(2, product.getCategoryID());
            pstmt.setString(3, product.getBarCode());
            pstmt.setString(4, barcodeImage);
            pstmt.setInt(5, product.getQuantity());
            pstmt.setBigDecimal(6, product.getPrice());
            pstmt.setBigDecimal(7, product.getCostPrice());
            pstmt.setInt(8, product.getProductID());
            pstmt.setLong(9, product.getStockVersion());

            boolean ok = pstmt.executeUpdate() > 0;
            if (ok) product.setQrCode(barcodeImage);
            if(ok)conn.commit();else conn.rollback();
            return ok;
            }catch(SQLException|RuntimeException e){conn.rollback();throw e;}

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================
       DELETE PRODUCT
    ========================= */
    public boolean deleteProduct(int productID) {

        String sql = "UPDATE Product SET IsArchived = TRUE WHERE ProductID = ? AND IsArchived = FALSE";

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
            WHERE p.ProductID = ? AND p.IsArchived = FALSE
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
        Product exact = findProductByCode(code);
        if (exact != null || code == null) return exact;
        // ZXing may represent a zero-prefixed EAN-13 as its equivalent UPC-A.
        if (code.matches("[0-9]{12}")) return findProductByCode("0" + code);
        if (code.matches("0[0-9]{12}")) return findProductByCode(code.substring(1));
        return null;
    }

    private Product findProductByCode(String code) {

        String sql = """
            SELECT p.*, c.CategoryName
            FROM Product p
            LEFT JOIN Category c ON p.CategoryID = c.CategoryID
            WHERE p.IsArchived = FALSE AND (p.QRCode = ? OR p.BarCode = ?)
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
            WHERE p.IsArchived = FALSE ORDER BY p.ProductName
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
            WHERE p.IsArchived = FALSE AND (p.ProductName LIKE ? OR p.BarCode LIKE ? OR c.CategoryName LIKE ?)
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
            WHERE p.IsArchived = FALSE AND p.Quantity < 10
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

        if (quantitySold <= 0) throw new SQLException("Sale quantity must be positive.");

        String sql = """
            UPDATE Product
            SET Quantity = Quantity - ?, NoSold = NoSold + ?
            WHERE ProductID = ? AND Quantity >= ? AND IsArchived = FALSE
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, quantitySold);
            pstmt.setInt(2, quantitySold);
            pstmt.setInt(3, productID);
            pstmt.setInt(4, quantitySold);
            if (pstmt.executeUpdate() != 1) throw new SQLException("Stock changed or is insufficient. Refresh the cart and retry.");
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
        product.setStockVersion(rs.getLong("StockVersion"));
        product.setNoSold(rs.getInt("NoSold"));
        product.setPrice(rs.getBigDecimal("Price"));
        product.setCostPrice(rs.getBigDecimal("CostPrice"));
        product.setCategoryName(rs.getString("CategoryName"));

        return product;
    }

    private static void lockBarcode(Connection c,String barcode,int product) throws SQLException {
        try(Statement s=c.createStatement()) {
            s.executeUpdate("INSERT IGNORE INTO BusinessSettings (SettingKey,SettingValue) VALUES ('inventory.catalog.lock','1')");
            try(ResultSet r=s.executeQuery("SELECT SettingValue FROM BusinessSettings WHERE SettingKey='inventory.catalog.lock' FOR UPDATE")) {
                if(!r.next())throw new SQLException("Catalog lock unavailable.");
            }
        }
        String equivalent=barcode.matches("[0-9]{12}")?"0"+barcode:barcode.matches("0[0-9]{12}")?barcode.substring(1):barcode;
        try(PreparedStatement p=c.prepareStatement("SELECT ProductID FROM Product WHERE IsArchived=FALSE AND ProductID<>? AND (BarCode=? OR BarCode=?) FOR UPDATE")) {
            p.setInt(1,product);p.setString(2,barcode);p.setString(3,equivalent);
            try(ResultSet r=p.executeQuery()){if(r.next())throw new SQLException("This barcode already belongs to an active product.");}
        }
    }

    public static void validate(Product product) {
        if (product == null || product.getProductName() == null || product.getProductName().isBlank()
                || product.getProductName().length() > 50 || product.getBarCode() == null
                || product.getBarCode().isBlank() || product.getBarCode().length() > 128
                || product.getQuantity() < 0)
            throw new IllegalArgumentException("Enter a product name (up to 50 characters), barcode (up to 128 characters), and non-negative whole quantity.");
        for (java.math.BigDecimal price : new java.math.BigDecimal[]{product.getPrice(), product.getCostPrice()}) {
            if (price == null || price.signum() < 0 || price.compareTo(new java.math.BigDecimal("99999999.99")) > 0
                    || price.stripTrailingZeros().scale() > 2)
                throw new IllegalArgumentException("Prices must be non-negative amounts with at most two decimal places.");
        }
        if (BarcodeUtil.generateBarcode(product.getBarCode()) == null)
            throw new IllegalArgumentException("Enter a valid Code 128 barcode.");
    }
}

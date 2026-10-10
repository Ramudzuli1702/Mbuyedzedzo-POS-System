package com.pos.services;

import com.pos.models.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Round-trips the product catalogue through a plain .xlsx file — export for
 * a backup/handoff copy or bulk editing in Excel, import to bring products
 * back in (e.g. a new shop's starting stock list) without re-typing them
 * one at a time through Add Product.
 *
 * The column order here IS the expected import format — export once, edit
 * in Excel, re-import, and it lines up with no extra mapping step.
 */
public class InventoryExcelService {

    private static final String[] HEADERS = {
        "Product Name", "Barcode", "Quantity", "Purchase Price", "Selling Price"
    };

    private final ProductService productService;
    private final CategoryService categoryService;

    public InventoryExcelService() {
        this.productService = new ProductService();
        this.categoryService = new CategoryService();
    }

    // ── Export ──────────────────────────────────────────────────────────────

    public void exportToExcel(List<Product> products, File target) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Inventory");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Product p : products) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(p.getProductName());
                row.createCell(1).setCellValue(p.getBarCode());
                row.createCell(2).setCellValue(p.getQuantity());
                row.createCell(3).setCellValue(p.getCostPrice() == null ? 0.0 : p.getCostPrice().doubleValue());
                row.createCell(4).setCellValue(p.getPrice() == null ? 0.0 : p.getPrice().doubleValue());
            }

            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(target)) {
                wb.write(fos);
            }
        }
    }

    // ── Import ──────────────────────────────────────────────────────────────

    public record ImportRow(int rowNumber, String productName, String barcode) {}

    public record ImportResult(
        List<ImportRow> added,
        List<ImportRow> duplicates,
        List<String> invalidRows // human-readable "Row N: reason"
    ) {}

    /**
     * Reads products from {@code source}, skips anything whose barcode
     * already exists (in the database, or earlier in this same file), and
     * inserts the rest. New products land in the same silent default
     * category every other no-category entry point uses (phone-added
     * products, the old debug-category removal) — Category was hidden from
     * the UI deliberately, so this keeps that consistent instead of
     * reintroducing it just for imported rows.
     */
    public ImportResult importFromExcel(File source, int importingStaffID) throws Exception {
        List<ImportRow> added = new ArrayList<>();
        List<ImportRow> duplicates = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        Set<String> existingBarcodes = new LinkedHashSet<>();
        for (Product p : productService.getAllProducts()) {
            if (p.getBarCode() != null && !p.getBarCode().isBlank()) {
                existingBarcodes.add(p.getBarCode().trim());
            }
        }

        int defaultCategoryId = categoryService.getOrCreateDefaultCategoryId();

        try (FileInputStream fis = new FileInputStream(source);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row, fmt)) continue;

                int rowNumber = r + 1; // 1-based, matching what Excel shows

                String name = fmt.formatCellValue(row.getCell(0)).trim();
                String barcode = fmt.formatCellValue(row.getCell(1)).trim();
                String qtyText = fmt.formatCellValue(row.getCell(2)).trim();
                String costText = fmt.formatCellValue(row.getCell(3)).trim();
                String priceText = fmt.formatCellValue(row.getCell(4)).trim();

                if (name.isEmpty()) {
                    invalid.add("Row " + rowNumber + ": missing product name.");
                    continue;
                }
                if (barcode.isEmpty()) {
                    invalid.add("Row " + rowNumber + " (" + name + "): missing barcode.");
                    continue;
                }

                Integer quantity = parseInt(qtyText);
                BigDecimal costPrice = parseMoney(costText);
                BigDecimal price = parseMoney(priceText);

                if (quantity == null) {
                    invalid.add("Row " + rowNumber + " (" + name + "): \"" + qtyText + "\" is not a valid quantity.");
                    continue;
                }
                if (price == null) {
                    invalid.add("Row " + rowNumber + " (" + name + "): \"" + priceText + "\" is not a valid selling price.");
                    continue;
                }
                if (costPrice == null) costPrice = BigDecimal.ZERO;

                ImportRow importRow = new ImportRow(rowNumber, name, barcode);

                if (existingBarcodes.contains(barcode)) {
                    duplicates.add(importRow);
                    continue;
                }

                Product product = new Product();
                product.setProductName(name);
                product.setBarCode(barcode);
                product.setQuantity(quantity);
                product.setCostPrice(costPrice);
                product.setPrice(price);
                product.setCategoryID(defaultCategoryId);
                product.setStaffID(importingStaffID);

                if (productService.addProduct(product)) {
                    added.add(importRow);
                    existingBarcodes.add(barcode); // guards against a repeated barcode later in the same file
                } else {
                    invalid.add("Row " + rowNumber + " (" + name + "): could not be saved — check the log.");
                }
            }
        }

        return new ImportResult(added, duplicates, invalid);
    }

    private boolean isRowBlank(Row row, DataFormatter fmt) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (!fmt.formatCellValue(row.getCell(i)).isBlank()) return false;
        }
        return true;
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return (int) Double.parseDouble(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace("R", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

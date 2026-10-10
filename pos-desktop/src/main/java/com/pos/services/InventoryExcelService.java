package com.pos.services;

import com.pos.models.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Round-trips the product catalogue through a plain .xlsx file — export for
 * a backup/handoff copy or bulk editing in Excel, import to bring products
 * back in (e.g. a new shop's starting stock list) without re-typing them
 * one at a time through Add Product.
 *
 * The column order here IS the expected import format — export once, edit
 * in Excel, re-import, and it lines up with no extra mapping step.
 *
 * Import is deliberately two-phase — {@link #analyze} never writes to the
 * database, it only reports what *would* happen (including any category
 * names in the sheet that don't exist yet); the caller shows that plan to
 * the user, and only {@link #commit} actually inserts anything, after the
 * user has confirmed it's correct and said what to do about new categories.
 */
public class InventoryExcelService {

    private static final String[] HEADERS = {
        "Product Name", "Category", "Barcode", "Quantity", "Purchase Price", "Selling Price"
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
                row.createCell(1).setCellValue(p.getCategoryName() == null ? "" : p.getCategoryName());
                row.createCell(2).setCellValue(p.getBarCode());
                row.createCell(3).setCellValue(p.getQuantity());
                row.createCell(4).setCellValue(p.getCostPrice() == null ? 0.0 : p.getCostPrice().doubleValue());
                row.createCell(5).setCellValue(p.getPrice() == null ? 0.0 : p.getPrice().doubleValue());
            }

            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(target)) {
                wb.write(fos);
            }
        }
    }

    // ── Import: analyze (read-only) ──────────────────────────────────────────

    public record ImportRow(int rowNumber, String productName, String barcode) {}

    /** A row that passed validation and isn't a duplicate — still needs its
     *  category resolved at commit time (depends on what the user decides
     *  about {@code newCategoryNames}). */
    public record PendingRow(
        int rowNumber, String productName, String barcode,
        String categoryName, // null/blank = "no category specified, use default"
        int quantity, BigDecimal costPrice, BigDecimal price
    ) {}

    public record ImportPlan(
        List<PendingRow> toAdd,
        List<ImportRow> duplicates,
        List<String> invalidRows, // human-readable "Row N: reason"
        Set<String> newCategoryNames // non-blank categories in the sheet that don't exist yet
    ) {}

    public record ImportResult(
        List<ImportRow> added,
        List<ImportRow> duplicates,
        List<String> invalidRows
    ) {}

    /**
     * Reads {@code source} and classifies every row — valid-to-add,
     * duplicate barcode (already in the database or repeated in this same
     * file), or invalid — without writing anything. Also collects every
     * distinct category name mentioned that isn't already one of the real
     * categories, so the caller can ask "add these, or was it a typo?"
     * before anything is actually created or inserted.
     */
    public ImportPlan analyze(File source) throws Exception {
        List<PendingRow> toAdd = new ArrayList<>();
        List<ImportRow> duplicates = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        Set<String> newCategoryNames = new LinkedHashSet<>();

        Set<String> existingBarcodes = new LinkedHashSet<>();
        for (Product p : productService.getAllProducts()) {
            if (p.getBarCode() != null && !p.getBarCode().isBlank()) {
                existingBarcodes.add(p.getBarCode().trim());
            }
        }

        Set<String> existingCategories = new LinkedHashSet<>();
        for (String c : categoryService.getAllCategories()) {
            existingCategories.add(c.trim().toLowerCase());
        }

        try (FileInputStream fis = new FileInputStream(source);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row, fmt)) continue;

                int rowNumber = r + 1; // 1-based, matching what Excel shows

                String name = fmt.formatCellValue(row.getCell(0)).trim();
                String category = fmt.formatCellValue(row.getCell(1)).trim();
                String barcode = fmt.formatCellValue(row.getCell(2)).trim();
                String qtyText = fmt.formatCellValue(row.getCell(3)).trim();
                String costText = fmt.formatCellValue(row.getCell(4)).trim();
                String priceText = fmt.formatCellValue(row.getCell(5)).trim();

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

                if (existingBarcodes.contains(barcode)) {
                    duplicates.add(new ImportRow(rowNumber, name, barcode));
                    continue;
                }

                if (!category.isBlank() && !existingCategories.contains(category.toLowerCase())) {
                    newCategoryNames.add(category);
                }

                toAdd.add(new PendingRow(rowNumber, name, barcode, category, quantity, costPrice, price));
                existingBarcodes.add(barcode); // guards against a repeated barcode later in the same file
            }
        }

        return new ImportPlan(toAdd, duplicates, invalid, newCategoryNames);
    }

    // ── Import: commit (writes) ──────────────────────────────────────────────

    /**
     * Actually creates {@code approvedNewCategories} and inserts every row
     * in {@code plan.toAdd()}. A row whose category wasn't approved (or had
     * none specified) falls back to the same silent default category every
     * other no-category entry point already uses.
     */
    public ImportResult commit(ImportPlan plan, Set<String> approvedNewCategories, int importingStaffID) {
        List<ImportRow> added = new ArrayList<>();
        List<String> invalid = new ArrayList<>(plan.invalidRows());

        Map<String, Integer> categoryIdByName = new HashMap<>();
        for (var pair : categoryService.getAllCategoriesWithId()) {
            categoryIdByName.put(pair.getValue().trim().toLowerCase(), pair.getKey());
        }
        for (String name : approvedNewCategories) {
            String key = name.trim().toLowerCase();
            if (categoryIdByName.containsKey(key)) continue; // already created (case-insensitive duplicate)
            int id = categoryService.addCategoryReturningId(name.trim());
            if (id > 0) categoryIdByName.put(key, id);
        }

        int defaultCategoryId = categoryService.getOrCreateDefaultCategoryId();

        for (PendingRow row : plan.toAdd()) {
            Integer categoryId = (row.categoryName() != null && !row.categoryName().isBlank())
                    ? categoryIdByName.get(row.categoryName().trim().toLowerCase())
                    : null;

            Product product = new Product();
            product.setProductName(row.productName());
            product.setBarCode(row.barcode());
            product.setQuantity(row.quantity());
            product.setCostPrice(row.costPrice());
            product.setPrice(row.price());
            product.setCategoryID(categoryId != null ? categoryId : defaultCategoryId);
            product.setStaffID(importingStaffID);

            if (productService.addProduct(product)) {
                added.add(new ImportRow(row.rowNumber(), row.productName(), row.barcode()));
            } else {
                invalid.add("Row " + row.rowNumber() + " (" + row.productName() + "): could not be saved — check the log.");
            }
        }

        return new ImportResult(added, plan.duplicates(), invalid);
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
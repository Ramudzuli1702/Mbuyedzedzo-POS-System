package com.pos.services;

import com.pos.services.StocktakeService.Count;
import com.pos.services.StocktakeService.CountSheet;
import com.pos.services.StocktakeService.Line;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Snapshot-bound count sheets. Reading Excel never writes to inventory or drafts. */
public final class StocktakeExcelService {
    private static final String[] HEADERS = {"Stocktake ID", "Product ID", "Product Name", "Category",
            "Barcode", "Loaded Stock", "Physical Count", "Variance", "Status"};
    public record ImportPlan(List<Count> counts, List<String> errors, int productRows) {
        public boolean valid() { return errors.isEmpty(); }
    }

    public void exportToExcel(CountSheet snapshot, File target) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stocktake");
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            CellStyle text = workbook.createCellStyle(); text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            CellStyle countStyle = workbook.createCellStyle();
            countStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            countStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) { Cell cell = header.createCell(i); cell.setCellValue(HEADERS[i]); cell.setCellStyle(headerStyle); }
            int rowNumber = 1;
            for (Line line : snapshot.items()) {
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(snapshot.id()); row.createCell(1).setCellValue(line.productId());
                row.createCell(2).setCellValue(line.name()); row.createCell(3).setCellValue(Objects.toString(line.category(), ""));
                Cell barcode = row.createCell(4); barcode.setCellStyle(text); barcode.setCellValue(Objects.toString(line.barcode(), ""));
                row.createCell(5).setCellValue(line.expected());
                Cell count = row.createCell(6); count.setCellStyle(countStyle);
                if (line.counted() != null) { count.setCellValue(line.counted()); row.createCell(7).setCellValue((long) line.counted() - line.expected()); }
                row.createCell(8).setCellValue(snapshot.status());
            }
            sheet.createFreezePane(3, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNumber - 1), 0, HEADERS.length - 1));
            int[] widths = {39, 13, 34, 22, 25, 16, 18, 14, 16};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            if (rowNumber > 1) {
                DataValidationHelper helper = sheet.getDataValidationHelper();
                DataValidation validation = helper.createValidation(helper.createIntegerConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN, "0", "1000000"),
                        new org.apache.poi.ss.util.CellRangeAddressList(1, rowNumber - 1, 6, 6));
                validation.setEmptyCellAllowed(true); validation.setShowErrorBox(true);
                validation.createErrorBox("Physical count", "Enter a whole quantity from 0 to 1,000,000, or leave blank for not counted.");
                sheet.addValidationData(validation);
            }
            Sheet instructions = workbook.createSheet("Instructions");
            String[] guidance = {"Stocktake: " + snapshot.id(), "Staff: " + snapshot.staffName(), "Status: " + snapshot.status(),
                    "Created: " + snapshot.createdAt(), "Notes: " + snapshot.notes(),
                    "Fill only Physical Count on the Stocktake sheet. Zero means counted with none remaining; blank means not counted.",
                    "Keep Stocktake ID, Product ID, Barcode and Loaded Stock unchanged. Rows may be sorted or omitted.",
                    "Import into the same draft. Import replaces saved draft counts; blank or omitted products become uncounted.",
                    "Import does not change live stock. Submit the draft, then an Admin or Manager approves under Approvals > Stocktakes.",
                    "Any stock movement since the snapshot requires a fresh count before approval.",
                    "Review notes: " + snapshot.reviewNotes()};
            for (int i = 0; i < guidance.length; i++) instructions.createRow(i).createCell(0).setCellValue(guidance[i]);
            instructions.setColumnWidth(0, 150 * 256);
            try (OutputStream out = new FileOutputStream(target)) { workbook.write(out); }
        }
    }

    public ImportPlan analyze(File source, CountSheet snapshot) throws IOException {
        List<Count> counts = new ArrayList<>(); List<String> errors = new ArrayList<>();
        Map<Integer, Line> products = new HashMap<>(); snapshot.items().forEach(line -> products.put(line.productId(), line));
        Set<Integer> seen = new HashSet<>(); int rows = 0;
        try (Workbook workbook = com.pos.utils.SpreadsheetFiles.open(source)) {
            Sheet sheet = workbook.getSheet("Stocktake");
            if (sheet == null) return new ImportPlan(List.of(), List.of("Use a stocktake export containing the Stocktake sheet."), 0);
            DataFormatter format = new DataFormatter(Locale.ROOT);
            Row header = sheet.getRow(0);
            for (int i = 0; i < HEADERS.length; i++)
                if (header == null || !HEADERS[i].equals(format.formatCellValue(header.getCell(i)).trim()))
                    return new ImportPlan(List.of(), List.of("The stocktake columns do not match. Export a fresh sheet and edit Physical Count only."), 0);
            if (sheet.getLastRowNum() > 100000) throw new IOException("Stocktake sheets may contain at most 100,000 product rows.");
            for (int n = 1; n <= sheet.getLastRowNum(); n++) {
                Row row = sheet.getRow(n); if (row == null) continue;
                boolean blank = true;
                for (int i = 0; i < HEADERS.length; i++) if (!format.formatCellValue(row.getCell(i)).isBlank()) { blank = false; break; }
                if (blank) continue;
                rows++;
                try {
                    if (!snapshot.id().equals(value(row, 0, format))) throw new IllegalArgumentException("belongs to another stocktake; open the matching draft or export this one.");
                    int id = integer(value(row, 1, format), 1, Integer.MAX_VALUE);
                    if (!seen.add(id)) throw new IllegalArgumentException("duplicate Product ID " + id + ".");
                    Line line = products.get(id);
                    if (line == null) throw new IllegalArgumentException("Product ID " + id + " is not in this inventory snapshot.");
                    if (!Objects.toString(line.barcode(), "").equals(value(row, 4, format))) throw new IllegalArgumentException("barcode changed; preserve the exported barcode as text.");
                    if (integer(value(row, 5, format), Integer.MIN_VALUE, Integer.MAX_VALUE) != line.expected()) throw new IllegalArgumentException("Loaded Stock changed; use the original exported value.");
                    String count = value(row, 6, format);
                    if (!count.isEmpty()) counts.add(new Count(id, integer(count, 0, 1000000)));
                } catch (IllegalArgumentException e) { errors.add("Row " + (n + 1) + ": " + e.getMessage()); }
            }
        }
        if (rows == 0) errors.add("The sheet contains no product rows.");
        return new ImportPlan(List.copyOf(counts), List.copyOf(errors), rows);
    }
    private String value(Row row, int column, DataFormatter format) {
        Cell cell = row.getCell(column);
        if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR))
            throw new IllegalArgumentException("formulas and error cells are not allowed in identifiers or quantities.");
        if (cell != null && cell.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        return format.formatCellValue(cell).trim();
    }
    private int integer(String value, int min, int max) {
        try { int result = new BigDecimal(value).intValueExact(); if (result < min || result > max) throw new ArithmeticException(); return result; }
        catch (NumberFormatException | ArithmeticException e) { throw new IllegalArgumentException("expected a whole quantity from " + min + " to " + max + "."); }
    }
}

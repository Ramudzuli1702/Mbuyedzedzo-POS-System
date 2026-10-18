package com.pos.services;

import com.pos.services.StocktakeService.*;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class StocktakeExcelServiceTest {
    @TempDir Path directory;
    private final StocktakeExcelService excel = new StocktakeExcelService();
    private final CountSheet snapshot = new CountSheet("4adf374a-bfd1-449a-bfaa-c33a5644eb09", 1, "Staff", "Draft", "Shelf check", "",
            "2026-10-18", null, null, List.of(
            new Line(1,"=Special Bread", "00001234-A", "Bakery",10,0L,0,10,0L),
            new Line(2,"Milk", "0123456789012", "Dairy",20,0L,null,20,0L)));
    private File file() throws Exception { File file=directory.resolve(UUID.randomUUID()+".xlsx").toFile();excel.exportToExcel(snapshot,file);return file; }
    private void edit(File file, Consumer<Sheet> edit) throws Exception {
        Workbook workbook;try(InputStream input=new FileInputStream(file)){workbook=WorkbookFactory.create(input);}
        try(workbook){edit.accept(workbook.getSheet("Stocktake"));try(OutputStream out=new FileOutputStream(file)){workbook.write(out);}}
    }
    @Test void roundTripPreservesZeroBlankAndLeadingZeroBarcodes() throws Exception {
        File file=file();var plan=excel.analyze(file,snapshot);assertTrue(plan.valid());assertEquals(2,plan.productRows());assertEquals(List.of(new Count(1,0)),plan.counts());
        try(InputStream in=new FileInputStream(file);Workbook workbook=WorkbookFactory.create(in)){
            Row row=workbook.getSheet("Stocktake").getRow(1);assertEquals(CellType.STRING,row.getCell(2).getCellType());
            assertEquals("00001234-A",row.getCell(4).getStringCellValue());assertEquals("@",row.getCell(4).getCellStyle().getDataFormatString());
            assertNotNull(workbook.getSheet("Instructions"));assertEquals(-10,row.getCell(7).getNumericCellValue());
        }
    }
    @Test void readsNumericCountsWithoutApplyingExcelDisplayRounding() throws Exception {
        File file=file();edit(file,s->{Cell count=s.getRow(2).getCell(6);count.setCellValue(12.5);CellStyle style=s.getWorkbook().createCellStyle();style.setDataFormat((short)1);count.setCellStyle(style);});
        assertFalse(excel.analyze(file,snapshot).valid());
        edit(file,s->s.getRow(2).getCell(6).setCellValue(12));var plan=excel.analyze(file,snapshot);assertTrue(plan.valid());assertEquals(new Count(2,12),plan.counts().get(1));
    }
    @Test void rejectsAnotherSnapshotEvenIfProductsMatch() throws Exception {
        File file=file();edit(file,s->s.getRow(1).getCell(0).setCellValue(UUID.randomUUID().toString()));
        assertTrue(excel.analyze(file,snapshot).errors().get(0).contains("another stocktake"));
    }
    @Test void rejectsDuplicateAndUnknownProductIds() throws Exception {
        File file=file();edit(file,s->s.getRow(2).getCell(1).setCellValue(1));assertTrue(excel.analyze(file,snapshot).errors().get(0).contains("duplicate"));
        edit(file,s->s.getRow(2).getCell(1).setCellValue(999));assertTrue(excel.analyze(file,snapshot).errors().get(0).contains("not in this inventory"));
    }
    @Test void rejectsChangedBaselineAndCorruptedBarcode() throws Exception {
        File file=file();edit(file,s->s.getRow(1).getCell(5).setCellValue(99));assertFalse(excel.analyze(file,snapshot).valid());
        edit(file,s->{s.getRow(1).getCell(5).setCellValue(10);s.getRow(1).getCell(4).setCellValue("1234-A");});assertFalse(excel.analyze(file,snapshot).valid());
    }
    @Test void rejectsFormulasNegativeCountsAndExcessiveQuantities() throws Exception {
        File file=file();edit(file,s->s.getRow(1).getCell(6).setCellFormula("1+1"));assertFalse(excel.analyze(file,snapshot).valid());
        for(int number:List.of(-1,1000001)){edit(file,s->{s.getRow(1).getCell(6).setBlank();s.getRow(1).getCell(6).setCellValue(number);});assertFalse(excel.analyze(file,snapshot).valid());}
    }
    @Test void omittedRowsBecomeUncountedAndBlankCountsStayBlank() throws Exception {
        File file=file();edit(file,s->{s.removeRow(s.getRow(1));s.getRow(2).getCell(6).setCellValue(7);});
        var plan=excel.analyze(file,snapshot);assertTrue(plan.valid());assertEquals(List.of(new Count(2,7)),plan.counts());
        edit(file,s->s.getRow(2).getCell(6).setBlank());assertTrue(excel.analyze(file,snapshot).counts().isEmpty());
    }
    @Test void rejectsWrongTemplateAndHeader() throws Exception {
        File file=file();edit(file,s->s.getRow(0).getCell(0).setCellValue("Inventory"));assertFalse(excel.analyze(file,snapshot).valid());
    }
}

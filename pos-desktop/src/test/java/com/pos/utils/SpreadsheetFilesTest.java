package com.pos.utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class SpreadsheetFilesTest {
    @TempDir Path directory;
    @Test void rejectsOversizedFileBeforeWorkbookParsing() throws Exception {
        Path file=directory.resolve("large.xlsx");
        try(RandomAccessFile data=new RandomAccessFile(file.toFile(),"rw")){data.setLength(10L*1024*1024+1);}
        assertThrows(IOException.class,()->SpreadsheetFiles.open(file.toFile()));
    }
    @Test void rejectsSmallZipWithOversizedExpandedEntry() throws Exception {
        Path file=directory.resolve("compressed.xlsx");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));byte[] zeros=new byte[1024*1024];
            for(int n=0;n<17;n++)zip.write(zeros);zip.closeEntry();
        }
        assertTrue(Files.size(file)<1024*1024);
        assertThrows(IOException.class,()->SpreadsheetFiles.open(file.toFile()));
    }
}

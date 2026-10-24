package com.pos.utils;

import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Bound input size and actual decompressed OOXML bytes before constructing a workbook model. */
public final class SpreadsheetFiles {
    private SpreadsheetFiles() {}
    private static final long MAX_FILE=10L*1024*1024,MAX_EXPANDED=64L*1024*1024,MAX_ENTRY=16L*1024*1024;
    public static Workbook open(File file) throws IOException {
        if(file==null || !file.isFile() || file.length()>MAX_FILE)throw new IOException("Choose an Excel sheet no larger than 10 MB.");
        // Freeze bytes before validation to prevent replacement between validation and parsing.
        byte[] data;
        try(InputStream input=new FileInputStream(file)) {
            data=input.readNBytes((int)MAX_FILE+1);
            if(data.length>MAX_FILE)throw new IOException("Excel sheet exceeds 10 MB.");
        }
        if(data.length>=2 && data[0]=='P' && data[1]=='K') {
            long expanded=0;int entries=0;Set<String> names=new HashSet<>();byte[] buffer=new byte[8192];
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(++entries>4096 || !names.add(entry.getName()))throw new IOException("Excel file contains too many or duplicate archive entries.");
                    long size=0;int read;
                    while((read=zip.read(buffer))!=-1) {
                        size+=read;expanded+=read;
                        if(size>MAX_ENTRY || expanded>MAX_EXPANDED)throw new IOException("Excel sheet expands beyond the safe import limit. Split it into smaller sheets.");
                    }
                }
            }
        }
        return WorkbookFactory.create(new ByteArrayInputStream(data));
    }
}

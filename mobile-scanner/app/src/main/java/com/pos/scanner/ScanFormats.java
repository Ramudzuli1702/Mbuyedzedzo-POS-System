package com.pos.scanner;

import com.google.zxing.BarcodeFormat;
import java.util.*;

public final class ScanFormats {
    private ScanFormats(){}
    public static Collection<BarcodeFormat> forMode(String mode) {
        if("connect".equals(mode))return EnumSet.of(BarcodeFormat.QR_CODE);
        return EnumSet.of(BarcodeFormat.CODE_128,BarcodeFormat.CODE_39,BarcodeFormat.CODE_93,
                BarcodeFormat.EAN_13,BarcodeFormat.EAN_8,BarcodeFormat.UPC_A,BarcodeFormat.UPC_E,
                BarcodeFormat.ITF,BarcodeFormat.CODABAR);
    }
    public static boolean sameProduct(String a,String b) {
        if(a==null||b==null)return false;
        if(a.equals(b))return true;
        return a.matches("\\d{12}")&&b.equals("0"+a)||b.matches("\\d{12}")&&a.equals("0"+b);
    }
}

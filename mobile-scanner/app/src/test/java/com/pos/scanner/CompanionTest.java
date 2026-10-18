package com.pos.scanner;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class CompanionTest {
    private static final String RECEIPT="SALES RECEIPT\nDate:      2026-10-18 10:20:30\nCashier:   Jane\nCustomer:  Sam\nEmail:     sam@example.test\n----------------------------------------\nItem                      Qty     Amount\n----------------------------------------\nA long product name that\nwraps onto a second line\n  2 x 12.50                       R25.00\nBread\n  1 x 8,50                         R8,50\n----------------------------------------\nSubtotal                         R33.50\nDiscount (TEST)                   -R3.50\n========================================\nTOTAL                            R30.00\n========================================\nPayment:   Cash\nTendered                         R50.00\nChange                           R20.00\n";

    @Test public void parsesCurrentWrappedReceiptAndNetTotal(){Receipt r=Receipt.fromString(RECEIPT);assertEquals(30,r.total,0.001);assertEquals("Jane",r.cashier);assertEquals(2,r.items.size());assertEquals("A long product name that wraps onto a second line",r.items.get(0).name);assertEquals(2,r.items.get(0).quantity);assertEquals(12.5,r.items.get(0).price,0.001);assertEquals(25,r.items.get(0).subtotal,0.001);assertEquals(8.5,r.items.get(1).subtotal,0.001);assertEquals(RECEIPT,r.fullReceiptText);}
    @Test public void retailReceiptDefaultsToWalkIn(){Receipt r=Receipt.fromString("Date: 2026-10-18\nCashier: Jane\nNET TOTAL            R1,234.50\n");assertEquals("Walk-in",r.name);assertEquals(1234.5,r.total,0.001);}
    @Test public void parsesLegacyTableAndStableReceiptIdentity(){String text="Item                 Qty Amount\nApple                2    R 10.00\nTOTAL: R 10.00\n";Receipt a=Receipt.fromString(text);assertEquals(1,a.items.size());assertEquals(5,a.items.get(0).price,0.001);assertEquals(a.getReceiptId(),Receipt.fromString(text).getReceiptId());}
    @Test public void legacyFramingPreservesReceiptWhitespaceAndInterleavedJson(){IncomingFrames frames=new IncomingFrames();StringBuilder raw=new StringBuilder();for(String line:RECEIPT.split("\n")){assertNull(frames.accept(line).receipt);raw.append(line).append('\n');}assertEquals("{\"type\":\"connected\"}",frames.accept("{\"type\":\"connected\"}").json);assertEquals(raw.toString(),frames.accept("===END_RECEIPT===").receipt);assertNull(frames.accept("===END_RECEIPT===").receipt);}
    @Test public void receiptMarkerInsideJsonIsNotAFrameTerminator(){String json="{\"type\":\"receipt\",\"text\":\"===END_RECEIPT===\\n\"}";assertEquals(json,new IncomingFrames().accept(json).json);}
    @Test public void legacyProductNamesStartingWithBracketsArePreserved(){IncomingFrames frames=new IncomingFrames();frames.accept("[Bulk] Rice");frames.accept("{Special} Bread");assertEquals("[Bulk] Rice\n{Special} Bread\n",frames.accept("===END_RECEIPT===").receipt);}
    @Test public void productAndPairingDecoderFormatsAreSeparate(){assertTrue(ScanFormats.forMode(null).contains(BarcodeFormat.CODE_128));assertFalse(ScanFormats.forMode("stocktake").contains(BarcodeFormat.QR_CODE));assertEquals(Collections.singleton(BarcodeFormat.QR_CODE),new HashSet<>(ScanFormats.forMode("connect")));}
    @Test public void decodesDesktopCode128WithoutDroppingLeadingZeroes() throws Exception {String code="00001234-A";assertEquals(code,decode(code,BarcodeFormat.CODE_128).getText());}
    @Test public void eanAndUpcEquivalentCodesMatchWithoutStrippingOtherZeroes() throws Exception {Result result=decode("0123456789012",BarcodeFormat.EAN_13);assertTrue(ScanFormats.sameProduct("0123456789012",result.getText()));assertFalse(ScanFormats.sameProduct("00001234","1234"));}
    private Result decode(String text,BarcodeFormat format) throws Exception {
        BitMatrix matrix=new MultiFormatWriter().encode(text,format,640,200);
        LuminanceSource source=new LuminanceSource(matrix.getWidth(),matrix.getHeight()){
            public byte[] getRow(int y,byte[] row){if(row==null||row.length<getWidth())row=new byte[getWidth()];for(int x=0;x<getWidth();x++)row[x]=(byte)(matrix.get(x,y)?0:255);return row;}
            public byte[] getMatrix(){byte[] all=new byte[getWidth()*getHeight()];for(int y=0;y<getHeight();y++)System.arraycopy(getRow(y,null),0,all,y*getWidth(),getWidth());return all;}
        };
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,ScanFormats.forMode(null));
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),hints);
    }
    @Test public void challengeProofMatchesDesktopPasswordStorageAndWipesPassword() throws Exception {
        byte[] salt="0123456789abcdef".getBytes(StandardCharsets.UTF_8);char[] password="Staff 'pass é!9".toCharArray();
        byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password,salt,210000,256)).getEncoded();
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));byte[] expected=mac.doFinal("mbuyedzedzo-stocktake:nonce|id".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected,PasswordProof.create(password,"PBKDF2-HMAC-SHA256",210000,salt,"nonce","id"));assertArrayEquals(new char[password.length],password);
    }
    @Test public void olderAndroidPbkdf2FallbackMatchesJdk() throws Exception {char[] password="unicode é!9".toCharArray();byte[] salt="0123456789abcdef".getBytes(StandardCharsets.UTF_8);assertArrayEquals(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password,salt,1000,256)).getEncoded(),PasswordProof.deriveLegacyAndroid(password,salt,1000));}
    @Test public void countValidationDistinguishesZeroFromUncounted(){assertEquals(Integer.valueOf(0),StocktakeDraft.parseCount("0"));assertNull(StocktakeDraft.parseCount(""));assertNull(StocktakeDraft.parseCount("1.5"));assertNull(StocktakeDraft.parseCount("-1"));assertNull(StocktakeDraft.parseCount("1000001"));}
    @Test public void localCountsRestoreOnlyToTheSameUnsubmittedSnapshot(){StocktakeDraft local=new StocktakeDraft();local.id="one";local.staffId=1;StocktakeDraft.Item item=new StocktakeDraft.Item();item.productId=7;item.counted=0;local.items.add(item);StocktakeDraft remote=new StocktakeDraft();remote.id="one";remote.staffId=1;StocktakeDraft.Item copy=new StocktakeDraft.Item();copy.productId=7;remote.items.add(copy);remote.restoreLocalCounts(local);assertEquals(Integer.valueOf(0),copy.counted);remote.status="Pending";copy.counted=4;remote.restoreLocalCounts(local);assertEquals(Integer.valueOf(4),copy.counted);}
}

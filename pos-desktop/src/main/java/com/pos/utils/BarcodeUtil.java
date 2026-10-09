package com.pos.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Generates a scannable 1D barcode image for a product's barcode value —
 * what a real price-gun/receipt-printer label actually looks like, unlike
 * the QR code this used to generate. CODE_128 is the format: it encodes any
 * printable text (not just digits, unlike EAN-13/UPC-A), so it works
 * whatever a shop's existing barcode numbering looks like.
 */
public class BarcodeUtil {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 120;

    /** Returns a base64-encoded PNG of the barcode, or null if the value can't be encoded. */
    public static String generateBarcode(String data) {
        if (data == null || data.isBlank()) return null;
        try {
            Code128Writer writer = new Code128Writer();
            BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.CODE_128, WIDTH, HEIGHT);

            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "PNG", baos);

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Image getBarcodeImage(String base64Barcode) {
        if (base64Barcode == null || base64Barcode.isBlank()) return null;
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Barcode);
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage bufferedImage = ImageIO.read(bais);
            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

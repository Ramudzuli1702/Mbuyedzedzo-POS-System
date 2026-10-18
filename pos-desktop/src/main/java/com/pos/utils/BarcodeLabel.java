package com.pos.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.oned.Code128Writer;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** High-resolution PNG with a scannable barcode and its readable value. */
public final class BarcodeLabel {
    private BarcodeLabel() {}

    public static void save(String code, Path destination) throws IOException {
        if(code==null || code.isBlank()) throw new IllegalArgumentException("Enter a barcode first.");
        BufferedImage bars=MatrixToImageWriter.toBufferedImage(
            new Code128Writer().encode(code, BarcodeFormat.CODE_128,960,240));
        BufferedImage label=new BufferedImage(bars.getWidth(),310,BufferedImage.TYPE_INT_RGB);
        var graphics=label.createGraphics();
        try {
            graphics.setColor(Color.WHITE);graphics.fillRect(0,0,label.getWidth(),label.getHeight());
            graphics.drawImage(bars,0,0,null);
            graphics.setColor(Color.BLACK);graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,32));
            double scale=Math.min(1,(label.getWidth()-40.0)/graphics.getFontMetrics().stringWidth(code));
            graphics.translate(label.getWidth()/2.0,285);graphics.scale(scale,1);
            graphics.drawString(code,-graphics.getFontMetrics().stringWidth(code)/2,0);
        } finally {graphics.dispose();}
        if(!ImageIO.write(label,"PNG",destination.toFile())) throw new IOException("PNG writer unavailable.");
    }
}

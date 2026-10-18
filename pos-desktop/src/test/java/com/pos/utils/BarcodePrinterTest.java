package com.pos.utils;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarcodePrinterTest {
    @Test void printsOneScannablePageWithoutChangingTheGraphicsTransform() throws Exception {
        BufferedImage output = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 300, 150);
            Paper paper = new Paper(); paper.setSize(300, 150); paper.setImageableArea(10, 10, 280, 130);
            PageFormat format = new PageFormat(); format.setPaper(paper);
            var transform = graphics.getTransform();
            Printable label = BarcodePrinter.page("00001234-A");
            assertEquals(Printable.PAGE_EXISTS, label.print(graphics, format, 0));
            assertEquals(transform, graphics.getTransform());
            assertEquals(Printable.NO_SUCH_PAGE, label.print(graphics, format, 1));
            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(output.getSubimage(10,10,240,60))));
            assertEquals("00001234-A", new MultiFormatReader().decode(bitmap).getText());
        } finally { graphics.dispose(); }
    }

    @Test void rejectsEmptyAndUnsupportedBarcodes() {
        assertThrows(IllegalArgumentException.class, () -> BarcodePrinter.page(""));
        assertThrows(IllegalArgumentException.class, () -> BarcodePrinter.page("\u2603"));
    }
}

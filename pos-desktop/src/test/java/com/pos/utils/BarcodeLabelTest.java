package com.pos.utils;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BarcodeLabelTest {
    @TempDir Path directory;
    @Test void savedPngHasScannableBarsAndAReadableValue() throws Exception {
        Path file=directory.resolve("barcode.png");
        BarcodeLabel.save("00001234-A",file);
        var image=ImageIO.read(file.toFile());
        assertEquals(310,image.getHeight());
        var bitmap=new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image.getSubimage(0,0,image.getWidth(),240))));
        assertEquals("00001234-A",new MultiFormatReader().decode(bitmap).getText());
        boolean text=false;
        for(int x=0;x<image.getWidth();x++)for(int y=250;y<310;y++)if((image.getRGB(x,y)&0xffffff)==0)text=true;
        assertTrue(text,"The saved label includes the barcode value below the bars");
    }
    @Test void invalidBarcodesDoNotCreateFiles() {
        Path file=directory.resolve("invalid.png");
        assertThrows(IllegalArgumentException.class,()->BarcodeLabel.save("",file));
        assertFalse(java.nio.file.Files.exists(file));
    }
}

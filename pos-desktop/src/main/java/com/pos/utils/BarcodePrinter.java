package com.pos.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.oned.Code128Writer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Prints a barcode without passing live JavaFX controls to the printer renderer. */
public final class BarcodePrinter {
    private BarcodePrinter() {}

    public static void print(String barcode, Consumer<String> finished) {
        // Native printer selection belongs on the AWT event thread. Spooling runs
        // separately so a slow/offline printer cannot block the till's UI thread.
        EventQueue.invokeLater(() -> {
            try {
                Printable page = page(barcode);
                if (PrinterJob.lookupPrintServices().length == 0) {
                    done(finished, "No printer was found. Connect a printer and try again.");
                    return;
                }
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setJobName("Mbuyedzedzo barcode");
                job.setPrintable(page);
                if (!job.printDialog()) { done(finished, null); return; }
                Thread worker = new Thread(() -> {
                    try { job.print(); done(finished, null); }
                    catch (PrinterException | RuntimeException | LinkageError ex) {
                        java.util.logging.Logger.getLogger(BarcodePrinter.class.getName()).log(java.util.logging.Level.WARNING, "Barcode print failed", ex);
                        job.cancel();
                        done(finished, "The barcode could not be printed. Check the printer connection and paper, then try again.");
                    }
                }, "barcode-printer");
                worker.setDaemon(true);
                worker.start();
            } catch (RuntimeException | LinkageError ex) {
                java.util.logging.Logger.getLogger(BarcodePrinter.class.getName()).log(java.util.logging.Level.WARNING, "Printer setup failed", ex);
                done(finished, "The printer could not be opened. Check your Windows printer settings and try again.");
            }
        });
    }

    private static void done(Consumer<String> finished, String message) {
        Platform.runLater(() -> finished.accept(message));
    }

    static Printable page(String barcode) {
        if (barcode == null || barcode.isBlank()) throw new IllegalArgumentException("Enter a barcode first.");
        BufferedImage image = MatrixToImageWriter.toBufferedImage(
                new Code128Writer().encode(barcode, BarcodeFormat.CODE_128, 640, 160));
        return (graphics, format, index) -> {
            if (index != 0) return Printable.NO_SUCH_PAGE;
            double width = format.getImageableWidth(), height = format.getImageableHeight();
            if (width <= 0 || height <= 0) throw new PrinterException("The selected paper has no printable area.");
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(format.getImageableX(), format.getImageableY());
                // Points: 240 x 80 fits a label and never enlarges on A4 paper.
                double scale = Math.min(1, Math.min(width / 240, height / 80));
                g.scale(scale, scale);
                g.setColor(Color.WHITE); g.fillRect(0, 0, 240, 80);
                g.drawImage(image, 0, 0, 240, 60, null);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10)); g.setColor(Color.BLACK);
                double textScale = Math.min(1, 236.0 / Math.max(1, g.getFontMetrics().stringWidth(barcode)));
                g.translate(120, 74); g.scale(textScale, 1);
                g.drawString(barcode, -g.getFontMetrics().stringWidth(barcode) / 2, 0);
            } finally { g.dispose(); }
            return Printable.PAGE_EXISTS;
        };
    }
}

package com.pos.utils;

import com.pos.models.Customer;
import com.pos.models.User;
import com.pos.services.CustomerService;
import com.pos.services.CustomerService.Purchase;
import com.pos.services.CustomerService.Sale;
import com.pos.services.ExchangeReturnService;
import com.pos.services.SettingsService;
import com.pos.services.TransactionService.PaymentInfo;
import com.pos.views.SalesView.CartItem;
import javafx.collections.ObservableList;
import javafx.print.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ReceiptGenerator — plain-text receipts formatted for a 40-column thermal
 * printer. Two entry points:
 *
 *   generateReceipt(...)     — live checkout receipt (SalesView)
 *   generateSaleReceipt(...) — reprint of a past sale (CustomerView),
 *                              showing net quantities after returns/exchanges
 */
public class ReceiptGenerator {

    private static final SettingsService settings = new SettingsService();

    private static final int W = 40;                       // receipt column width
    private static final String RULE   = "-".repeat(W) + "\n";
    private static final String DRULE  = "=".repeat(W) + "\n";

    private static final DateTimeFormatter FULL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Live checkout receipt ────────────────────────────────────────────────

    public static String generateReceipt(Customer customer,
                                         ObservableList<CartItem> items,
                                         User cashier,
                                         PaymentInfo paymentInfo) {
        return generateReceipt(customer, items, cashier, paymentInfo, null, BigDecimal.ZERO);
    }

    public static String generateReceipt(Customer customer,
                                         ObservableList<CartItem> items,
                                         User cashier,
                                         PaymentInfo paymentInfo,
                                         String promoCode,
                                         BigDecimal discountAmount) {
        StringBuilder r = new StringBuilder();
        header(r, "SALES RECEIPT");

        meta(r, "Date",     LocalDateTime.now().format(FULL_FMT));
        meta(r, "Cashier",  cashier.getFullNames());
        meta(r, "Customer", customer.getFullNames());
        if (notBlank(customer.getEmailAddress())) meta(r, "Email", customer.getEmailAddress());
        r.append(RULE);

        r.append(String.format("%-22s %6s %10s%n", "Item", "Qty", "Amount"));
        r.append(RULE);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            itemLines(r, item.getProductName(), item.getQuantity(),
                      item.getPrice(), item.getSubtotal());
            subtotal = subtotal.add(item.getSubtotal());
        }

        r.append(RULE);
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        moneyLine(r, "Subtotal", subtotal);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            moneyLine(r, "Discount" + (notBlank(promoCode) ? " (" + promoCode + ")" : ""),
                      discount.negate());
        }
        r.append(DRULE);
        moneyLine(r, "TOTAL", subtotal.subtract(discount));
        r.append(DRULE);

        r.append('\n');
        meta(r, "Payment", paymentInfo.getPaymentMethod());
        if ("Cash".equalsIgnoreCase(paymentInfo.getPaymentMethod())) {
            moneyLine(r, "Tendered", paymentInfo.getAmountPaid());
            moneyLine(r, "Change",   paymentInfo.getChangeGiven());
        }

        footer(r);
        return r.toString();
    }

    // ── Historical sale reprint ─────────────────────────────────────────────

    public static String generateSaleReceipt(Sale sale,
                                             Customer customer,
                                             CustomerService customerService,
                                             ExchangeReturnService exchangeService) {
        StringBuilder r = new StringBuilder();
        header(r, "SALES RECEIPT (REPRINT)");

        meta(r, "Sale #",   String.valueOf(sale.getSaleID()));
        meta(r, "Date",     sale.getSaleDate().format(FULL_FMT));
        meta(r, "Cashier",  sale.getStaffName());
        meta(r, "Customer", customer.getFullNames());
        if (notBlank(customer.getEmailAddress())) meta(r, "Email", customer.getEmailAddress());
        r.append(RULE);

        r.append(String.format("%-22s %6s %10s%n", "Item", "Qty", "Amount"));
        r.append(RULE);

        BigDecimal netTotal = BigDecimal.ZERO;
        for (Purchase item : sale.getItems()) {
            int remaining = customerService.getRemainingQuantity(item.getTransactionID());
            BigDecimal lineTotal = BigDecimal.valueOf(remaining * item.getSalePrice());
            itemLines(r, item.getProductName(), remaining,
                      BigDecimal.valueOf(item.getSalePrice()), lineTotal);

            if (notBlank(item.getPromoCode())) {
                r.append(indent("promo: " + item.getPromoCode()));
            }

            for (ExchangeReturnService.ExchangeRequest ex :
                    exchangeService.getExchangesForTransaction(item.getTransactionID())) {
                r.append(indent("exchanged -> "
                        + (notBlank(ex.getNewProductName()) ? ex.getNewProductName() : "pending")
                        + "  [" + ex.getStatus() + "]"));
                break;
            }
            for (ExchangeReturnService.ReturnRequest ret :
                    exchangeService.getReturnsForTransaction(item.getTransactionID())) {
                r.append(indent(String.format("returned %d  refund R%.2f  [%s]",
                        ret.getReturnQuantity(), ret.getRefundAmount(), ret.getStatus())));
                break;
            }

            netTotal = netTotal.add(lineTotal);
        }

        r.append(DRULE);
        moneyLine(r, "NET TOTAL", netTotal);
        r.append(DRULE);
        footer(r);
        return r.toString();
    }

    // ── Print ───────────────────────────────────────────────────────────────

    public static void printReceipt(String receiptText) {
        System.out.println(receiptText);
        try {
            Printer printer = Printer.getDefaultPrinter();
            if (printer == null) return;
            PrinterJob job = PrinterJob.createPrinterJob(printer);
            if (job == null) return;
            Text text = new Text(receiptText);
            text.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 9pt;");
            if (job.printPage(new TextFlow(text))) job.endJob();
        } catch (Exception e) {
            System.err.println("Printing not available: " + e.getMessage());
        }
    }

    // ── Save ────────────────────────────────────────────────────────────────

    public static boolean saveReceipt(String receiptText, String fileName) {
        return saveReceiptToFile(receiptText, fileName) != null;
    }

    /**
     * Writes the receipt to a .txt file and returns it (or null on failure).
     *
     * A configured absolute path in Settings is honoured; otherwise receipts
     * land in "Documents/&lt;App Name&gt;/Receipts" so they are always findable
     * and writable, even when the app runs from Program Files.
     */
    public static File saveReceiptToFile(String receiptText, String fileName) {
        try {
            Path dir = receiptsDir();
            Files.createDirectories(dir);
            File out = dir.resolve(sanitize(fileName) + ".txt").toFile();
            try (FileWriter writer = new FileWriter(out)) {
                writer.write(receiptText);
            }
            System.out.println("Receipt saved: " + out.getAbsolutePath());
            return out;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Folder receipts are saved into. */
    public static Path receiptsDir() {
        String configured = settings.getReceiptSavePath();
        if (notBlank(configured)) {
            Path p = Paths.get(configured.trim());
            if (p.isAbsolute()) return p;
        }
        return Paths.get(System.getProperty("user.home"), "Documents",
                         com.pos.Branding.APP_NAME, "Receipts");
    }

    private static String sanitize(String name) {
        return name == null ? "receipt" : name.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    // ── Layout helpers ──────────────────────────────────────────────────────

    private static void header(StringBuilder r, String docType) {
        String name    = orDefault(settings.getBusinessName(), "");
        String address = settings.getBusinessAddress();
        String phone   = settings.getBusinessPhone();
        String vatNo   = settings.getBusinessVatNo();

        r.append(DRULE);
        if (notBlank(name)) r.append(centre(name.toUpperCase())).append('\n');
        for (String l : wrapCentre(address)) r.append(l).append('\n');
        if (notBlank(phone)) r.append(centre("Tel: " + phone)).append('\n');
        if (notBlank(vatNo)) r.append(centre("VAT No: " + vatNo)).append('\n');
        r.append(DRULE);
        r.append(centre(docType)).append('\n');
        r.append(RULE);
    }

    private static void footer(StringBuilder r) {
        String msg = orDefault(settings.getReceiptFooter(), "Thank you for your purchase!");
        r.append('\n');
        for (String l : wrapCentre(msg)) r.append(l).append('\n');
        r.append('\n');
        r.append(centre("Powered by " + com.pos.Branding.APP_NAME)).append('\n');
        r.append(DRULE);
    }

    private static void meta(StringBuilder r, String label, String value) {
        r.append(String.format("%-10s %s%n", label + ":", value == null ? "" : value));
    }

    /** Product name (wrapped) then a "qty x unit" / amount line. */
    private static void itemLines(StringBuilder r, String name, int qty,
                                  BigDecimal unit, BigDecimal amount) {
        for (String l : wrap(name == null ? "" : name, W)) r.append(l).append('\n');
        String left = String.format("  %d x %.2f", qty, unit == null ? 0.0 : unit.doubleValue());
        r.append(pad(left, money(amount))).append('\n');
    }

    /** A right-aligned "Label .......... R123.45" line. */
    private static void moneyLine(StringBuilder r, String label, BigDecimal amount) {
        r.append(pad(label, money(amount))).append('\n');
    }

    private static String money(BigDecimal v) {
        double d = v == null ? 0.0 : v.doubleValue();
        return (d < 0 ? "-R" : "R") + String.format("%.2f", Math.abs(d));
    }

    /** Left text + right text on one W-wide line, right-justified. */
    private static String pad(String left, String right) {
        int gap = W - left.length() - right.length();
        if (gap < 1) return left + " " + right;
        return left + " ".repeat(gap) + right;
    }

    private static String indent(String s) {
        return "    " + s + "\n";
    }

    private static String centre(String text) {
        if (text == null || text.isBlank()) return "";
        text = text.strip();
        if (text.length() >= W) return text.substring(0, W);
        int pad = (W - text.length()) / 2;
        return " ".repeat(pad) + text;
    }

    private static java.util.List<String> wrapCentre(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String l : wrap(s, W)) out.add(centre(l));
        return out;
    }

    /** Word-wrap without dropping anything; long single words are hard-split. */
    private static java.util.List<String> wrap(String s, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (s == null || s.isBlank()) return lines;
        StringBuilder cur = new StringBuilder();
        for (String word : s.strip().split("\\s+")) {
            while (word.length() > width) {
                if (cur.length() > 0) { lines.add(cur.toString()); cur.setLength(0); }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (cur.length() == 0)                cur.append(word);
            else if (cur.length() + 1 + word.length() <= width) cur.append(' ').append(word);
            else { lines.add(cur.toString()); cur.setLength(0); cur.append(word); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String orDefault(String s, String d) { return notBlank(s) ? s : d; }
}

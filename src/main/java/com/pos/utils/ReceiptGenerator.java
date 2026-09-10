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
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

/**
 * ReceiptGenerator — handles both live POS receipts and historical sale receipts.
 *
 * Live receipt  : generateReceipt(...)   — called from SalesView at checkout
 * History receipt: generateSaleReceipt(...) — called from CustomerView for past sales
 *
 * Both pull business profile from BusinessSettings (name, address, phone, VAT, footer).
 */
public class ReceiptGenerator {

    private static final SettingsService settings = new SettingsService();

    private static final String SEP  = "=====================================\n";
    private static final String DASH = "-------------------------------------\n";
    private static final int    WIDTH = 37;

    private static final DateTimeFormatter FULL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // ── Live POS receipt (called from SalesView) ──────────────────────────────

    public static String generateReceipt(Customer customer,
                                         ObservableList<CartItem> items,
                                         User cashier,
                                         PaymentInfo paymentInfo) {
        return generateReceipt(customer, items, cashier, paymentInfo, null, BigDecimal.ZERO);
    }

    /**
     * Full live receipt with optional promo discount.
     *
     * @param promoCode      the promo code used (or null)
     * @param discountAmount the discount deducted (or ZERO)
     */
    public static String generateReceipt(Customer customer,
                                         ObservableList<CartItem> items,
                                         User cashier,
                                         PaymentInfo paymentInfo,
                                         String promoCode,
                                         BigDecimal discountAmount) {
        StringBuilder r = new StringBuilder();

        r.append(header());

        // Transaction info
        r.append("Date:     ").append(LocalDateTime.now().format(FULL_FMT)).append("\n");
        r.append("Cashier:  ").append(cashier.getFullNames()).append("\n");
        r.append("Customer: ").append(customer.getFullNames()).append("\n");
        r.append("Email:    ").append(customer.getEmailAddress()).append("\n\n");

        // Items
        r.append(DASH);
        r.append(String.format("%-20s %5s %8s\n", "Item", "Qty", "Price"));
        r.append(DASH);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            r.append(String.format("%-20s %5d R%7.2f\n",
                    truncate(item.getProductName(), 20),
                    item.getQuantity(),
                    item.getSubtotal()));
            subtotal = subtotal.add(item.getSubtotal());
        }

        r.append(DASH);
        r.append(String.format("%-26s R%7.2f\n", "SUBTOTAL:", subtotal));

        // Promo discount
        if (promoCode != null && !promoCode.isBlank() &&
                discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            r.append(String.format("%-20s %6s -R%7.2f\n",
                    "PROMO (" + promoCode + ")", "", discountAmount));
        }

        BigDecimal total = subtotal.subtract(
                discountAmount != null ? discountAmount : BigDecimal.ZERO);
        r.append(String.format("%-26s R%7.2f\n", "TOTAL:", total));
        r.append(DASH);

        // Payment
        r.append("Payment: ").append(paymentInfo.getPaymentMethod()).append("\n");
        if ("Cash".equals(paymentInfo.getPaymentMethod())) {
            r.append(String.format("Paid:    R%7.2f\n", paymentInfo.getAmountPaid()));
            r.append(String.format("Change:  R%7.2f\n", paymentInfo.getChangeGiven()));
        }

        r.append(footer());
        return r.toString();
    }

    // ── Historical sale receipt (called from CustomerView) ────────────────────

    /**
     * Generates a receipt for a past sale retrieved from customer purchase history.
     *
     * Each line item shows remaining (net) quantity after approved returns.
     * If a line has an exchange or return on record, it is annotated below the item.
     * The net total reflects quantities remaining after returns.
     *
     * @param sale            the Sale object (contains items and metadata)
     * @param customer        the customer the sale belongs to
     * @param customerService used to look up remaining quantities per transaction
     * @param exchangeService used to look up exchange/return records per transaction
     */
    public static String generateSaleReceipt(Sale sale,
                                             Customer customer,
                                             CustomerService customerService,
                                             ExchangeReturnService exchangeService) {
        StringBuilder r = new StringBuilder();

        r.append(header());

        // Transaction info
        r.append("Sale #:   ").append(sale.getSaleID()).append("\n");
        r.append("Date:     ").append(sale.getSaleDate().format(FULL_FMT)).append("\n");
        r.append("Cashier:  ").append(sale.getStaffName()).append("\n");
        r.append("Customer: ").append(customer.getFullNames()).append("\n");
        r.append("Email:    ").append(customer.getEmailAddress()).append("\n\n");

        // Items
        r.append(DASH);
        r.append(String.format("%-20s %5s %8s\n", "Item", "Qty", "Amount"));
        r.append(DASH);

        BigDecimal netTotal = BigDecimal.ZERO;

        for (Purchase item : sale.getItems()) {
            int remaining = customerService.getRemainingQuantity(item.getTransactionID());
            BigDecimal lineTotal = BigDecimal.valueOf(remaining * item.getSalePrice());

            r.append(String.format("%-20s %5d R%7.2f\n",
                    truncate(item.getProductName(), 20),
                    remaining,
                    lineTotal));

            // Promo on this line
            if (item.getPromoCode() != null && !item.getPromoCode().isBlank()) {
                r.append("  Promo code: ").append(item.getPromoCode()).append("\n");
            }

            // Exchange annotation
            ObservableList<ExchangeReturnService.ExchangeRequest> exchanges =
                    exchangeService.getExchangesForTransaction(item.getTransactionID());
            if (!exchanges.isEmpty()) {
                ExchangeReturnService.ExchangeRequest ex = exchanges.get(0);
                r.append(String.format("  [EXCHANGE] %-12s → %-12s  Status: %s\n",
                        truncate(ex.getProductName(), 12),
                        truncate(ex.getNewProductName() != null ? ex.getNewProductName() : "—", 12),
                        ex.getStatus()));
                if (ex.getNewPrice() != null) {
                    r.append(String.format("             Original: R%-7.2f  New: R%-7.2f\n",
                            ex.getOriginalPrice(), ex.getNewPrice()));
                }
                if (ex.getReason() != null && !ex.getReason().isBlank()) {
                    r.append("             Reason: ").append(ex.getReason()).append("\n");
                }
            }

            // Return annotation
            ObservableList<ExchangeReturnService.ReturnRequest> returns =
                    exchangeService.getReturnsForTransaction(item.getTransactionID());
            if (!returns.isEmpty()) {
                ExchangeReturnService.ReturnRequest ret = returns.get(0);
                r.append(String.format("  [RETURN]   Qty: %d  Refund: R%-7.2f  Status: %s\n",
                        ret.getReturnQuantity(),
                        ret.getRefundAmount(),
                        ret.getStatus()));
                if (ret.getReason() != null && !ret.getReason().isBlank()) {
                    r.append("             Reason: ").append(ret.getReason()).append("\n");
                }
            }

            netTotal = netTotal.add(lineTotal);
        }

        r.append(DASH);
        r.append(String.format("%-26s R%7.2f\n", "NET TOTAL:", netTotal));
        r.append(footer());

        return r.toString();
    }

    // ── Print ─────────────────────────────────────────────────────────────────

    public static void printReceipt(String receiptText) {
        System.out.println(receiptText);
        try {
            Printer printer = Printer.getDefaultPrinter();
            if (printer != null) {
                PrinterJob job = PrinterJob.createPrinterJob(printer);
                if (job != null) {
                    Text text = new Text(receiptText);
                    text.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10pt;");
                    boolean printed = job.printPage(new TextFlow(text));
                    if (printed) job.endJob();
                }
            }
        } catch (Exception e) {
            System.err.println("Printing not available: " + e.getMessage());
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Saves receipt to the configured receipts folder.
     * Creates the folder if it doesn't exist.
     */
    public static boolean saveReceipt(String receiptText, String fileName) {
        String savePath = settings.getReceiptSavePath();
        try {
            Files.createDirectories(Paths.get(savePath));
            File file = new File(savePath + fileName + ".txt");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(receiptText);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Shared header / footer ────────────────────────────────────────────────

    private static String header() {
        String businessName = settings.getBusinessName();
        String address      = settings.getBusinessAddress();
        String phone        = settings.getBusinessPhone();
        String vatNo        = settings.getBusinessVatNo();

        StringBuilder h = new StringBuilder();
        h.append(SEP);
        h.append(centre(businessName, WIDTH)).append("\n");
        if (!address.isBlank()) h.append(centre(address, WIDTH)).append("\n");
        if (!phone.isBlank())   h.append(centre("Tel: " + phone, WIDTH)).append("\n");
        if (!vatNo.isBlank())   h.append(centre("VAT: " + vatNo, WIDTH)).append("\n");
        h.append(SEP).append("\n");
        return h.toString();
    }

    private static String footer() {
        String footerMsg = settings.getReceiptFooter();
        return SEP + centre(footerMsg, WIDTH) + "\n" + SEP;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String centre(String text, int width) {
        if (text == null || text.isBlank()) return "";
        if (text.length() >= width) return text;
        int pad = (width - text.length()) / 2;
        return " ".repeat(pad) + text;
    }

    /**
     * Wraps a long string across multiple lines, each at most `width` characters.
     * No words are omitted — the full text is always printed.
     * Used for product names and other fields that may exceed the receipt column width.
     */
    private static String wrap(String str, int width) {
        if (str == null) return "";
        if (str.length() <= width) return str;
        StringBuilder out = new StringBuilder();
        int start = 0;
        while (start < str.length()) {
            int end = Math.min(start + width, str.length());
            if (start > 0) out.append("\n").append(" ".repeat(width - (end - start))); // indent continuation
            out.append(str, start, end);
            start = end;
        }
        return out.toString();
    }

    // Keep truncate as a no-op passthrough so existing callers compile unchanged.
    // All receipt formatting now uses wrap() instead.
    private static String truncate(String str, int length) {
        return wrap(str, length);
    }
}
package com.pos.scanner;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Receipt implements Serializable {
    private String receiptId;
    public String name;  // Customer name
    public String email;
    public String cashier;
    public String date;
    public double total;
    public List<ReceiptItem> items = new ArrayList<>();  // Renamed to ReceiptItem
    public String fullReceiptText;  // Raw text for display
    public long timestamp;

    // Inner class renamed to ReceiptItem
    public static class ReceiptItem implements Serializable {
        public String name;
        public int quantity;
        public double price;
        public double subtotal;

        public ReceiptItem(String name, int quantity, double price, double subtotal) {
            this.name = name;
            this.quantity = quantity;
            this.price = price;
            this.subtotal = subtotal;
        }

        // Getters for adapter
        public String getName() { return name; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
        public double getSubtotal() { return subtotal; }
    }

    // Constructor
    public Receipt() {
        this.receiptId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    // Getters for adapters/activities
    public String getReceiptId() { return receiptId; }
    public String getCustomerName() { return name; }
    public String getCustomerEmail() { return email; }
    public String getCashierName() { return cashier; }
    public String getDate() { return date; }
    public List<ReceiptItem> getItems() { return items; }
    public double getTotal() { return total; }
    public String getFullReceiptText() { return fullReceiptText; }

    // Setter for raw text
    public void setFullReceiptText(String text) { this.fullReceiptText = text; }

    /**
     * Parse the desktop receipt text format (as before)
     */
    public static Receipt fromString(String text) {
        Receipt receipt = new Receipt();
        receipt.setFullReceiptText(text);  // Store raw for display
        String[] lines = text.split("\n");
        boolean inItemsSection = false;

        for (String line : lines) {
            line = line.trim();

            // Skip dividers/headers/footers
            if (line.contains("===") || line.contains("---") || line.isEmpty() || line.contains("POINT OF SALE") || line.contains("Thank you")) {
                continue;
            }

            // Parse headers (adjusted for "Customer:" not "Name:")
            if (line.startsWith("Date:")) {
                receipt.date = line.substring(5).trim();
            } else if (line.startsWith("Cashier:")) {
                receipt.cashier = line.substring(8).trim();
            } else if (line.startsWith("Customer:")) {
                receipt.name = line.substring(9).trim();
            } else if (line.startsWith("Email:")) {
                receipt.email = line.substring(6).trim();
            } else if (line.contains("TOTAL:")) {
                // Extract total: "TOTAL: R 11.00"
                Pattern totalPattern = Pattern.compile("TOTAL:\\s*R?\\s*([0-9.]+)");
                Matcher matcher = totalPattern.matcher(line);
                if (matcher.find()) {
                    receipt.total = Double.parseDouble(matcher.group(1));
                }
                break;  // End of items
            } else if (line.startsWith("Item")) {  // Table header
                inItemsSection = true;
                continue;
            }

            // Parse items
            if (inItemsSection && !line.startsWith("TOTAL:")) {
                // Regex for: "Apple                1    R  5.00"
                Pattern itemPattern = Pattern.compile("^(?<name>.{1,25}?\\S)\\s{2,}(?<qty>\\d+)\\s{2,}R?\\s*(?<price>[0-9.]+)$");
                Matcher matcher = itemPattern.matcher(line);
                if (matcher.find()) {
                    try {
                        String itemName = matcher.group("name").trim();
                        int qty = Integer.parseInt(matcher.group("qty"));
                        double price = Double.parseDouble(matcher.group("price"));
                        double subtotal = price * qty;
                        receipt.items.add(new Receipt.ReceiptItem(itemName, qty, price, subtotal));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return receipt;
    }
}
package com.pos.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-language descriptions of each screen, shown by the info button and the
 * first-launch walkthrough. Keyed the same way as MainDashboard's section
 * names, in menu order.
 */
public final class HelpContent {

    public record Entry(String title, String body) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put("sales", new Entry("Sales",
            "Scan a barcode or search for a product to add it to the sale. Check the quantities and any discounts, then take payment. When the sale is complete, you can print or email the receipt."));
        ENTRIES.put("salesHistory", new Entry("Sales History",
            "Find a past sale by date or receipt details. Open it to check the items, reprint a receipt, or start a return or exchange."));
        ENTRIES.put("inventory", new Entry("Inventory",
            "Add products, update prices and keep track of stock. Open a product to change its details, add stock or print its barcode.\n\nUse categories to group your products. Import from Excel adds products in bulk and shows a preview before saving. Export to Excel saves a copy of your current stock list. You can choose its save folder in Settings.\n\nProducts added from a connected phone appear here for you to review."));
        ENTRIES.put("stocktaking", new Entry("Stocktaking",
            "Start a stocktake and enter the quantities you find on the shelf. You can count here, on a connected phone, or export an Excel sheet and import it back into the same draft. Enter 0 when there is no stock; leave a count blank if you have not checked it yet.\n\nSave a draft to continue later. When you submit it, an Admin or Manager checks the counts in Approvals before stock changes. If stock has moved during the count, you will need a fresh stocktake for the affected products."));
        ENTRIES.put("users", new Entry("User Management",
            "Add staff accounts and choose each person's role. Open an account to update their details or access. Staff use the same accounts to sign in on a connected phone."));
        ENTRIES.put("customers", new Entry("Customers",
            "Save your customers' contact details and find their past purchases. Open a customer to update their details, check their purchase history or manage their marketing preferences."));
        ENTRIES.put("reports", new Entry("Reports",
            "Choose a date range to check sales, profit, products and staff performance. Open a summary card for more detail where available. Export to Excel saves the reports to the folder you chose in Settings."));
        ENTRIES.put("sessions", new Entry("Sessions",
            "Check when staff signed in and out, along with the activity recorded for each session. Open a session to see its details and generate a declaration or statement."));
        ENTRIES.put("approvals", new Entry("Approvals",
            "Review requests that need an Admin or Manager's approval. Open a request, check the details and approve or decline it.\n\nIn Stocktakes, compare the original stock, counted quantities and current stock before approving a change. You can export the count to Excel. If stock has moved since counting began, ask for a fresh count."));
        ENTRIES.put("marketing", new Entry("Marketing",
            "Set up promotions and discounts, choose when they run and check how they are being used. Open a promotion to update its details or end it."));
        ENTRIES.put("subscription", new Entry("Subscription",
            "Check your license, see when it renews and manage your subscription."));
        ENTRIES.put("settings", new Entry("Settings",
            "Update your business details, receipt layout, tax rate and printer preferences. Choose where receipts, reports, inventory sheets, stocktake sheets and backups are saved. Save your changes before leaving the screen."));
    }

    private HelpContent() {}

    public static Entry get(String section) {
        return ENTRIES.get(section);
    }

    /** Every section, in menu order, for driving the first-launch walkthrough. */
    public static java.util.List<String> sectionsInOrder() {
        return new java.util.ArrayList<>(ENTRIES.keySet());
    }
}

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
        ENTRIES.put("sales", new Entry(
            "Sales",
            "This is your till. Scan or search for a product, add it to the cart, "
            + "then take payment by cash or card. You can apply a promo code, give "
            + "a customer discount, and print or email the receipt when you're done."
        ));
        ENTRIES.put("salesHistory", new Entry(
            "Sales History",
            "Every till sale, in one place, so you can look one up and process a return or "
            + "exchange. Retail sales aren't tied to a named customer account, so this is how "
            + "you find a past sale instead of looking it up under a customer."
        ));
        ENTRIES.put("inventory", new Entry(
            "Inventory",
            "Add new products and keep your stock counts up to date. Each product "
            + "gets a barcode you can print and stick on the shelf. You'll enter both "
            + "a purchase price (what you paid) and a selling price (what the customer "
            + "pays) so your profit reports are accurate.\n\n"
            + "Put every product in a category (Dairy, Bakery, Cleaning...) and use "
            + "Manage Categories to add, rename or remove them. The category filter at "
            + "the top narrows the list to one group, and your reports use categories "
            + "too.\n\n"
            + "Above the product list, Export to Excel saves your stock list as a "
            + "spreadsheet, and Import from Excel adds many products at once. Before "
            + "anything is saved, the app shows you exactly what will be added and "
            + "asks whether to create any categories it hasn't seen before, so a typo "
            + "in the sheet won't quietly create a new category. Products whose "
            + "barcode is already in your inventory are skipped.\n\n"
            + "You can also add products from the scanner app on your phone: pick a "
            + "category, scan the barcode, and the product appears here for you to "
            + "approve."
        ));
        ENTRIES.put("users", new Entry(
            "User Management",
            "Add accounts for your staff and decide what each person can see and do. "
            + "Cashiers can make sales; managers and admins can also see reports, "
            + "approve refunds, and change settings."
        ));
        ENTRIES.put("customers", new Entry(
            "Customers",
            "Keep a record of your regular customers and their contact details. "
            + "You can look up their purchase history and use it to run targeted "
            + "promotions."
        ));
        ENTRIES.put("reports", new Entry(
            "Reports",
            "See how the business is doing: revenue, profit margin, best and worst "
            + "selling products, staff performance, and stock health. Pick a date "
            + "range and export any report to Excel to share or keep for your records."
        ));
        ENTRIES.put("sessions", new Entry(
            "Sessions",
            "A log of every time a staff member logged in and out, and for how long. "
            + "Handy for checking who worked a shift and when."
        ));
        ENTRIES.put("approvals", new Entry(
            "Approvals",
            "Refunds and exchanges over a certain amount need a manager's sign-off. "
            + "Those requests show up here, waiting for someone with the right "
            + "permissions to approve or decline them."
        ));
        ENTRIES.put("marketing", new Entry(
            "Marketing",
            "Create promo codes and discounts, set when they run, and see how well "
            + "each one performs once customers start using it."
        ));
        ENTRIES.put("subscription", new Entry(
            "Subscription",
            "Check your license status and renewal date, and manage your "
            + "subscription from here."
        ));
        ENTRIES.put("settings", new Entry(
            "Settings",
            "Set up your business details, receipt layout, tax rate, printer, and "
            + "other system preferences."
        ));
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
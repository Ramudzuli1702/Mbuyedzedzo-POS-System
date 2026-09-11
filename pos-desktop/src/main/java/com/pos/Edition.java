package com.pos;

import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Which product this build IS. Fixed at build time — not a user setting.
 *
 *   STANDARD — customers have accounts. Customer + Marketing screens, promo
 *              targeting, receipt-by-email, customer-insight reports.
 *   RETAIL   — walk-in sales only. No customer or marketing screens; every
 *              sale is booked against a single "Walk-in" account.
 *
 * The value is read from {@code /pos-edition.properties}, which Maven filters
 * at build time from the {@code pos.edition} property:
 *
 *   mvn package            -> Standard POS installer
 *   mvn -P retail package  -> Retail POS installer
 *
 * A customer's licence unlocks one product; the other isn't in their build.
 * When the value is missing (e.g. running unfiltered sources in an IDE) it
 * defaults to STANDARD.
 */
public enum Edition {

    STANDARD,
    RETAIL;

    private static final Edition VALUE = load();

    public static Edition current() {
        return VALUE;
    }

    private static Edition load() {
        try (InputStream in = Edition.class.getResourceAsStream("/pos-edition.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("pos.edition", "").trim();
                if (RETAIL.name().equalsIgnoreCase(v))   return RETAIL;
                if (STANDARD.name().equalsIgnoreCase(v)) return STANDARD;
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(Edition.class)
                    .warn("Could not read pos-edition.properties, defaulting to STANDARD", e);
        }
        return STANDARD;
    }

    public boolean isRetail()      { return this == RETAIL; }
    public boolean hasCustomers()  { return this == STANDARD; }

    public String displayName() {
        return this == RETAIL
            ? "Retail — walk-in sales, no customer accounts"
            : "Standard — customers have accounts";
    }
}

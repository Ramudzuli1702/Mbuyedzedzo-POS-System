package com.pos;

import com.pos.services.SettingsService;
import org.slf4j.LoggerFactory;

/**
 * Which flavour of the POS this install runs as. One codebase, two products:
 *
 *   STANDARD — customers have accounts. Customer + Marketing screens, promo
 *              targeting, receipt-by-email, customer-insight reports.
 *   RETAIL   — walk-in sales only. No customer or marketing screens; every
 *              sale is booked against a single "Walk-in" account.
 *
 * Stored as the {@code pos.edition} business setting; read once and cached.
 * Changing it takes effect on the next login (the sidebar is built then).
 * When licensing lands, the edition is fixed by the license key.
 */
public enum Edition {

    STANDARD,
    RETAIL;

    public static final String SETTING_KEY = "pos.edition";

    private static volatile Edition cached;

    public static Edition current() {
        Edition e = cached;
        if (e == null) {
            e = load();
            cached = e;
        }
        return e;
    }

    /** Persists the choice and refreshes the cache. */
    public static void set(Edition edition) {
        try {
            new SettingsService().saveBusinessSetting(SETTING_KEY, edition.name());
        } catch (Exception e) {
            LoggerFactory.getLogger(Edition.class).error("Could not save POS edition", e);
        }
        cached = edition;
    }

    /** Forces a re-read from settings (e.g. after another admin changed it). */
    public static void reload() {
        cached = load();
    }

    private static Edition load() {
        try {
            String v = new SettingsService().getBusinessSetting(SETTING_KEY, STANDARD.name());
            return RETAIL.name().equalsIgnoreCase(v) ? RETAIL : STANDARD;
        } catch (Exception e) {
            LoggerFactory.getLogger(Edition.class).warn("Could not read POS edition, defaulting to STANDARD", e);
            return STANDARD;
        }
    }

    public boolean isRetail()      { return this == RETAIL; }
    public boolean hasCustomers()  { return this == STANDARD; }

    public String displayName() {
        return this == RETAIL
            ? "Retail — walk-in sales, no customer accounts"
            : "Standard — customers have accounts";
    }
}

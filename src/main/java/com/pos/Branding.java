package com.pos;

/**
 * Central place for the product's name and short name. This is the software
 * name, which is distinct from the <em>business</em> name a shop configures in
 * Settings — that one appears on receipts and in customer emails.
 *
 * The name reflects the build's {@link Edition}: the Retail build is a
 * separate product ("Mvelelo Retail POS").
 */
public final class Branding {

    public static final String APP_NAME  = Edition.current().isRetail()
            ? "Mvelelo Retail POS"
            : "Mvelelo POS System";

    public static final String APP_SHORT = Edition.current().isRetail()
            ? "Mvelelo Retail"
            : "Mvelelo POS";

    private Branding() {}
}

package com.pos;

/**
 * Central place for the product's name and short name. This is the software
 * name, which is distinct from the <em>business</em> name a shop configures in
 * Settings — that one appears on receipts and in customer emails.
 *
 * The name reflects the build's {@link Edition}: the Retail build is a
 * separate product ("Mbuyedzedzo Retail POS").
 */
public final class Branding {

    public static final String APP_NAME  = Edition.current().isRetail()
            ? "Mbuyedzedzo Retail POS"
            : "Mbuyedzedzo POS System";

    public static final String APP_SHORT = Edition.current().isRetail()
            ? "Mbuyedzedzo Retail"
            : "Mbuyedzedzo POS";

    /** The brand's meaning, shown under the logo on the login/activation screens and receipts. */
    public static final String APP_TAGLINE = "Restoration";

    /** Classpath location of the full logo (mark + wordmark), bundled as a resource. */
    public static final String LOGO_PATH = "/brand/logo.png";

    /** Classpath location of the mark-only logo (transparent background). */
    public static final String LOGO_MARK_PATH = "/brand/logo-mark.png";

    /**
     * The live licensing server every shop's desktop app activates against by
     * default. {@link com.pos.license.LicenseStore} falls back to this if no
     * server URL is saved yet; {@code ActivationView} still lets it be
     * overridden (e.g. to point at a local dev instance) via its Server field.
     */
    public static final String LICENSE_SERVER_URL = "https://mbuyedzedzo-licensing.azurewebsites.net";

    private Branding() {}
}

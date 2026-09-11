package com.pos.license;

import com.pos.Branding;
import com.pos.Edition;
import com.pos.license.LicenseClient.LicenseServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether this install may run, and drives activation / trial /
 * revalidation against the licensing server.
 *
 *  - a valid, fresh token  → ACTIVE
 *  - a token whose 7-day window lapsed but the server was reached within the
 *    last {@value #OFFLINE_GRACE_DAYS} days → GRACE (runs, nags to reconnect)
 *  - anything else → NEEDS_ACTIVATION (Activation screen blocks startup)
 */
public final class LicenseManager {

    private static final Logger log = LoggerFactory.getLogger(LicenseManager.class);
    private static final int OFFLINE_GRACE_DAYS = 14;

    public enum State { ACTIVE, GRACE, NEEDS_ACTIVATION }

    public record Status(State state, String message, LicenseToken token) {
        public boolean canRun() { return state != State.NEEDS_ACTIVATION; }
    }

    private final LicenseStore store = LicenseStore.load();

    public String serverUrl()            { return store.serverUrl; }
    public void setServerUrl(String url) { store.serverUrl = url; store.save(); }

    /** Product code this build expects the licence to be for. */
    public static String requiredProduct() {
        return Edition.current().isRetail() ? "POS_RETAIL" : "POS_STANDARD";
    }

    // ── Startup check (offline) ─────────────────────────────────────────────

    public Status check() {
        if (store.token == null || store.token.isBlank()) {
            return new Status(State.NEEDS_ACTIVATION, "This copy of " + Branding.APP_NAME
                    + " has not been activated.", null);
        }
        LicenseToken t;
        try {
            t = LicenseToken.verify(store.token);
        } catch (LicenseToken.LicenseException e) {
            log.warn("Stored token rejected: {}", e.getMessage());
            return new Status(State.NEEDS_ACTIVATION, "The stored licence is invalid. Please activate again.", null);
        }

        if (!requiredProduct().equals(t.product)) {
            return new Status(State.NEEDS_ACTIVATION,
                    "This licence is for " + pretty(t.product) + ", but this is " + pretty(requiredProduct()) + ".", t);
        }
        if (!MachineFingerprint.get().equals(t.fingerprint)) {
            return new Status(State.NEEDS_ACTIVATION,
                    "This licence is tied to a different computer. Activate it here, or ask an admin to transfer it.", t);
        }
        if (t.licenseExpired()) {
            return new Status(State.NEEDS_ACTIVATION,
                    "This licence expired on " + t.licenseExpiry + ".", t);
        }
        if (t.tokenFresh()) {
            return new Status(State.ACTIVE, "Licensed to " + t.key, t);
        }
        // token window lapsed — lean on the offline grace
        Instant lastOk = Instant.ofEpochSecond(store.lastValidatedEpoch);
        if (store.lastValidatedEpoch > 0
                && Duration.between(lastOk, Instant.now()).toDays() <= OFFLINE_GRACE_DAYS) {
            return new Status(State.GRACE,
                    "Licence needs to re-check with the server. Connect to the internet soon.", t);
        }
        return new Status(State.NEEDS_ACTIVATION,
                "This licence hasn't been verified in over " + OFFLINE_GRACE_DAYS
                + " days. Connect to the internet and activate again.", t);
    }

    // ── Online operations ──────────────────────────────────────────────────

    /** Activate with a purchased key. Throws LicenseServerException on failure. */
    public void activate(String key) {
        LicenseClient c = new LicenseClient(store.serverUrl);
        String token = c.activate(key.trim().toUpperCase(), MachineFingerprint.get(),
                machineLabel(), requiredProduct(), appVersion());
        LicenseToken.verify(token);   // sanity-check the signature before trusting it
        store.licenseKey = key.trim().toUpperCase();
        store.token = token;
        store.lastValidatedEpoch = Instant.now().getEpochSecond();
        store.save();
    }

    /** Start a free trial for this product on this machine. */
    public void startTrial() {
        LicenseClient c = new LicenseClient(store.serverUrl);
        String token = c.startTrial(requiredProduct(), MachineFingerprint.get(), machineLabel(), appVersion());
        LicenseToken parsed = LicenseToken.verify(token);
        store.licenseKey = parsed.key;
        store.token = token;
        store.lastValidatedEpoch = Instant.now().getEpochSecond();
        store.save();
    }

    /**
     * Best-effort background re-check on launch. Refreshes the token, or clears
     * the activation if the server says the licence is dead.
     */
    public void revalidateInBackground() {
        if (store.token == null || store.licenseKey == null) return;
        Thread t = new Thread(() -> {
            try {
                String fresh = new LicenseClient(store.serverUrl)
                        .validate(store.licenseKey, MachineFingerprint.get());
                LicenseToken.verify(fresh);
                store.token = fresh;
                store.lastValidatedEpoch = Instant.now().getEpochSecond();
                store.save();
                log.info("Licence re-validated with the server.");
            } catch (LicenseServerException e) {
                if (e.unreachable()) {
                    log.info("Licence re-validation skipped — server unreachable.");
                } else {
                    log.warn("Server rejected the licence ({}). Clearing activation.", e.code);
                    store.clearActivation();
                }
            } catch (Exception e) {
                log.warn("Licence re-validation failed: {}", e.getMessage());
            }
        }, "License-Revalidate");
        t.setDaemon(true);
        t.start();
    }

    public void deactivateThisMachine() {
        try {
            if (store.token != null && store.licenseKey != null) {
                new LicenseClient(store.serverUrl).deactivate(store.licenseKey, MachineFingerprint.get());
            }
        } catch (Exception e) {
            log.warn("Deactivate call failed: {}", e.getMessage());
        }
        store.clearActivation();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static String machineLabel() {
        String host = System.getenv("COMPUTERNAME");
        return host != null ? host : System.getProperty("user.name", "unknown");
    }

    private static String appVersion() {
        String v = LicenseManager.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    private static String pretty(String product) {
        return "POS_RETAIL".equals(product) ? "Retail POS" : "Standard POS";
    }
}

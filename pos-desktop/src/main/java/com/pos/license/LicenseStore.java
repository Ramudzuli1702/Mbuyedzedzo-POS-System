package com.pos.license;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The local licence record: server URL, the signed token, and when the server
 * last confirmed the licence. Lives next to db.properties in
 * {@code %PROGRAMDATA%\POS System\config\license.json}.
 */
public final class LicenseStore {

    private static final Logger log = LoggerFactory.getLogger(LicenseStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String serverUrl = com.pos.Branding.LICENSE_SERVER_URL;
    public String licenseKey;
    public String token;
    public long lastValidatedEpoch;   // 0 = never

    private static Path path() {
        String base = System.getenv("PROGRAMDATA");
        if (base == null || base.isBlank()) base = System.getProperty("user.home");
        return Path.of(base, "POS System", "config", "license.json");
    }

    public static LicenseStore load() {
        File f = path().toFile();
        if (f.isFile()) {
            try {
                LicenseStore s = GSON.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), LicenseStore.class);
                if (s != null) {
                    // Migrate a never-activated local config still pointed at the old
                    // "http://localhost:8080" factory default onto the live server. A
                    // config that already has a real activation (licenseKey set) is left
                    // alone — that's a deliberate dev/local setup, not a stale default.
                    boolean neverActivated = s.licenseKey == null || s.licenseKey.isBlank();
                    boolean staleDefault = s.serverUrl == null || s.serverUrl.isBlank()
                            || (neverActivated && "http://localhost:8080".equals(s.serverUrl));
                    if (staleDefault) s.serverUrl = com.pos.Branding.LICENSE_SERVER_URL;
                    return s;
                }
            } catch (Exception e) {
                log.warn("Could not read license.json — starting fresh ({})", e.getMessage());
            }
        }
        return new LicenseStore();
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Could not write license.json", e);
        }
    }

    public void clearActivation() {
        this.licenseKey = null;
        this.token = null;
        this.lastValidatedEpoch = 0;
        save();
    }
}

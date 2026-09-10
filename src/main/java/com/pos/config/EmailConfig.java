package com.pos.config;

import java.io.*;
import java.util.Properties;

/**
 * Loads email credentials from a config file outside your source tree.
 * File location: [project_root]/config/email.properties
 * NEVER commit this file to Git — add it to .gitignore
 */
public class EmailConfig {

    private static final String CONFIG_PATH = "config/email.properties";
    private static Properties props;

    public static String getUsername() { return get("email.username"); }
    public static String getPassword() { return get("email.password"); }
    public static String getFromName() { return get("email.fromName", "POS System"); }

    private static String get(String key) {
        return get(key, null);
    }

    private static String get(String key, String defaultValue) {
        if (props == null) load();
        return props.getProperty(key, defaultValue);
    }

    private static void load() {
        props = new Properties();
        File file = new File(CONFIG_PATH);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                System.out.println("✅ Email config loaded from " + CONFIG_PATH);
            } catch (IOException e) {
                System.err.println("❌ Failed to load email config: " + e.getMessage());
            }
        } else {
            System.err.println("⚠️ Email config not found at " + CONFIG_PATH);
            System.err.println("   Create config/email.properties with email.username and email.password");
        }
    }
}
package com.pos.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Default names for user documents; identifiers remain readable and path-safe. */
public final class DocumentNames {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS");
    private static LocalDateTime last;
    private DocumentNames() {}

    public static synchronized String name(String type, Object reference, String extension) {
        LocalDateTime now = LocalDateTime.now();
        if (last != null && !now.isAfter(last)) now = last.plusNanos(1_000_000);
        // Round to filename precision before remembering the value.
        now = now.withNano((now.getNano() / 1_000_000) * 1_000_000);
        if (last != null && !now.isAfter(last)) now = last.plusNanos(1_000_000);
        last = now;
        String ref = reference == null ? "" : part(reference.toString());
        return "Mbuyedzedzo_" + part(type) + (ref.isEmpty() ? "" : "_" + ref)
                + "_" + STAMP.format(now) + "." + part(extension).toLowerCase(Locale.ROOT);
    }

    private static String part(String value) {
        if (value == null) return "";
        String result = value.trim().replaceAll("[^a-zA-Z0-9-]+", "-").replaceAll("^-+|-+$", "");
        return result.length() > 90 ? result.substring(0, 90) : result;
    }
}

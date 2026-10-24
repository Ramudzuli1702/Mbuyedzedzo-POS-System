package com.pos.utils;

import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** User-scoped Windows DPAPI; no application-wide key or machine-name-derived secret. */
public final class WindowsSecret {
    private WindowsSecret() {}
    public static String protect(byte[] plain) {
        if(!System.getProperty("os.name", "").startsWith("Windows"))
            throw new IllegalStateException("Windows credential protection is required.");
        return "dpapi:" + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(plain));
    }
    public static byte[] unprotect(String stored) {
        if(!stored.startsWith("dpapi:"))throw new IllegalArgumentException("Unknown protected credential format.");
        return Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.substring(6)));
    }
}

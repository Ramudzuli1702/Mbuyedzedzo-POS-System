package com.pos.license;

import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stable-ish identifier for this computer: SHA-256 of the physical MAC
 * addresses plus the machine name and OS user. Virtual / loopback adapters are
 * ignored so VPNs don't shift it. It can still change (new network card, wiped
 * OS) — that's what the "transfer / reset machine binding" flow is for.
 */
public final class MachineFingerprint {

    private static volatile String cached;

    private MachineFingerprint() {}

    public static String get() {
        String v = cached;
        if (v == null) {
            v = compute();
            cached = v;
        }
        return v;
    }

    private static String compute() {
        List<String> macs = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                String name = ni.getName().toLowerCase();
                String disp = String.valueOf(ni.getDisplayName()).toLowerCase();
                if (disp.contains("virtual") || disp.contains("vpn") || disp.contains("hyper-v")
                        || disp.contains("vmware") || disp.contains("loopback") || name.startsWith("veth")) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length == 6) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) sb.append(String.format("%02x", b));
                    macs.add(sb.toString());
                }
            }
        } catch (Exception ignored) {
            // fall through — we still have the machine name / user
        }
        Collections.sort(macs);

        String seed = String.join(",", macs)
                + "|" + orEmpty(System.getenv("COMPUTERNAME"))
                + "|" + orEmpty(System.getProperty("user.name"))
                + "|" + orEmpty(System.getProperty("os.arch"));

        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : h) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute machine fingerprint", e);
        }
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }
}

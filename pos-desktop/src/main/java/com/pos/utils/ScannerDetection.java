package com.pos.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Whether a USB barcode scanner appears to be connected. There's no generic
 * Windows API that labels a HID device "this one is a barcode scanner" —
 * most scanners just present themselves as an ordinary keyboard — so this
 * combines two honest, partial signals instead of pretending to have a
 * definitive one:
 *
 *   1. Hardware hint: scan the connected keyboard/HID device list (via
 *      PowerShell) for common scanner vendor/product name fragments. Catches
 *      most real scanners, but misses rebranded/generic ones, and can never
 *      prove a plain "HID Keyboard Device" isn't actually a scanner.
 *   2. Behavioural confirmation: {@link BarcodeScannerSupport} already tells
 *      a scan apart from human typing by speed; every time it recognises one
 *      anywhere in the app, it calls {@link #recordScan()}. A scan seen
 *      recently is proof positive regardless of what the device calls
 *      itself, and is weighted more heavily than the hardware hint.
 */
public final class ScannerDetection {

    private static final long RECENT_SCAN_WINDOW_MS = 30 * 60 * 1000; // 30 minutes
    private static final String[] KEYWORDS = {
        "scanner", "barcode", "honeywell", "symbol", "zebra", "datalogic",
        "netum", "eyoyo", "tera", "inateck", "cipherlab", "opticon",
        "socket mobile", "newland", "unitech", "metrologic"
    };

    private static volatile long lastScanAt = 0;

    private ScannerDetection() {}

    /** Called by BarcodeScannerSupport whenever it recognises a real scan. */
    public static void recordScan() {
        lastScanAt = System.currentTimeMillis();
    }

    public static boolean isRecentlyActive() {
        return lastScanAt > 0 && (System.currentTimeMillis() - lastScanAt) < RECENT_SCAN_WINDOW_MS;
    }

    /** Best-effort, slow (spawns PowerShell) — call off the FX thread, periodically, not per-frame. */
    public static boolean isHardwareDetected() {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-PnpDevice -PresentOnly | Where-Object { $_.Class -eq 'Keyboard' -or $_.Class -eq 'HIDClass' } "
                    + "| Select-Object -ExpandProperty FriendlyName")
                    .redirectErrorStream(true)
                    .start();

            List<String> lines;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                lines = reader.lines().toList();
            }
            p.waitFor();

            for (String line : lines) {
                String lower = line.toLowerCase();
                for (String keyword : KEYWORDS) {
                    if (lower.contains(keyword)) return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Scanner hardware check failed: " + e.getMessage());
        }
        return false;
    }

    public static boolean isConnected() {
        return isRecentlyActive() || isHardwareDetected();
    }
}

package com.pos.utils;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

/**
 * Recognises input from USB "keyboard-wedge" barcode scanners — the mode
 * almost every off-the-shelf USB/Bluetooth scanner ships in by default, where
 * the device just "types" the code character-by-character into whatever has
 * focus and finishes with Enter. No vendor SDK or driver is involved; the OS
 * sees it as an ordinary (very fast) keyboard.
 *
 * <p>A human can't type a barcode anywhere near scanner speed, so a burst of
 * keystrokes arriving faster than {@link #MAX_INTER_KEY_MILLIS} apart, ended
 * by Enter, is treated as a scan and reported to the callback. This is
 * scene-wide (an event filter, so it sees keys before any control does) —
 * a scan is recognised no matter which control currently has focus, not only
 * when a particular text field is focused.
 *
 * <p>Caveat: the characters of a fast burst still land in whichever control
 * had focus as they're typed (only the terminating Enter is consumed once a
 * scan is confirmed) — callers that want a clean field should clear it
 * themselves in the callback, the way {@code addProductToCart} already does.
 */
public final class BarcodeScannerSupport {

    private static final long MAX_INTER_KEY_MILLIS = 50;
    private static final int MIN_SCAN_LENGTH = 3;

    private final StringBuilder buffer = new StringBuilder();
    private long lastKeyAt = 0;
    private boolean fastSoFar = true;

    private BarcodeScannerSupport() {}

    public static void attach(Scene scene, Consumer<String> onScan) {
        BarcodeScannerSupport support = new BarcodeScannerSupport();

        scene.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty() || ch.charAt(0) == '\r' || ch.charAt(0) == '\n') return;

            long now = System.currentTimeMillis();
            boolean withinBurst = support.buffer.length() > 0
                    && (now - support.lastKeyAt) <= MAX_INTER_KEY_MILLIS;

            if (support.buffer.length() > 0 && !withinBurst) {
                support.buffer.setLength(0);
                support.fastSoFar = true;
            } else if (support.buffer.length() > 0) {
                support.fastSoFar = support.fastSoFar && withinBurst;
            }
            support.buffer.append(ch);
            support.lastKeyAt = now;
        });

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ENTER) return;

            String candidate = support.buffer.toString().trim();
            boolean looksLikeScan = support.fastSoFar && candidate.length() >= MIN_SCAN_LENGTH;

            support.buffer.setLength(0);
            support.fastSoFar = true;

            if (looksLikeScan) {
                e.consume();
                onScan.accept(candidate);
            }
        });
    }
}

package com.pos.setup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

/**
 * Tracks when this machine last backed up (or was reminded to), so
 * MainDashboard can nag every 7 days until the user actually runs a backup.
 *
 * Stored next to tutorial-seen.flag/db.properties — same per-machine, per-
 * edition convention as {@link TutorialState}.
 */
public final class BackupReminder {

    private static final long INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private static final File FILE = new File(
        System.getenv("PROGRAMDATA") + File.separator +
        "POS System" + File.separator +
        "config" + File.separator +
        "backup-reminder-" + com.pos.Edition.current().name() + ".flag");

    private BackupReminder() {}

    /**
     * True once 7 days have passed since the last backup or the last time
     * this reminder was shown. A machine that has never recorded either is
     * given a 7-day grace period from first launch rather than nagging
     * immediately on day one.
     */
    public static boolean isDue() {
        if (!FILE.exists()) {
            touch();
            return false;
        }
        try {
            long last = Long.parseLong(Files.readString(FILE.toPath()).trim());
            return Instant.now().toEpochMilli() - last >= INTERVAL_MILLIS;
        } catch (Exception e) {
            return false;
        }
    }

    /** Call after a successful backup — pushes the next reminder out 7 days. */
    public static void markBackedUp() {
        touch();
    }

    /** Call after showing the reminder, regardless of which button was clicked. */
    public static void markReminded() {
        touch();
    }

    private static void touch() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null) Files.createDirectories(dir.toPath());
            Files.writeString(FILE.toPath(), String.valueOf(Instant.now().toEpochMilli()));
        } catch (IOException e) {
            System.err.println("Could not persist backup-reminder flag: " + e.getMessage());
        }
    }
}

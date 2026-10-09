package com.pos.setup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

/**
 * Whether this machine has already seen the first-launch "get to know the
 * system" walkthrough. Recorded once, the first time MainDashboard shows it,
 * and never shown automatically again after that (the info button in the
 * sidebar still opens the same content on demand at any time).
 *
 * Stored next to db.properties/terms-accepted.flag — same per-machine
 * convention as {@link TermsAcceptance}, and scoped per edition for the same
 * reason: Standard and Retail are separate installed products.
 */
public final class TutorialState {

    private static final File FLAG = new File(
        System.getenv("PROGRAMDATA") + File.separator +
        "POS System" + File.separator +
        "config" + File.separator +
        "tutorial-seen-" + com.pos.Edition.current().name() + ".flag");

    private TutorialState() {}

    public static boolean hasSeenTutorial() {
        return FLAG.exists();
    }

    public static void markSeen() {
        try {
            File dir = FLAG.getParentFile();
            if (dir != null) Files.createDirectories(dir.toPath());
            Files.writeString(FLAG.toPath(), "Seen " + LocalDateTime.now() + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Could not persist tutorial-seen flag: " + e.getMessage());
        }
    }
}

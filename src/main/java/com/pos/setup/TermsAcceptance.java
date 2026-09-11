package com.pos.setup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

/**
 * Whether this machine has accepted the end-user licence agreement. Recorded
 * once, the first time {@link com.pos.views.TermsView} is accepted — never
 * shown again after that, even across reinstalls/updates that keep this file.
 *
 * Stored next to db.properties ({@code %PROGRAMDATA%\POS System\config\}) —
 * same per-machine convention as {@link com.pos.database.DatabaseConnection}.
 */
public final class TermsAcceptance {

    private static final File FLAG = new File(
        System.getenv("PROGRAMDATA") + File.separator +
        "POS System" + File.separator +
        "config" + File.separator +
        "terms-accepted.flag");

    private TermsAcceptance() {}

    public static boolean isAccepted() {
        return FLAG.exists();
    }

    public static void accept() {
        try {
            File dir = FLAG.getParentFile();
            if (dir != null) Files.createDirectories(dir.toPath());
            Files.writeString(FLAG.toPath(), "Accepted " + LocalDateTime.now() + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Could not persist terms acceptance: " + e.getMessage());
        }
    }
}

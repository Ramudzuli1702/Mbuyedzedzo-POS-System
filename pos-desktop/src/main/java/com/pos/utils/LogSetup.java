package com.pos.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-time logging bootstrap. Call {@link #init()} as the very first line of
 * {@code main()} — before any {@code LoggerFactory.getLogger(...)} runs — so the
 * {@code pos.log.dir} system property is set before logback reads its config.
 *
 * Log files live next to the app config:
 *   %PROGRAMDATA%\POS System\logs\pos.log      (Windows)
 *   ~/.pos-logs/pos.log                        (fallback / other OSes)
 */
public final class LogSetup {

    private static boolean done = false;

    private LogSetup() {}

    public static synchronized void init() {
        if (done) return;
        done = true;

        Path dir = resolveLogDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            // Fall back to the working directory; logback will still start.
            dir = Paths.get("logs");
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
        }
        System.setProperty("pos.log.dir", dir.toAbsolutePath().toString());

        // If any logger fired before this method ran, logback already resolved
        // its file path from the (then-unset) property and used the fallback.
        // Re-read the config now that pos.log.dir is set.
        reconfigureLogback();

        // Mirror everything printed to the console into the rolling log file,
        // so legacy println / printStackTrace calls are captured without
        // touching hundreds of call sites. Routed to a FILE-only logger, so
        // there is no write loop with the console appender.
        Logger console = LoggerFactory.getLogger("console.capture");
        System.setOut(tee(System.out, console));
        System.setErr(tee(System.err, console));

        Logger log = LoggerFactory.getLogger(LogSetup.class);
        log.info("=== {} starting ===", com.pos.Branding.APP_NAME);
        log.info("Log directory: {}", dir.toAbsolutePath());

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
            LoggerFactory.getLogger("uncaught")
                .error("Uncaught exception on thread {}", thread.getName(), ex));
    }

    /** Reload logback.xml so appenders pick up the now-set {@code pos.log.dir}. */
    private static void reconfigureLogback() {
        try {
            org.slf4j.ILoggerFactory f = LoggerFactory.getILoggerFactory();
            if (f instanceof ch.qos.logback.classic.LoggerContext ctx) {
                ctx.reset();
                ch.qos.logback.classic.joran.JoranConfigurator jc =
                        new ch.qos.logback.classic.joran.JoranConfigurator();
                jc.setContext(ctx);
                try (java.io.InputStream in = LogSetup.class.getResourceAsStream("/logback.xml")) {
                    if (in != null) jc.doConfigure(in);
                }
            }
        } catch (Throwable t) {
            // logging config isn't worth crashing startup over
        }
    }

    /** A PrintStream that writes to {@code original} and also logs each completed line. */
    private static PrintStream tee(PrintStream original, Logger sink) {
        OutputStream teed = new OutputStream() {
            private final ByteArrayOutputStream line = new ByteArrayOutputStream(256);

            @Override public synchronized void write(int b) {
                original.write(b);
                if (b == '\n') emit();
                else if (b != '\r') line.write(b);
            }

            @Override public synchronized void write(byte[] b, int off, int len) {
                original.write(b, off, len);
                for (int i = 0; i < len; i++) {
                    int c = b[off + i] & 0xFF;
                    if (c == '\n') emit();
                    else if (c != '\r') line.write(c);
                }
            }

            @Override public void flush() { original.flush(); }

            private void emit() {
                if (line.size() == 0) return;
                String s = line.toString(StandardCharsets.UTF_8);
                line.reset();
                try { sink.info(s); } catch (Throwable ignored) { /* never break println */ }
            }
        };
        return new PrintStream(teed, true, StandardCharsets.UTF_8);
    }

    /** The directory log files are written to (valid only after {@link #init()}). */
    public static Path logDir() {
        String p = System.getProperty("pos.log.dir");
        return p != null ? Paths.get(p) : resolveLogDir();
    }

    private static Path resolveLogDir() {
        String programData = System.getenv("PROGRAMDATA");
        if (programData != null && !programData.isBlank()) {
            return Paths.get(programData, "POS System", "logs");
        }
        return Paths.get(System.getProperty("user.home"), ".pos-logs");
    }

    /** Best-effort "open the log folder in the file manager" for a Settings button. */
    public static void openLogFolder() {
        try {
            File dir = logDir().toFile();
            if (dir.isDirectory() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(LogSetup.class).warn("Could not open log folder", e);
        }
    }
}

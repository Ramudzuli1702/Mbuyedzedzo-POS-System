package com.pos.utils;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogSetupTest {

    @Test
    void initCreatesLogFileAndCapturesConsoleOutputWithoutLooping() throws Exception {
        PrintStream realOut = System.out;
        PrintStream realErr = System.err;
        try {
            LogSetup.init();

            String marker = "logsetup-smoke-" + System.nanoTime();
            System.out.println(marker);                         // legacy println path
            new RuntimeException(marker + "-ex").printStackTrace(); // printStackTrace path
            LoggerFactory.getLogger(LogSetupTest.class).info(marker + "-slf4j"); // real logger

            // give the async-free file appender a moment on slow CI
            Thread.sleep(200);

            Path logFile = LogSetup.logDir().resolve("pos.log");
            assertTrue(Files.exists(logFile), "pos.log should exist at " + logFile);

            List<String> lines = Files.readAllLines(logFile);
            String joined = String.join("\n", lines);
            assertTrue(joined.contains(marker), "println output should be in the log file");
            assertTrue(joined.contains(marker + "-ex"), "stack traces should be in the log file");
            assertTrue(joined.contains(marker + "-slf4j"), "slf4j output should be in the log file");
        } finally {
            System.setOut(realOut);
            System.setErr(realErr);
        }
    }
}

package com.pos.scanner;

import com.google.gson.*;
import java.io.IOException;

/** Check desktop capabilities before offering staff sign-in. */
final class ScannerHandshake {
    static void requireSupported(String frame) throws IOException {
        try {
            JsonObject hello=JsonParser.parseString(frame).getAsJsonObject();
            if(!"connected".equals(hello.get("type").getAsString())
                    || hello.get("protocolVersion").getAsInt()<4
                    || !hello.get("requiresLogin").getAsBoolean())throw new IllegalArgumentException();
        } catch(RuntimeException e) {
            throw new IncompatibleDesktop();
        }
    }
    static final class IncompatibleDesktop extends IOException {
        IncompatibleDesktop(){super("Update the desktop and phone app together, then scan the desktop's current connection QR code.");}
    }
}

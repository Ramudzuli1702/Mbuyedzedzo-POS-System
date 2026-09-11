package com.pos.license;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineFingerprintTest {

    @Test
    void isA64CharHexHashAndStable() {
        String a = MachineFingerprint.get();
        String b = MachineFingerprint.get();
        assertNotNull(a);
        assertEquals(64, a.length());
        assertTrue(a.matches("[0-9a-f]{64}"));
        assertEquals(a, b, "fingerprint must be stable within a run");
    }
}

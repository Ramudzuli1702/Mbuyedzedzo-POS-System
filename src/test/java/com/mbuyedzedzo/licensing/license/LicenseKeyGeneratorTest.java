package com.mbuyedzedzo.licensing.license;

import com.mbuyedzedzo.licensing.domain.Product;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LicenseKeyGeneratorTest {

    private final LicenseKeyGenerator gen = new LicenseKeyGenerator();

    @RepeatedTest(50)
    void generatedKeyHasTheRightShapeAndPassesItsOwnChecksum() {
        String key = gen.generate(Product.POS_STANDARD);
        assertTrue(key.matches("POS-\\d{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"), key);
        assertTrue(gen.hasValidChecksum(key), key);
    }

    @Test
    void retailKeysUseTheRetPrefix() {
        assertTrue(gen.generate(Product.POS_RETAIL).startsWith("RET-"));
    }

    @Test
    void aMistypedKeyFailsTheChecksum() {
        String key = gen.generate(Product.POS_STANDARD);
        // flip one character in the body
        char[] c = key.toCharArray();
        int i = 5; // inside the year/block area
        c[i] = c[i] == 'A' ? 'B' : 'A';
        assertFalse(gen.hasValidChecksum(new String(c)));
    }

    @Test
    void checksumIsCaseInsensitiveAndTrims() {
        String key = gen.generate(Product.POS_STANDARD);
        assertTrue(gen.hasValidChecksum("  " + key.toLowerCase() + "  "));
    }

    @Test
    void nonsenseIsRejected() {
        assertFalse(gen.hasValidChecksum(null));
        assertFalse(gen.hasValidChecksum("not-a-key"));
        assertFalse(gen.hasValidChecksum("POS-2026-XXXX-XXXX-XXXX"));
    }
}

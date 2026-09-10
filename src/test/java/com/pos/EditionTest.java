package com.pos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditionTest {

    @Test
    void standardHasCustomersRetailDoesNot() {
        assertTrue(Edition.STANDARD.hasCustomers());
        assertFalse(Edition.STANDARD.isRetail());

        assertFalse(Edition.RETAIL.hasCustomers());
        assertTrue(Edition.RETAIL.isRetail());
    }

    @Test
    void currentIsResolvedAndConsistent() {
        // Value depends on the build profile (default STANDARD, `-P retail` RETAIL).
        Edition e = Edition.current();
        assertNotNull(e);
        assertEquals(e == Edition.STANDARD, e.hasCustomers());
        assertEquals(e == Edition.RETAIL,   e.isRetail());
    }

    @Test
    void displayNamesAreDistinctAndNonEmpty() {
        assertNotEquals(Edition.STANDARD.displayName(), Edition.RETAIL.displayName());
        assertFalse(Edition.STANDARD.displayName().isBlank());
        assertFalse(Edition.RETAIL.displayName().isBlank());
    }
}

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
    void defaultsToStandardWhenSettingIsUnavailable() {
        // No database in this test — getBusinessSetting falls back to its
        // default, so current() must resolve to STANDARD, never throw.
        Edition.reload();
        assertEquals(Edition.STANDARD, Edition.current());
    }

    @Test
    void displayNamesAreDistinctAndNonEmpty() {
        assertNotEquals(Edition.STANDARD.displayName(), Edition.RETAIL.displayName());
        assertFalse(Edition.STANDARD.displayName().isBlank());
        assertFalse(Edition.RETAIL.displayName().isBlank());
    }
}

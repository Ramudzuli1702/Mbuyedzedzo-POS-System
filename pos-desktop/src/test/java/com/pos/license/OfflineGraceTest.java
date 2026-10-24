package com.pos.license;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class OfflineGraceTest {
    @Test void graceUsesSignedIssueTimeAndRejectsFutureDates() {
        Instant now=Instant.parse("2026-10-19T10:00:00Z");
        assertFalse(LicenseManager.withinOfflineGrace(now.plusSeconds(1),now));
        assertFalse(LicenseManager.withinOfflineGrace(Instant.EPOCH,now));
        assertFalse(LicenseManager.withinOfflineGrace(now.minus(Duration.ofDays(14)).minusSeconds(1),now));
        assertTrue(LicenseManager.withinOfflineGrace(now.minus(Duration.ofDays(14)),now));
        assertTrue(LicenseManager.withinOfflineGrace(now.minus(Duration.ofDays(8)),now));
    }
}

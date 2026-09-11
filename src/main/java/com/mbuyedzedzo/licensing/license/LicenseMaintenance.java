package com.mbuyedzedzo.licensing.license;

import com.mbuyedzedzo.licensing.domain.AuditLog;
import com.mbuyedzedzo.licensing.domain.License;
import com.mbuyedzedzo.licensing.domain.LicenseStatus;
import com.mbuyedzedzo.licensing.repo.AuditLogRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Flips ISSUED/ACTIVE licences whose expiry has passed to EXPIRED. */
@Component
public class LicenseMaintenance {

    private static final Logger log = LoggerFactory.getLogger(LicenseMaintenance.class);

    private final LicenseRepo licenses;
    private final AuditLogRepo audit;

    public LicenseMaintenance(LicenseRepo licenses, AuditLogRepo audit) {
        this.licenses = licenses;
        this.audit = audit;
    }

    /** Every hour, and once shortly after startup. */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 30_000)
    @Transactional
    public void sweepExpired() {
        Instant cutoff = Instant.now();
        List<License> due = licenses.expiringBetween(Instant.EPOCH, cutoff);
        int n = 0;
        for (License l : due) {
            if (l.getStatus() == LicenseStatus.EXPIRED) continue;
            l.setStatus(LicenseStatus.EXPIRED);
            licenses.save(l);
            AuditLog a = new AuditLog();
            a.setActor("system");
            a.setAction("EXPIRE");
            a.setTarget(l.getLicenseKey());
            a.setDetail("expired on " + l.getExpiresAt());
            audit.save(a);
            n++;
        }
        if (n > 0) log.info("Expired {} licence(s).", n);
    }
}

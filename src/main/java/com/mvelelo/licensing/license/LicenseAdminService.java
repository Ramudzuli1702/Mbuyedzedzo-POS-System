package com.mvelelo.licensing.license;

import com.mvelelo.licensing.domain.*;
import com.mvelelo.licensing.repo.ActivationRepo;
import com.mvelelo.licensing.repo.AuditLogRepo;
import com.mvelelo.licensing.repo.LicenseRepo;
import com.mvelelo.licensing.repo.TransferLogRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Admin-side license lifecycle: issue, revoke, suspend, renew, transfer. */
@Service
public class LicenseAdminService {

    private final LicenseRepo licenses;
    private final ActivationRepo activations;
    private final TransferLogRepo transfers;
    private final AuditLogRepo audit;
    private final LicenseKeyGenerator keyGen;

    public LicenseAdminService(LicenseRepo licenses, ActivationRepo activations,
                               TransferLogRepo transfers, AuditLogRepo audit,
                               LicenseKeyGenerator keyGen) {
        this.licenses = licenses;
        this.activations = activations;
        this.transfers = transfers;
        this.audit = audit;
        this.keyGen = keyGen;
    }

    @Transactional
    public License issue(Product product, LicenseType type, int maxMachines,
                         Instant expiresAt, Long customerId, Long issuedBy, String notes, String actor) {
        License l = new License();
        l.setLicenseKey(keyGen.generateUnique(product, licenses::existsByLicenseKey));
        l.setProduct(product);
        l.setType(type);
        l.setStatus(LicenseStatus.ISSUED);
        l.setMaxMachines(Math.max(1, maxMachines));
        l.setExpiresAt(type == LicenseType.PERPETUAL ? null : expiresAt);
        l.setCustomerId(customerId);
        l.setIssuedBy(issuedBy);
        l.setNotes(notes);
        licenses.save(l);
        writeAudit(actor, "ISSUE", l.getLicenseKey(),
                product + " " + type + " x" + l.getMaxMachines());
        return l;
    }

    @Transactional
    public void revoke(long licenseId, String reason, String actor) {
        License l = licenses.findById(licenseId).orElseThrow();
        l.setStatus(LicenseStatus.REVOKED);
        l.setRevokedAt(Instant.now());
        l.setRevokeReason(reason);
        licenses.save(l);
        writeAudit(actor, "REVOKE", l.getLicenseKey(), reason);
    }

    @Transactional
    public void setSuspended(long licenseId, boolean suspended, String actor) {
        License l = licenses.findById(licenseId).orElseThrow();
        l.setStatus(suspended ? LicenseStatus.SUSPENDED
                : (activations.countByLicenseIdAndActiveTrue(licenseId) > 0
                        ? LicenseStatus.ACTIVE : LicenseStatus.ISSUED));
        licenses.save(l);
        writeAudit(actor, suspended ? "SUSPEND" : "UNSUSPEND", l.getLicenseKey(), null);
    }

    @Transactional
    public void renew(long licenseId, Instant newExpiry, String actor) {
        License l = licenses.findById(licenseId).orElseThrow();
        l.setExpiresAt(newExpiry);
        if (l.getStatus() == LicenseStatus.EXPIRED) {
            l.setStatus(activations.countByLicenseIdAndActiveTrue(licenseId) > 0
                    ? LicenseStatus.ACTIVE : LicenseStatus.ISSUED);
        }
        licenses.save(l);
        writeAudit(actor, "RENEW", l.getLicenseKey(), "until " + newExpiry);
    }

    /** Frees every machine slot so the key can be activated on a new machine. */
    @Transactional
    public void resetMachineBinding(long licenseId, String reason, String actor) {
        License l = licenses.findById(licenseId).orElseThrow();
        for (Activation a : activations.findByLicenseIdOrderByActivatedAtDesc(licenseId)) {
            if (a.isActive()) {
                a.setActive(false);
                activations.save(a);
                TransferLog t = new TransferLog();
                t.setLicenseId(licenseId);
                t.setOldFingerprint(a.getMachineFingerprint());
                t.setReason(reason);
                t.setPerformedBy(null);
                transfers.save(t);
            }
        }
        writeAudit(actor, "TRANSFER_RESET", l.getLicenseKey(), reason);
    }

    private void writeAudit(String actor, String action, String target, String detail) {
        AuditLog a = new AuditLog();
        a.setActor(actor);
        a.setAction(action);
        a.setTarget(target);
        a.setDetail(detail);
        audit.save(a);
    }
}

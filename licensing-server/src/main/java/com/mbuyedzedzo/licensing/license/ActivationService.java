package com.mbuyedzedzo.licensing.license;

import com.mbuyedzedzo.licensing.api.LicenseException;
import com.mbuyedzedzo.licensing.api.LicenseException.Code;
import com.mbuyedzedzo.licensing.config.LicensingProperties;
import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.repo.ActivationRepo;
import com.mbuyedzedzo.licensing.repo.AuditLogRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Everything the desktop-facing API does: activate, validate, trial, deactivate. */
@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final LicenseRepo licenses;
    private final ActivationRepo activations;
    private final AuditLogRepo audit;
    private final LicenseKeyGenerator keyGen;
    private final LicenseTokenService tokens;
    private final LicensingProperties props;

    public ActivationService(LicenseRepo licenses, ActivationRepo activations, AuditLogRepo audit,
                             LicenseKeyGenerator keyGen, LicenseTokenService tokens,
                             LicensingProperties props) {
        this.licenses = licenses;
        this.activations = activations;
        this.audit = audit;
        this.keyGen = keyGen;
        this.tokens = tokens;
        this.props = props;
    }

    public record Result(LicenseTokenService.Issued token, License license) {}

    // ── Activate ────────────────────────────────────────────────────────────

    @Transactional
    public Result activate(String key, String fingerprint, String machineLabel,
                           Product product, String appVersion) {
        if (!keyGen.hasValidChecksum(key)) throw new LicenseException(Code.BAD_CHECKSUM);

        License license = licenses.findByLicenseKey(normalise(key))
                .orElseThrow(() -> new LicenseException(Code.INVALID_KEY));

        if (license.getProduct() != product) {
            throw new LicenseException(Code.WRONG_PRODUCT,
                    "This key is for " + license.getProduct() + ", not " + product);
        }
        assertUsable(license);

        Activation activation = activations
                .findByLicenseIdAndMachineFingerprint(license.getId(), fingerprint)
                .orElse(null);

        if (activation == null) {
            long active = activations.countByLicenseIdAndActiveTrue(license.getId());
            if (active >= license.getMaxMachines()) {
                throw new LicenseException(Code.MACHINE_LIMIT,
                        "This key is already active on its maximum of " + license.getMaxMachines()
                                + " machine(s). Transfer it from the admin portal.");
            }
            activation = new Activation();
            activation.setLicenseId(license.getId());
            activation.setMachineFingerprint(fingerprint);
        }
        activation.setMachineLabel(machineLabel);
        activation.setAppVersion(appVersion);
        activation.setActive(true);
        activation.setLastSeenAt(Instant.now());
        activations.save(activation);

        if (license.getStatus() == LicenseStatus.ISSUED) {
            license.setStatus(LicenseStatus.ACTIVE);
            licenses.save(license);
        }

        writeAudit("ACTIVATE", key, "machine=" + shortFp(fingerprint) + " label=" + machineLabel);
        return new Result(tokens.issue(license, fingerprint), license);
    }

    // ── Validate / refresh ──────────────────────────────────────────────────

    @Transactional
    public Result validate(String key, String fingerprint) {
        License license = licenses.findByLicenseKey(normalise(key))
                .orElseThrow(() -> new LicenseException(Code.INVALID_KEY));

        Activation activation = activations
                .findByLicenseIdAndMachineFingerprint(license.getId(), fingerprint)
                .filter(Activation::isActive)
                .orElseThrow(() -> new LicenseException(Code.NOT_ACTIVATED,
                        "This machine is not activated for that key."));

        assertUsable(license);

        activation.setLastSeenAt(Instant.now());
        activations.save(activation);
        return new Result(tokens.issue(license, fingerprint), license);
    }

    // ── Trial ───────────────────────────────────────────────────────────────

    @Transactional
    public Result startTrial(Product product, String fingerprint, String machineLabel, String appVersion) {
        long usedOnThisMachine = activations.findByMachineFingerprint(fingerprint).stream()
                .map(a -> licenses.findById(a.getLicenseId()).orElse(null))
                .filter(l -> l != null && l.getType() == LicenseType.TRIAL && l.getProduct() == product)
                .count();
        if (usedOnThisMachine >= props.trial().maxPerMachine()) {
            throw new LicenseException(Code.TRIAL_ALREADY_USED,
                    "A trial has already been used on this machine.");
        }

        License license = new License();
        license.setLicenseKey(uniqueKey(product));
        license.setProduct(product);
        license.setType(LicenseType.TRIAL);
        license.setStatus(LicenseStatus.ACTIVE);
        license.setMaxMachines(1);
        license.setExpiresAt(Instant.now().plus(props.trial().days(), ChronoUnit.DAYS));
        license.setNotes("Self-service trial");
        licenses.save(license);

        Activation activation = new Activation();
        activation.setLicenseId(license.getId());
        activation.setMachineFingerprint(fingerprint);
        activation.setMachineLabel(machineLabel);
        activation.setAppVersion(appVersion);
        activations.save(activation);

        writeAudit("TRIAL_START", license.getLicenseKey(), "machine=" + shortFp(fingerprint));
        return new Result(tokens.issue(license, fingerprint), license);
    }

    // ── Deactivate (frees a machine slot) ───────────────────────────────────

    @Transactional
    public void deactivate(String key, String fingerprint) {
        License license = licenses.findByLicenseKey(normalise(key))
                .orElseThrow(() -> new LicenseException(Code.INVALID_KEY));
        activations.findByLicenseIdAndMachineFingerprint(license.getId(), fingerprint)
                .ifPresent(a -> {
                    a.setActive(false);
                    activations.save(a);
                    writeAudit("DEACTIVATE", key, "machine=" + shortFp(fingerprint));
                });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void assertUsable(License license) {
        switch (license.getStatus()) {
            case REVOKED  -> throw new LicenseException(Code.REVOKED, "This licence has been revoked.");
            case SUSPENDED -> throw new LicenseException(Code.SUSPENDED, "This licence is suspended.");
            default -> { /* ISSUED / ACTIVE / EXPIRED handled below */ }
        }
        if (license.isExpired()) {
            if (license.getStatus() != LicenseStatus.EXPIRED) {
                license.setStatus(LicenseStatus.EXPIRED);
                licenses.save(license);
            }
            throw new LicenseException(Code.EXPIRED, "This licence expired on " + license.getExpiresAt() + ".");
        }
    }

    private String uniqueKey(Product product) {
        return keyGen.generateUnique(product, licenses::existsByLicenseKey);
    }

    private static String normalise(String key) {
        return key == null ? "" : key.trim().toUpperCase();
    }

    private static String shortFp(String fp) {
        return fp == null ? "?" : fp.substring(0, Math.min(12, fp.length()));
    }

    private void writeAudit(String action, String target, String detail) {
        AuditLog a = new AuditLog();
        a.setActor("api");
        a.setAction(action);
        a.setTarget(target);
        a.setDetail(detail);
        audit.save(a);
    }
}

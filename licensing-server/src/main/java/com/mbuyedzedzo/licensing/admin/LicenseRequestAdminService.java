package com.mbuyedzedzo.licensing.admin;

import com.mbuyedzedzo.licensing.commerce.EmailService;
import com.mbuyedzedzo.licensing.commerce.PricingService;
import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.license.LicenseAdminService;
import com.mbuyedzedzo.licensing.repo.AuditLogRepo;
import com.mbuyedzedzo.licensing.repo.CustomerRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRequestRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * Admin-side handling of self-service license requests — the stand-in for
 * the PayFast checkout while that's still in testing. Approving does exactly
 * what {@code OrderFulfillmentService.fulfil()} does for a real payment:
 * find-or-create the customer, issue the license, generate a one-time
 * account-setup link for a new customer, and email both to them.
 */
@Service
public class LicenseRequestAdminService {

    private static final SecureRandom random = new SecureRandom();

    private final LicenseRequestRepo requests;
    private final CustomerRepo customers;
    private final LicenseAdminService licenseAdmin;
    private final EmailService email;
    private final PricingService pricing;
    private final AuditLogRepo audit;
    private final String accountBaseUrl;

    public LicenseRequestAdminService(LicenseRequestRepo requests, CustomerRepo customers,
                                       LicenseAdminService licenseAdmin, EmailService email,
                                       PricingService pricing, AuditLogRepo audit,
                                       org.springframework.core.env.Environment env) {
        this.requests = requests;
        this.customers = customers;
        this.licenseAdmin = licenseAdmin;
        this.email = email;
        this.pricing = pricing;
        this.audit = audit;
        this.accountBaseUrl = env.getProperty("app.base-url", "http://localhost:8080");
    }

    public List<LicenseRequest> listPending() {
        return requests.findByStatusOrderByRequestedAtAsc(LicenseRequestStatus.PENDING);
    }

    public long countPending() {
        return requests.countByStatus(LicenseRequestStatus.PENDING);
    }

    @Transactional
    public void approve(AdminPrincipal me, long requestId, int maxMachines) {
        LicenseRequest req = requirePending(requestId);

        // Built and saved exactly once, fully populated — saving a brand-new
        // customer before its token is generated (a separate save() call
        // later) inserts it with set_password_token = NULL, and SQL Server
        // only allows one NULL per row in a unique column: the second new
        // customer ever created this way collides with the first and the
        // whole approval fails with a constraint violation.
        boolean isNewCustomer = customers.findByEmail(req.getEmail()).isEmpty();
        Customer customer = customers.findByEmail(req.getEmail()).orElseGet(Customer::new);
        if (isNewCustomer) {
            customer.setOrgName(req.getBusinessName());
            customer.setContactName(req.getContactName());
            customer.setEmail(req.getEmail());
            customer.setPhone(req.getPhone());
            customer.setCreatedBy(me.id());
        }

        String setPasswordUrl = null;
        if (customer.getPasswordHash() == null) {
            String token = generateToken();
            customer.setSetPasswordToken(token);
            customer.setSetPasswordTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
            setPasswordUrl = accountBaseUrl + "/account/set-password?token=" + token;
        }
        customer = customers.save(customer);

        Instant expiresAt = req.getLicenseType() == LicenseType.SUBSCRIPTION
                ? Instant.now().plus(31, ChronoUnit.DAYS)
                : null;

        License license = licenseAdmin.issue(
                req.getProduct(), req.getLicenseType(), Math.max(1, maxMachines), expiresAt,
                customer.getId(), me.id(),
                "Approved license request #" + req.getId(), me.email());

        String licenseTypeLabel = req.getLicenseType() == LicenseType.SUBSCRIPTION
                ? "monthly subscription" : "perpetual licence";
        email.sendLicenseEmail(req.getEmail(), req.getContactName(),
                pricing.displayName(req.getProduct()), license.getLicenseKey(),
                licenseTypeLabel, setPasswordUrl);

        req.setStatus(LicenseRequestStatus.APPROVED);
        req.setResolvedAt(Instant.now());
        req.setResolvedBy(me.id());
        req.setCustomerId(customer.getId());
        req.setLicenseId(license.getId());
        requests.save(req);

        writeAudit(me, "LICENSE_REQUEST_APPROVED", req, "license=" + license.getLicenseKey());
    }

    @Transactional
    public void reject(AdminPrincipal me, long requestId, String reason) {
        LicenseRequest req = requirePending(requestId);
        req.setStatus(LicenseRequestStatus.REJECTED);
        req.setResolvedAt(Instant.now());
        req.setResolvedBy(me.id());
        req.setRejectReason(reason);
        requests.save(req);

        writeAudit(me, "LICENSE_REQUEST_REJECTED", req, reason);
    }

    private LicenseRequest requirePending(long requestId) {
        LicenseRequest req = requests.findById(requestId)
                .orElseThrow(() -> new CustomerAdminService.NotFound("license request " + requestId));
        if (req.getStatus() != LicenseRequestStatus.PENDING) {
            throw new IllegalStateException("This request was already " + req.getStatus().name().toLowerCase() + ".");
        }
        return req;
    }

    private void writeAudit(AdminPrincipal me, String action, LicenseRequest req, String detail) {
        AuditLog a = new AuditLog();
        a.setActor(me.email());
        a.setAction(action);
        a.setTarget("license_request#" + req.getId());
        a.setDetail(detail);
        audit.save(a);
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

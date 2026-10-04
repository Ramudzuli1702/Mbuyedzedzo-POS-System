package com.mbuyedzedzo.licensing.admin;

import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.license.LicenseAdminService;
import com.mbuyedzedzo.licensing.repo.AuditLogRepo;
import com.mbuyedzedzo.licensing.repo.CustomerRepo;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import com.mbuyedzedzo.licensing.repo.TransferRequestRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Customer self-service transfer requests, awaiting admin approval — the
 * "customer self-service is planned" line from the README. Approving calls
 * the existing LicenseAdminService.resetMachineBinding (which writes its own
 * TransferLog entry, unchanged), so an approved self-service request behaves
 * exactly like an admin-initiated transfer.
 */
@Service
public class TransferRequestAdminService {

    private final TransferRequestRepo requests;
    private final LicenseRepo licenses;
    private final CustomerRepo customers;
    private final LicenseAdminService licenseAdmin;
    private final AuditLogRepo audit;

    public TransferRequestAdminService(TransferRequestRepo requests, LicenseRepo licenses, CustomerRepo customers,
                                        LicenseAdminService licenseAdmin, AuditLogRepo audit) {
        this.requests = requests;
        this.licenses = licenses;
        this.customers = customers;
        this.licenseAdmin = licenseAdmin;
        this.audit = audit;
    }

    public record PendingItem(TransferRequest request, License license, Customer customer) {}

    /**
     * Same visibility rule LicenseQueryService.detail() already uses: an agent
     * only sees requests for licenses they personally issued; a storefront
     * purchase (issuedBy=null) is visible to super admins only.
     */
    public List<PendingItem> listPending(AdminPrincipal me) {
        return requests.findByStatusOrderByRequestedAtAsc(TransferRequestStatus.PENDING).stream()
                .map(tr -> {
                    License l = licenses.findById(tr.getLicenseId()).orElse(null);
                    if (l == null) return null;
                    if (!me.isSuperAdmin() && (l.getIssuedBy() == null || !l.getIssuedBy().equals(me.id()))) return null;
                    Customer c = customers.findById(tr.getCustomerId()).orElse(null);
                    return new PendingItem(tr, l, c);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public void approve(AdminPrincipal me, long requestId) {
        TransferRequest tr = requireVisible(me, requestId);
        licenseAdmin.resetMachineBinding(tr.getLicenseId(),
                "Approved self-service transfer request #" + tr.getId(), me.email());
        resolve(tr, TransferRequestStatus.APPROVED, me);
    }

    @Transactional
    public void deny(AdminPrincipal me, long requestId) {
        TransferRequest tr = requireVisible(me, requestId);
        resolve(tr, TransferRequestStatus.DENIED, me);
    }

    private TransferRequest requireVisible(AdminPrincipal me, long requestId) {
        TransferRequest tr = requests.findById(requestId)
                .orElseThrow(() -> new CustomerAdminService.NotFound("transfer request " + requestId));
        License l = licenses.findById(tr.getLicenseId())
                .orElseThrow(() -> new CustomerAdminService.NotFound("transfer request " + requestId));
        if (!me.isSuperAdmin() && (l.getIssuedBy() == null || !l.getIssuedBy().equals(me.id()))) {
            throw new CustomerAdminService.NotFound("transfer request " + requestId);
        }
        if (tr.getStatus() != TransferRequestStatus.PENDING) {
            throw new IllegalStateException("This request was already " + tr.getStatus().name().toLowerCase() + ".");
        }
        return tr;
    }

    private void resolve(TransferRequest tr, TransferRequestStatus outcome, AdminPrincipal me) {
        tr.setStatus(outcome);
        tr.setResolvedAt(Instant.now());
        tr.setResolvedBy(me.id());
        requests.save(tr);

        AuditLog a = new AuditLog();
        a.setActor(me.email());
        a.setAction("TRANSFER_REQUEST_" + outcome.name());
        a.setTarget("transfer_request#" + tr.getId());
        a.setDetail("license_id=" + tr.getLicenseId());
        audit.save(a);
    }
}

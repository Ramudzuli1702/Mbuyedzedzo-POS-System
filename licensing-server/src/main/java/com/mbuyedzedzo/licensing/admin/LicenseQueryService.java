package com.mbuyedzedzo.licensing.admin;

import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.repo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LicenseQueryService {

    private final LicenseRepo licenses;
    private final ActivationRepo activations;
    private final TransferLogRepo transfers;
    private final CustomerRepo customers;

    public LicenseQueryService(LicenseRepo licenses, ActivationRepo activations,
                               TransferLogRepo transfers, CustomerRepo customers) {
        this.licenses = licenses;
        this.activations = activations;
        this.transfers = transfers;
        this.customers = customers;
    }

    public Page<License> list(AdminPrincipal me, Pageable pageable) {
        return me.isSuperAdmin()
                ? licenses.findAll(pageable)
                : licenses.findByIssuedBy(me.id(), pageable);
    }

    public record Detail(License license, Customer customer,
                         List<Activation> activations, List<TransferLog> transfers,
                         long activeMachines) {}

    public Detail detail(AdminPrincipal me, long id) {
        License l = licenses.findById(id).orElseThrow(() -> new CustomerAdminService.NotFound("license " + id));
        if (!me.isSuperAdmin() && (l.getIssuedBy() == null || !l.getIssuedBy().equals(me.id()))) {
            throw new CustomerAdminService.NotFound("license " + id);
        }
        Customer c = l.getCustomerId() == null ? null : customers.findById(l.getCustomerId()).orElse(null);
        return new Detail(l, c,
                activations.findByLicenseIdOrderByActivatedAtDesc(id),
                transfers.findByLicenseIdOrderByPerformedAtDesc(id),
                activations.countByLicenseIdAndActiveTrue(id));
    }

    /** Customer names by id, for rendering a license list. */
    public Map<Long, String> customerNames(List<License> page) {
        List<Long> ids = page.stream().map(License::getCustomerId).filter(java.util.Objects::nonNull).distinct().toList();
        return customers.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getOrgName));
    }
}

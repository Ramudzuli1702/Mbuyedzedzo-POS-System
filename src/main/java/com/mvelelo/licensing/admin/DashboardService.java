package com.mvelelo.licensing.admin;

import com.mvelelo.licensing.domain.License;
import com.mvelelo.licensing.domain.LicenseStatus;
import com.mvelelo.licensing.repo.ActivationRepo;
import com.mvelelo.licensing.repo.CustomerRepo;
import com.mvelelo.licensing.repo.LicenseRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DashboardService {

    private final LicenseRepo licenses;
    private final CustomerRepo customers;
    private final ActivationRepo activations;

    public DashboardService(LicenseRepo licenses, CustomerRepo customers, ActivationRepo activations) {
        this.licenses = licenses;
        this.customers = customers;
        this.activations = activations;
    }

    public record Stats(long activeLicenses, long trialsOrIssued, long revoked,
                        long customers, long activationsThisMonth,
                        List<License> expiringSoon) {}

    public Stats stats() {
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        return new Stats(
                licenses.countByStatus(LicenseStatus.ACTIVE),
                licenses.countByStatus(LicenseStatus.ISSUED),
                licenses.countByStatus(LicenseStatus.REVOKED),
                customers.count(),
                activations.countByActivatedAtAfter(monthAgo),
                licenses.expiringBetween(Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS)));
    }
}

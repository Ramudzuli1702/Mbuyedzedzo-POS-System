package com.mvelelo.licensing.repo;

import com.mvelelo.licensing.domain.Activation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivationRepo extends JpaRepository<Activation, Long> {
    Optional<Activation> findByLicenseIdAndMachineFingerprint(Long licenseId, String fingerprint);
    long countByLicenseIdAndActiveTrue(Long licenseId);
    List<Activation> findByLicenseIdOrderByActivatedAtDesc(Long licenseId);
    long countByActivatedAtAfter(Instant since);
}

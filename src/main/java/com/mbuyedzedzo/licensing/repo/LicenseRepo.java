package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.License;
import com.mbuyedzedzo.licensing.domain.LicenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LicenseRepo extends JpaRepository<License, Long> {

    Optional<License> findByLicenseKey(String licenseKey);
    boolean existsByLicenseKey(String licenseKey);
    Page<License> findByIssuedBy(Long issuedBy, Pageable pageable);
    List<License> findByCustomerId(Long customerId);
    long countByStatus(LicenseStatus status);

    @Query("""
           select l from License l
           where l.expiresAt is not null
             and l.expiresAt between :from and :to
             and l.status in (com.mbuyedzedzo.licensing.domain.LicenseStatus.ACTIVE,
                              com.mbuyedzedzo.licensing.domain.LicenseStatus.ISSUED)
           order by l.expiresAt asc
           """)
    List<License> expiringBetween(@Param("from") Instant from, @Param("to") Instant to);
}

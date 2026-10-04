package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.LicenseRequest;
import com.mbuyedzedzo.licensing.domain.LicenseRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicenseRequestRepo extends JpaRepository<LicenseRequest, Long> {
    List<LicenseRequest> findByStatusOrderByRequestedAtAsc(LicenseRequestStatus status);
    long countByStatus(LicenseRequestStatus status);
}

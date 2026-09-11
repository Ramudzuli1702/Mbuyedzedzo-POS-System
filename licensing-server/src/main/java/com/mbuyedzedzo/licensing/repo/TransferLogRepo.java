package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.TransferLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferLogRepo extends JpaRepository<TransferLog, Long> {
    List<TransferLog> findByLicenseIdOrderByPerformedAtDesc(Long licenseId);
    long countByLicenseId(Long licenseId);
}

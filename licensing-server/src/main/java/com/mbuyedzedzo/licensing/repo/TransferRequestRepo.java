package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.TransferRequest;
import com.mbuyedzedzo.licensing.domain.TransferRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferRequestRepo extends JpaRepository<TransferRequest, Long> {
    List<TransferRequest> findByCustomerId(Long customerId);
    List<TransferRequest> findByStatusOrderByRequestedAtAsc(TransferRequestStatus status);
    List<TransferRequest> findByLicenseIdAndStatus(Long licenseId, TransferRequestStatus status);
}

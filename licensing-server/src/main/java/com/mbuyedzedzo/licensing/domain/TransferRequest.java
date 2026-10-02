package com.mbuyedzedzo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A customer's self-service request to free up their license's machine slot
 * (e.g. they got a new PC). An admin/agent approves (calling the existing
 * {@code LicenseAdminService.resetMachineBinding}, which writes its own
 * {@link TransferLog} entry as it already does) or denies it.
 */
@Entity
@Table(name = "transfer_request")
@Getter
@Setter
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_id", nullable = false)
    private Long licenseId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferRequestStatus status = TransferRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;
}

package com.mbuyedzedzo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A visitor's self-service request for a license — the stand-in for the
 * PayFast checkout while that's still in testing. An admin/agent reviews it
 * in the portal: approving issues a real license key (and a login for a new
 * customer) and emails both; rejecting records a reason.
 */
@Entity
@Table(name = "license_request")
@Getter
@Setter
public class LicenseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false)
    private LicenseType licenseType;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LicenseRequestStatus status = LicenseRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "license_id")
    private Long licenseId;

    @Column(name = "reject_reason")
    private String rejectReason;
}

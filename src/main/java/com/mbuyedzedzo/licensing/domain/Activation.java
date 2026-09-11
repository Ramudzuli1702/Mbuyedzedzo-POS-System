package com.mbuyedzedzo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "activation",
       uniqueConstraints = @UniqueConstraint(name = "uq_activation_slot",
               columnNames = {"license_id", "machine_fingerprint"}))
@Getter
@Setter
public class Activation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_id", nullable = false)
    private Long licenseId;

    @Column(name = "machine_fingerprint", nullable = false, length = 64)
    private String machineFingerprint;

    @Column(name = "machine_label")
    private String machineLabel;

    @Column(name = "app_version")
    private String appVersion;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();
}

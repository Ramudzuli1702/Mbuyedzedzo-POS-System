package com.mvelelo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** email of the acting admin, or "api" / "system". */
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    private String target;

    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant at = Instant.now();
}

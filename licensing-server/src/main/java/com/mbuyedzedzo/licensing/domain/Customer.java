package com.mbuyedzedzo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "customer")
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_name", nullable = false)
    private String orgName;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String email;

    /** Set once the customer has followed the "set your password" link. Null = no self-service login yet. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Single-use token emailed after a confirmed purchase so the buyer can set their password. */
    @Column(name = "set_password_token")
    private String setPasswordToken;

    @Column(name = "set_password_token_expires_at")
    private Instant setPasswordTokenExpiresAt;

    private String phone;

    private String notes;

    /** admin_user.id of the agent/admin who created this customer. Null = self-registered via the storefront. */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

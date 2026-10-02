package com.mbuyedzedzo.licensing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A storefront purchase attempt — one row per checkout, regardless of outcome. */
@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Our own order reference — sent to PayFast as m_payment_id, echoed back in the ITN. */
    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    /** Set once the buyer's account is identified/created (at ITN-verified payment time). */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "buyer_email", nullable = false)
    private String buyerEmail;

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false)
    private LicenseType licenseType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "ZAR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, length = 20)
    private String gateway = "PAYFAST";

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "license_id")
    private Long licenseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;
}

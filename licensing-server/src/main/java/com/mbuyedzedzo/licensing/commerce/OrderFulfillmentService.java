package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.license.LicenseAdminService;
import com.mbuyedzedzo.licensing.repo.CustomerRepo;
import com.mbuyedzedzo.licensing.repo.OrderRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * What happens once a PayFast ITN has been verified (signature + the
 * validate-postback both confirm it): find-or-create the buyer's account,
 * issue the license, and email it to them. Idempotent — PayFast can and does
 * resend ITNs, and a second delivery must not issue a second license.
 */
@Service
public class OrderFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentService.class);
    private static final SecureRandom random = new SecureRandom();

    private final OrderRepo orders;
    private final CustomerRepo customers;
    private final LicenseAdminService licenseAdminService;
    private final EmailService email;
    private final PricingService pricing;
    private final String accountBaseUrl;

    public OrderFulfillmentService(OrderRepo orders, CustomerRepo customers,
                                    LicenseAdminService licenseAdminService, EmailService email,
                                    PricingService pricing,
                                    org.springframework.core.env.Environment env) {
        this.orders = orders;
        this.customers = customers;
        this.licenseAdminService = licenseAdminService;
        this.email = email;
        this.pricing = pricing;
        this.accountBaseUrl = env.getProperty("app.base-url", "http://localhost:8080");
    }

    public enum Outcome { FULFILLED, ALREADY_PAID, UNKNOWN_ORDER, AMOUNT_MISMATCH }

    /**
     * Entry point for the webhook: locks the order row for the rest of this
     * transaction (see {@link OrderRepo#findByReferenceForUpdate}), so a
     * second concurrent ITN for the same order blocks here instead of racing
     * the idempotency check below, and checks the paid amount under that same
     * lock — not as a separate, racily-TOCTOU-able step beforehand.
     */
    @Transactional
    public Outcome handleVerifiedPayment(String reference, BigDecimal amountPaid, String gatewayPaymentId) {
        Optional<Order> maybe = orders.findByReferenceForUpdate(reference);
        if (maybe.isEmpty()) return Outcome.UNKNOWN_ORDER;

        Order order = maybe.get();
        if (order.getStatus() == OrderStatus.PAID) return Outcome.ALREADY_PAID;

        if (amountPaid == null || amountPaid.compareTo(order.getAmount()) < 0) {
            log.warn("Order {} amount mismatch: expected {}, ITN said {} — NOT fulfilling",
                    reference, order.getAmount(), amountPaid);
            return Outcome.AMOUNT_MISMATCH;
        }

        fulfil(order, gatewayPaymentId);
        return Outcome.FULFILLED;
    }

    @Transactional
    public void fulfil(Order order, String gatewayPaymentId) {
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order {} already fulfilled — ignoring duplicate ITN", order.getReference());
            return;
        }

        // Built and saved exactly once, fully populated — saving a brand-new
        // customer before its token is generated (a separate save() call
        // later) inserts it with set_password_token = NULL, and SQL Server
        // only allows one NULL per row in a unique column: the second new
        // customer ever created this way collides with the first and the
        // whole fulfilment fails with a constraint violation.
        boolean isNewCustomer = customers.findByEmail(order.getBuyerEmail()).isEmpty();
        Customer customer = customers.findByEmail(order.getBuyerEmail()).orElseGet(Customer::new);
        if (isNewCustomer) {
            customer.setOrgName(order.getBuyerName());
            customer.setContactName(order.getBuyerName());
            customer.setEmail(order.getBuyerEmail());
            customer.setCreatedBy(null); // self-registered via the storefront
        }

        String setPasswordUrl = null;
        if (customer.getPasswordHash() == null) {
            String token = generateToken();
            customer.setSetPasswordToken(token);
            customer.setSetPasswordTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
            setPasswordUrl = accountBaseUrl + "/account/set-password?token=" + token;
        }
        customer = customers.save(customer);

        Instant expiresAt = order.getLicenseType() == LicenseType.SUBSCRIPTION
                ? Instant.now().plus(31, ChronoUnit.DAYS)
                : null;

        License license = licenseAdminService.issue(
                order.getProduct(), order.getLicenseType(), 1, expiresAt,
                customer.getId(), null,
                "Storefront purchase " + order.getReference(), "storefront");

        order.setStatus(OrderStatus.PAID);
        order.setGatewayPaymentId(gatewayPaymentId);
        order.setPaidAt(Instant.now());
        order.setCustomerId(customer.getId());
        order.setLicenseId(license.getId());
        orders.save(order);

        String licenseTypeLabel = order.getLicenseType() == LicenseType.SUBSCRIPTION
                ? "monthly subscription" : "perpetual licence";

        email.sendLicenseEmail(order.getBuyerEmail(), order.getBuyerName(),
                pricing.displayName(order.getProduct()), license.getLicenseKey(),
                licenseTypeLabel, setPasswordUrl);

        log.info("Order {} fulfilled: license {} issued to {}",
                order.getReference(), license.getLicenseKey(), order.getBuyerEmail());
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

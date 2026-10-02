package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.config.PayFastProperties;
import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Order;
import com.mbuyedzedzo.licensing.domain.Product;
import com.mbuyedzedzo.licensing.repo.OrderRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;

/** Starts a storefront purchase: records a pending Order, builds the signed PayFast redirect. */
@Service
public class CheckoutService {

    private static final String REF_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"; // no 0/O, 1/I/L
    private final SecureRandom random = new SecureRandom();

    private final OrderRepo orders;
    private final PricingService pricing;
    private final PayFastClient payFast;
    private final PayFastProperties payFastProps;

    public CheckoutService(OrderRepo orders, PricingService pricing, PayFastClient payFast, PayFastProperties payFastProps) {
        this.orders = orders;
        this.pricing = pricing;
        this.payFast = payFast;
        this.payFastProps = payFastProps;
    }

    public record CheckoutResult(Order order, Map<String, String> payFastFields, String processUrl) {}

    public static class InvalidCheckoutException extends RuntimeException {
        public InvalidCheckoutException(String message) { super(message); }
    }

    public CheckoutResult start(Product product, LicenseType type, String buyerName, String buyerEmail) {
        String name = buyerName == null ? "" : buyerName.trim();
        String email = buyerEmail == null ? "" : buyerEmail.trim().toLowerCase();

        // Matches customer.org_name/contact_name (VARCHAR 160) and .email (VARCHAR 190) —
        // reject clearly here rather than let an oversized submission hit a DB constraint
        // violation later, deep inside payment fulfillment.
        if (name.isEmpty() || name.length() > 160) {
            throw new InvalidCheckoutException("Please enter your name (max 160 characters).");
        }
        if (email.isEmpty() || email.length() > 190 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidCheckoutException("Please enter a valid email address.");
        }

        BigDecimal amount = pricing.priceFor(product, type);

        Order order = new Order();
        order.setReference(generateReference());
        order.setBuyerEmail(email);
        order.setBuyerName(name);
        order.setProduct(product);
        order.setLicenseType(type);
        order.setAmount(amount);
        orders.save(order);

        String[] names = splitName(order.getBuyerName());
        Map<String, String> fields = payFast.buildPaymentFields(
                order.getReference(), amount, pricing.displayName(product),
                names[0], names[1], order.getBuyerEmail());

        return new CheckoutResult(order, fields, payFastProps.processUrl());
    }

    private static String[] splitName(String full) {
        String[] parts = full.trim().split("\\s+", 2);
        return parts.length == 2 ? parts : new String[]{parts[0], ""};
    }

    private String generateReference() {
        StringBuilder sb = new StringBuilder("ORD-");
        for (int i = 0; i < 10; i++) sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
        return sb.toString();
    }
}

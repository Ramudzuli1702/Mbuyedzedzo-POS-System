package com.mbuyedzedzo.licensing.commerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Receives PayFast's Instant Transaction Notification (ITN) — a server-to-
 * server POST, independent of the buyer's browser redirect, which is the
 * only notification that's actually trustworthy (the browser redirect to
 * /buy/success can be skipped, retried, or never happen at all).
 *
 * Always returns 200: PayFast retries on anything else, and a malformed/
 * forged request is simply logged and ignored rather than surfaced as an
 * HTTP error (which would itself leak information to an attacker probing it).
 */
@RestController
public class PayFastWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayFastWebhookController.class);

    private final PayFastClient payFast;
    private final OrderFulfillmentService fulfillment;

    public PayFastWebhookController(PayFastClient payFast, OrderFulfillmentService fulfillment) {
        this.payFast = payFast;
        this.fulfillment = fulfillment;
    }

    @PostMapping("/webhooks/payfast/itn")
    public ResponseEntity<String> itn(@RequestParam Map<String, String> params) {
        String reference = params.get("m_payment_id");
        log.info("PayFast ITN received for order {}: status={}", reference, params.get("payment_status"));

        if (!payFast.signatureValid(params)) {
            log.warn("PayFast ITN for {} failed signature check — ignoring", reference);
            return ResponseEntity.ok("ignored");
        }

        if (!payFast.confirmWithPayFast(rawFormBody(params))) {
            log.warn("PayFast ITN for {} failed the validate-postback — ignoring", reference);
            return ResponseEntity.ok("ignored");
        }

        if (reference == null) return ResponseEntity.ok("ignored");

        if (!"COMPLETE".equalsIgnoreCase(params.get("payment_status"))) {
            log.info("Order {} payment_status={} — not fulfilling", reference, params.get("payment_status"));
            return ResponseEntity.ok("ok");
        }

        // Locked fetch + amount check + fulfillment all happen inside one transaction in
        // handleVerifiedPayment, so a resent/concurrent ITN for the same order can't race
        // past the idempotency check the way a separate lookup-then-act here could.
        var outcome = fulfillment.handleVerifiedPayment(
                reference, parseAmount(params.get("amount_gross")), params.get("pf_payment_id"));
        log.info("Order {}: {}", reference, outcome);
        return ResponseEntity.ok("ok");
    }

    private static BigDecimal parseAmount(String s) {
        try {
            return s == null ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reconstructs a form-urlencoded body from the parsed params, for the validate-postback. */
    private static String rawFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(UriUtils.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}

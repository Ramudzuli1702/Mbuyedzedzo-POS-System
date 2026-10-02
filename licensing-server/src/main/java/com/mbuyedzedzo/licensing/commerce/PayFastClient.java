package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.config.PayFastProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds signed PayFast payment requests and verifies Instant Transaction
 * Notifications (ITNs) — see https://developers.payfast.co.za/docs.
 *
 * Signature algorithm (identical for outgoing requests and incoming ITNs):
 * concatenate "key=urlencoded(value)" for every non-blank field, IN THE
 * ORDER THE FIELDS APPEAR (insertion order — a LinkedHashMap, not alphabetical),
 * joined with "&", with the passphrase appended last if one is configured,
 * then MD5 the result (lowercase hex). PayFast's own encoding quirk: spaces
 * become "+", not "%20" — {@link UriUtils#encode} alone doesn't do that, so
 * we post-process it.
 */
@Component
public class PayFastClient {

    private final PayFastProperties props;
    private final RestClient restClient = RestClient.create();

    public PayFastClient(PayFastProperties props) {
        this.props = props;
    }

    /** Builds the full set of form fields (incl. signature) to POST to {@link PayFastProperties#processUrl()}. */
    public Map<String, String> buildPaymentFields(String orderReference, BigDecimal amount, String itemName,
                                                   String buyerFirstName, String buyerLastName, String buyerEmail) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant_id", props.merchantId());
        fields.put("merchant_key", props.merchantKey());
        fields.put("return_url", props.returnUrl());
        fields.put("cancel_url", props.cancelUrl());
        fields.put("notify_url", props.notifyUrl());
        fields.put("name_first", buyerFirstName);
        fields.put("name_last", buyerLastName);
        fields.put("email_address", buyerEmail);
        fields.put("m_payment_id", orderReference);
        fields.put("amount", amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        fields.put("item_name", itemName);

        fields.put("signature", signature(fields));
        return fields;
    }

    /** Recomputes the signature from posted ITN fields and compares it to the one PayFast sent. */
    public boolean signatureValid(Map<String, String> postedFields) {
        String theirs = postedFields.get("signature");
        if (theirs == null) return false;

        Map<String, String> toSign = new LinkedHashMap<>(postedFields);
        toSign.remove("signature");
        return signature(toSign).equalsIgnoreCase(theirs);
    }

    /**
     * The authoritative check PayFast recommends: post the raw ITN body back
     * to their validate endpoint; only "VALID" confirms it really came from
     * PayFast (vs. someone forging a POST to our notify_url).
     */
    public boolean confirmWithPayFast(String rawPostBody) {
        try {
            ResponseEntity<String> resp = restClient.post()
                    .uri(props.validateUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(rawPostBody)
                    .retrieve()
                    .toEntity(String.class);
            return resp.getBody() != null && resp.getBody().trim().equals("VALID");
        } catch (Exception e) {
            return false;
        }
    }

    private String signature(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String value = e.getValue();
            if (value == null || value.isBlank()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(payFastEncode(value));
        }
        if (props.passphrase() != null && !props.passphrase().isBlank()) {
            sb.append('&').append("passphrase").append('=').append(payFastEncode(props.passphrase()));
        }
        return md5Hex(sb.toString());
    }

    /** PayFast wants application/x-www-form-urlencoded style: spaces as "+". */
    private static String payFastEncode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8).replace("%20", "+");
    }

    private static String md5Hex(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

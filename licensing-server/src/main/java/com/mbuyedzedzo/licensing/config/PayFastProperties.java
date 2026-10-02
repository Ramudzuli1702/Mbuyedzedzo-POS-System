package com.mbuyedzedzo.licensing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayFast merchant credentials and endpoints. Defaults are PayFast's own
 * published sandbox test credentials (no account needed) — see
 * https://developers.payfast.co.za/docs#testing — so the whole purchase flow
 * is demoable out of the box. Override every field via env vars once you
 * have a real merchant account (see licensing-server/README.md).
 */
@ConfigurationProperties(prefix = "payfast")
public record PayFastProperties(
        String merchantId,
        String merchantKey,
        String passphrase,
        String processUrl,
        String validateUrl,
        String returnUrl,
        String cancelUrl,
        String notifyUrl) {
}

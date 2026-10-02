package com.mbuyedzedzo.licensing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * List prices in ZAR. Placeholder figures — change freely in application.yml
 * (or the PRICING_* env vars) without touching any code. Subscriptions are
 * priced per month; a purchase grants one month from payment, and buying
 * again (same flow) extends it.
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        BigDecimal posStandardPerpetual,
        BigDecimal posStandardSubscription,
        BigDecimal posRetailPerpetual,
        BigDecimal posRetailSubscription) {
}

package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.config.PricingProperties;
import com.mbuyedzedzo.licensing.domain.LicenseType;
import com.mbuyedzedzo.licensing.domain.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PricingService {

    private final PricingProperties prices;

    public PricingService(PricingProperties prices) {
        this.prices = prices;
    }

    /** PERPETUAL or SUBSCRIPTION only — TRIAL isn't sold, it's self-service from the desktop app. */
    public BigDecimal priceFor(Product product, LicenseType type) {
        return switch (product) {
            case POS_STANDARD -> type == LicenseType.PERPETUAL
                    ? prices.posStandardPerpetual() : prices.posStandardSubscription();
            case POS_RETAIL -> type == LicenseType.PERPETUAL
                    ? prices.posRetailPerpetual() : prices.posRetailSubscription();
        };
    }

    public String displayName(Product product) {
        return product == Product.POS_RETAIL ? "Mbuyedzedzo Retail POS" : "Mbuyedzedzo POS System";
    }
}

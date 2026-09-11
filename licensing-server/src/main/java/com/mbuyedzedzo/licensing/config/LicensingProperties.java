package com.mbuyedzedzo.licensing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "licensing")
public record LicensingProperties(Token token, Trial trial, BootstrapAdmin bootstrapAdmin) {

    public record Token(
            String issuer,
            int ttlDays,
            String privateKeyPath,
            String publicKeyPath) {}

    public record Trial(int days, int maxPerMachine) {}

    public record BootstrapAdmin(String email, String password) {}
}

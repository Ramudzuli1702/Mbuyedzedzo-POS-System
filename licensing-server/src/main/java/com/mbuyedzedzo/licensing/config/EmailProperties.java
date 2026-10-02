package com.mbuyedzedzo.licensing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mail")
public record EmailProperties(String fromName) {
}

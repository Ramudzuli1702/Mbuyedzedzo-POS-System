package com.mbuyedzedzo.licensing.api;

import com.mbuyedzedzo.licensing.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Request/response records for the desktop-facing API (/api/v1/**).

record ActivateRequest(
        @NotBlank String key,
        @NotBlank String fingerprint,
        String machineLabel,
        @NotNull Product product,
        String appVersion) {}

record ValidateRequest(
        @NotBlank String key,
        @NotBlank String fingerprint) {}

record TrialRequest(
        @NotNull Product product,
        @NotBlank String fingerprint,
        String machineLabel,
        String appVersion) {}

record DeactivateRequest(
        @NotBlank String key,
        @NotBlank String fingerprint) {}

record TokenResponse(
        String token,
        String expiresAt,
        String licenseStatus,
        String licenseType,
        String licenseExpiresAt) {}

record ErrorResponse(String code, String message) {}

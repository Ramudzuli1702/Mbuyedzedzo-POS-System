package com.mvelelo.licensing.domain;

public enum LicenseStatus {
    ISSUED,      // generated, never activated
    ACTIVE,      // activated on >= 1 machine
    SUSPENDED,   // temporarily blocked (e.g. non-payment)
    REVOKED,     // permanently killed
    EXPIRED      // past expires_at
}

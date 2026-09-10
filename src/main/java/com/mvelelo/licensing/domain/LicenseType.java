package com.mvelelo.licensing.domain;

public enum LicenseType {
    PERPETUAL,      // never expires
    SUBSCRIPTION,   // expires_at set, renewable
    TRIAL           // short, machine-bound, not renewable
}

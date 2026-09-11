package com.mbuyedzedzo.licensing.domain;

public enum Role {
    SUPER_ADMIN,   // full access
    SALES_AGENT    // own customers/licenses only; cannot revoke or manage agents
}

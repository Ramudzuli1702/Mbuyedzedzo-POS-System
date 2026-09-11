package com.mbuyedzedzo.licensing.domain;

/** The two Mbuyedzedzo POS products. Matches the desktop's com.pos.Edition. */
public enum Product {
    POS_STANDARD,
    POS_RETAIL;

    /** Short prefix used in license keys (POS- / RET-). */
    public String keyPrefix() {
        return this == POS_RETAIL ? "RET" : "POS";
    }
}

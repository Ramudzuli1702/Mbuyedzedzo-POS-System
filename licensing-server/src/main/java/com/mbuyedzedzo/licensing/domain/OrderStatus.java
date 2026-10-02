package com.mbuyedzedzo.licensing.domain;

public enum OrderStatus {
    PENDING,    // created, waiting on the gateway redirect/ITN
    PAID,       // ITN verified — a license has been issued
    FAILED,     // gateway reported the payment failed
    CANCELLED   // buyer abandoned checkout
}

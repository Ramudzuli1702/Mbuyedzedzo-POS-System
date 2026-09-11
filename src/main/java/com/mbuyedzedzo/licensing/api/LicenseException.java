package com.mbuyedzedzo.licensing.api;

import org.springframework.http.HttpStatus;

/** A licensing failure with a stable machine-readable code the desktop reacts to. */
public class LicenseException extends RuntimeException {

    public enum Code {
        INVALID_KEY(HttpStatus.NOT_FOUND),
        BAD_CHECKSUM(HttpStatus.UNPROCESSABLE_ENTITY),
        WRONG_PRODUCT(HttpStatus.UNPROCESSABLE_ENTITY),
        NOT_ACTIVATED(HttpStatus.CONFLICT),
        MACHINE_LIMIT(HttpStatus.CONFLICT),
        REVOKED(HttpStatus.FORBIDDEN),
        SUSPENDED(HttpStatus.FORBIDDEN),
        EXPIRED(HttpStatus.FORBIDDEN),
        TRIAL_ALREADY_USED(HttpStatus.CONFLICT);

        final HttpStatus status;
        Code(HttpStatus status) { this.status = status; }
    }

    private final Code code;

    public LicenseException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public LicenseException(Code code) {
        this(code, code.name());
    }

    public Code code() { return code; }
    public HttpStatus status() { return code.status; }
}

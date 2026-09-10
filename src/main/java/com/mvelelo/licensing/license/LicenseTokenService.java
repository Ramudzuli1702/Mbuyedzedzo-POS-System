package com.mvelelo.licensing.license;

import com.mvelelo.licensing.config.LicensingProperties;
import com.mvelelo.licensing.domain.License;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Signs (and, for tests, verifies) the short-lived RS256 token the desktop
 * app stores and checks offline on every launch.
 */
@Service
public class LicenseTokenService {

    private final SigningKeys keys;
    private final LicensingProperties props;

    public LicenseTokenService(SigningKeys keys, LicensingProperties props) {
        this.keys = keys;
        this.props = props;
    }

    /** @return a signed JWT and the instant it expires. */
    public Issued issue(License license, String fingerprint) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.token().ttlDays(), ChronoUnit.DAYS);
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(props.token().issuer())
                    .subject(license.getLicenseKey())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .claim("product", license.getProduct().name())
                    .claim("fingerprint", fingerprint)
                    .claim("licenseType", license.getType().name())
                    .claim("licenseStatus", license.getStatus().name())
                    .claim("maxMachines", license.getMaxMachines())
                    .claim("licenseExpiresAt",
                            license.getExpiresAt() == null ? null : license.getExpiresAt().toString())
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(keys.privateKey()));
            return new Issued(jwt.serialize(), exp);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign license token", e);
        }
    }

    /** Verify signature + expiry (used by tests; the desktop does its own). */
    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(keys.publicKey()))) {
                throw new IllegalArgumentException("Bad signature");
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (c.getExpirationTime() == null || c.getExpirationTime().toInstant().isBefore(Instant.now())) {
                throw new IllegalArgumentException("Token expired");
            }
            return c;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token: " + e.getMessage(), e);
        }
    }

    public record Issued(String token, Instant expiresAt) {}
}

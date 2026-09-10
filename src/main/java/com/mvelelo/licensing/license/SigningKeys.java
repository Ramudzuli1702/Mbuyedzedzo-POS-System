package com.mvelelo.licensing.license;

import com.mvelelo.licensing.config.LicensingProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Holds the RSA keypair that signs license tokens. Loads PEM files from the
 * configured paths; if they are missing, generates a 2048-bit dev pair and
 * writes them (with a loud warning — real deployments must supply their own).
 *
 * The PUBLIC key is what the desktop POS bundles to verify tokens offline.
 */
@Component
public class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

    private final LicensingProperties props;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public SigningKeys(LicensingProperties props) {
        this.props = props;
    }

    @PostConstruct
    void load() throws Exception {
        Path priv = Path.of(props.token().privateKeyPath());
        Path pub  = Path.of(props.token().publicKeyPath());

        if (!Files.exists(priv) || !Files.exists(pub)) {
            log.warn("License signing keys not found at {} / {} — generating a DEV keypair. "
                    + "Do NOT use this in production; supply your own keys.", priv, pub);
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            if (priv.getParent() != null) Files.createDirectories(priv.getParent());
            Files.writeString(priv, pem("PRIVATE KEY", kp.getPrivate().getEncoded()));
            Files.writeString(pub,  pem("PUBLIC KEY",  kp.getPublic().getEncoded()));
        }

        this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der(Files.readString(priv))));
        this.publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der(Files.readString(pub))));
        log.info("License signing keys loaded (RSA {} bit)", privateKey.getModulus().bitLength());
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey()   { return publicKey; }

    /** Test helper: a SigningKeys backed by an in-memory keypair. */
    static SigningKeys withKeyPair(KeyPair kp) {
        SigningKeys k = new SigningKeys(null);
        k.privateKey = (RSAPrivateKey) kp.getPrivate();
        k.publicKey = (RSAPublicKey) kp.getPublic();
        return k;
    }

    /** The public key as a one-line Base64 string, for pasting into the desktop build. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    private static byte[] der(String pem) {
        String body = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                         .replaceAll("-----END [A-Z ]+-----", "")
                         .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String pem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }
}

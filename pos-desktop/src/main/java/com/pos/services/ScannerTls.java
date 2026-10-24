package com.pos.services;

import com.pos.utils.WindowsSecret;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

/** Stable, user-protected desktop identity. Phones pin the certificate from the pairing QR. */
final class ScannerTls {
    private final SSLContext context;
    private final String fingerprint;
    ScannerTls(Path identity) throws Exception {
        KeyStore keys=KeyStore.getInstance("PKCS12");
        if(Files.exists(identity)) {
            byte[] bytes=WindowsSecret.unprotect(Files.readString(identity));
            try { keys.load(new ByteArrayInputStream(bytes),new char[0]); }
            finally {Arrays.fill(bytes,(byte)0);}
        } else {
            keys.load(null,new char[0]);
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);
            KeyPair pair=generator.generateKeyPair();X500Name name=new X500Name("CN=Mbuyedzedzo POS Scanner");
            Instant now=Instant.now();
            X509Certificate cert=new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(name,new BigInteger(160,new SecureRandom()).abs(),
                            Date.from(now.minusSeconds(86400)),Date.from(now.plusSeconds(10L*365*86400)),name,pair.getPublic())
                            .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            cert.verify(pair.getPublic());
            keys.setKeyEntry("scanner",pair.getPrivate(),new char[0],new java.security.cert.Certificate[]{cert});
            ByteArrayOutputStream data=new ByteArrayOutputStream();keys.store(data,new char[0]);
            Files.createDirectories(identity.toAbsolutePath().getParent());
            Path temp=Files.createTempFile(identity.toAbsolutePath().getParent(),"scanner-identity-",".tmp");
            try {
                Files.writeString(temp,WindowsSecret.protect(data.toByteArray()));
                Files.move(temp,identity,StandardCopyOption.ATOMIC_MOVE);
            } finally {Files.deleteIfExists(temp);}
        }
        X509Certificate cert=(X509Certificate)keys.getCertificate("scanner");cert.checkValidity();
        fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(keys,new char[0]);
        context=SSLContext.getInstance("TLS");context.init(managers.getKeyManagers(),null,new SecureRandom());
    }
    String fingerprint(){return fingerprint;}
    SSLServerSocket socket(int port) throws IOException {
        SSLServerSocket socket=(SSLServerSocket)context.getServerSocketFactory().createServerSocket(port);
        socket.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});return socket;
    }
    static Path defaultIdentity() {
        String local=System.getenv("LOCALAPPDATA");
        Path base=local==null?Paths.get(System.getProperty("user.home"),".mbuyedzedzo"):Paths.get(local,"Mbuyedzedzo");
        return base.resolve(com.pos.Edition.current().name().toLowerCase(Locale.ROOT)).resolve("scanner-identity.dat");
    }
}

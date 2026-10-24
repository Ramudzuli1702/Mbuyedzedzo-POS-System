package com.pos.scanner;
import org.junit.Test;
import java.security.*;
import java.security.cert.*;
import static org.junit.Assert.*;
public class PinnedDesktopTest {
    @Test public void acceptsOnlyCertificatePinnedFromDesktopPairingCode() throws Exception {
        X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(getClass().getResourceAsStream("/desktop-test-certificate.der"));
        StringBuilder pin=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()))pin.append(String.format("%02x",b & 255));
        PinnedDesktop.verify(new X509Certificate[]{certificate},pin.toString());
        assertThrows(CertificateException.class,()->PinnedDesktop.verify(new X509Certificate[]{certificate},new String(new char[64]).replace('\0','0')));
        assertThrows(CertificateException.class,()->PinnedDesktop.verify(new X509Certificate[0],pin.toString()));
        assertThrows(CertificateException.class,()->PinnedDesktop.factory(null));
    }
}

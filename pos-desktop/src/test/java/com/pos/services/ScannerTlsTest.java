package com.pos.services;

import com.pos.utils.WindowsSecret;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class ScannerTlsTest {
    @TempDir Path directory;
    @Test void identityPersistsAsProtectedDataAndRealTlsHandshakeWorks() throws Exception {
        Path identity=directory.resolve("identity.dat");ScannerTls desktop=new ScannerTls(identity);
        assertTrue(Files.readString(identity).startsWith("dpapi:"));
        assertEquals(desktop.fingerprint(),new ScannerTls(identity).fingerprint());
        SSLContext client=SSLContext.getInstance("TLS");
        client.init(null,new TrustManager[]{new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth) throws CertificateException {throw new CertificateException();}
            public void checkServerTrusted(X509Certificate[] chain,String auth) throws CertificateException {
                try {chain[0].checkValidity();assertEquals(desktop.fingerprint(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())));}
                catch(GeneralSecurityException e){throw new CertificateException(e);}
            }
        }},new SecureRandom());
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(SSLServerSocket listener=desktop.socket(0)) {
            var response=worker.submit(()->{try(SSLSocket socket=(SSLSocket)listener.accept()){socket.setSoTimeout(5000);socket.startHandshake();socket.getOutputStream().write(42);return true;}});
            try(SSLSocket phone=(SSLSocket)client.getSocketFactory().createSocket("127.0.0.1",listener.getLocalPort())) {
                phone.setEnabledProtocols(new String[]{"TLSv1.2"});phone.setSoTimeout(5000);phone.startHandshake();
                assertEquals(42,phone.getInputStream().read());assertTrue(response.get(10,TimeUnit.SECONDS));
                // Public-only fixture for the Android pinning regression tests.
                Path fixture=Path.of("target","scanner-test-certificate.der");Files.write(fixture,phone.getSession().getPeerCertificates()[0].getEncoded());
            }
        }finally{worker.shutdownNow();}
    }
    @Test void dpapiRoundTripDoesNotUseAnEmbeddedApplicationKey() {
        byte[] plain="Synthetic SMTP password".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String protectedValue=WindowsSecret.protect(plain);
        assertFalse(protectedValue.contains("Synthetic"));assertArrayEquals(plain,WindowsSecret.unprotect(protectedValue));
        assertThrows(RuntimeException.class,()->WindowsSecret.unprotect("dpapi:AAAA"));
    }
}

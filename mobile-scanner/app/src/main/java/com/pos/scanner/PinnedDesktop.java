package com.pos.scanner;

import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;
import java.nio.charset.StandardCharsets;

/** The desktop certificate hash is conveyed out of band in its pairing QR code. */
final class PinnedDesktop {
    private PinnedDesktop() {}
    static SSLSocketFactory factory(final String fingerprint) throws GeneralSecurityException {
        if(fingerprint==null || !fingerprint.matches("[a-f0-9]{64}"))throw new CertificateException("Scan the desktop's secure pairing QR code.");
        TrustManager trust=new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth) throws CertificateException {throw new CertificateException("Not a client trust manager");}
            public void checkServerTrusted(X509Certificate[] chain,String auth) throws CertificateException {
                verify(chain,fingerprint);
            }
        };
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{trust},new SecureRandom());
        return context.getSocketFactory();
    }
    static void verify(X509Certificate[] chain,String fingerprint) throws CertificateException {
        try {
            if(chain==null || chain.length==0)throw new CertificateException("Missing desktop certificate");
            chain[0].checkValidity();StringBuilder actual=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))actual.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            if(fingerprint==null || !MessageDigest.isEqual(actual.toString().getBytes(StandardCharsets.US_ASCII),fingerprint.getBytes(StandardCharsets.US_ASCII)))
                throw new CertificateException("Desktop identity changed. Scan its pairing QR again.");
        }catch(GeneralSecurityException e){throw new CertificateException("Desktop identity could not be verified",e);}
    }
}

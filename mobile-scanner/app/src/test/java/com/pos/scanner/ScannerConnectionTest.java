package com.pos.scanner;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScannerConnectionTest {
    @Test public void signInRequiresSecureDesktopCapabilities() throws Exception {
        ScannerHandshake.requireSupported("{\"type\":\"connected\",\"protocolVersion\":4,\"requiresLogin\":true}");
        for(String frame:new String[]{null,"garbage","{}",
                "{\"type\":\"connected\",\"protocolVersion\":3,\"requiresLogin\":true}",
                "{\"type\":\"connected\",\"protocolVersion\":4,\"requiresLogin\":false}"})
            assertThrows(ScannerHandshake.IncompatibleDesktop.class,()->ScannerHandshake.requireSupported(frame));
    }
    @Test public void incompatiblePairingDoesNotTriggerAnAutomaticReconnectLoop() {
        SSLHandshakeException wrongDesktop=new SSLHandshakeException("Fixture only");
        wrongDesktop.initCause(new CertificateException("Wrong identity"));
        assertFalse(ConnectionFailure.retry(wrongDesktop));
        assertFalse(ConnectionFailure.retry(new ScannerHandshake.IncompatibleDesktop()));
        assertTrue(ConnectionFailure.message(wrongDesktop).contains("QR"));
        assertTrue(ConnectionFailure.message(wrongDesktop).contains("updated"));
    }
    @Test public void networkInterruptionsCanReconnectAndExplainTheFailure() {
        assertTrue(ConnectionFailure.retry(new IOException("Connection reset")));
        assertTrue(ConnectionFailure.retry(new SocketTimeoutException()));
        assertTrue(ConnectionFailure.message(new SocketTimeoutException()).contains("timed out"));
    }
}

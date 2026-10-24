package com.pos.scanner;

import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLException;

final class ConnectionFailure {
    static boolean retry(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof CertificateException || cause instanceof SSLHandshakeException
                    || cause instanceof ScannerHandshake.IncompatibleDesktop)return false;
        return true;
    }
    static String message(Throwable failure) {
        if(!retry(failure))return "Secure pairing failed. Use the updated desktop and phone app, then scan the desktop's current connection QR code.";
        if(failure instanceof SSLException)return "The secure connection was interrupted. Check that both apps are updated and scan the desktop's current QR code.";
        if(failure instanceof SocketTimeoutException)return "The desktop connection timed out. Reconnect and sign in again.";
        return "Connection to the desktop was lost. Check WiFi and that the desktop is still running.";
    }
}

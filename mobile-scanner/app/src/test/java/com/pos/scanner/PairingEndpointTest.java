package com.pos.scanner;
import org.junit.Test;
import static org.junit.Assert.*;
public class PairingEndpointTest {
    @Test public void requiresIdentityAndValidPort() {
        String pin=new String(new char[64]).replace('\0','a');
        PairingEndpoint endpoint=PairingEndpoint.parse("POS-TLS:192.168.1.5:8888:"+pin);
        assertNotNull(endpoint);assertEquals("192.168.1.5",endpoint.host);assertEquals(8888,endpoint.port);assertEquals(pin,endpoint.fingerprint);
        assertNull(PairingEndpoint.parse("POS:192.168.1.5:8888"));assertNull(PairingEndpoint.parse("192.168.1.5:8888"));
        assertNull(PairingEndpoint.parse("POS-TLS:192.168.1.5:0:"+pin));assertNull(PairingEndpoint.parse("POS-TLS:192.168.1.5:65536:"+pin));
        assertNull(PairingEndpoint.parse("POS-TLS:192.168.1.5:8888:invalid"));
    }
}

package com.pos.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import com.pos.utils.PasswordUtil;

class CompanionTest {
    @Test void idleUnauthenticatedPhoneDoesNotBlockTheNextConnection() throws Exception {
        var service=new StocktakeService(()->{throw new AssertionError("Database must not be accessed");});
        var receipts=new MobileReceiptService(()->{throw new AssertionError("Database must not be accessed");});
        var server=new WiFiHandler(service,receipts,List::of,0);assertTrue(server.startListening(null,null));
        try(java.net.Socket first=new java.net.Socket("127.0.0.1",server.getPort())) {
            first.setSoTimeout(2000);var a=new BufferedReader(new InputStreamReader(first.getInputStream()));assertNotNull(a.readLine());
            try(java.net.Socket next=new java.net.Socket("127.0.0.1",server.getPort())) {
                next.setSoTimeout(2000);var b=new BufferedReader(new InputStreamReader(next.getInputStream()));
                assertNotNull(b.readLine());assertEquals(-1,a.read());assertTrue(server.isConnected());
            }
        }finally{server.cleanup();}
    }
    @Test void boundedFramesHandlePartialReadsAndUnicode() throws Exception {StringReader r=new StringReader("{\"type\":\"scan\",\"data\":\"é\"}\r\n{\"type\":\"scan\"}\n");assertEquals("{\"type\":\"scan\",\"data\":\"é\"}",WiFiHandler.readFrame(r,1024));assertEquals("{\"type\":\"scan\"}",WiFiHandler.readFrame(r,1024));assertNull(WiFiHandler.readFrame(r,1024));}
    @Test void oversizedFramesAreRejected(){assertThrows(IOException.class,()->WiFiHandler.readFrame(new StringReader("a".repeat(101)),100));}
    @Test void invalidCountsAreRejectedBeforeDatabaseAccess(){assertThrows(StocktakeService.Rejected.class,()->StocktakeService.validateCounts(List.of()));assertThrows(StocktakeService.Rejected.class,()->StocktakeService.validateCounts(List.of(new StocktakeService.Count(1,-1))));assertThrows(StocktakeService.Rejected.class,()->StocktakeService.validateCounts(List.of(new StocktakeService.Count(1,1),new StocktakeService.Count(1,2))));assertDoesNotThrow(()->StocktakeService.validateCounts(List.of(new StocktakeService.Count(1,0))));}
    @Test void proofsAreBoundToNonceAndChallengeAndSupportLegacyAccounts() throws Exception {String hash=PasswordUtil.hashPassword("Staff pass!9");assertArrayEquals(StocktakeProtocol.proof(hash,"nonce","one"),StocktakeProtocol.proof(hash,"nonce","one"));assertFalse(Arrays.equals(StocktakeProtocol.proof(hash,"nonce","one"),StocktakeProtocol.proof(hash,"nonce","two")));String legacy=Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest("staff".getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertEquals(32,StocktakeProtocol.proof(legacy,"nonce","one").length);}
}

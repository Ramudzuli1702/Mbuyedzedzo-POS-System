package com.pos.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import com.pos.utils.PasswordUtil;

class CompanionTest {
    @Test void boundedFramesHandlePartialReadsAndUnicode() throws Exception {StringReader r=new StringReader("{\"type\":\"scan\",\"data\":\"é\"}\r\n{\"type\":\"scan\"}\n");assertEquals("{\"type\":\"scan\",\"data\":\"é\"}",WiFiHandler.readFrame(r,1024));assertEquals("{\"type\":\"scan\"}",WiFiHandler.readFrame(r,1024));assertNull(WiFiHandler.readFrame(r,1024));}
    @Test void oversizedFramesAreRejected(){assertThrows(IOException.class,()->WiFiHandler.readFrame(new StringReader("a".repeat(101)),100));}
    @Test void invalidCountsAreRejectedBeforeDatabaseAccess(){assertThrows(StocktakeService.Rejected.class,()->StocktakeService.validateCounts(List.of()));assertThrows(StocktakeService.Rejected.class,()->StocktakeService.validateCounts(List.of(new StocktakeService.Count(1,-1))));assertThrows(StocktakeService.Rejected.class,()->StocktakeService.validateCounts(List.of(new StocktakeService.Count(1,1),new StocktakeService.Count(1,2))));assertDoesNotThrow(()->StocktakeService.validateCounts(List.of(new StocktakeService.Count(1,0))));}
    @Test void proofsAreBoundToNonceAndChallengeAndSupportLegacyAccounts() throws Exception {String hash=PasswordUtil.hashPassword("Staff pass!9");assertArrayEquals(StocktakeProtocol.proof(hash,"nonce","one"),StocktakeProtocol.proof(hash,"nonce","one"));assertFalse(Arrays.equals(StocktakeProtocol.proof(hash,"nonce","one"),StocktakeProtocol.proof(hash,"nonce","two")));String legacy=Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest("staff".getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertEquals(32,StocktakeProtocol.proof(legacy,"nonce","one").length);}
}

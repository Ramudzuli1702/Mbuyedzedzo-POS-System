package com.pos.utils;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class LoginAttemptsTest {
    @Test void reconnectsDoNotResetAttemptsAndExpiredWindowsRecover() {
        AtomicLong clock=new AtomicLong();var attempts=new LoginAttempts(clock::get);
        for(int i=0;i<5;i++)assertTrue(attempts.begin("Staff@Example.test"));
        assertFalse(attempts.begin(" staff@example.test "));
        clock.set(60_000_000_000L);assertTrue(attempts.begin("staff@example.test"));
        attempts.success("staff@example.test");assertTrue(attempts.begin("staff@example.test"));
    }
}

package com.pos.utils;

import java.util.*;
import java.util.function.LongSupplier;

/** Shared across desktop login and phone sockets, with bounded memory and a global attempt budget. */
public final class LoginAttempts {
    public static final LoginAttempts SHARED=new LoginAttempts(System::nanoTime);
    private final LongSupplier clock;
    private final Map<String,Window> windows=new HashMap<>();
    private static final long MINUTE=60_000_000_000L;
    private record Window(long start,int attempts) {}
    public LoginAttempts(LongSupplier clock){this.clock=clock;}
    public synchronized boolean begin(String account) {
        long now=clock.getAsLong();windows.entrySet().removeIf(e->now-e.getValue().start()>=MINUTE);
        String key=account==null?"":account.trim().toLowerCase(Locale.ROOT);
        if(key.length()>254 || windows.size()>5000)return false;
        return take("*global*",120,now) && take("account:"+key,5,now);
    }
    private boolean take(String key,int max,long now) {
        Window w=windows.get(key);
        if(w==null){windows.put(key,new Window(now,1));return true;}
        if(w.attempts()>=max)return false;
        windows.put(key,new Window(w.start(),w.attempts()+1));return true;
    }
    public synchronized void success(String account){if(account!=null)windows.remove("account:"+account.trim().toLowerCase(Locale.ROOT));}
}

package com.pos.services;

import com.google.gson.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Per-socket stocktake authentication. Passwords/verifiers are never sent over the scanner link. */
public final class StocktakeProtocol {
    private final StocktakeService service;
    private final Gson gson=new Gson();
    private StocktakeService.Account challengeAccount;
    private volatile StocktakeService.Account signedIn;
    private String nonce, challengeId, token;
    private long challengeUntil, blockedUntil;
    private volatile long sessionUntil;
    private int attempts;
    private String challengeEmail;
    public StocktakeProtocol() { this(new StocktakeService()); }
    public StocktakeProtocol(StocktakeService service) { this.service=service; }

    public JsonObject handle(JsonObject request) {
        JsonObject response=new JsonObject(); response.addProperty("type","response");
        if(request.has("requestId")) response.add("requestId",request.get("requestId"));
        try {
            String type=string(request,"type",50);
            Object data;
            if(type.equals("stocktake_challenge") || type.equals("auth_challenge")) data=challenge(string(request,"email",254));
            else if(type.equals("stocktake_login") || type.equals("auth_login")) data=login(request);
            else {
                requireSession(request);
                String id=type.equals("stocktake_logout") || type.equals("auth_logout")?"":string(request,"stocktakeId",36);
                data=switch(type) {
                    case "stocktake_start" -> service.start(id,signedIn.id());
                    case "stocktake_resume" -> service.resume(id,signedIn.id());
                    case "stocktake_submit" -> {
                        JsonArray array=request.getAsJsonArray("counts");
                        if(array==null || array.size()>100000) throw new StocktakeService.Rejected("INVALID","Invalid count list.");
                        List<StocktakeService.Count> counts=new ArrayList<>();
                        for(JsonElement e:array) {
                            JsonObject row=e.getAsJsonObject();
                            counts.add(new StocktakeService.Count(row.get("productId").getAsBigDecimal().intValueExact(),row.get("quantity").getAsBigDecimal().intValueExact()));
                        }
                        yield service.submit(id,signedIn.id(),counts,request.has("notes")?string(request,"notes",1000):"");
                    }
                    case "stocktake_logout", "auth_logout" -> { signedIn=null;token=null;yield Map.of("signedOut",true); }
                    default -> throw new StocktakeService.Rejected("UNSUPPORTED","Update the desktop to use stocktaking.");
                };
            }
            response.addProperty("ok",true); response.add("data",gson.toJsonTree(data));
        } catch(StocktakeService.Rejected e) {
            response.addProperty("ok",false);response.addProperty("code",e.code);response.addProperty("message",e.getMessage());
        } catch(IllegalArgumentException | IllegalStateException | ArithmeticException | NullPointerException e) {
            response.addProperty("ok",false);response.addProperty("code","INVALID");response.addProperty("message","The request contains invalid values.");
        } catch(Exception e) {
            response.addProperty("ok",false);response.addProperty("code","UNAVAILABLE");response.addProperty("message","The desktop could not complete this request. Please retry.");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Companion request failed: {}",e.getClass().getSimpleName());
        }
        return response;
    }
    private Map<String,Object> challenge(String email) throws Exception {
        if(!com.pos.utils.LoginAttempts.SHARED.begin(email))throw new StocktakeService.Rejected("RATE_LIMIT","Too many sign-in attempts. Wait one minute.");
        challengeEmail=email;
        long now=System.currentTimeMillis();
        if(now<blockedUntil) throw new StocktakeService.Rejected("RATE_LIMIT","Too many sign-in attempts. Wait one minute.");
        if(++attempts>5) { blockedUntil=now+60000;attempts=0;throw new StocktakeService.Rejected("RATE_LIMIT","Too many sign-in attempts. Wait one minute."); }
        signedIn=null;token=null;
        challengeAccount=service.account(email.trim());
        nonce=random();challengeId=UUID.randomUUID().toString();challengeUntil=now+120000;
        String hash=challengeAccount==null?"pbkdf2:sha256:210000:"+Base64.getEncoder().encodeToString(new byte[16])+":"+Base64.getEncoder().encodeToString(new byte[32]):challengeAccount.passwordHash();
        Map<String,Object> data=new HashMap<>();
        data.put("nonce",nonce);data.put("challengeId",challengeId);
        if(hash.startsWith("pbkdf2:sha256:")) {
            String[] parts=hash.split(":");
            data.put("algorithm","PBKDF2-HMAC-SHA256");data.put("iterations",Integer.parseInt(parts[2]));data.put("salt",parts[3]);
        } else { data.put("algorithm","SHA-256");data.put("iterations",1);data.put("salt",""); }
        return data;
    }
    private Map<String,Object> login(JsonObject request) throws Exception {
        StocktakeService.Account account=challengeAccount;
        String expectedId=challengeId;
        challengeAccount=null;challengeId=null; // single-use even on a bad proof
        boolean valid=account!=null && expectedId!=null && System.currentTimeMillis()<=challengeUntil
                && expectedId.equals(string(request,"challengeId",36));
        byte[] supplied;
        try { supplied=Base64.getDecoder().decode(string(request,"proof",200)); }
        catch(IllegalArgumentException e) { supplied=new byte[0]; }
        byte[] expected=valid?proof(account.passwordHash(),nonce,expectedId):new byte[32];
        if(!valid || !MessageDigest.isEqual(supplied,expected)) throw new StocktakeService.Rejected("AUTH_FAILED","Email or password is incorrect.");
        if(com.pos.utils.PasswordUtil.needsRehash(account.passwordHash()))
            throw new StocktakeService.Rejected("AUTH_FAILED","Sign in on the desktop once to upgrade this account's password protection, then retry on the phone.");
        StocktakeService.Account current=service.requireStaff(account.id(),false);
        if(!current.passwordHash().equals(account.passwordHash())) throw new StocktakeService.Rejected("AUTH_FAILED","Sign in again using your current password.");
        signedIn=current;token=random();sessionUntil=System.currentTimeMillis()+30*60*1000;attempts=0;
        com.pos.utils.LoginAttempts.SHARED.success(challengeEmail);
        return Map.of("token",token,"staffId",current.id(),"name",current.name(),"role",current.role());
    }
    public void requireSession(JsonObject request) throws Exception {
        if(signedIn==null || System.currentTimeMillis()>sessionUntil || !MessageDigest.isEqual(
                (request.has("token") && !request.get("token").isJsonNull()?request.get("token").getAsString():"").getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new StocktakeService.Rejected("AUTH_REQUIRED","Your sign-in expired. Sign in again; your saved counts will be kept.");
        StocktakeService.Account current=service.requireStaff(signedIn.id(),false);
        if(!current.passwordHash().equals(signedIn.passwordHash())) {
            signedIn=null;token=null;throw new StocktakeService.Rejected("AUTH_REQUIRED","Your account changed. Sign in again.");
        }
        sessionUntil=System.currentTimeMillis()+30*60*1000;
    }
    public boolean isAuthenticated() {
        StocktakeService.Account account=signedIn;
        if(account==null || System.currentTimeMillis()>sessionUntil)return false;
        try { return service.requireStaff(account.id(),false).passwordHash().equals(account.passwordHash()); }
        catch(Exception e) { return false; }
    }
    public static byte[] proof(String hash,String nonce,String id) throws Exception {
        String key=hash.startsWith("pbkdf2:sha256:")?hash.split(":")[4]:hash;
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Base64.getDecoder().decode(key),"HmacSHA256"));
        return mac.doFinal(("mbuyedzedzo-stocktake:"+nonce+"|"+id).getBytes(StandardCharsets.UTF_8));
    }
    private static String random() { byte[] b=new byte[32];new SecureRandom().nextBytes(b);return HexFormat.of().formatHex(b); }
    private static String string(JsonObject json,String key,int max) {
        if(!json.has(key) || json.get(key).isJsonNull()) throw new StocktakeService.Rejected("INVALID","A required value is missing.");
        String value=json.get(key).getAsString();
        if(value.length()>max) throw new StocktakeService.Rejected("INVALID","A value is too long.");return value;
    }
}

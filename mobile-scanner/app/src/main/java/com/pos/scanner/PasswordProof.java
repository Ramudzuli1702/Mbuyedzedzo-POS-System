package com.pos.scanner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Derive locally. Neither the password nor the reusable stored verifier leaves the phone. */
public final class PasswordProof {
    private PasswordProof(){}
    public static byte[] create(char[] password,String algorithm,int iterations,byte[] salt,String nonce,String id) throws Exception {
        byte[] key;
        try {
            if("SHA-256".equals(algorithm)) key=MessageDigest.getInstance("SHA-256").digest(new String(password).getBytes(StandardCharsets.UTF_8));
            else if("PBKDF2-HMAC-SHA256".equals(algorithm)&&iterations>=1&&iterations<=1000000&&salt.length>=8&&salt.length<=64) {
                PBEKeySpec spec=new PBEKeySpec(password,salt,iterations,256);
                try { key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
                catch(java.security.NoSuchAlgorithmException e) { key=deriveLegacyAndroid(password,salt,iterations); }
                finally { spec.clearPassword(); }
            } else throw new IllegalArgumentException("Unsupported sign-in challenge. Update the desktop.");
        } finally { java.util.Arrays.fill(password,'\0'); }
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return mac.doFinal(("mbuyedzedzo-stocktake:"+nonce+"|"+id).getBytes(StandardCharsets.UTF_8));
        } finally { java.util.Arrays.fill(key,(byte)0); }
    }
    // API 24/25 do not expose PBKDF2WithHmacSHA256, but do provide HmacSHA256.
    static byte[] deriveLegacyAndroid(char[] password,byte[] salt,int iterations) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");byte[] bytes=new String(password).getBytes(StandardCharsets.UTF_8);
        mac.init(new SecretKeySpec(bytes,"HmacSHA256"));java.util.Arrays.fill(bytes,(byte)0);
        byte[] block=java.util.Arrays.copyOf(salt,salt.length+4);block[block.length-1]=1;
        byte[] u=mac.doFinal(block),key=u.clone();
        for(int i=1;i<iterations;i++){u=mac.doFinal(u);for(int n=0;n<key.length;n++)key[n]^=u[n];}
        java.util.Arrays.fill(u,(byte)0);return key;
    }
}

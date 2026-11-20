package com.smartcity360.ejb.security;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16, HASH_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        return "sc360$1$pbkdf2-sha256$" + ITERATIONS + "$" + b64(salt) + "$" + b64(hash);
    }

    public boolean verify(char[] password, String stored) {
        try {
            String[] p = stored.split("\\$", -1);
            if (p.length != 6 || !p[0].equals("sc360") || !p[1].equals("1") || !p[2].equals("pbkdf2-sha256"))
                return false;
            int iterations = Integer.parseInt(p[3]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getUrlDecoder().decode(p[4]), expected = Base64.getUrlDecoder().decode(p[5]);
            if (salt.length < 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean needsUpgrade(String stored) {
        try {
            String[] p = stored.split("\\$", -1);
            return p.length != 6 || !p[0].equals("sc360") || !p[1].equals("1") || !p[2].equals("pbkdf2-sha256") || Integer.parseInt(p[3]) < ITERATIONS;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] v) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(v);
    }
}

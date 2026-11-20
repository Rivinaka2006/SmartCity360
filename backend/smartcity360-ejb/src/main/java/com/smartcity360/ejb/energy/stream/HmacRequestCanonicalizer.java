package com.smartcity360.ejb.energy.stream;

import javax.crypto.*;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;

public final class HmacRequestCanonicalizer {
    private HmacRequestCanonicalizer() {
    }

    public static byte[] sign(SecretKey key, String meter, int version, Instant timestamp, String nonce, String method, String path, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(canonical(meter, version, timestamp, nonce, method, path, payload));
        } catch (GeneralSecurityException e) {
            throw new EnergyStreamException("CRYPTO_UNAVAILABLE", e);
        }
    }

    static byte[] canonical(String meter, int version, Instant timestamp, String nonce, String method, String path, byte[] payload) {
        if (meter == null || timestamp == null || nonce == null || nonce.length() < 8 || nonce.length() > 128 || method == null || path == null || payload == null)
            throw new EnergyStreamException("METER_PROOF_INVALID");
        try {
            var out = new ByteArrayOutputStream();
            var d = new DataOutputStream(out);
            write(d, "SC360-HMAC-1");
            write(d, meter);
            d.writeInt(version);
            d.writeLong(timestamp.toEpochMilli());
            write(d, nonce);
            write(d, method.toUpperCase(java.util.Locale.ROOT));
            write(d, path);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            d.writeInt(digest.length);
            d.write(digest);
            return out.toByteArray();
        } catch (IOException | GeneralSecurityException e) {
            throw new EnergyStreamException("CRYPTO_UNAVAILABLE", e);
        }
    }

    private static void write(DataOutputStream d, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        d.writeInt(b.length);
        d.write(b);
    }
}

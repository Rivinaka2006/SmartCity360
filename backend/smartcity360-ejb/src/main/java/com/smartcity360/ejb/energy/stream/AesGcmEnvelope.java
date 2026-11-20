package com.smartcity360.ejb.energy.stream;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

public final class AesGcmEnvelope {
    private static final byte[] MAGIC = {'S', 'C', '3', '6'};
    private final KeyProvider keys;
    private final int max;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEnvelope(KeyProvider k, int m) {
        keys = k;
        max = m;
    }

    public byte[] encrypt(byte[] plain) {
        if (plain == null || plain.length > max) throw new EnergyStreamException("PAYLOAD_SIZE_INVALID");
        EncryptionKey k = keys.currentEncryptionKey();
        byte[] id = k.id().getBytes(StandardCharsets.UTF_8);
        if (id.length < 1 || id.length > 128) throw new EnergyStreamException("ENCRYPTION_KEY_INVALID");
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, k.key(), new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(id));
            byte[] cipher = c.doFinal(plain);
            var out = new ByteArrayOutputStream();
            var d = new DataOutputStream(out);
            d.write(MAGIC);
            d.writeByte(1);
            d.writeShort(id.length);
            d.write(id);
            d.write(nonce);
            d.writeInt(cipher.length);
            d.write(cipher);
            return out.toByteArray();
        } catch (GeneralSecurityException | IOException e) {
            throw new EnergyStreamException("ENCRYPTION_FAILED", e);
        }
    }

    public byte[] decrypt(byte[] envelope) {
        if (envelope == null || envelope.length > max + 512) throw new EnergyStreamException("CIPHERTEXT_INVALID");
        try {
            var d = new DataInputStream(new ByteArrayInputStream(envelope));
            byte[] magic = d.readNBytes(4);
            if (!java.util.Arrays.equals(MAGIC, magic) || d.readUnsignedByte() != 1)
                throw new EnergyStreamException("CIPHERTEXT_INVALID");
            int len = d.readUnsignedShort();
            if (len < 1 || len > 128) throw new EnergyStreamException("CIPHERTEXT_INVALID");
            byte[] id = d.readNBytes(len), nonce = d.readNBytes(12);
            int clen = d.readInt();
            if (clen < 16 || clen > max + 16 || d.available() != clen)
                throw new EnergyStreamException("CIPHERTEXT_INVALID");
            byte[] cipher = d.readNBytes(clen);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, keys.resolveEncryptionKey(new String(id, StandardCharsets.UTF_8)), new GCMParameterSpec(128, nonce));
            c.updateAAD(aad(id));
            byte[] plain = c.doFinal(cipher);
            if (plain.length > max) throw new EnergyStreamException("PAYLOAD_SIZE_INVALID");
            return plain;
        } catch (EnergyStreamException e) {
            throw e;
        } catch (IOException | GeneralSecurityException e) {
            throw new EnergyStreamException("CIPHERTEXT_INVALID", e);
        }
    }

    private static byte[] aad(byte[] id) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(MAGIC);
        out.write(1);
        out.write(id);
        return out.toByteArray();
    }
}

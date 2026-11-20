package com.smartcity360.ejb.energy.stream;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class PayaraKeyProvider implements KeyProvider {
    static final String CURRENT_AES_KEY_ID = "smartcity360.energy.aes.current-key-id";
    private static final Logger LOGGER = Logger.getLogger(PayaraKeyProvider.class.getName());
    private final Function<String, String> values;
    private final long maxEncryptionsPerKey;
    private final ConcurrentHashMap<String, AtomicLong> encryptionUses = new ConcurrentHashMap<>();

    public PayaraKeyProvider() {
        this(name -> ConfigProvider.getConfig().getOptionalValue(name, String.class).orElse(null), 1L << 32);
    }

    PayaraKeyProvider(Function<String, String> values) {
        this(values, 1L << 32);
    }

    PayaraKeyProvider(Function<String, String> values, long maxEncryptionsPerKey) {
        this.values = values;
        this.maxEncryptionsPerKey = maxEncryptionsPerKey;
    }

    @Override
    public MeterKey activeMeterHmacKey(String keyId, int version) {
        String validatedKeyId = requiredId(keyId, KeyResolutionStage.METER_HMAC_KEY_IDENTIFIER);
        SecretKey key = decode(
                validatedKeyId,
                "HmacSHA256",
                KeyResolutionStage.METER_HMAC_KEY_VALUE);
        return new MeterKey(validatedKeyId, version, key);
    }

    @Override
    public SecretKey resolveMeterHmacKey(String keyId, int version) {
        return activeMeterHmacKey(keyId, version).key();
    }

    @Override
    public EncryptionKey currentEncryptionKey() {
        String id = requiredId(
                values.apply(CURRENT_AES_KEY_ID),
                KeyResolutionStage.CURRENT_AES_KEY_IDENTIFIER);
        SecretKey key = decode(id, "AES", KeyResolutionStage.AES_KEY_VALUE);
        if (encryptionUses.computeIfAbsent(id, ignored -> new AtomicLong()).incrementAndGet()
                > maxEncryptionsPerKey) {
            throw new EnergyStreamException("ENCRYPTION_KEY_USAGE_LIMIT");
        }
        return new EncryptionKey(id, key);
    }

    public void validateCurrentEncryptionKey(String expectedKeyId) {
        String id = requiredId(
                values.apply(CURRENT_AES_KEY_ID),
                KeyResolutionStage.CURRENT_AES_KEY_IDENTIFIER);
        if (expectedKeyId == null || !expectedKeyId.equals(id)) {
            throw new EnergyStreamException("KEY_CONFIGURATION_INVALID");
        }
        resolveEncryptionKey(id);
    }

    @Override
    public SecretKey resolveEncryptionKey(String keyId) {
        String validatedKeyId = requiredId(keyId, KeyResolutionStage.AES_KEY_IDENTIFIER);
        return decode(validatedKeyId, "AES", KeyResolutionStage.AES_KEY_VALUE);
    }

    private SecretKey decode(
            String property,
            String algorithm,
            KeyResolutionStage resolutionStage) {
        String encoded = values.apply(property);
        if (encoded == null || encoded.isBlank()) {
            throw missingConfiguration(resolutionStage);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != 32) {
                throw new EnergyStreamException("KEY_CONFIGURATION_INVALID");
            }
            return new SecretKeySpec(bytes, algorithm);
        } catch (IllegalArgumentException exception) {
            throw new EnergyStreamException("KEY_CONFIGURATION_INVALID");
        }
    }

    private static String requiredId(String id, KeyResolutionStage resolutionStage) {
        if (id == null || id.isBlank() || id.length() > 128) {
            throw missingConfiguration(resolutionStage);
        }
        return id;
    }

    private static EnergyStreamException missingConfiguration(
            KeyResolutionStage resolutionStage) {
        LOGGER.log(
                Level.SEVERE,
                "Energy stream key resolution failed: stage="
                        + resolutionStage
                        + ", reason=MISSING");
        return new EnergyStreamException("KEY_CONFIGURATION_MISSING");
    }

    private enum KeyResolutionStage {
        METER_HMAC_KEY_IDENTIFIER,
        METER_HMAC_KEY_VALUE,
        CURRENT_AES_KEY_IDENTIFIER,
        AES_KEY_IDENTIFIER,
        AES_KEY_VALUE
    }
}

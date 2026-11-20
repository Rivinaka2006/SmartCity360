package com.smartcity360.ejb.energy.stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayaraKeyProviderTest {
    private static final String MISSING_METER_KEY_VALUE =
            "Energy stream key resolution failed: stage=METER_HMAC_KEY_VALUE, reason=MISSING";
    private static final String MISSING_METER_KEY_IDENTIFIER =
            "Energy stream key resolution failed: stage=METER_HMAC_KEY_IDENTIFIER, reason=MISSING";
    private static final String MISSING_CURRENT_AES_KEY_IDENTIFIER =
            "Energy stream key resolution failed: stage=CURRENT_AES_KEY_IDENTIFIER, reason=MISSING";
    private static final String MISSING_AES_KEY_IDENTIFIER =
            "Energy stream key resolution failed: stage=AES_KEY_IDENTIFIER, reason=MISSING";
    private static final String MISSING_AES_KEY_VALUE =
            "Energy stream key resolution failed: stage=AES_KEY_VALUE, reason=MISSING";

    @Test
    void decodesExactIndependent256BitKeys() {
        Map<String, String> values = new HashMap<>();
        values.put("smartcity360.energy.aes.current-key-id", "energy.aes.v1");
        values.put("energy.aes.v1", Base64.getEncoder().encodeToString(bytes(1)));
        values.put("meter.hmac.abc.v1", Base64.getEncoder().encodeToString(bytes(2)));
        PayaraKeyProvider provider = new PayaraKeyProvider(values::get);

        assertEquals(32, provider.currentEncryptionKey().key().getEncoded().length);
        assertEquals(32, provider.activeMeterHmacKey("meter.hmac.abc.v1", 1).key().getEncoded().length);
        assertFalse(Arrays.equals(
                provider.currentEncryptionKey().key().getEncoded(),
                provider.activeMeterHmacKey("meter.hmac.abc.v1", 1).key().getEncoded()));
    }

    @Test
    void validatesTheConfiguredCurrentKeyIsTheDedicatedAcademicKey() {
        String demoKeyId = "smartcity360.energy.aes.academic.viva.v1";
        Map<String, String> values = Map.of(
                PayaraKeyProvider.CURRENT_AES_KEY_ID, demoKeyId,
                demoKeyId, Base64.getEncoder().encodeToString(bytes(4)));
        PayaraKeyProvider provider = new PayaraKeyProvider(values::get);

        provider.validateCurrentEncryptionKey(demoKeyId);

        assertCode(
                "KEY_CONFIGURATION_INVALID",
                () -> provider.validateCurrentEncryptionKey("another.aes.key"));
    }

    @Test
    void failsClosedForMalformedAndWrongLengthValues() {
        assertCode(
                "KEY_CONFIGURATION_INVALID",
                () -> new PayaraKeyProvider(key -> "not-base64").resolveEncryptionKey("x"));

        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertCode(
                "KEY_CONFIGURATION_INVALID",
                () -> new PayaraKeyProvider(key -> shortKey).resolveMeterHmacKey("x", 1));
    }

    @Test
    void identifiesMissingMeterHmacKeyValueWithoutLoggingIdentifier() {
        String meterKeyIdentifier = "meter.hmac.sensitive-identifier.v1";
        PayaraKeyProvider provider = new PayaraKeyProvider(key -> null);

        List<String> messages = captureLogMessages(() -> assertCode(
                "KEY_CONFIGURATION_MISSING",
                () -> provider.activeMeterHmacKey(meterKeyIdentifier, 1)));

        assertEquals(List.of(MISSING_METER_KEY_VALUE), messages);
        assertFalse(String.join(" ", messages).contains(meterKeyIdentifier));
    }

    @Test
    void identifiesMissingMeterHmacKeyIdentifier() {
        PayaraKeyProvider provider = new PayaraKeyProvider(key -> null);

        List<String> messages = captureLogMessages(() -> assertCode(
                "KEY_CONFIGURATION_MISSING",
                () -> provider.activeMeterHmacKey(" ", 1)));

        assertEquals(List.of(MISSING_METER_KEY_IDENTIFIER), messages);
    }

    @Test
    void identifiesMissingCurrentAesKeyIdentifier() {
        PayaraKeyProvider provider = new PayaraKeyProvider(key -> null);

        List<String> messages = captureLogMessages(() -> assertCode(
                "KEY_CONFIGURATION_MISSING",
                provider::currentEncryptionKey));

        assertEquals(List.of(MISSING_CURRENT_AES_KEY_IDENTIFIER), messages);
    }

    @Test
    void identifiesMissingAesKeyValueWithoutLoggingIdentifier() {
        String aesKeyIdentifier = "energy.aes.sensitive-identifier.v1";
        Map<String, String> values = Map.of(
                PayaraKeyProvider.CURRENT_AES_KEY_ID,
                aesKeyIdentifier);
        PayaraKeyProvider provider = new PayaraKeyProvider(values::get);

        List<String> messages = captureLogMessages(() -> assertCode(
                "KEY_CONFIGURATION_MISSING",
                provider::currentEncryptionKey));

        assertEquals(List.of(MISSING_AES_KEY_VALUE), messages);
        assertFalse(String.join(" ", messages).contains(aesKeyIdentifier));
    }

    @Test
    void identifiesMissingEnvelopeAesKeyIdentifier() {
        PayaraKeyProvider provider = new PayaraKeyProvider(key -> null);

        List<String> messages = captureLogMessages(() -> assertCode(
                "KEY_CONFIGURATION_MISSING",
                () -> provider.resolveEncryptionKey(" ")));

        assertEquals(List.of(MISSING_AES_KEY_IDENTIFIER), messages);
    }

    @Test
    void enforcesPerKeyEncryptionUsageLimit() {
        Map<String, String> values = Map.of(
                "smartcity360.energy.aes.current-key-id",
                "energy.aes.v1",
                "energy.aes.v1",
                Base64.getEncoder().encodeToString(bytes(3)));
        PayaraKeyProvider provider = new PayaraKeyProvider(values::get, 2);

        provider.currentEncryptionKey();
        provider.currentEncryptionKey();

        assertCode("ENCRYPTION_KEY_USAGE_LIMIT", provider::currentEncryptionKey);
    }

    private static byte[] bytes(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static void assertCode(String expectedCode, Executable action) {
        EnergyStreamException exception = assertThrows(EnergyStreamException.class, action);
        assertEquals(expectedCode, exception.code());
    }

    private static List<String> captureLogMessages(Executable action) {
        Logger logger = Logger.getLogger(PayaraKeyProvider.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            action.execute();
            return handler.messages();
        } catch (Throwable throwable) {
            throw new AssertionError(throwable);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(previousUseParentHandlers);
            logger.setLevel(previousLevel);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private List<String> messages() {
            return List.copyOf(messages);
        }
    }
}

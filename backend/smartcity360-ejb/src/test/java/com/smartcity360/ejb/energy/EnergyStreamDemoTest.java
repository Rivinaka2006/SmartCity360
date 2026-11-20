package com.smartcity360.ejb.energy;

import com.smartcity360.common.energy.EnergyStreamDemoResult;
import com.smartcity360.common.energy.EnergyStreamServiceException;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnergyStreamDemoTest {
    @Test
    void performsRealAuthenticatedCompressedAndEncryptedRoundTripWithoutInjectedServices() throws Exception {
        EnergyStreamServiceBean service = new EnergyStreamServiceBean();
        assertNull(service.meters);
        assertNull(service.runs);
        assertNull(service.recorder);
        assertNull(service.keys);
        assertNull(service.replay);

        EnergyStreamDemoResult result = service.demonstrate("1.25");

        assertTrue(result.demonstrationOnly());
        assertEquals(
                List.of("AUTHENTICATION", "COMPRESSION", "ENCRYPTION"),
                result.decoratorLayers());
        assertTrue(result.authenticatedProcessingSucceeded());
        assertTrue(result.recoveredOriginalReading());
        assertEquals(15, result.readingIntervalMinutes());
        assertTrue(result.inputPayload().contains("\"consumptionKwh\":1.25"));
        assertTrue(result.inputPayload().contains("\"intervalMinutes\":15"));
        assertEquals(
                result.inputPayload().getBytes(StandardCharsets.UTF_8).length,
                result.inputBytes());
        byte[] encryptedEnvelope = Base64.getDecoder().decode(result.encryptedEnvelopeBase64());
        assertEquals(encryptedEnvelope.length, result.encryptedOutputBytes());
        assertArrayEquals(new byte[]{'S', 'C', '3', '6'}, Arrays.copyOf(encryptedEnvelope, 4));
        assertNull(service.meters);
        assertNull(service.runs);
        assertNull(service.recorder);
        assertNull(service.keys);
        assertNull(service.replay);
    }

    @Test
    void createsFreshCryptographicMaterialForSeparateDemonstrations() throws Exception {
        EnergyStreamServiceBean service = new EnergyStreamServiceBean();
        byte[] firstEnvelope = Base64.getDecoder().decode(service.demonstrate("1.25").encryptedEnvelopeBase64());
        byte[] secondEnvelope = Base64.getDecoder().decode(service.demonstrate("1.25").encryptedEnvelopeBase64());

        assertFalse(Arrays.equals(firstEnvelope, secondEnvelope));
        assertFalse(Arrays.equals(gcmNonce(firstEnvelope), gcmNonce(secondEnvelope)));
    }

    @Test
    void rejectsMalformedNonFiniteNegativeAndOversizedInputs() {
        EnergyStreamServiceBean service = new EnergyStreamServiceBean();
        for (String invalid : List.of(
                "",
                "not-a-number",
                "NaN",
                "Infinity",
                "-1",
                "0",
                "100001",
                "1e3",
                "1.1234567",
                "999999999999999999999999999999999")) {
            EnergyStreamServiceException exception = assertThrows(
                    EnergyStreamServiceException.class,
                    () -> service.demonstrate(invalid),
                    invalid);
            assertEquals("DEMO_INPUT_INVALID", exception.code());
        }
        assertEquals(
                "DEMO_INPUT_INVALID",
                assertThrows(EnergyStreamServiceException.class, () -> service.demonstrate(null)).code());
    }

    @Test
    void responseContractDoesNotContainCryptographicSecretFields() {
        List<String> fieldNames = Arrays.stream(EnergyStreamDemoResult.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .toList();

        assertFalse(fieldNames.stream().anyMatch(name ->
                name.contains("key")
                        || name.contains("secret")
                        || name.contains("signature")
                        || name.contains("nonce")
                        || name.contains("credential")));
    }

    @Test
    void ejbDemoOperationIsAdminOnly() throws Exception {
        RolesAllowed roles = EnergyStreamServiceBean.class
                .getMethod("demonstrate", String.class)
                .getAnnotation(RolesAllowed.class);

        assertNotNull(roles);
        assertArrayEquals(new String[]{"ADMIN"}, roles.value());
    }

    private static byte[] gcmNonce(byte[] envelope) {
        int keyIdLength = Short.toUnsignedInt(ByteBuffer.wrap(envelope, 5, 2).getShort());
        int nonceOffset = 7 + keyIdLength;
        return Arrays.copyOfRange(envelope, nonceOffset, nonceOffset + 12);
    }
}

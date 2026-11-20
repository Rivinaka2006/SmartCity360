package com.smartcity360.ejb.energy;

import com.smartcity360.common.energy.EnergyProcessCommand;
import com.smartcity360.common.energy.EnergyStreamIntegratedDemoResult;
import com.smartcity360.common.energy.EnergyStreamService;
import com.smartcity360.common.energy.EnergyStreamServiceException;
import com.smartcity360.ejb.energy.stream.AesGcmEnvelope;
import com.smartcity360.ejb.energy.stream.EncryptionKey;
import com.smartcity360.ejb.energy.stream.EnergyStreamException;
import com.smartcity360.ejb.energy.stream.GzipPayloadCodec;
import com.smartcity360.ejb.energy.stream.InMemoryReplayProtector;
import com.smartcity360.ejb.energy.stream.MeterKey;
import com.smartcity360.ejb.energy.stream.PayaraKeyProvider;
import com.smartcity360.ejb.persistence.model.EnergyMeterEntity;
import com.smartcity360.ejb.persistence.model.EnergyStreamRunEntity;
import com.smartcity360.ejb.persistence.model.ZoneEntity;
import com.smartcity360.ejb.persistence.repository.EnergyMeterRepository;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnergyStreamIntegratedDemoTest {
    private static final String METER_KEY_ID = EnergyStreamServiceBean.INTEGRATED_DEMO_HMAC_KEY_ID;
    private static final UUID RUN_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    @Test
    void integratedFeatureIsDisabledByDefault() {
        EnergyStreamServiceBean service = new EnergyStreamServiceBean();

        assertFalse(service.integratedDemoPrepared());
        assertEquals(
                "DEMO_DISABLED",
                assertThrows(
                        EnergyStreamServiceException.class,
                        () -> service.processIntegratedDemoReading("1.25"))
                        .code());
    }

    @Test
    void signsTheFixedFixtureRequestAndReusesProductionProcessingAndRecordingPath() throws Exception {
        EnergyStreamServiceBean service = service(new TestKeys(true), new RecordingRecorder());

        assertTrue(service.integratedDemoPrepared());
        EnergyStreamIntegratedDemoResult result = service.processIntegratedDemoReading("1.25");

        assertEquals(RUN_ID, result.runId());
        assertEquals("SUCCESS", result.status());
        assertEquals("ACADEMIC-VIVA-METER", result.meterCode());
        assertEquals(
                "{\"meterCode\":\"ACADEMIC-VIVA-METER\",\"consumptionKwh\":1.25,\"intervalMinutes\":15}",
                result.submittedPayload());
        assertEquals(
                java.util.List.of("RAW", "AUTHENTICATION", "COMPRESSION", "ENCRYPTION"),
                result.decoratorLayers());
        assertTrue(result.meterAuthenticated());
        assertTrue(result.encryptionCompleted());
        assertTrue(result.processingRecordPersisted());

        byte[] envelopeBytes = Base64.getDecoder().decode(result.protectedPayloadBase64());
        byte[] compressed = new AesGcmEnvelope(new TestKeys(true), 1_024).decrypt(envelopeBytes);
        byte[] recovered = GzipPayloadCodec.decompress(compressed, 1_024);
        assertArrayEquals(result.submittedPayload().getBytes(StandardCharsets.UTF_8), recovered);
    }

    @Test
    void reportsMissingFixtureOrEncryptionCredentialAsNotPrepared() {
        EnergyStreamServiceBean missingKeyService = service(new TestKeys(false), new RecordingRecorder(), meter());
        EnergyStreamServiceBean missingMeterService = service(new TestKeys(true), new RecordingRecorder(), null);

        assertFalse(missingKeyService.integratedDemoPrepared());
        assertFalse(missingMeterService.integratedDemoPrepared());
        assertEquals(
                "DEMO_INPUT_INVALID",
                assertThrows(
                        EnergyStreamServiceException.class,
                        () -> missingKeyService.processIntegratedDemoReading("0"))
                        .code());
    }

    @Test
    void rejectsInputOutsideTheExistingDemoValidationLimits() {
        EnergyStreamServiceBean service = new EnergyStreamServiceBean();
        service.integratedDemoEnabled = true;

        assertEquals(
                "DEMO_INPUT_INVALID",
                assertThrows(
                        EnergyStreamServiceException.class,
                        () -> service.processIntegratedDemoReading("1.1234567"))
                        .code());
    }

    private static EnergyStreamServiceBean service(TestKeys keys, RecordingRecorder recorder) {
        return service(keys, recorder, meter());
    }

    private static EnergyStreamServiceBean service(
            TestKeys keys,
            RecordingRecorder recorder,
            EnergyMeterEntity meter) {
        EnergyStreamServiceBean service = new EnergyStreamServiceBean();
        service.meters = new FixtureMeterRepository(meter);
        service.keys = keys;
        service.replay = new InMemoryReplayProtector();
        service.recorder = recorder;
        service.demoRateLimiter = new EnergyStreamDemoRateLimiter();
        service.integratedDemoEnabled = true;
        service.session = sessionContext(service);
        return service;
    }

    private static EnergyMeterEntity meter() {
        ZoneEntity building = new ZoneEntity();
        building.setId(UUID.fromString("a2000000-0000-0000-0000-000000000001"));
        building.setZoneType(ZoneEntity.ZoneType.BUILDING);
        building.setCode(EnergyStreamServiceBean.INTEGRATED_DEMO_BUILDING_CODE);
        building.setStatus(ZoneEntity.Status.ACTIVE);

        EnergyMeterEntity meter = new EnergyMeterEntity();
        meter.setId(UUID.fromString("a3000000-0000-0000-0000-000000000001"));
        meter.setMeterCode(EnergyStreamServiceBean.INTEGRATED_DEMO_METER_CODE);
        meter.setStatus(EnergyMeterEntity.Status.ACTIVE);
        meter.setCredentialKeyId(METER_KEY_ID);
        meter.setCredentialKeyVersion(1);
        meter.setBuildingZone(building);
        return meter;
    }

    private static SessionContext sessionContext(EnergyStreamServiceBean service) {
        EnergyStreamService serviceView = (EnergyStreamService) Proxy.newProxyInstance(
                EnergyStreamService.class.getClassLoader(),
                new Class<?>[]{EnergyStreamService.class},
                (proxy, method, arguments) -> {
                    if ("process".equals(method.getName())) {
                        return service.process((EnergyProcessCommand) arguments[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (SessionContext) Proxy.newProxyInstance(
                SessionContext.class.getClassLoader(),
                new Class<?>[]{SessionContext.class},
                (proxy, method, arguments) -> {
                    if ("getBusinessObject".equals(method.getName())) {
                        return serviceView;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class FixtureMeterRepository extends EnergyMeterRepository {
        private final EnergyMeterEntity meter;

        private FixtureMeterRepository(EnergyMeterEntity meter) {
            this.meter = meter;
        }

        @Override
        public Optional<EnergyMeterEntity> findByMeterCode(String code) {
            return meter != null && meter.getMeterCode().equals(code)
                    ? Optional.of(meter)
                    : Optional.empty();
        }

        @Override
        public Optional<EnergyMeterEntity> findByMeterCodeWithBuildingZone(String code) {
            return findByMeterCode(code);
        }
    }

    private static final class RecordingRecorder extends EnergyStreamRunRecorder {
        @Override
        public UUID record(
                EnergyMeterEntity meter,
                String chain,
                int input,
                Integer output,
                EnergyStreamRunEntity.Status status,
                Integer duration,
                String error) {
            assertEquals(EnergyStreamRunEntity.Status.SUCCESS, status);
            assertEquals("[\"RAW\",\"AUTHENTICATION\",\"COMPRESSION\",\"ENCRYPTION\"]", chain);
            assertTrue(input > 0);
            assertNotNull(output);
            assertNull(error);
            return RUN_ID;
        }
    }

    private static final class TestKeys extends PayaraKeyProvider {
        private final SecretKey hmacKey = new SecretKeySpec(new byte[32], "HmacSHA256");
        private final SecretKey aesKey = new SecretKeySpec(new byte[32], "AES");
        private final boolean available;

        private TestKeys(boolean available) {
            this.available = available;
        }

        @Override
        public MeterKey activeMeterHmacKey(String keyId, int version) {
            if (!available || !METER_KEY_ID.equals(keyId) || version != 1) {
                throw new EnergyStreamException("METER_KEY_UNAVAILABLE");
            }
            return new MeterKey(keyId, version, hmacKey);
        }

        @Override
        public void validateCurrentEncryptionKey(String expectedKeyId) {
            if (!available) {
                throw new EnergyStreamException("KEY_CONFIGURATION_MISSING");
            }
            assertEquals(EnergyStreamServiceBean.INTEGRATED_DEMO_AES_KEY_ID, expectedKeyId);
        }

        @Override
        public EncryptionKey currentEncryptionKey() {
            if (!available) {
                throw new EnergyStreamException("KEY_CONFIGURATION_MISSING");
            }
            return new EncryptionKey(EnergyStreamServiceBean.INTEGRATED_DEMO_AES_KEY_ID, aesKey);
        }

        @Override
        public SecretKey resolveEncryptionKey(String keyId) {
            if (!available || !EnergyStreamServiceBean.INTEGRATED_DEMO_AES_KEY_ID.equals(keyId)) {
                throw new EnergyStreamException("ENCRYPTION_KEY_UNAVAILABLE");
            }
            return aesKey;
        }
    }
}

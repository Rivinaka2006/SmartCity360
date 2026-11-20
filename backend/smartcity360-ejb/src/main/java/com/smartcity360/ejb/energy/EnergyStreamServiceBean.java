package com.smartcity360.ejb.energy;

import com.smartcity360.common.energy.*;
import com.smartcity360.ejb.energy.stream.*;
import com.smartcity360.ejb.persistence.model.*;
import com.smartcity360.ejb.persistence.repository.*;
import com.smartcity360.ejb.security.SecurityAuditService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.*;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.security.auth.Destroyable;
import java.math.BigDecimal;

@Stateless
@DeclareRoles({"ADMIN", "CITY_MANAGER", "LOCAL_STAFF", "SUPERVISOR"})
public class EnergyStreamServiceBean implements EnergyStreamService {
    private static final int MAX = 1_048_576;
    private static final int DEMO_MAX_PAYLOAD_BYTES = 1_024;
    private static final BigDecimal DEMO_MAX_CONSUMPTION_KWH = new BigDecimal("100000");
    private static final Pattern DEMO_CONSUMPTION_FORMAT = Pattern.compile("[0-9]{1,9}(\\.[0-9]{1,6})?");
    static final String INTEGRATED_DEMO_METER_CODE = "ACADEMIC-VIVA-METER";
    static final String INTEGRATED_DEMO_BUILDING_CODE = "ACADEMIC-VIVA-BUILDING";
    static final String INTEGRATED_DEMO_HMAC_KEY_ID = "smartcity360.energy.hmac.academic.viva.v1";
    static final String INTEGRATED_DEMO_AES_KEY_ID = "smartcity360.energy.aes.academic.viva.v1";
    static final String INTEGRATED_DEMO_ENABLED_PROPERTY = "smartcity360.energy.stream.demo.integrated.enabled";
    private static final String INTEGRATED_DEMO_PATH = "/api/v1/energy/streams/demo/process-reading";
    private static final List<String> INTEGRATED_DEMO_LAYERS = List.of(
            "AUTHENTICATION",
            "COMPRESSION",
            "ENCRYPTION");
    @Inject
    EnergyMeterRepository meters;
    @Inject
    EnergyStreamRunRepository runs;
    @Inject
    UserRepository users;
    @Inject
    PayaraKeyProvider keys;
    @Inject
    InMemoryReplayProtector replay;
    @Inject
    EnergyStreamRunRecorder recorder;
    @Inject
    EnergyStreamDemoRateLimiter demoRateLimiter;
    @Inject
    @ConfigProperty(name = INTEGRATED_DEMO_ENABLED_PROPERTY, defaultValue = "false")
    boolean integratedDemoEnabled;
    @Inject
    SecurityAuditService audit;
    @Resource
    SessionContext session;

    @Override
    @PermitAll
    public EnergyProcessResult process(EnergyProcessCommand c) throws EnergyStreamServiceException {
        if (c == null || c.payload() == null || c.payload().length > MAX || c.meterCode() == null)
            throw new EnergyStreamServiceException("REQUEST_INVALID");
        EnergyMeterEntity meter = meters.findByMeterCode(c.meterCode()).orElseThrow(() -> new EnergyStreamServiceException("METER_AUTHENTICATION_FAILED"));
        List<ProcessingLayer> layers = parse(c.layers());
        long start = System.nanoTime();
        try {
            if (meter.getStatus() != EnergyMeterEntity.Status.ACTIVE || meter.getCredentialKeyId() == null || meter.getCredentialKeyVersion() == null)
                throw new EnergyStreamException("METER_AUTHENTICATION_FAILED");
            if (meter.getCredentialKeyVersion() != c.credentialVersion())
                throw new EnergyStreamException("METER_KEY_VERSION_MISMATCH");
            var proof = new MeterProof(meter.getMeterCode(), c.credentialVersion(), c.timestamp(), c.nonce(), c.method(), c.path(), c.signature());
            StreamResult result = new EnergyStreamPipelineFactory(keys, replay, MAX).create(layers).process(new StreamContext(c.payload(), proof, meter.getCredentialKeyId()));
            int duration = duration(start);
            String chain = json(result.layers());
            UUID id = recorder.record(meter, chain, c.payload().length, result.output().length, EnergyStreamRunEntity.Status.SUCCESS, duration, null);
            return new EnergyProcessResult(id, result.output(), result.layers().stream().map(Enum::name).toList(), c.payload().length, result.output().length);
        } catch (EnergyStreamException e) {
            recorder.record(meter, json(layers), c.payload().length, null, EnergyStreamRunEntity.Status.FAILED, duration(start), safe(e.code()));
            throw new EnergyStreamServiceException(safe(e.code()));
        }
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public EnergyStreamDemoResult demonstrate(String consumptionKwh) throws EnergyStreamServiceException {
        BigDecimal consumption = parseDemoConsumption(consumptionKwh);
        String normalizedConsumption = consumption.stripTrailingZeros().toPlainString();
        String payloadText = "{\"meterCode\":\"ACADEMIC-DEMO-METER\",\"consumptionKwh\":"
                + normalizedConsumption
                + ",\"intervalMinutes\":15}";
        byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        if (payload.length > DEMO_MAX_PAYLOAD_BYTES) {
            throw new EnergyStreamServiceException("DEMO_INPUT_INVALID");
        }

        SecureRandom random = new SecureRandom();
        byte[] hmacBytes = new byte[32];
        byte[] aesBytes = new byte[32];
        byte[] nonceBytes = new byte[18];
        byte[] signature = null;
        EphemeralKeyProvider keys = null;

        try {
            random.nextBytes(hmacBytes);
            random.nextBytes(aesBytes);
            random.nextBytes(nonceBytes);
            EphemeralSecretKey hmacKey = new EphemeralSecretKey(hmacBytes, "HmacSHA256");
            EphemeralSecretKey aesKey = new EphemeralSecretKey(aesBytes, "AES");
            String meterKeyId = "demo-hmac-" + UUID.randomUUID();
            String encryptionKeyId = "demo-aes-" + UUID.randomUUID();
            keys = new EphemeralKeyProvider(meterKeyId, encryptionKeyId, hmacKey, aesKey);

            Instant timestamp = Instant.now();
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
            signature = HmacRequestCanonicalizer.sign(
                    hmacKey,
                    "ACADEMIC-DEMO-METER",
                    1,
                    timestamp,
                    nonce,
                    "POST",
                    "/api/v1/energy/streams/demo",
                    payload);
            MeterProof proof = new MeterProof(
                    "ACADEMIC-DEMO-METER",
                    1,
                    timestamp,
                    nonce,
                    "POST",
                    "/api/v1/energy/streams/demo",
                    signature);
            Arrays.fill(signature, (byte) 0);
            signature = null;
            Arrays.fill(nonceBytes, (byte) 0);

            StreamResult result = new EnergyStreamPipelineFactory(
                    keys,
                    new InMemoryReplayProtector(),
                    DEMO_MAX_PAYLOAD_BYTES)
                    .create(List.of(
                            ProcessingLayer.AUTHENTICATION,
                            ProcessingLayer.COMPRESSION,
                            ProcessingLayer.ENCRYPTION))
                    .process(new StreamContext(payload, proof, meterKeyId));
            List<ProcessingLayer> expectedLayers = List.of(
                    ProcessingLayer.RAW,
                    ProcessingLayer.AUTHENTICATION,
                    ProcessingLayer.COMPRESSION,
                    ProcessingLayer.ENCRYPTION);
            if (!result.layers().equals(expectedLayers)) {
                throw new EnergyStreamServiceException("DEMO_PROCESSING_FAILED");
            }

            byte[] encryptedEnvelope = result.output();
            AesGcmEnvelope envelope = new AesGcmEnvelope(keys, DEMO_MAX_PAYLOAD_BYTES);
            byte[] compressedPayload = envelope.decrypt(encryptedEnvelope);
            byte[] recoveredPayload = GzipPayloadCodec.decompress(compressedPayload, DEMO_MAX_PAYLOAD_BYTES);
            boolean recoveredOriginal = MessageDigest.isEqual(payload, recoveredPayload);
            if (!recoveredOriginal) {
                throw new EnergyStreamServiceException("DEMO_PROCESSING_FAILED");
            }

            List<String> decoratorLayers = result.layers().stream()
                    .skip(1)
                    .map(Enum::name)
                    .toList();
            return new EnergyStreamDemoResult(
                    true,
                    payloadText,
                    15,
                    decoratorLayers,
                    payload.length,
                    encryptedEnvelope.length,
                    Base64.getEncoder().encodeToString(encryptedEnvelope),
                    true,
                    true);
        } catch (EnergyStreamException exception) {
            throw new EnergyStreamServiceException("DEMO_PROCESSING_FAILED");
        } finally {
            Arrays.fill(hmacBytes, (byte) 0);
            Arrays.fill(aesBytes, (byte) 0);
            Arrays.fill(nonceBytes, (byte) 0);
            if (signature != null) {
                Arrays.fill(signature, (byte) 0);
            }
            if (keys != null) {
                keys.destroy();
            }
        }
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean integratedDemoPrepared() {
        if (!integratedDemoEnabled) {
            return false;
        }
        try {
            requireIntegratedDemoMeter();
            return true;
        } catch (EnergyStreamServiceException exception) {
            if ("DEMO_PREPARATION_REQUIRED".equals(exception.code())) {
                return false;
            }
            throw new EJBException("Integrated energy demo readiness could not be checked");
        }
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public EnergyStreamIntegratedDemoResult processIntegratedDemoReading(String consumptionKwh)
            throws EnergyStreamServiceException {
        if (!integratedDemoEnabled) {
            throw new EnergyStreamServiceException("DEMO_DISABLED");
        }
        BigDecimal consumption = parseDemoConsumption(consumptionKwh);
        if (demoRateLimiter == null || !demoRateLimiter.tryAcquire()) {
            throw new EnergyStreamServiceException("DEMO_RATE_LIMITED");
        }

        EnergyMeterEntity meter = requireIntegratedDemoMeter();
        int credentialVersion = meter.getCredentialKeyVersion();
        MeterKey meterKey;
        try {
            meterKey = keys.activeMeterHmacKey(meter.getCredentialKeyId(), credentialVersion);
            if (!INTEGRATED_DEMO_HMAC_KEY_ID.equals(meterKey.id())
                    || meterKey.version() != credentialVersion) {
                throw new EnergyStreamException("METER_KEY_UNAVAILABLE");
            }
        } catch (EnergyStreamException exception) {
            throw new EnergyStreamServiceException("DEMO_PREPARATION_REQUIRED");
        }

        String normalizedConsumption = consumption.stripTrailingZeros().toPlainString();
        String payloadText = "{\"meterCode\":\""
                + INTEGRATED_DEMO_METER_CODE
                + "\",\"consumptionKwh\":"
                + normalizedConsumption
                + ",\"intervalMinutes\":15}";
        byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        if (payload.length > DEMO_MAX_PAYLOAD_BYTES) {
            throw new EnergyStreamServiceException("DEMO_INPUT_INVALID");
        }

        byte[] nonceBytes = new byte[18];
        byte[] signature = null;
        try {
            new SecureRandom().nextBytes(nonceBytes);
            Instant timestamp = Instant.now();
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
            signature = HmacRequestCanonicalizer.sign(
                    meterKey.key(),
                    INTEGRATED_DEMO_METER_CODE,
                    credentialVersion,
                    timestamp,
                    nonce,
                    "POST",
                    INTEGRATED_DEMO_PATH,
                    payload);
            EnergyProcessCommand command = new EnergyProcessCommand(
                    INTEGRATED_DEMO_METER_CODE,
                    credentialVersion,
                    timestamp,
                    nonce,
                    "POST",
                    INTEGRATED_DEMO_PATH,
                    signature,
                    payload,
                    INTEGRATED_DEMO_LAYERS);
            EnergyProcessResult processed = session.getBusinessObject(EnergyStreamService.class).process(command);
            return new EnergyStreamIntegratedDemoResult(
                    processed.runId(),
                    "SUCCESS",
                    INTEGRATED_DEMO_METER_CODE,
                    payloadText,
                    processed.layers(),
                    processed.inputBytes(),
                    processed.outputBytes(),
                    Base64.getEncoder().encodeToString(processed.output()),
                    true,
                    true,
                    true);
        } catch (EnergyStreamException exception) {
            throw new EnergyStreamServiceException("DEMO_PROCESSING_FAILED");
        } finally {
            Arrays.fill(nonceBytes, (byte) 0);
            if (signature != null) {
                Arrays.fill(signature, (byte) 0);
            }
            Arrays.fill(payload, (byte) 0);
        }
    }

    private EnergyMeterEntity requireIntegratedDemoMeter() throws EnergyStreamServiceException {
        EnergyMeterEntity meter = meters.findByMeterCodeWithBuildingZone(INTEGRATED_DEMO_METER_CODE)
                .orElseThrow(() -> new EnergyStreamServiceException("DEMO_PREPARATION_REQUIRED"));
        ZoneEntity building = meter.getBuildingZone();
        if (meter.getStatus() != EnergyMeterEntity.Status.ACTIVE
                || meter.getCredentialKeyId() == null
                || meter.getCredentialKeyVersion() == null
                || meter.getCredentialKeyVersion() != 1
                || !INTEGRATED_DEMO_HMAC_KEY_ID.equals(meter.getCredentialKeyId())
                || building == null
                || building.getZoneType() != ZoneEntity.ZoneType.BUILDING
                || building.getStatus() != ZoneEntity.Status.ACTIVE
                || !INTEGRATED_DEMO_BUILDING_CODE.equals(building.getCode())) {
            throw new EnergyStreamServiceException("DEMO_PREPARATION_REQUIRED");
        }
        try {
            MeterKey hmacKey = keys.activeMeterHmacKey(meter.getCredentialKeyId(), meter.getCredentialKeyVersion());
            if (!INTEGRATED_DEMO_HMAC_KEY_ID.equals(hmacKey.id())
                    || hmacKey.version() != meter.getCredentialKeyVersion()) {
                throw new EnergyStreamException("METER_KEY_UNAVAILABLE");
            }
            keys.validateCurrentEncryptionKey(INTEGRATED_DEMO_AES_KEY_ID);
        } catch (EnergyStreamException exception) {
            throw new EnergyStreamServiceException("DEMO_PREPARATION_REQUIRED");
        }
        return meter;
    }

    private static BigDecimal parseDemoConsumption(String consumptionKwh) throws EnergyStreamServiceException {
        if (consumptionKwh == null
                || consumptionKwh.length() > 32
                || !DEMO_CONSUMPTION_FORMAT.matcher(consumptionKwh).matches()) {
            throw new EnergyStreamServiceException("DEMO_INPUT_INVALID");
        }
        try {
            BigDecimal consumption = new BigDecimal(consumptionKwh);
            if (consumption.signum() <= 0 || consumption.compareTo(DEMO_MAX_CONSUMPTION_KWH) > 0) {
                throw new EnergyStreamServiceException("DEMO_INPUT_INVALID");
            }
            return consumption;
        } catch (NumberFormatException exception) {
            throw new EnergyStreamServiceException("DEMO_INPUT_INVALID");
        }
    }

    private static final class EphemeralKeyProvider implements KeyProvider {
        private final String meterKeyId;
        private final String encryptionKeyId;
        private final EphemeralSecretKey hmacKey;
        private final EphemeralSecretKey aesKey;

        private EphemeralKeyProvider(
                String meterKeyId,
                String encryptionKeyId,
                EphemeralSecretKey hmacKey,
                EphemeralSecretKey aesKey) {
            this.meterKeyId = meterKeyId;
            this.encryptionKeyId = encryptionKeyId;
            this.hmacKey = hmacKey;
            this.aesKey = aesKey;
        }

        @Override
        public MeterKey activeMeterHmacKey(String keyId, int version) {
            if (!meterKeyId.equals(keyId) || version != 1) {
                throw new EnergyStreamException("METER_KEY_UNAVAILABLE");
            }
            return new MeterKey(meterKeyId, 1, hmacKey);
        }

        @Override
        public SecretKey resolveMeterHmacKey(String keyId, int version) {
            return activeMeterHmacKey(keyId, version).key();
        }

        @Override
        public EncryptionKey currentEncryptionKey() {
            return new EncryptionKey(encryptionKeyId, aesKey);
        }

        @Override
        public SecretKey resolveEncryptionKey(String keyId) {
            if (!encryptionKeyId.equals(keyId)) {
                throw new EnergyStreamException("ENCRYPTION_KEY_UNAVAILABLE");
            }
            return aesKey;
        }

        private void destroy() {
            hmacKey.destroy();
            aesKey.destroy();
        }
    }

    private static final class EphemeralSecretKey implements SecretKey, Destroyable {
        private static final long serialVersionUID = 1L;
        private final String algorithm;
        private final byte[] encoded;
        private boolean destroyed;

        private EphemeralSecretKey(byte[] encoded, String algorithm) {
            this.encoded = encoded.clone();
            this.algorithm = algorithm;
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public synchronized byte[] getEncoded() {
            if (destroyed) {
                throw new IllegalStateException("Ephemeral key has been destroyed");
            }
            return encoded.clone();
        }

        @Override
        public synchronized void destroy() {
            Arrays.fill(encoded, (byte) 0);
            destroyed = true;
        }

        @Override
        public synchronized boolean isDestroyed() {
            return destroyed;
        }
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public byte[] verify(byte[] envelope) throws EnergyStreamServiceException {
        try {
            byte[] plain = new AesGcmEnvelope(keys, MAX).decrypt(envelope);
            UUID actor = users.findByEmail(session.getCallerPrincipal().getName()).map(UserEntity::getId).orElse(null);
            audit.record("ENERGY_STREAM_VERIFY", actor, "SUCCESS", null);
            return plain;
        } catch (EnergyStreamException e) {
            throw new EnergyStreamServiceException(safe(e.code()));
        }
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR"})
    public List<EnergyRunView> listRuns(int offset, int limit) {
        return runs.list(offset, Math.min(Math.max(limit, 1), 100)).stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR"})
    public EnergyRunView findRun(UUID id) throws EnergyStreamServiceException {
        return runs.findById(id).map(this::view).orElseThrow(() -> new EnergyStreamServiceException("RUN_NOT_FOUND"));
    }

    private EnergyRunView view(EnergyStreamRunEntity r) {
        return new EnergyRunView(r.getId(), r.getMeter().getMeterCode(), r.getDecoratorChain(), r.getInputBytes(), r.getOutputBytes(), r.getStatus().name(), r.getDurationMs(), r.getErrorCode(), r.getExecutedAt());
    }

    private static List<ProcessingLayer> parse(List<String> names) throws EnergyStreamServiceException {
        try {
            return names.stream().map(s -> ProcessingLayer.valueOf(s.toUpperCase(Locale.ROOT))).toList();
        } catch (Exception e) {
            throw new EnergyStreamServiceException("LAYER_CONFIGURATION_INVALID");
        }
    }

    private static int duration(long start) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - start) / 1_000_000L);
    }

    private static String json(List<ProcessingLayer> layers) {
        return layers.stream().map(l -> "\"" + l.name() + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String safe(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "PROCESSING_FAILED";
    }
}

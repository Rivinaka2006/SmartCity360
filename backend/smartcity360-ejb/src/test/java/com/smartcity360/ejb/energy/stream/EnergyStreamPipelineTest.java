package com.smartcity360.ejb.energy.stream;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class EnergyStreamPipelineTest {
    private static final byte[] HMAC = bytes(0x11);
    private static final byte[] AES = bytes(0x22);
    private static final Instant NOW = Instant.parse("2026-11-01T03:30:00Z");

    @Test
    void securePipelineAuthenticatesThenCompressesThenEncryptsAndRecoversExactBytes() {
        byte[] payload = "meter-reading:".repeat(100).getBytes(StandardCharsets.UTF_8);
        var keys = keys();
        var proof = proof(payload, "nonce-001", 1, "POST", "/api/v1/energy/streams/process");
        var context = new StreamContext(payload, proof, "meter.hmac.demo.v1");
        var factory = new EnergyStreamPipelineFactory(keys, replay(), 1_048_576);

        StreamResult result = factory.create(List.of(ProcessingLayer.AUTHENTICATION,
                ProcessingLayer.COMPRESSION, ProcessingLayer.ENCRYPTION)).process(context);

        assertEquals(List.of(ProcessingLayer.RAW, ProcessingLayer.AUTHENTICATION,
                ProcessingLayer.COMPRESSION, ProcessingLayer.ENCRYPTION), result.layers());
        byte[] compressed = new AesGcmEnvelope(keys, 1_048_576).decrypt(result.output());
        assertArrayEquals(payload, GzipPayloadCodec.decompress(compressed, 1_048_576));
    }

    @Test
    void hmacRejectsPayloadMethodVersionAndSignatureChanges() {
        byte[] payload = "42".getBytes(StandardCharsets.UTF_8);
        MeterProof valid = proof(payload, "nonce-002", 1, "POST", "/api/v1/energy/streams/process");
        var authenticator = new MeterAuthenticator(keys(), replay());
        authenticator.authenticate(new StreamContext(payload, valid, "meter.hmac.demo.v1"));

        assertCode("METER_MAC_INVALID", () -> authenticator.authenticate(new StreamContext("43".getBytes(StandardCharsets.UTF_8), proof(payload, "nonce-003", 1, "POST", "/api/v1/energy/streams/process"), "meter.hmac.demo.v1")));
        MeterProof signedPost = proof(payload, "nonce-004", 1, "POST", "/api/v1/energy/streams/process");
        MeterProof alteredMethod = new MeterProof(signedPost.meterCode(), signedPost.credentialVersion(), signedPost.timestamp(),
                signedPost.nonce(), "PUT", signedPost.path(), signedPost.mac());
        assertCode("METER_MAC_INVALID", () -> authenticator.authenticate(new StreamContext(payload, alteredMethod, "meter.hmac.demo.v1")));
        assertCode("METER_KEY_VERSION_MISMATCH", () -> authenticator.authenticate(new StreamContext(payload, proof(payload, "nonce-005", 2, "POST", "/api/v1/energy/streams/process"), "meter.hmac.demo.v1")));
    }

    @Test
    void rejectsExpiredAndReplayedProofIncludingConcurrentReplay() throws Exception {
        byte[] payload = {1};
        var authenticator = new MeterAuthenticator(keys(), replay());
        assertCode("METER_TIMESTAMP_INVALID", () -> authenticator.authenticate(new StreamContext(payload,
                signed(payload, "nonce-old", 1, NOW.minusSeconds(301)), "meter.hmac.demo.v1")));

        MeterProof proof = proof(payload, "same-nonce", 1, "POST", "/api/v1/energy/streams/process");
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> (Callable<Boolean>) () -> {
                        try { authenticator.authenticate(new StreamContext(payload, proof, "meter.hmac.demo.v1")); return true; }
                        catch (EnergyStreamException e) { assertEquals("METER_REPLAY", e.code()); return false; }
                    }).toList();
            long successes = pool.invokeAll(calls).stream().filter(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).count();
            assertEquals(1, successes);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void compressionRoundTripsEmptyIncompressibleAndHighlyCompressibleAndBoundsExpansion() {
        for (byte[] value : List.of(new byte[0], randomBytes(4096), "x".repeat(10000).getBytes(StandardCharsets.UTF_8)))
            assertArrayEquals(value, GzipPayloadCodec.decompress(GzipPayloadCodec.compress(value, 20_000), 20_000));
        assertCode("DECOMPRESSED_SIZE_LIMIT", () -> GzipPayloadCodec.decompress(
                GzipPayloadCodec.compress("x".repeat(10000).getBytes(StandardCharsets.UTF_8), 20_000), 100));
        assertCode("COMPRESSED_PAYLOAD_INVALID", () -> GzipPayloadCodec.decompress(new byte[]{1,2,3}, 100));
    }

    @Test
    void aesGcmUsesUniqueNoncesAndRejectsTamperingAndUnknownKeys() {
        var envelope = new AesGcmEnvelope(keys(), 1_048_576);
        byte[] one = envelope.encrypt("hello".getBytes(StandardCharsets.UTF_8));
        byte[] two = envelope.encrypt("hello".getBytes(StandardCharsets.UTF_8));
        assertFalse(java.util.Arrays.equals(one, two));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), envelope.decrypt(one));
        one[one.length - 1] ^= 1;
        assertCode("CIPHERTEXT_INVALID", () -> envelope.decrypt(one));
        assertCode("ENCRYPTION_KEY_UNAVAILABLE", () -> new AesGcmEnvelope(new TestKeys(false), 100).encrypt(new byte[]{1}));
    }

    @Test
    void factoryRejectsMissingAuthenticationDuplicatesAndUnsupportedOrder() {
        var factory = new EnergyStreamPipelineFactory(keys(), replay(), 1000);
        assertCode("AUTHENTICATION_REQUIRED", () -> factory.create(List.of(ProcessingLayer.COMPRESSION)));
        assertCode("DUPLICATE_LAYER", () -> factory.create(List.of(ProcessingLayer.AUTHENTICATION, ProcessingLayer.AUTHENTICATION)));
        assertCode("LAYER_ORDER_INVALID", () -> factory.create(List.of(ProcessingLayer.AUTHENTICATION, ProcessingLayer.ENCRYPTION, ProcessingLayer.COMPRESSION)));
        StreamResult raw = factory.createInternalDemo(List.of()).process(new StreamContext(new byte[]{7}, null, null));
        assertEquals(List.of(ProcessingLayer.RAW), raw.layers());
        assertCode("PAYLOAD_SIZE_INVALID", () -> factory.createInternalDemo(List.of()).process(new StreamContext(new byte[1001], null, null)));
    }

    private MeterProof proof(byte[] payload, String nonce, int version, String method, String path) {
        byte[] mac = HmacRequestCanonicalizer.sign(new SecretKeySpec(HMAC, "HmacSHA256"), "METER-1", version, NOW, nonce, method, path, payload);
        return new MeterProof("METER-1", version, NOW, nonce, method, path, mac);
    }
    private MeterProof signed(byte[] payload, String nonce, int version, Instant timestamp) {
        byte[] mac = HmacRequestCanonicalizer.sign(new SecretKeySpec(HMAC, "HmacSHA256"), "METER-1", version, timestamp, nonce, "POST", "/api/v1/energy/streams/process", payload);
        return new MeterProof("METER-1", version, timestamp, nonce, "POST", "/api/v1/energy/streams/process", mac);
    }
    private TestKeys keys() { return new TestKeys(true); }
    private InMemoryReplayProtector replay() { return new InMemoryReplayProtector(Clock.fixed(NOW, ZoneOffset.UTC), 300, 1000); }
    private static byte[] bytes(int value) { byte[] b = new byte[32]; java.util.Arrays.fill(b, (byte)value); return b; }
    private static byte[] randomBytes(int length) { byte[] b = new byte[length]; new java.security.SecureRandom().nextBytes(b); return b; }
    private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) { assertEquals(code, assertThrows(EnergyStreamException.class, action).code()); }

    private static final class TestKeys implements KeyProvider {
        private final boolean available;
        private TestKeys(boolean available) { this.available = available; }
        public MeterKey activeMeterHmacKey(String id, int version) { if (!available) throw new EnergyStreamException("METER_KEY_UNAVAILABLE"); return new MeterKey(id, 1, new SecretKeySpec(HMAC, "HmacSHA256")); }
        public SecretKey resolveMeterHmacKey(String id, int version) { return activeMeterHmacKey(id, version).key(); }
        public EncryptionKey currentEncryptionKey() { if (!available) throw new EnergyStreamException("ENCRYPTION_KEY_UNAVAILABLE"); return new EncryptionKey("energy.aes.v1", new SecretKeySpec(AES, "AES")); }
        public SecretKey resolveEncryptionKey(String id) { if (!available || !Set.of("energy.aes.v1").contains(id)) throw new EnergyStreamException("ENCRYPTION_KEY_UNAVAILABLE"); return new SecretKeySpec(AES, "AES"); }
    }
}

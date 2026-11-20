package com.smartcity360.ejb.energy.stream;

import java.security.MessageDigest;

public final class MeterAuthenticator {
    private final KeyProvider keys;
    private final InMemoryReplayProtector replay;

    public MeterAuthenticator(KeyProvider keys, InMemoryReplayProtector replay) {
        this.keys = keys;
        this.replay = replay;
    }

    public void authenticate(StreamContext c) {
        MeterProof p = c.proof();
        if (p == null || p.mac() == null) throw new EnergyStreamException("METER_PROOF_INVALID");
        MeterKey active = keys.activeMeterHmacKey(c.credentialKeyId(), p.credentialVersion());
        if (active.version() != p.credentialVersion()) throw new EnergyStreamException("METER_KEY_VERSION_MISMATCH");
        byte[] expected = HmacRequestCanonicalizer.sign(active.key(), p.meterCode(), p.credentialVersion(), p.timestamp(), p.nonce(), p.method(), p.path(), c.payload());
        if (!MessageDigest.isEqual(expected, p.mac())) throw new EnergyStreamException("METER_MAC_INVALID");
        replay.accept(p.meterCode(), p.credentialVersion(), p.timestamp(), p.nonce());
    }
}

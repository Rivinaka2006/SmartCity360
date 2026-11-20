package com.smartcity360.ejb.energy.stream;

public record StreamContext(byte[] payload, MeterProof proof, String credentialKeyId) {
    public StreamContext {
        payload = payload == null ? null : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload == null ? null : payload.clone();
    }
}

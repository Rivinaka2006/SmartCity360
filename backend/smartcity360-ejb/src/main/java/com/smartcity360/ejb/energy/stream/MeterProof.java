package com.smartcity360.ejb.energy.stream;

import java.time.Instant;

public record MeterProof(String meterCode, int credentialVersion, Instant timestamp, String nonce, String method,
                         String path, byte[] mac) {
    public MeterProof {
        mac = mac == null ? null : mac.clone();
    }

    @Override
    public byte[] mac() {
        return mac == null ? null : mac.clone();
    }
}

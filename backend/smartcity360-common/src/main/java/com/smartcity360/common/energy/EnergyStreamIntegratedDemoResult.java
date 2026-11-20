package com.smartcity360.common.energy;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record EnergyStreamIntegratedDemoResult(
        UUID runId,
        String status,
        String meterCode,
        String submittedPayload,
        List<String> decoratorLayers,
        int inputBytes,
        int encryptedOutputBytes,
        String protectedPayloadBase64,
        boolean meterAuthenticated,
        boolean encryptionCompleted,
        boolean processingRecordPersisted) implements Serializable {
    public EnergyStreamIntegratedDemoResult {
        decoratorLayers = List.copyOf(decoratorLayers);
    }
}

package com.smartcity360.common.energy;

import java.io.Serializable;
import java.util.List;

public record EnergyStreamDemoResult(
        boolean demonstrationOnly,
        String inputPayload,
        int readingIntervalMinutes,
        List<String> decoratorLayers,
        int inputBytes,
        int encryptedOutputBytes,
        String encryptedEnvelopeBase64,
        boolean authenticatedProcessingSucceeded,
        boolean recoveredOriginalReading) implements Serializable {
    public EnergyStreamDemoResult {
        decoratorLayers = List.copyOf(decoratorLayers);
    }
}

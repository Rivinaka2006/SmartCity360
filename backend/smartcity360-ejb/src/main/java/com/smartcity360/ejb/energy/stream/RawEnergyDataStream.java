package com.smartcity360.ejb.energy.stream;

import java.util.*;

public final class RawEnergyDataStream implements EnergyDataStream {
    private final int maxBytes;

    public RawEnergyDataStream(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public StreamResult process(StreamContext c) {
        if (c == null || c.payload() == null || c.payload().length > maxBytes)
            throw new EnergyStreamException("PAYLOAD_SIZE_INVALID");
        return new StreamResult(c.payload(), List.of(ProcessingLayer.RAW), Map.of("content", "energy-meter"));
    }
}

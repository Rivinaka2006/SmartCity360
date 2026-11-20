package com.smartcity360.ejb.energy.stream;

import java.util.*;

public final class EnergyStreamPipelineFactory {
    private final KeyProvider keys;
    private final InMemoryReplayProtector replay;
    private final int max;

    public EnergyStreamPipelineFactory(KeyProvider k, InMemoryReplayProtector r, int m) {
        keys = k;
        replay = r;
        max = m;
    }

    public EnergyDataStream createInternalDemo(List<ProcessingLayer> layers) {
        if (layers == null || layers.isEmpty()) return new RawEnergyDataStream(max);
        return create(layers);
    }

    public EnergyDataStream create(List<ProcessingLayer> layers) {
        if (layers == null || !layers.contains(ProcessingLayer.AUTHENTICATION))
            throw new EnergyStreamException("AUTHENTICATION_REQUIRED");
        if (new HashSet<>(layers).size() != layers.size()) throw new EnergyStreamException("DUPLICATE_LAYER");
        List<ProcessingLayer> allowed = List.of(ProcessingLayer.AUTHENTICATION, ProcessingLayer.COMPRESSION, ProcessingLayer.ENCRYPTION);
        int last = -1;
        for (var l : layers) {
            int i = allowed.indexOf(l);
            if (i < 0 || i < last) throw new EnergyStreamException("LAYER_ORDER_INVALID");
            last = i;
        }
        EnergyDataStream s = new RawEnergyDataStream(max);
        for (var l : layers)
            s = switch (l) {
                case AUTHENTICATION -> new AuthenticationDecorator(s, new MeterAuthenticator(keys, replay));
                case COMPRESSION -> new CompressionDecorator(s, max);
                case ENCRYPTION -> new EncryptionDecorator(s, new AesGcmEnvelope(keys, max));
                default -> throw new EnergyStreamException("LAYER_ORDER_INVALID");
            };
        return s;
    }
}

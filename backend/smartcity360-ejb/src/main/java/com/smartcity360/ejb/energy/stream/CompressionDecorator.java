package com.smartcity360.ejb.energy.stream;

public final class CompressionDecorator extends StreamDecorator {
    private final int max;

    public CompressionDecorator(EnergyDataStream d, int max) {
        super(d);
        this.max = max;
    }

    public StreamResult process(StreamContext c) {
        StreamResult r = delegate.process(c);
        return r.append(ProcessingLayer.COMPRESSION, GzipPayloadCodec.compress(r.output(), max));
    }
}

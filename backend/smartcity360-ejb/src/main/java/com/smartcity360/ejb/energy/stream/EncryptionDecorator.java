package com.smartcity360.ejb.energy.stream;

public final class EncryptionDecorator extends StreamDecorator {
    private final AesGcmEnvelope crypto;

    public EncryptionDecorator(EnergyDataStream d, AesGcmEnvelope c) {
        super(d);
        crypto = c;
    }

    public StreamResult process(StreamContext c) {
        StreamResult r = delegate.process(c);
        return r.append(ProcessingLayer.ENCRYPTION, crypto.encrypt(r.output()));
    }
}

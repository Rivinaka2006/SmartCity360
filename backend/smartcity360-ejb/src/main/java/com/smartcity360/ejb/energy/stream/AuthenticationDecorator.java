package com.smartcity360.ejb.energy.stream;

public final class AuthenticationDecorator extends StreamDecorator {
    private final MeterAuthenticator auth;

    public AuthenticationDecorator(EnergyDataStream d, MeterAuthenticator a) {
        super(d);
        auth = a;
    }

    public StreamResult process(StreamContext c) {
        auth.authenticate(c);
        StreamResult r = delegate.process(c);
        return r.append(ProcessingLayer.AUTHENTICATION, r.output());
    }
}

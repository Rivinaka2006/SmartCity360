package com.smartcity360.ejb.energy.stream;

public abstract class StreamDecorator implements EnergyDataStream {
    protected final EnergyDataStream delegate;

    protected StreamDecorator(EnergyDataStream delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate");
        this.delegate = delegate;
    }
}

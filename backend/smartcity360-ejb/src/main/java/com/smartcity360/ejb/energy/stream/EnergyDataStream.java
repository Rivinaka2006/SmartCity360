package com.smartcity360.ejb.energy.stream;

@FunctionalInterface
public interface EnergyDataStream {
    StreamResult process(StreamContext context);
}

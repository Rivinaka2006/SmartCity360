package com.smartcity360.ejb.energy.stream;

import java.util.*;

public record StreamResult(byte[] output, List<ProcessingLayer> layers, Map<String, String> metadata) {
    public StreamResult {
        output = output.clone();
        layers = List.copyOf(layers);
        metadata = Map.copyOf(metadata);
    }

    @Override
    public byte[] output() {
        return output.clone();
    }

    public StreamResult append(ProcessingLayer layer, byte[] bytes) {
        var next = new ArrayList<>(layers);
        next.add(layer);
        return new StreamResult(bytes, next, metadata);
    }
}

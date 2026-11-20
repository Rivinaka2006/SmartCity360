package com.smartcity360.ejb.energy.stream;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.*;
import java.util.*;

@ApplicationScoped
public class InMemoryReplayProtector {
    private Clock clock;
    private long windowSeconds;
    private int capacity;
    private final LinkedHashMap<String, Instant> seen = new LinkedHashMap<>();

    public InMemoryReplayProtector() {
        this(Clock.systemUTC(), 300, 100_000);
    }

    public InMemoryReplayProtector(Clock clock, long windowSeconds, int capacity) {
        this.clock = clock;
        this.windowSeconds = windowSeconds;
        this.capacity = capacity;
    }

    public synchronized void accept(String meter, int version, Instant timestamp, String nonce) {
        Instant now = clock.instant();
        if (timestamp.isBefore(now.minusSeconds(windowSeconds)) || timestamp.isAfter(now.plusSeconds(windowSeconds)))
            throw new EnergyStreamException("METER_TIMESTAMP_INVALID");
        seen.entrySet().removeIf(e -> e.getValue().isBefore(now.minusSeconds(windowSeconds)));
        String key = meter + '|' + version + '|' + nonce;
        if (seen.containsKey(key)) throw new EnergyStreamException("METER_REPLAY");
        if (seen.size() >= capacity) {
            Iterator<String> it = seen.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        seen.put(key, timestamp);
    }
}

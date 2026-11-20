package com.smartcity360.ejb.energy;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class EnergyStreamDemoRateLimiter {
    private static final Duration MINIMUM_INTERVAL = Duration.ofSeconds(2);

    private final Clock clock;
    private Instant nextAllowedAt = Instant.MIN;

    public EnergyStreamDemoRateLimiter() {
        this(Clock.systemUTC());
    }

    EnergyStreamDemoRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (now.isBefore(nextAllowedAt)) {
            return false;
        }
        nextAllowedAt = now.plus(MINIMUM_INTERVAL);
        return true;
    }
}

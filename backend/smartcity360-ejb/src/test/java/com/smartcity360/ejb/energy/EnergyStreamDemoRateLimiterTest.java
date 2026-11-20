package com.smartcity360.ejb.energy;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyStreamDemoRateLimiterTest {
    @Test
    void allowsOneIntegratedDemoSubmissionPerTwoSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-09T12:00:00Z"));
        EnergyStreamDemoRateLimiter limiter = new EnergyStreamDemoRateLimiter(clock);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        clock.advanceSeconds(2);
        assertTrue(limiter.tryAcquire());
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initialInstant) {
            instant = new AtomicReference<>(initialInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void advanceSeconds(long seconds) {
            instant.updateAndGet(value -> value.plusSeconds(seconds));
        }
    }
}

package com.smartcity360.ejb.streetlights.domain.flyweight;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LightingProfileFactoryTest {
    @Test void sameKeyReusesIdentityWhileVersionsRemainDistinct() {
        LightingProfileFactory factory = new LightingProfileFactory(8);
        UUID id = UUID.randomUUID();
        SharedLightingProfile first = factory.resolve(new ProfileKey(id, 1), () -> profile(id, 1));
        SharedLightingProfile same = factory.resolve(new ProfileKey(id, 1), () -> fail("cache hit must not load"));
        SharedLightingProfile nextVersion = factory.resolve(new ProfileKey(id, 2), () -> profile(id, 2));

        assertSame(first, same);
        assertNotSame(first, nextVersion);
        assertEquals(new FlyweightStatistics(2, 8, 1, 2, 0, 0), factory.statistics());
    }

    @Test void concurrentResolutionConstructsExactlyOneInstance() throws Exception {
        LightingProfileFactory factory = new LightingProfileFactory(8);
        UUID id = UUID.randomUUID(); ProfileKey key = new ProfileKey(id, 1);
        AtomicInteger constructions = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<SharedLightingProfile>> calls = new ArrayList<>();
            for (int i = 0; i < 48; i++) calls.add(() -> factory.resolve(key, () -> {
                constructions.incrementAndGet(); return profile(id, 1);
            }));
            List<Future<SharedLightingProfile>> futures = pool.invokeAll(calls);
            SharedLightingProfile expected = futures.get(0).get();
            for (Future<SharedLightingProfile> future : futures) assertSame(expected, future.get());
            assertEquals(1, constructions.get());
            assertEquals(47, factory.statistics().hits());
        } finally { pool.shutdownNow(); }
    }

    @Test void boundedLruEvictsAndInvalidationRemovesAllVersions() {
        LightingProfileFactory factory = new LightingProfileFactory(3);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        factory.resolve(new ProfileKey(first, 1), () -> profile(first, 1));
        factory.resolve(new ProfileKey(second, 1), () -> profile(second, 1));
        factory.resolve(new ProfileKey(third, 1), () -> profile(third, 1));
        assertEquals(3, factory.statistics().cacheSize());
        factory.resolve(new ProfileKey(second, 2), () -> profile(second, 2));
        assertEquals(1, factory.statistics().evictions());
        assertEquals(2, factory.invalidateProfile(second));
        assertEquals(1, factory.statistics().cacheSize());
    }

    @Test void failedCreationDoesNotPoisonCache() {
        LightingProfileFactory factory = new LightingProfileFactory(2);
        UUID id = UUID.randomUUID(); ProfileKey key = new ProfileKey(id, 1);
        assertThrows(IllegalStateException.class, () -> factory.resolve(key, () -> { throw new IllegalStateException("load failed"); }));
        SharedLightingProfile recovered = factory.resolve(key, () -> profile(id, 1));
        assertNotNull(recovered);
        assertEquals(2, factory.statistics().misses());
        assertEquals(1, factory.statistics().cacheSize());
    }

    private static SharedLightingProfile profile(UUID id, int version) {
        return new SharedLightingProfile(new ProfileKey(id, version), "Maker", "Model",
                new BigDecimal("60.00"), 75, "DALI", "fw", "{}");
    }
}

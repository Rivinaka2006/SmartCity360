package com.smartcity360.ejb.streetlights.domain.flyweight;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.function.Supplier;

@ApplicationScoped
public class LightingProfileFactory {
    public static final int DEFAULT_CAPACITY = 1_024;
    private final int capacity;
    private final LinkedHashMap<ProfileKey, SharedLightingProfile> cache =
            new LinkedHashMap<>(16, 0.75f, true);
    private long hits;
    private long misses;
    private long evictions;
    private long contextsProcessed;

    public LightingProfileFactory() {
        this(DEFAULT_CAPACITY);
    }

    public LightingProfileFactory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized SharedLightingProfile resolve(ProfileKey key, Supplier<SharedLightingProfile> loader) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loader, "loader");
        SharedLightingProfile existing = cache.get(key);
        if (existing != null) {
            hits++;
            return existing;
        }
        misses++;
        SharedLightingProfile created = Objects.requireNonNull(loader.get(), "loader result");
        if (!created.key().equals(key)) throw new IllegalArgumentException("loaded profile key mismatch");
        cache.put(key, created);
        if (cache.size() > capacity) {
            Iterator<ProfileKey> iterator = cache.keySet().iterator();
            iterator.next();
            iterator.remove();
            evictions++;
        }
        return created;
    }

    public synchronized boolean invalidate(ProfileKey key) {
        return cache.remove(key) != null;
    }

    public synchronized int invalidateProfile(UUID profileId) {
        int before = cache.size();
        cache.keySet().removeIf(key -> key.profileId().equals(profileId));
        return before - cache.size();
    }

    public synchronized void recordContextProcessed() {
        contextsProcessed++;
    }

    public synchronized FlyweightStatistics statistics() {
        return new FlyweightStatistics(cache.size(), capacity, hits, misses, evictions, contextsProcessed);
    }
}

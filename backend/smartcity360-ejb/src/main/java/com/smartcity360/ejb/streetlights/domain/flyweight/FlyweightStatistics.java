package com.smartcity360.ejb.streetlights.domain.flyweight;

public record FlyweightStatistics(int cacheSize, int capacity, long hits, long misses,
                                  long evictions, long contextsProcessed) {
    public double contextToFlyweightRatio() {
        return cacheSize == 0 ? 0D : (double) contextsProcessed / cacheSize;
    }
}

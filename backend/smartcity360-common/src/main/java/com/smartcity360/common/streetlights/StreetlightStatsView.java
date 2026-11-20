package com.smartcity360.common.streetlights;

public record StreetlightStatsView(long persistedStreetlights, long distinctActiveProfiles,
                                   int cachedFlyweights, int cacheCapacity, long cacheHits,
                                   long cacheMisses, long cacheEvictions, long contextsProcessed,
                                   double contextToFlyweightRatio) {}

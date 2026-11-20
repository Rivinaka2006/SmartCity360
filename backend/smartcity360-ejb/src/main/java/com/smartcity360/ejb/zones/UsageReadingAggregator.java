package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.zones.domain.composite.UsageReport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public final class UsageReadingAggregator {
    private UsageReadingAggregator() {
    }

    public static Map<UUID, UsageReport> aggregate(Set<UUID> buildingIds, List<ReadingSlice> readings,
                                                   OffsetDateTime from, OffsetDateTime to) {
        Map<UUID, Set<UUID>> meters = new HashMap<>();
        for (ReadingSlice reading : readings)
            meters.computeIfAbsent(reading.buildingId(), ignored -> new HashSet<>()).add(reading.meterId());
        Map<UUID, Integer> counts = new HashMap<>();
        meters.forEach((id, values) -> counts.put(id, values.size()));
        return aggregate(buildingIds, readings, counts, from, to);
    }

    public static Map<UUID, UsageReport> aggregate(Set<UUID> buildingIds, List<ReadingSlice> readings,
                                                   Map<UUID, Integer> meterCounts,
                                                   OffsetDateTime from, OffsetDateTime to) {
        if (!from.isBefore(to)) throw new ZoneOperationException("INVALID_REPORTING_PERIOD");
        Map<UUID, List<ReadingSlice>> byBuilding = new HashMap<>();
        Map<UUID, List<ReadingSlice>> byMeter = new HashMap<>();
        for (ReadingSlice reading : readings) {
            if (!buildingIds.contains(reading.buildingId()))
                throw new ZoneOperationException("ENERGY_READING_OUTSIDE_SUBTREE");
            if (reading.start().isBefore(from) || reading.end().isAfter(to))
                throw new ZoneOperationException("ENERGY_READING_OUTSIDE_RANGE");
            byBuilding.computeIfAbsent(reading.buildingId(), ignored -> new ArrayList<>()).add(reading);
            byMeter.computeIfAbsent(reading.meterId(), ignored -> new ArrayList<>()).add(reading);
        }
        for (List<ReadingSlice> meterReadings : byMeter.values()) {
            meterReadings.sort(Comparator.comparing(ReadingSlice::start).thenComparing(ReadingSlice::end));
            OffsetDateTime previousEnd = null;
            for (ReadingSlice reading : meterReadings) {
                if (previousEnd != null && reading.start().isBefore(previousEnd)) {
                    throw new ZoneOperationException("OVERLAPPING_ENERGY_READINGS");
                }
                previousEnd = reading.end();
            }
        }
        Map<UUID, UsageReport> result = new HashMap<>();
        for (UUID buildingId : buildingIds) {
            List<ReadingSlice> buildingReadings = byBuilding.getOrDefault(buildingId, List.of());
            BigDecimal total = buildingReadings.stream().map(ReadingSlice::kwh).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(buildingId, UsageReport.forBuilding(from, to, total, buildingReadings.size(),
                    meterCounts.getOrDefault(buildingId, 0)));
        }
        return Map.copyOf(result);
    }

    public record ReadingSlice(UUID buildingId, UUID meterId, OffsetDateTime start,
                               OffsetDateTime end, BigDecimal kwh) {
        public ReadingSlice {
            Objects.requireNonNull(buildingId);
            Objects.requireNonNull(meterId);
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            Objects.requireNonNull(kwh);
            if (!start.isBefore(end) || kwh.signum() < 0) throw new IllegalArgumentException("invalid reading");
        }
    }
}

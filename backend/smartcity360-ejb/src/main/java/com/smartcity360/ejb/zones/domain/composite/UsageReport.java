package com.smartcity360.ejb.zones.domain.composite;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public record UsageReport(OffsetDateTime from, OffsetDateTime to, BigDecimal totalKwh,
                          int readingCount, int meterCount, int buildingsWithReadings,
                          int buildingsWithoutReadings) {
    public UsageReport {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(totalKwh, "totalKwh");
        if (!from.isBefore(to) || totalKwh.signum() < 0 || readingCount < 0 || meterCount < 0
                || buildingsWithReadings < 0 || buildingsWithoutReadings < 0) {
            throw new IllegalArgumentException("invalid usage report");
        }
    }

    public static UsageReport empty(OffsetDateTime from, OffsetDateTime to) {
        return new UsageReport(from, to, BigDecimal.ZERO, 0, 0, 0, 0);
    }

    public static UsageReport forBuilding(OffsetDateTime from, OffsetDateTime to, BigDecimal total,
                                          int readings, int meters) {
        return new UsageReport(from, to, total, readings, meters, readings == 0 ? 0 : 1,
                readings == 0 ? 1 : 0);
    }

    public UsageReport plus(UsageReport other) {
        if (!from.equals(other.from) || !to.equals(other.to)) {
            throw new IllegalArgumentException("usage ranges differ");
        }
        return new UsageReport(from, to, totalKwh.add(other.totalKwh), readingCount + other.readingCount,
                meterCount + other.meterCount, buildingsWithReadings + other.buildingsWithReadings,
                buildingsWithoutReadings + other.buildingsWithoutReadings);
    }
}

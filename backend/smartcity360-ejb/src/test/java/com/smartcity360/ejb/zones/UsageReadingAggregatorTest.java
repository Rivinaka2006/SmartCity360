package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.zones.domain.composite.UsageReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UsageReadingAggregatorTest {
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-11-02T00:00:00Z");

    @Test
    void aggregatesMultipleMetersAndRetainsBuildingWithoutReadings() {
        UUID measured = UUID.randomUUID(), absent = UUID.randomUUID();
        UUID meter1 = UUID.randomUUID(), meter2 = UUID.randomUUID();
        var readings = List.of(
                reading(measured, meter1, 0, 6, "2.5000"),
                reading(measured, meter1, 6, 12, "3.0000"),
                reading(measured, meter2, 0, 12, "7.0000"));

        var result = UsageReadingAggregator.aggregate(Set.of(measured, absent), readings, FROM, TO);

        UsageReport measuredReport = result.get(measured);
        assertEquals(new BigDecimal("12.5000"), measuredReport.totalKwh());
        assertEquals(3, measuredReport.readingCount());
        assertEquals(2, measuredReport.meterCount());
        assertEquals(1, result.get(absent).buildingsWithoutReadings());
    }

    @Test
    void rejectsOverlappingIntervalsForTheSameMeter() {
        UUID building = UUID.randomUUID(), meter = UUID.randomUUID();
        var readings = List.of(reading(building, meter, 0, 8, "2"), reading(building, meter, 7, 12, "3"));
        ZoneOperationException exception = assertThrows(ZoneOperationException.class,
                () -> UsageReadingAggregator.aggregate(Set.of(building), readings, FROM, TO));
        assertEquals("OVERLAPPING_ENERGY_READINGS", exception.code());
    }

    @Test
    void rejectsReadingOutsideRequestedContainmentRange() {
        UUID building = UUID.randomUUID();
        var outside = new UsageReadingAggregator.ReadingSlice(building, UUID.randomUUID(),
                FROM.minusMinutes(1), FROM.plusHours(1), BigDecimal.ONE);
        assertEquals("ENERGY_READING_OUTSIDE_RANGE", assertThrows(ZoneOperationException.class,
                () -> UsageReadingAggregator.aggregate(Set.of(building), List.of(outside), FROM, TO)).code());
    }

    private static UsageReadingAggregator.ReadingSlice reading(UUID building, UUID meter, int startHour,
                                                                int endHour, String kwh) {
        return new UsageReadingAggregator.ReadingSlice(building, meter, FROM.plusHours(startHour),
                FROM.plusHours(endHour), new BigDecimal(kwh));
    }
}

package com.smartcity360.ejb.zones.domain.composite;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneCompositeTest {
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-11-02T00:00:00Z");

    @Test
    void polymorphicCityShutdownVisitsEveryBuildingAndRepeatedShutdownChangesNone() {
        BuildingLeaf first = building("B-1");
        BuildingLeaf second = building("B-2");
        CompositeZone street = composite(ZoneKind.STREET, "S-1").add(first).add(second);
        CompositeZone city = composite(ZoneKind.CITY, "C-1")
                .add(composite(ZoneKind.DISTRICT, "D-1").add(street));
        Map<UUID, PowerState> states = new HashMap<>();
        PowerOperationContext context = new PowerOperationContext((id, desired) -> {
            PowerState before = states.getOrDefault(id, PowerState.ON);
            states.put(id, desired);
            return before != desired;
        });

        ZoneComponent target = city;
        assertEquals(new PowerOperationResult(2, 2), target.shutdown(context));
        assertEquals(new PowerOperationResult(2, 0), target.shutdown(context));
        assertEquals(PowerState.OFF, states.get(first.id()));
        assertEquals(PowerState.OFF, states.get(second.id()));
    }

    @Test
    void restoreWorksUniformlyForLeafStreetDistrictAndCity() {
        for (ZoneKind targetKind : new ZoneKind[]{ZoneKind.BUILDING, ZoneKind.STREET, ZoneKind.DISTRICT, ZoneKind.CITY}) {
            BuildingLeaf leaf = building("B-" + targetKind);
            ZoneComponent target = switch (targetKind) {
                case BUILDING -> leaf;
                case STREET -> composite(ZoneKind.STREET, "S").add(leaf);
                case DISTRICT -> composite(ZoneKind.DISTRICT, "D").add(composite(ZoneKind.STREET, "S").add(leaf));
                case CITY -> composite(ZoneKind.CITY, "C").add(composite(ZoneKind.DISTRICT, "D").add(composite(ZoneKind.STREET, "S").add(leaf)));
            };
            Map<UUID, PowerState> states = new HashMap<>();
            states.put(leaf.id(), PowerState.OFF);
            PowerOperationResult result = target.restore(new PowerOperationContext((id, desired) -> states.put(id, desired) != desired));
            assertEquals(new PowerOperationResult(1, 1), result);
            assertEquals(PowerState.ON, states.get(leaf.id()));
        }
    }

    @Test
    void shutdownWorksUniformlyForLeafStreetDistrictAndCity() {
        for (ZoneKind targetKind : new ZoneKind[]{ZoneKind.BUILDING, ZoneKind.STREET, ZoneKind.DISTRICT, ZoneKind.CITY}) {
            BuildingLeaf leaf = building("B-SHUTDOWN-" + targetKind);
            ZoneComponent target = switch (targetKind) {
                case BUILDING -> leaf;
                case STREET -> composite(ZoneKind.STREET, "S").add(leaf);
                case DISTRICT -> composite(ZoneKind.DISTRICT, "D").add(composite(ZoneKind.STREET, "S").add(leaf));
                case CITY -> composite(ZoneKind.CITY, "C").add(composite(ZoneKind.DISTRICT, "D").add(composite(ZoneKind.STREET, "S").add(leaf)));
            };
            Map<UUID, PowerState> states = new HashMap<>();
            PowerOperationResult result = target.shutdown(new PowerOperationContext((id, desired) -> states.put(id, desired) != desired));
            assertEquals(new PowerOperationResult(1, 1), result);
            assertEquals(PowerState.OFF, states.get(leaf.id()));
        }
    }

    @Test
    void emptyCompositeHasZeroPowerTargetsAndExplicitNoReadingUsage() {
        CompositeZone district = composite(ZoneKind.DISTRICT, "D-EMPTY");
        assertEquals(PowerOperationResult.NONE, district.shutdown(new PowerOperationContext((id, state) -> true)));
        assertEquals(UsageReport.empty(FROM, TO), district.reportUsage(new UsageQuery(FROM, TO,
                (id, from, to) -> fail("empty composite must not query a leaf"))));
    }

    @Test
    void usageAggregatesMultipleBuildingsMetersAndPreservesNoReadingDistinction() {
        BuildingLeaf measured = building("B-MEASURED");
        BuildingLeaf zero = building("B-ZERO");
        BuildingLeaf absent = building("B-ABSENT");
        CompositeZone street = composite(ZoneKind.STREET, "S")
                .add(measured).add(zero).add(absent);
        Map<UUID, UsageReport> values = Map.of(
                measured.id(), UsageReport.forBuilding(FROM, TO, new BigDecimal("12.5000"), 3, 2),
                zero.id(), UsageReport.forBuilding(FROM, TO, new BigDecimal("0.0000"), 1, 1),
                absent.id(), UsageReport.forBuilding(FROM, TO, BigDecimal.ZERO, 0, 0));

        UsageReport report = street.reportUsage(new UsageQuery(FROM, TO, (id, from, to) -> values.get(id)));

        assertEquals(new BigDecimal("12.5000"), report.totalKwh());
        assertEquals(4, report.readingCount());
        assertEquals(3, report.meterCount());
        assertEquals(2, report.buildingsWithReadings());
        assertEquals(1, report.buildingsWithoutReadings());
    }

    @Test
    void compositeRejectsLeafChildrenAndDuplicateIds() {
        CompositeZone city = composite(ZoneKind.CITY, "C");
        assertThrows(IllegalArgumentException.class, () -> city.add(building("B")));
        CompositeZone district = composite(ZoneKind.DISTRICT, "D");
        city.add(district);
        assertThrows(IllegalArgumentException.class, () -> city.add(district));
    }

    @Test
    void usageQueryRejectsEmptyOrReversedRange() {
        UsageQuery.UsageSource source = (id, from, to) -> UsageReport.empty(from, to);
        assertThrows(IllegalArgumentException.class, () -> new UsageQuery(FROM, FROM, source));
        assertThrows(IllegalArgumentException.class, () -> new UsageQuery(TO, FROM, source));
    }

    private static BuildingLeaf building(String code) {
        return new BuildingLeaf(UUID.randomUUID(), code, code);
    }

    private static CompositeZone composite(ZoneKind kind, String code) {
        return new CompositeZone(UUID.randomUUID(), code, code, kind);
    }
}

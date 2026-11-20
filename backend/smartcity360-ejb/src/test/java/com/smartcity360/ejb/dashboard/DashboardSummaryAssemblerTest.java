package com.smartcity360.ejb.dashboard;

import com.smartcity360.common.dashboard.DashboardSummaryView;
import com.smartcity360.ejb.persistence.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardSummaryAssemblerTest {

    @Test
    void fillsMissingCategoriesWithZeroAndMissingPowerWithUnknown() {
        OffsetDateTime asOf = OffsetDateTime.parse("2026-11-06T12:00:00Z");
        DashboardSummaryView result = DashboardSummaryAssembler.assemble(
                asOf,
                7,
                3,
                Map.of(BuildingPowerStateEntity.PowerState.ON, 2L),
                Map.of(ComplaintEntity.Status.OPEN, 4L),
                Map.of(StreetlightEntity.OperationalState.FAULT, 1L),
                Map.of(PolicyEntity.Status.ACTIVE, 2L),
                new DashboardSummaryAssembler.EnergyAggregate(null, 0));

        assertEquals(7, result.zones().total());
        assertEquals(3, result.zones().buildings());
        assertEquals(2, result.power().on());
        assertEquals(0, result.power().off());
        assertEquals(1, result.power().unknown());
        assertEquals(4, result.complaints().open());
        assertEquals(0, result.complaints().resolved());
        assertEquals(1, result.streetlights().fault());
        assertEquals(0, result.streetlights().on());
        assertEquals(2, result.policies().active());
        assertEquals(0, result.policies().draft());
        assertNull(result.energy().consumptionKwh());
        assertEquals(0, result.energy().readingCount());
    }

    @Test
    void preservesMeasuredZeroEnergyAndUsesExactUtcWindow() {
        OffsetDateTime asOf = OffsetDateTime.parse("2026-11-06T12:00:00Z");
        DashboardSummaryView result = DashboardSummaryAssembler.assemble(
                asOf,
                0,
                0,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new DashboardSummaryAssembler.EnergyAggregate(BigDecimal.ZERO, 3));

        assertEquals(asOf.minusHours(24), result.energy().from());
        assertEquals(asOf, result.energy().to());
        assertEquals(BigDecimal.ZERO, result.energy().consumptionKwh());
        assertEquals(3, result.energy().readingCount());
    }
}

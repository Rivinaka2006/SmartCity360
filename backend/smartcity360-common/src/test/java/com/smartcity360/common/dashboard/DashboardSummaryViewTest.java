package com.smartcity360.common.dashboard;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardSummaryViewTest {

    @Test
    void exposesExplicitOperationalMetricsAndNullableEnergy() {
        OffsetDateTime asOf = OffsetDateTime.parse("2026-11-06T12:00:00Z");
        OffsetDateTime from = asOf.minusHours(24);
        DashboardSummaryView summary = new DashboardSummaryView(
                asOf,
                new DashboardSummaryView.ZoneSummary(12, 5),
                new DashboardSummaryView.PowerSummary(3, 1, 1),
                new DashboardSummaryView.ComplaintSummary(4, 2, 1, 1, 0, 0),
                new DashboardSummaryView.StreetlightSummary(8, 5, 1, 1, 1),
                new DashboardSummaryView.EnergySummary(from, asOf, null, 0),
                new DashboardSummaryView.PolicySummary(2, 1, 1, 0));

        assertEquals(asOf, summary.asOf());
        assertEquals(12, summary.zones().total());
        assertEquals(5, summary.zones().buildings());
        assertEquals(1, summary.power().unknown());
        assertEquals(2, summary.complaints().open());
        assertEquals(1, summary.streetlights().fault());
        assertNull(summary.energy().consumptionKwh());
        assertEquals(0, summary.energy().readingCount());
        assertEquals(1, summary.policies().draft());
    }

    @Test
    void retainsMeasuredZeroConsumptionAsARealValue() {
        OffsetDateTime to = OffsetDateTime.parse("2026-11-06T12:00:00Z");
        DashboardSummaryView.EnergySummary energy = new DashboardSummaryView.EnergySummary(
                to.minusHours(24), to, BigDecimal.ZERO, 2);

        assertEquals(BigDecimal.ZERO, energy.consumptionKwh());
        assertEquals(2, energy.readingCount());
    }
}

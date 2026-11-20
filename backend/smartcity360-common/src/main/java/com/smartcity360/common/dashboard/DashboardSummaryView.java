package com.smartcity360.common.dashboard;

import jakarta.json.bind.annotation.JsonbNillable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DashboardSummaryView(
        OffsetDateTime asOf,
        ZoneSummary zones,
        PowerSummary power,
        ComplaintSummary complaints,
        StreetlightSummary streetlights,
        EnergySummary energy,
        PolicySummary policies) {

    public record ZoneSummary(long total, long buildings) {
    }

    public record PowerSummary(long on, long off, long unknown) {
    }

    public record ComplaintSummary(
            long total,
            long open,
            long inProgress,
            long escalated,
            long resolved,
            long rejected) {
    }

    public record StreetlightSummary(
            long total,
            long on,
            long off,
            long fault,
            long maintenance) {
    }

    public record EnergySummary(
            OffsetDateTime from,
            OffsetDateTime to,
            @JsonbNillable BigDecimal consumptionKwh,
            long readingCount) {
    }

    public record PolicySummary(long total, long draft, long active, long inactive) {
    }
}

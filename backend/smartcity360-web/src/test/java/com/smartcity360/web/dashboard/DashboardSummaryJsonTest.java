package com.smartcity360.web.dashboard;

import com.smartcity360.common.dashboard.DashboardSummaryView;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardSummaryJsonTest {

    @Test
    void serializesNullConsumptionPropertyExplicitly() throws Exception {
        String json = serialize(null);

        assertTrue(json.contains("\"consumptionKwh\":null"), json);
    }

    @Test
    void serializesZeroConsumptionAsNumericZero() throws Exception {
        String json = serialize(BigDecimal.ZERO);

        assertTrue(json.contains("\"consumptionKwh\":0"), json);
    }

    @Test
    void serializesPositiveConsumptionAsNumericValue() throws Exception {
        String json = serialize(new BigDecimal("12.5000"));

        assertTrue(json.contains("\"consumptionKwh\":12.5000"), json);
    }

    private static String serialize(BigDecimal consumptionKwh) throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-06T12:00:00Z");
        DashboardSummaryView summary = new DashboardSummaryView(
                now,
                new DashboardSummaryView.ZoneSummary(0, 0),
                new DashboardSummaryView.PowerSummary(0, 0, 0),
                new DashboardSummaryView.ComplaintSummary(0, 0, 0, 0, 0, 0),
                new DashboardSummaryView.StreetlightSummary(0, 0, 0, 0, 0),
                new DashboardSummaryView.EnergySummary(now.minusHours(24), now, consumptionKwh, consumptionKwh == null ? 0 : 1),
                new DashboardSummaryView.PolicySummary(0, 0, 0, 0));
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.toJson(summary);
        }
    }
}

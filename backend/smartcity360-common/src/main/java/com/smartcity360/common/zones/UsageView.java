package com.smartcity360.common.zones;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UsageView(UUID targetZoneId, OffsetDateTime from, OffsetDateTime to, BigDecimal totalKwh,
                        int readingCount, int meterCount, int buildingsWithReadings,
                        int buildingsWithoutReadings, String inclusionRule) {}

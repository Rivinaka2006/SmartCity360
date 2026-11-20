package com.smartcity360.common.streetlights;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record StreetlightView(UUID id, String assetTag, UUID profileId, int profileVersion,
                              UUID streetZoneId, BigDecimal latitude, BigDecimal longitude,
                              String addressLabel, String operationalState,
                              Integer brightnessOverridePct, int effectiveBrightnessPct,
                              LocalDate installedOn, OffsetDateTime lastSeenAt,
                              OffsetDateTime registeredAt) {}

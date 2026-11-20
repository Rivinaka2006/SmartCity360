package com.smartcity360.common.streetlights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StreetlightRegisterCommand(String assetTag, UUID profileId, UUID streetZoneId,
                                         BigDecimal latitude, BigDecimal longitude,
                                         String addressLabel, LocalDate installedOn) {}

package com.smartcity360.common.zones;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ZoneCommandView(UUID id, UUID targetZoneId, UUID issuedBy, String command, String status,
                              int affectedBuildings, String message, OffsetDateTime issuedAt) {}

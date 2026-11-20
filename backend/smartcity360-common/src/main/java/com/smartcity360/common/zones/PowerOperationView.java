package com.smartcity360.common.zones;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PowerOperationView(UUID commandId, UUID targetZoneId, String command, int targetedBuildings,
                                 int changedBuildings, String message, OffsetDateTime executedAt) {}

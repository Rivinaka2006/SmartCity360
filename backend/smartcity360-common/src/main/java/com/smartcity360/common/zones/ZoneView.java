package com.smartcity360.common.zones;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ZoneView(UUID id, UUID parentId, String type, String code, String name, String status,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

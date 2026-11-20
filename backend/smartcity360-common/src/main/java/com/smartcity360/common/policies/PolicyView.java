package com.smartcity360.common.policies;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyView(UUID id, String policyCode, String name, String domain, String description,
                         String status, UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }

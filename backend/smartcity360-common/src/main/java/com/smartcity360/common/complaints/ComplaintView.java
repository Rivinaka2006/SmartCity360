package com.smartcity360.common.complaints;

import jakarta.json.bind.annotation.JsonbNillable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ComplaintView(
        UUID id,
        String referenceNo,
        UUID residentId,
        UUID zoneId,
        String title,
        String description,
        String severity,
        String status,
        @JsonbNillable UUID assignedToUserId,
        String currentHandlerLevel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        @JsonbNillable OffsetDateTime resolvedAt,
        @JsonbNillable String zoneName,
        @JsonbNillable String zoneCode,
        @JsonbNillable String assignedUserDisplayName) {
}

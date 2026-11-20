package com.smartcity360.ejb.zones.domain.composite;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record UsageQuery(OffsetDateTime from, OffsetDateTime to, UsageSource source) {
    public UsageQuery {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(source, "source");
        if (!from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
    }

    @FunctionalInterface
    public interface UsageSource {
        UsageReport load(UUID buildingId, OffsetDateTime from, OffsetDateTime to);
    }
}

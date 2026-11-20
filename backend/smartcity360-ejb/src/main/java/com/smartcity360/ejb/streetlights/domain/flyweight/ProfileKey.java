package com.smartcity360.ejb.streetlights.domain.flyweight;

import java.util.Objects;
import java.util.UUID;

public record ProfileKey(UUID profileId, int version) {
    public ProfileKey {
        Objects.requireNonNull(profileId, "profileId");
        if (version < 1) throw new IllegalArgumentException("profile version must be positive");
    }
}

package com.smartcity360.ejb.zones.domain.composite;

import java.util.Objects;
import java.util.UUID;

public record PowerOperationContext(StateChanger stateChanger) {
    public PowerOperationContext {
        Objects.requireNonNull(stateChanger, "stateChanger");
    }

    @FunctionalInterface
    public interface StateChanger {
        boolean change(UUID buildingId, PowerState desiredState);
    }
}

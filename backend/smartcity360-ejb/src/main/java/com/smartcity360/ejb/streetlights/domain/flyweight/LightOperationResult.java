package com.smartcity360.ejb.streetlights.domain.flyweight;

import java.util.Objects;

public record LightOperationResult(StreetlightContext context, LightState previousState,
                                   LightState currentState, boolean changed,
                                   int effectiveBrightnessPct, LightCommand.Type command) {
    public LightOperationResult {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(command, "command");
    }
}

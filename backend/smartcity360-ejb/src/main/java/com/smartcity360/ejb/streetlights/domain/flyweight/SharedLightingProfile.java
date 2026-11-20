package com.smartcity360.ejb.streetlights.domain.flyweight;

import java.math.BigDecimal;
import java.util.Objects;

public record SharedLightingProfile(ProfileKey key, String manufacturer, String model,
                                    BigDecimal wattage, int defaultBrightnessPct,
                                    String controllerType, String firmwareFamily,
                                    String configurationJson) implements StreetlightFlyweight {
    public SharedLightingProfile {
        Objects.requireNonNull(key, "key");
        manufacturer = text(manufacturer, 80, "manufacturer");
        model = text(model, 100, "model");
        Objects.requireNonNull(wattage, "wattage");
        if (wattage.signum() <= 0) throw new IllegalArgumentException("wattage must be positive");
        if (defaultBrightnessPct < 0 || defaultBrightnessPct > 100)
            throw new IllegalArgumentException("default brightness out of range");
        controllerType = text(controllerType, 40, "controllerType");
        if (firmwareFamily != null && firmwareFamily.length() > 50)
            throw new IllegalArgumentException("firmwareFamily too long");
        configurationJson = Objects.requireNonNull(configurationJson, "configurationJson");
    }

    @Override
    public LightOperationResult apply(StreetlightContext context, LightCommand command) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(command, "command");
        if (context.state() == LightState.FAULT || context.state() == LightState.MAINTENANCE)
            throw new FlyweightDomainException("STREETLIGHT_UNAVAILABLE");
        StreetlightContext result = switch (command.type()) {
            case TURN_ON -> context.withState(LightState.ON);
            case TURN_OFF -> context.withState(LightState.OFF);
            case SET_BRIGHTNESS -> context.withBrightness(command.brightnessPct());
        };
        return new LightOperationResult(result, context.state(), result.state(), !result.equals(context),
                effectiveBrightness(result), command.type());
    }

    @Override
    public int effectiveBrightness(StreetlightContext context) {
        return context.brightnessOverridePct() == null ? defaultBrightnessPct : context.brightnessOverridePct();
    }

    private static String text(String value, int max, String name) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new IllegalArgumentException(name + " invalid");
        return value.strip();
    }
}

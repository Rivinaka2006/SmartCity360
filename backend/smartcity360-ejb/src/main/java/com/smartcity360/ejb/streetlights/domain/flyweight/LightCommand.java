package com.smartcity360.ejb.streetlights.domain.flyweight;

import java.util.Objects;

public record LightCommand(Type type, Integer brightnessPct) {
    public enum Type {TURN_ON, TURN_OFF, SET_BRIGHTNESS}

    public LightCommand {
        Objects.requireNonNull(type, "type");
        if (type == Type.SET_BRIGHTNESS) {
            if (brightnessPct == null || brightnessPct < 0 || brightnessPct > 100)
                throw new IllegalArgumentException("brightness must be between 0 and 100");
        } else if (brightnessPct != null) {
            throw new IllegalArgumentException("brightness is valid only for SET_BRIGHTNESS");
        }
    }

    public static LightCommand on() {
        return new LightCommand(Type.TURN_ON, null);
    }

    public static LightCommand off() {
        return new LightCommand(Type.TURN_OFF, null);
    }

    public static LightCommand brightness(int value) {
        return new LightCommand(Type.SET_BRIGHTNESS, value);
    }
}

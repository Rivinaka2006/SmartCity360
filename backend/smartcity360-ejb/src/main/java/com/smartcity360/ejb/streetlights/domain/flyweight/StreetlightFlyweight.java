package com.smartcity360.ejb.streetlights.domain.flyweight;

public interface StreetlightFlyweight {
    ProfileKey key();

    LightOperationResult apply(StreetlightContext context, LightCommand command);

    int effectiveBrightness(StreetlightContext context);
}

package com.smartcity360.ejb.streetlights.domain.flyweight;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record StreetlightContext(UUID streetlightId, UUID streetZoneId, BigDecimal latitude,
                                 BigDecimal longitude, String addressLabel, LightState state,
                                 Integer brightnessOverridePct) {
    private static final BigDecimal MIN_LAT = new BigDecimal("-90");
    private static final BigDecimal MAX_LAT = new BigDecimal("90");
    private static final BigDecimal MIN_LON = new BigDecimal("-180");
    private static final BigDecimal MAX_LON = new BigDecimal("180");

    public StreetlightContext {
        Objects.requireNonNull(streetlightId, "streetlightId");
        Objects.requireNonNull(streetZoneId, "streetZoneId");
        Objects.requireNonNull(state, "state");
        if ((latitude == null) != (longitude == null))
            throw new IllegalArgumentException("latitude and longitude must both be present or absent");
        if (latitude != null && (latitude.compareTo(MIN_LAT) < 0 || latitude.compareTo(MAX_LAT) > 0))
            throw new IllegalArgumentException("latitude out of range");
        if (longitude != null && (longitude.compareTo(MIN_LON) < 0 || longitude.compareTo(MAX_LON) > 0))
            throw new IllegalArgumentException("longitude out of range");
        if (brightnessOverridePct != null && (brightnessOverridePct < 0 || brightnessOverridePct > 100))
            throw new IllegalArgumentException("brightness override out of range");
        if (addressLabel != null && addressLabel.length() > 255)
            throw new IllegalArgumentException("address label too long");
    }

    public StreetlightContext withState(LightState replacement) {
        return new StreetlightContext(streetlightId, streetZoneId, latitude, longitude,
                addressLabel, replacement, brightnessOverridePct);
    }

    public StreetlightContext withBrightness(Integer replacement) {
        return new StreetlightContext(streetlightId, streetZoneId, latitude, longitude,
                addressLabel, state, replacement);
    }
}

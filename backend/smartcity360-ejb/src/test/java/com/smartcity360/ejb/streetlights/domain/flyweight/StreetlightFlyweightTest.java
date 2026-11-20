package com.smartcity360.ejb.streetlights.domain.flyweight;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StreetlightFlyweightTest {
    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STREET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test void immutableProfileAppliesCommandsWithoutRetainingExtrinsicState() {
        SharedLightingProfile profile = profile(1, 70);
        StreetlightContext first = context("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", LightState.OFF, null);
        StreetlightContext second = context("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", LightState.ON, 40);

        LightOperationResult on = profile.apply(first, LightCommand.on());
        LightOperationResult brighterWhileOff = profile.apply(first, LightCommand.brightness(85));

        assertEquals(LightState.ON, on.context().state());
        assertTrue(on.changed());
        assertEquals(70, on.effectiveBrightnessPct());
        assertEquals(LightState.OFF, brighterWhileOff.context().state());
        assertEquals(85, brighterWhileOff.context().brightnessOverridePct());
        assertEquals(LightState.OFF, first.state());
        assertEquals(LightState.ON, second.state());
        assertEquals(40, profile.effectiveBrightness(second));
    }

    @Test void onAndOffAreIdempotentAndUnavailableStatesRejectOperations() {
        SharedLightingProfile profile = profile(1, 65);
        assertFalse(profile.apply(context("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", LightState.ON, null), LightCommand.on()).changed());
        assertFalse(profile.apply(context("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", LightState.OFF, null), LightCommand.off()).changed());
        FlyweightDomainException fault = assertThrows(FlyweightDomainException.class,
                () -> profile.apply(context("cccccccc-cccc-cccc-cccc-cccccccccccc", LightState.FAULT, null), LightCommand.on()));
        assertEquals("STREETLIGHT_UNAVAILABLE", fault.code());
    }

    @Test void validatesIntrinsicAndExtrinsicRanges() {
        assertThrows(IllegalArgumentException.class, () -> new SharedLightingProfile(
                new ProfileKey(PROFILE_ID, 1), "Maker", "Model", BigDecimal.ZERO,
                50, "CTRL", "FW", "{}"));
        assertThrows(IllegalArgumentException.class, () -> profile(1, 101));
        assertThrows(IllegalArgumentException.class, () -> new StreetlightContext(
                UUID.randomUUID(), STREET_ID, new BigDecimal("91"), BigDecimal.ZERO,
                "bad", LightState.OFF, null));
        assertThrows(IllegalArgumentException.class, () -> context(
                "dddddddd-dddd-dddd-dddd-dddddddddddd", LightState.OFF, 101));
        assertThrows(IllegalArgumentException.class, () -> LightCommand.brightness(-1));
    }

    private static SharedLightingProfile profile(int version, int brightness) {
        return new SharedLightingProfile(new ProfileKey(PROFILE_ID, version), "Maker", "Model",
                new BigDecimal("75.50"), brightness, "DALI", "family-a", "{\"mode\":\"eco\"}");
    }

    private static StreetlightContext context(String id, LightState state, Integer brightness) {
        return new StreetlightContext(UUID.fromString(id), STREET_ID, new BigDecimal("6.927079"),
                new BigDecimal("79.861244"), "Colombo", state, brightness);
    }
}

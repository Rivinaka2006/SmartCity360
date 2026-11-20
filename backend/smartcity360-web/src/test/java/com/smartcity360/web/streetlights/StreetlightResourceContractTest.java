package com.smartcity360.web.streetlights;

import com.smartcity360.web.energy.MeterIngestionBoundary;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StreetlightResourceContractTest {
    @Test
    void resourcesExposeSeparateStaticRoutesAndRoleBoundaries() throws Exception {
        Class<?> streetlights = Class.forName("com.smartcity360.web.streetlights.StreetlightResource");
        assertEquals("/streetlights", streetlights.getAnnotation(Path.class).value());
        Method stats = streetlights.getMethod("statistics");
        assertEquals("/stats", stats.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                Set.of(stats.getAnnotation(RolesAllowed.class).value()));
        Method on = streetlights.getMethod("turnOn", java.util.UUID.class);
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), Set.of(on.getAnnotation(RolesAllowed.class).value()));

        Class<?> profiles = Class.forName("com.smartcity360.web.streetlights.LightingProfileResource");
        assertEquals("/lighting-profiles", profiles.getAnnotation(Path.class).value());
        Method create = profiles.getMethod("create", LightingProfileResource.CreateBody.class);
        assertEquals(Set.of("ADMIN"), Set.of(create.getAnnotation(RolesAllowed.class).value()));
    }

    @Test
    void streetlightWritesRemainBrowserCsrfProtected() {
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "streetlights", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "streetlights/id/on", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "lighting-profiles", null));
    }

    @Test
    void serviceErrorsMapToControlledStatuses() {
        assertEquals(400, StreetlightServiceExceptionMapper.statusFor("BRIGHTNESS_INVALID"));
        assertEquals(404, StreetlightServiceExceptionMapper.statusFor("STREETLIGHT_NOT_FOUND"));
        assertEquals(409, StreetlightServiceExceptionMapper.statusFor("PROFILE_IN_USE"));
    }

    @Test
    void registrationRejectsZoneCodesAsControlledInvalidUuidRequests() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> StreetlightResource.parseUuid("S002"));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Streetlight request rejected", ((java.util.Map<?, ?>) exception.getResponse().getEntity()).get("error"));
        assertEquals("REQUEST_INVALID", ((java.util.Map<?, ?>) exception.getResponse().getEntity()).get("code"));
    }
}

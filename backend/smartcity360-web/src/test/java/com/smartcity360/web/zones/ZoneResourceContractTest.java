package com.smartcity360.web.zones;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneResourceContractTest {
    @Test
    void exposesVersionedZoneRoutesWithRoleProtectedStateChanges() throws Exception {
        Class<?> type = Class.forName("com.smartcity360.web.zones.ZoneResource");
        assertEquals("/zones", type.getAnnotation(Path.class).value());
        Method parents = type.getMethod("parents", String.class, String.class, int.class, int.class);
        assertNotNull(parents.getAnnotation(GET.class));
        assertEquals("/parents", parents.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                Set.of(parents.getAnnotation(RolesAllowed.class).value()));
        Method complaintLocations = type.getMethod("complaintLocations", String.class, int.class, int.class);
        assertNotNull(complaintLocations.getAnnotation(GET.class));
        assertEquals("/complaint-locations", complaintLocations.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"),
                Set.of(complaintLocations.getAnnotation(RolesAllowed.class).value()));
        Method path = type.getMethod("path", UUID.class);
        assertEquals("/{id}/path", path.getAnnotation(Path.class).value());
        assertNotNull(path.getAnnotation(GET.class));
        Method shutdown = type.getMethod("shutdown", UUID.class);
        assertEquals("/{id}/shutdown", shutdown.getAnnotation(Path.class).value());
        assertNotNull(shutdown.getAnnotation(POST.class));
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), Set.of(shutdown.getAnnotation(RolesAllowed.class).value()));
        Method create = type.getMethod("create", ZoneResource.CreateBody.class);
        assertNotNull(create.getAnnotation(POST.class));
        assertEquals(Set.of("ADMIN"), Set.of(create.getAnnotation(RolesAllowed.class).value()));
        Method deactivate = type.getMethod("deactivate", UUID.class);
        assertEquals("/{id}/deactivate", deactivate.getAnnotation(Path.class).value());
        assertNotNull(deactivate.getAnnotation(POST.class));
        assertEquals(Set.of("ADMIN"), Set.of(deactivate.getAnnotation(RolesAllowed.class).value()));
        assertNotNull(type.getMethod("usage", UUID.class, String.class, String.class).getAnnotation(GET.class));
    }

    @Test
    void noZoneRouteIsClassifiedAsMachineCsrfExempt() {
        assertFalse(com.smartcity360.web.energy.MeterIngestionBoundary.isMachineRequest("POST", "zones/abc/shutdown", null));
        assertFalse(com.smartcity360.web.energy.MeterIngestionBoundary.isMachineRequest("PUT", "zones/abc", null));
    }

    @Test
    void parentsRouteForwardsCitySearchAndReturnsEmptyResultsWithoutConflict() {
        ZoneResource resource = new ZoneResource();
        resource.zones = (com.smartcity360.common.zones.ZoneService) Proxy.newProxyInstance(
                com.smartcity360.common.zones.ZoneService.class.getClassLoader(),
                new Class[]{com.smartcity360.common.zones.ZoneService.class},
                (proxy, method, arguments) -> method.getName().equals("findParentOptions")
                        ? List.of()
                        : null);

        assertEquals(List.of(), resource.parents("CITY", "001", 0, 25));
    }
}

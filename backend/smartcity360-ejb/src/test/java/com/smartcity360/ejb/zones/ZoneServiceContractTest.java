package com.smartcity360.ejb.zones;

import com.smartcity360.common.zones.ZoneService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZoneServiceContractTest {
    @Test
    void serviceIsAStatelessTransactionBoundaryWithRoleProtectedOperations() throws Exception {
        Class<?> type = Class.forName("com.smartcity360.ejb.zones.ZoneServiceBean");
        assertNotNull(type.getAnnotation(Stateless.class));
        assertTrue(ZoneService.class.isAssignableFrom(type));
        assertEquals(TransactionAttributeType.SUPPORTS, type.getAnnotation(TransactionAttribute.class).value());
        assertRoles(type.getMethod("shutdown", java.util.UUID.class), "ADMIN", "CITY_MANAGER");
        assertRoles(type.getMethod("restore", java.util.UUID.class), "ADMIN", "CITY_MANAGER");
        assertEquals(TransactionAttributeType.REQUIRED,
                type.getMethod("shutdown", java.util.UUID.class).getAnnotation(TransactionAttribute.class).value());
        assertRoles(type.getMethod("usage", java.util.UUID.class, java.time.OffsetDateTime.class,
                java.time.OffsetDateTime.class), "ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF");
        assertRoles(type.getMethod("findParentOptions", String.class, String.class, int.class, int.class),
                "ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF");
        assertRoles(type.getMethod("complaintLocations", String.class, int.class, int.class),
                "ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT");
    }

    @Test
    void commonZoneContractDoesNotExposeJpaEntities() {
        for (Method method : ZoneService.class.getMethods()) {
            assertFalse(method.getReturnType().getName().contains("persistence.model"));
            assertTrue(Arrays.stream(method.getParameterTypes())
                    .noneMatch(type -> type.getName().contains("persistence.model")));
        }
    }

    private static void assertRoles(Method method, String... expected) {
        assertEquals(Set.of(expected), Set.of(method.getAnnotation(RolesAllowed.class).value()));
    }
}

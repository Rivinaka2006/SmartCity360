package com.smartcity360.ejb.streetlights;

import com.smartcity360.common.streetlights.*;
import com.smartcity360.ejb.persistence.repository.LightingProfileRepository;
import com.smartcity360.ejb.persistence.repository.StreetlightRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StreetlightServiceContractTest {
    @Test void serviceIsStatelessAndMutationsUseRequiredTransactions() throws Exception {
        Class<?> type = Class.forName("com.smartcity360.ejb.streetlights.StreetlightServiceBean");
        assertNotNull(type.getAnnotation(Stateless.class));
        assertTrue(StreetlightService.class.isAssignableFrom(type));
        assertEquals(TransactionAttributeType.SUPPORTS, type.getAnnotation(TransactionAttribute.class).value());
        for (String name : List.of("createProfile", "createProfileVersion", "deactivateProfile", "register",
                "turnOn", "turnOff", "setBrightness", "reassignProfile")) {
            Method method = Arrays.stream(type.getMethods()).filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            assertEquals(TransactionAttributeType.REQUIRED, method.getAnnotation(TransactionAttribute.class).value(), name);
        }
        Method create = type.getMethod("createProfile", LightingProfileCreateCommand.class);
        assertEquals(Set.of("ADMIN"), Set.of(create.getAnnotation(RolesAllowed.class).value()));
        Method on = type.getMethod("turnOn", UUID.class);
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), Set.of(on.getAnnotation(RolesAllowed.class).value()));
    }

    @Test void sharedContractContainsNoJpaTypes() {
        for (Method method : StreetlightService.class.getMethods()) {
            assertFalse(method.getReturnType().getName().contains("persistence.model"));
            assertTrue(Arrays.stream(method.getParameterTypes()).noneMatch(t -> t.getName().contains("persistence.model")));
        }
    }

    @Test void repositoriesExposeTransactionScopedUniquenessSerialization() throws Exception {
        assertNotNull(LightingProfileRepository.class.getMethod("lockProfileCode", String.class));
        assertNotNull(StreetlightRepository.class.getMethod("lockAssetTag", String.class));
    }
}

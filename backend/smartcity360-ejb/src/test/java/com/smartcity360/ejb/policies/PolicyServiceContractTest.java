package com.smartcity360.ejb.policies;

import com.smartcity360.common.policies.*;
import com.smartcity360.ejb.persistence.repository.PolicyRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PolicyServiceContractTest {
    @Test
    void serviceUsesContainerTransactionsAndApprovedRoleBoundaries() throws Exception {
        Class<?> type = Class.forName("com.smartcity360.ejb.policies.PolicyServiceBean");
        assertNotNull(type.getAnnotation(Stateless.class));
        assertTrue(PolicyService.class.isAssignableFrom(type));
        assertEquals(TransactionAttributeType.SUPPORTS, type.getAnnotation(TransactionAttribute.class).value());
        for (String name : List.of("create", "createVersion", "validateVersion", "activateVersion",
                "deactivate", "evaluate")) {
            Method method = Arrays.stream(type.getMethods()).filter(candidate -> candidate.getName().equals(name))
                    .findFirst().orElseThrow();
            assertEquals(TransactionAttributeType.REQUIRED, method.getAnnotation(TransactionAttribute.class).value(), name);
        }
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), roles(type, "create", PolicyCreateCommand.class));
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                roles(type, "evaluate", UUID.class, PolicyEvaluationCommand.class));
        assertEquals(Set.of("ADMIN"), roles(type, "deactivate", UUID.class));
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"),
                roles(type, "listVersions", UUID.class, int.class, int.class));
    }

    @Test
    void policyRepositoryExposesParentLockUsedByVersionLifecycle() throws Exception {
        assertNotNull(PolicyRepository.class.getMethod("findByIdForUpdate", UUID.class));
        assertNotNull(PolicyRepository.class.getMethod("findByIdForRead", UUID.class));
        assertNotNull(PolicyRepository.class.getMethod("lockPolicyCode", String.class));
    }

    @Test
    void sharedContractDoesNotExposeJpaTypes() {
        for (Method method : PolicyService.class.getMethods()) {
            assertFalse(method.getReturnType().getName().contains("persistence.model"));
            assertTrue(Arrays.stream(method.getParameterTypes()).noneMatch(t -> t.getName().contains("persistence.model")));
        }
    }

    private static Set<String> roles(Class<?> type, String name, Class<?>... parameters) throws Exception {
        return Set.of(type.getMethod(name, parameters).getAnnotation(RolesAllowed.class).value());
    }
}

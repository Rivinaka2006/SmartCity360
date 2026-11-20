package com.smartcity360.web.policies;

import com.smartcity360.web.energy.MeterIngestionBoundary;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PolicyResourceContractTest {
    @Test
    void resourceExposesUnambiguousRoutesAndApprovedRoles() throws Exception {
        Class<?> type = Class.forName("com.smartcity360.web.policies.PolicyResource");
        assertEquals("/policies", type.getAnnotation(Path.class).value());
        Method versions = type.getMethod("versions", UUID.class, int.class, int.class);
        assertEquals("/{id}/versions", versions.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), Set.of(versions.getAnnotation(RolesAllowed.class).value()));
        Method activate = type.getMethod("activate", UUID.class, UUID.class);
        assertEquals("/{id}/versions/{versionId}/activate", activate.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), Set.of(activate.getAnnotation(RolesAllowed.class).value()));
        Method evaluate = type.getMethod("evaluate", UUID.class, PolicyResource.EvaluationBody.class);
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                Set.of(evaluate.getAnnotation(RolesAllowed.class).value()));
    }

    @Test
    void policyMutationsRemainProtectedByBrowserCsrfBoundary() {
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "policies", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "policies/id/versions", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "policies/id/evaluate", null));
    }

    @Test
    void serviceErrorsMapWithoutLeakingInternals() {
        assertEquals(400, PolicyServiceExceptionMapper.statusFor("DSL_INVALID"));
        assertEquals(404, PolicyServiceExceptionMapper.statusFor("POLICY_NOT_FOUND"));
        assertEquals(409, PolicyServiceExceptionMapper.statusFor("VERSION_NOT_VALID"));
        assertEquals(413, PolicyServiceExceptionMapper.statusFor("SOURCE_LENGTH_LIMIT"));
    }
}

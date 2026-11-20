package com.smartcity360.ejb.dashboard;

import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardServiceSecurityContractTest {

    @Test
    void summaryRequiresOperationalRolesAtEjbBoundary() throws Exception {
        Method summary = DashboardServiceBean.class.getMethod("summary");

        assertNotNull(DashboardServiceBean.class.getAnnotation(jakarta.ejb.Stateless.class));
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                Set.of(summary.getAnnotation(RolesAllowed.class).value()));
    }
}

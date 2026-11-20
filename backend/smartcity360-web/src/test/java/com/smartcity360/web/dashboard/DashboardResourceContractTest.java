package com.smartcity360.web.dashboard;

import com.smartcity360.common.dashboard.DashboardService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardResourceContractTest {

    @Test
    void exposesSecuredDashboardSummaryRoute() throws Exception {
        assertEquals("/dashboard", DashboardResource.class.getAnnotation(Path.class).value());
        Method summary = DashboardResource.class.getMethod("summary");
        assertEquals("/summary", summary.getAnnotation(Path.class).value());
        assertNotNull(summary.getAnnotation(GET.class));
        assertEquals(MediaType.APPLICATION_JSON, DashboardResource.class.getAnnotation(Produces.class).value()[0]);
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                Set.of(summary.getAnnotation(RolesAllowed.class).value()));
        assertEquals(DashboardService.class, DashboardResource.class.getDeclaredField("dashboard").getType());
    }
}

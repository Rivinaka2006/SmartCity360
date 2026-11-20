package com.smartcity360.web.health;

import com.smartcity360.common.health.HealthService;
import com.smartcity360.common.health.HealthStatus;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthResourceTest {

    @Test
    void healthDelegatesToEjbContract() {
        HealthService service = () -> new HealthStatus("UP", "smartcity360");
        HealthResource resource = new HealthResource(service);

        HealthStatus response = resource.health();

        assertEquals("UP", response.getStatus());
        assertEquals("smartcity360", response.getService());
    }

    @Test
    void healthEndpointUsesExpectedRouteAndMediaType() throws NoSuchMethodException {
        assertEquals("/health", HealthResource.class.getAnnotation(Path.class).value());

        Method health = HealthResource.class.getMethod("health");
        assertNotNull(health.getAnnotation(GET.class));
        assertEquals(MediaType.APPLICATION_JSON, health.getAnnotation(Produces.class).value()[0]);
    }
}

package com.smartcity360.web.health;

import com.smartcity360.common.health.HealthService;
import com.smartcity360.common.health.HealthStatus;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/health")
@RequestScoped
public class HealthResource {

    @Inject
    private HealthService healthService;

    public HealthResource() {
    }

    HealthResource(HealthService healthService) {
        this.healthService = healthService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HealthStatus health() {
        return healthService.currentStatus();
    }
}

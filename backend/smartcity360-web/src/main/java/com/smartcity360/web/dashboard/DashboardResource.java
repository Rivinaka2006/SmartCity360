package com.smartcity360.web.dashboard;

import com.smartcity360.common.dashboard.DashboardService;
import com.smartcity360.common.dashboard.DashboardSummaryView;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {
    @EJB
    private DashboardService dashboard;

    @GET
    @Path("/summary")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public DashboardSummaryView summary() {
        return dashboard.summary();
    }
}

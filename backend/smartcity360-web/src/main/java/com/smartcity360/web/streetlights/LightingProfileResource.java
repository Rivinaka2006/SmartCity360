package com.smartcity360.web.streetlights;

import com.smartcity360.common.streetlights.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/lighting-profiles")
@Produces(MediaType.APPLICATION_JSON)
public class LightingProfileResource {
    @EJB
    StreetlightService streetlights;

    @GET
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<LightingProfileView> list(@QueryParam("activeOnly") @DefaultValue("true") boolean activeOnly,
                                          @QueryParam("offset") @DefaultValue("0") int offset,
                                          @QueryParam("limit") @DefaultValue("50") int limit) {
        return streetlights.listProfiles(activeOnly, offset, limit);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public LightingProfileView get(@PathParam("id") UUID id) {
        return streetlights.getProfile(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response create(CreateBody body) {
        if (body == null) throw new BadRequestException();
        LightingProfileView created = streetlights.createProfile(command(body));
        return Response.created(URI.create("lighting-profiles/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/{id}/versions")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response createVersion(@PathParam("id") UUID id, CreateBody body) {
        if (body == null) throw new BadRequestException();
        LightingProfileView created = streetlights.createProfileVersion(id, command(body));
        return Response.created(URI.create("lighting-profiles/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/{id}/deactivate")
    @RolesAllowed("ADMIN")
    public LightingProfileView deactivate(@PathParam("id") UUID id) {
        return streetlights.deactivateProfile(id);
    }

    private static LightingProfileCreateCommand command(CreateBody body) {
        return new LightingProfileCreateCommand(body.profileCode, body.manufacturer, body.model, body.wattage,
                body.defaultBrightnessPct, body.controllerType, body.firmwareFamily, body.configurationJson);
    }

    public static final class CreateBody {
        public String profileCode;
        public String manufacturer;
        public String model;
        public BigDecimal wattage;
        public Integer defaultBrightnessPct;
        public String controllerType;
        public String firmwareFamily;
        public String configurationJson;
    }
}

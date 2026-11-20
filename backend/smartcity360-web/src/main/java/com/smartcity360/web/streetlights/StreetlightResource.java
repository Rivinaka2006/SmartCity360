package com.smartcity360.web.streetlights;

import com.smartcity360.common.streetlights.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/streetlights")
@Produces(MediaType.APPLICATION_JSON)
public class StreetlightResource {
    @EJB
    StreetlightService streetlights;

    @GET
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<StreetlightView> list(@QueryParam("streetZoneId") UUID streetZoneId,
                                      @QueryParam("offset") @DefaultValue("0") int offset,
                                      @QueryParam("limit") @DefaultValue("50") int limit) {
        return streetlights.list(streetZoneId, offset, limit);
    }

    @GET
    @Path("/stats")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public StreetlightStatsView statistics() {
        return streetlights.statistics();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public StreetlightView get(@PathParam("id") UUID id) {
        return streetlights.get(id);
    }

    @GET
    @Path("/{id}/profile")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public LightingProfileView effectiveProfile(@PathParam("id") UUID id) {
        return streetlights.effectiveProfile(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response register(RegisterBody body) {
        if (body == null) {
            throw new BadRequestException();
        }
        UUID profileId = parseUuid(body.profileId);
        UUID streetZoneId = parseUuid(body.streetZoneId);
        StreetlightView created = streetlights.register(new StreetlightRegisterCommand(
                body.assetTag,
                profileId,
                streetZoneId,
                body.latitude,
                body.longitude,
                body.addressLabel,
                body.installedOn));
        return Response.created(URI.create("streetlights/" + created.id())).entity(created).build();
    }

    static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException(Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "error", "Streetlight request rejected",
                            "code", "REQUEST_INVALID"))
                    .build());
        }
    }

    @POST
    @Path("/{id}/on")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public LightOperationView turnOn(@PathParam("id") UUID id) {
        return streetlights.turnOn(id);
    }

    @POST
    @Path("/{id}/off")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public LightOperationView turnOff(@PathParam("id") UUID id) {
        return streetlights.turnOff(id);
    }

    @POST
    @Path("/{id}/brightness")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public LightOperationView brightness(@PathParam("id") UUID id, BrightnessBody body) {
        if (body == null) throw new BadRequestException();
        return streetlights.setBrightness(id, new BrightnessCommand(body.brightnessPct));
    }

    @POST
    @Path("/{id}/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public StreetlightView profile(@PathParam("id") UUID id, ProfileBody body) {
        if (body == null) throw new BadRequestException();
        return streetlights.reassignProfile(id, new StreetlightProfileCommand(body.profileId));
    }

    public static final class RegisterBody {
        public String assetTag;
        public String profileId;
        public String streetZoneId;
        public BigDecimal latitude;
        public BigDecimal longitude;
        public String addressLabel;
        public LocalDate installedOn;
    }

    public static final class BrightnessBody {
        public Integer brightnessPct;
    }

    public static final class ProfileBody {
        public UUID profileId;
    }
}

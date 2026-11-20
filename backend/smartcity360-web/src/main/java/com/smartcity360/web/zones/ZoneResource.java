package com.smartcity360.web.zones;

import com.smartcity360.common.zones.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Path("/zones")
@Produces(MediaType.APPLICATION_JSON)
public class ZoneResource {
    @EJB
    ZoneService zones;

    @GET
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneView> list(@QueryParam("offset") @DefaultValue("0") int offset,
                               @QueryParam("limit") @DefaultValue("50") int limit) {
        return zones.list(offset, limit);
    }

    @GET
    @Path("/parents")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneView> parents(@QueryParam("type") String type,
                                  @QueryParam("search") @DefaultValue("") String search,
                                  @QueryParam("offset") @DefaultValue("0") int offset,
                                  @QueryParam("limit") @DefaultValue("25") int limit) {
        return zones.findParentOptions(type, search, offset, limit);
    }

    @GET
    @Path("/complaint-locations")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
    public List<ComplaintLocationView> complaintLocations(
            @QueryParam("search") @DefaultValue("") String search,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("25") int limit) {
        return zones.complaintLocations(search, offset, limit);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ZoneView get(@PathParam("id") UUID id) {
        return zones.get(id);
    }

    @GET
    @Path("/{id}/tree")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ZoneTreeView tree(@PathParam("id") UUID id) {
        return zones.tree(id);
    }

    @GET
    @Path("/{id}/path")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneView> path(@PathParam("id") UUID id) {
        return zones.path(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response create(CreateBody body) {
        if (body == null) throw new BadRequestException();
        ZoneView created = zones.create(new ZoneCreateCommand(body.type, body.code, body.name, body.parentId));
        return Response.created(URI.create("zones/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public ZoneView update(@PathParam("id") UUID id, UpdateBody body) {
        if (body == null) throw new BadRequestException();
        return zones.update(id, new ZoneUpdateCommand(body.code, body.name, body.parentId));
    }

    @POST
    @Path("/{id}/deactivate")
    @RolesAllowed("ADMIN")
    public ZoneView deactivate(@PathParam("id") UUID id) {
        return zones.deactivate(id);
    }

    @POST
    @Path("/{id}/shutdown")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public PowerOperationView shutdown(@PathParam("id") UUID id) {
        return zones.shutdown(id);
    }

    @POST
    @Path("/{id}/restore")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public PowerOperationView restore(@PathParam("id") UUID id) {
        return zones.restore(id);
    }

    @GET
    @Path("/{id}/usage")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public UsageView usage(@PathParam("id") UUID id, @QueryParam("from") String from,
                           @QueryParam("to") String to) {
        try {
            return zones.usage(id, OffsetDateTime.parse(from), OffsetDateTime.parse(to));
        } catch (NullPointerException | java.time.format.DateTimeParseException exception) {
            throw new BadRequestException("Invalid reporting interval");
        }
    }

    @GET
    @Path("/{id}/commands")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneCommandView> commands(@PathParam("id") UUID id,
                                          @QueryParam("offset") @DefaultValue("0") int offset,
                                          @QueryParam("limit") @DefaultValue("50") int limit) {
        return zones.commands(id, offset, limit);
    }

    public static final class CreateBody {
        public String type;
        public String code;
        public String name;
        public UUID parentId;
    }

    public static final class UpdateBody {
        public String code;
        public String name;
        public UUID parentId;
    }
}

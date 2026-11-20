package com.smartcity360.web.complaints;

import com.smartcity360.common.complaints.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/complaints")
@Produces(MediaType.APPLICATION_JSON)
public class ComplaintResource {
    @EJB
    ComplaintService complaints;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("RESIDENT")
    public Response submit(SubmitBody body) {
        if (body == null) throw new BadRequestException();
        ComplaintView created = complaints.submit(
                new ComplaintSubmitCommand(body.title, body.description, body.severity, body.zoneId));
        return Response.created(URI.create("complaints/" + created.id())).entity(created).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    public List<ComplaintView> list(@QueryParam("status") String status,
                                    @QueryParam("severity") String severity,
                                    @QueryParam("offset") @DefaultValue("0") int offset,
                                    @QueryParam("limit") @DefaultValue("50") int limit) {
        return complaints.list(status, severity, offset, limit);
    }

    @GET
    @Path("/my")
    @RolesAllowed("RESIDENT")
    public List<ComplaintView> mine(@QueryParam("offset") @DefaultValue("0") int offset,
                                    @QueryParam("limit") @DefaultValue("50") int limit) {
        return complaints.my(offset, limit);
    }

    @GET
    @Path("/queue")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ComplaintView> queue(@QueryParam("offset") @DefaultValue("0") int offset,
                                     @QueryParam("limit") @DefaultValue("50") int limit) {
        return complaints.queue(offset, limit);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
    public ComplaintView get(@PathParam("id") UUID id) {
        return complaints.get(id);
    }

    @GET
    @Path("/{id}/history")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
    public List<ComplaintEventView> history(@PathParam("id") UUID id,
                                            @QueryParam("offset") @DefaultValue("0") int offset,
                                            @QueryParam("limit") @DefaultValue("50") int limit) {
        return complaints.history(id, offset, limit);
    }

    @POST
    @Path("/{id}/assign")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ComplaintView assign(@PathParam("id") UUID id, AssignBody body) {
        if (body == null) throw new BadRequestException();
        return complaints.assign(id, new AssignmentCommand(body.assigneeId));
    }

    @POST
    @Path("/{id}/escalate")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ComplaintView escalate(@PathParam("id") UUID id, EscalateBody body) {
        if (body == null) throw new BadRequestException();
        return complaints.escalate(id, new EscalationCommand(body.expectedLevel, body.reason));
    }

    @POST
    @Path("/{id}/start")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ComplaintView start(@PathParam("id") UUID id) {
        return complaints.start(id);
    }

    @POST
    @Path("/{id}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ComplaintView resolve(@PathParam("id") UUID id, ResolveBody body) {
        if (body == null) throw new BadRequestException();
        return complaints.resolve(id, new ResolutionCommand(body.note));
    }

    public static final class SubmitBody {
        public String title;
        public String description;
        public String severity;
        public UUID zoneId;
    }

    public static final class AssignBody {
        public UUID assigneeId;
    }

    public static final class EscalateBody {
        public String expectedLevel;
        public String reason;
    }

    public static final class ResolveBody {
        public String note;
    }
}

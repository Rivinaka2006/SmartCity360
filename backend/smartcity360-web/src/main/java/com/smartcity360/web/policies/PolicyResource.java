package com.smartcity360.web.policies;

import com.smartcity360.common.policies.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.net.URI;
import java.util.*;

@Path("/policies")
@Produces(MediaType.APPLICATION_JSON)
public class PolicyResource {
    @EJB
    PolicyService policies;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public Response create(CreateBody body) {
        if (body == null) throw new BadRequestException();
        PolicyView created = policies.create(new PolicyCreateCommand(body.policyCode, body.name, body.domain, body.description));
        return Response.created(URI.create("policies/" + created.id())).entity(created).build();
    }

    @GET
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<PolicyView> list(@QueryParam("offset") @DefaultValue("0") int offset,
                                 @QueryParam("limit") @DefaultValue("50") int limit) {
        return policies.list(offset, limit);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public PolicyView get(@PathParam("id") UUID id) {
        return policies.get(id);
    }

    @POST
    @Path("/{id}/versions")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public Response createVersion(@PathParam("id") UUID id, VersionBody body) {
        if (body == null) throw new BadRequestException();
        PolicyVersionView created = policies.createVersion(id, new PolicyVersionCreateCommand(body.dslExpression));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}/versions")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public List<PolicyVersionView> versions(@PathParam("id") UUID id,
                                            @QueryParam("offset") @DefaultValue("0") int offset,
                                            @QueryParam("limit") @DefaultValue("50") int limit) {
        return policies.listVersions(id, offset, limit);
    }

    @POST
    @Path("/{id}/versions/{versionId}/validate")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public PolicyVersionView validate(@PathParam("id") UUID id, @PathParam("versionId") UUID versionId) {
        return policies.validateVersion(id, versionId);
    }

    @POST
    @Path("/{id}/versions/{versionId}/activate")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public PolicyVersionView activate(@PathParam("id") UUID id, @PathParam("versionId") UUID versionId) {
        return policies.activateVersion(id, versionId);
    }

    @POST
    @Path("/{id}/evaluate")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public Response evaluate(@PathParam("id") UUID id, EvaluationBody body) {
        if (body == null) throw new BadRequestException();
        PolicyEvaluationView evaluation = policies.evaluate(id, new PolicyEvaluationCommand(body.context));
        return Response.status("ERROR".equals(evaluation.status()) ? 400 : 200).entity(evaluation).build();
    }

    @POST
    @Path("/{id}/deactivate")
    @RolesAllowed("ADMIN")
    public PolicyView deactivate(@PathParam("id") UUID id) {
        return policies.deactivate(id);
    }

    @GET
    @Path("/{id}/evaluations")
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public List<PolicyEvaluationView> evaluations(@PathParam("id") UUID id,
                                                  @QueryParam("offset") @DefaultValue("0") int offset,
                                                  @QueryParam("limit") @DefaultValue("50") int limit) {
        return policies.listEvaluations(id, offset, limit);
    }

    public static final class CreateBody {
        public String policyCode;
        public String name;
        public String domain;
        public String description;
    }

    public static final class VersionBody {
        public String dslExpression;
    }

    public static final class EvaluationBody {
        public Map<String, Object> context = Map.of();
    }
}

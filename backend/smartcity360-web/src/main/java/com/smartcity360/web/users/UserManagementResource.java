package com.smartcity360.web.users;

import com.smartcity360.common.users.ManagedUserView;
import com.smartcity360.common.users.UserCreateCommand;
import com.smartcity360.common.users.UserManagementService;
import com.smartcity360.common.users.UserRoleUpdateCommand;
import com.smartcity360.common.users.UserStatusUpdateCommand;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class UserManagementResource {
    @EJB
    UserManagementService users;

    @GET
    public List<ManagedUserView> list(
            @QueryParam("search") @DefaultValue("") String search,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("25") int limit) {
        return users.list(search, offset, limit);
    }

    @POST
    public Response create(CreateUserBody body) {
        if (body == null) {
            throw new BadRequestException();
        }
        try {
            ManagedUserView created = users.create(new UserCreateCommand(
                    body.fullName,
                    body.email,
                    body.password,
                    body.role,
                    body.integrationTestAccount));
            return Response.created(URI.create("users/" + created.id())).entity(created).build();
        } finally {
            body.password = null;
        }
    }

    @PUT
    @Path("/{id}/roles")
    public ManagedUserView updateRoles(@PathParam("id") UUID id, UpdateRolesBody body) {
        if (body == null) {
            throw new BadRequestException();
        }
        return users.updateRoles(id, new UserRoleUpdateCommand(body.roles));
    }

    @PUT
    @Path("/{id}/status")
    public ManagedUserView updateStatus(@PathParam("id") UUID id, UpdateStatusBody body) {
        if (body == null) {
            throw new BadRequestException();
        }
        return users.updateStatus(id, new UserStatusUpdateCommand(body.status));
    }

    public static final class CreateUserBody {
        public String fullName;
        public String email;
        public String password;
        public String role;
        public boolean integrationTestAccount;
    }

    public static final class UpdateRolesBody {
        public Set<String> roles;
    }

    public static final class UpdateStatusBody {
        public String status;
    }
}

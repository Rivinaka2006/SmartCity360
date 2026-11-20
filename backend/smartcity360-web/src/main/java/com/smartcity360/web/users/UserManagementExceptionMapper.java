package com.smartcity360.web.users;

import com.smartcity360.common.users.UserManagementException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class UserManagementExceptionMapper implements ExceptionMapper<UserManagementException> {
    @Override
    public Response toResponse(UserManagementException exception) {
        return Response.status(status(exception.code()))
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "User request rejected", "code", exception.code()))
                .build();
    }

    private int status(String code) {
        return switch (code) {
            case "USER_NOT_FOUND" -> 404;
            case "FORBIDDEN", "SELF_LOCKOUT_PREVENTED" -> 403;
            case "EMAIL_ALREADY_EXISTS", "LAST_ACTIVE_ADMIN" -> 409;
            default -> 400;
        };
    }
}

package com.smartcity360.web.security;

import com.smartcity360.common.security.AuthenticationGateway;
import jakarta.ejb.EJBAccessException;
import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

import java.util.Map;

@Provider
public class SecurityExceptionMapper implements ExceptionMapper<EJBAccessException> {
    @Inject
    AuthenticationGateway gateway;
    @Inject
    SecurityContext security;

    public Response toResponse(EJBAccessException e) {
        gateway.recordAuthorizationDenied(security.getCallerPrincipal() == null ? "anonymous" : security.getCallerPrincipal().getName());
        return Response.status(403).entity(Map.of("error", "Forbidden")).type(MediaType.APPLICATION_JSON).build();
    }
}

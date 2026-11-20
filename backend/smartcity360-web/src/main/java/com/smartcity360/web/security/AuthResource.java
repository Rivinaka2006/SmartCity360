package com.smartcity360.web.security;

import com.smartcity360.common.security.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.*;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;

@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
    public static class LoginRequest {
        public String email;
        public String password;
    }

    @Inject
    jakarta.security.enterprise.SecurityContext security;
    @Inject
    CallerSecurityService callers;
    @Inject
    AuthenticationGateway gateway;
    @Inject
    CsrfTokenService csrf;
    @Inject
    LoginThrottle throttle;
    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;

    @GET
    @Path("/csrf")
    public Map<String, String> csrf() {
        return Map.of("token", csrf.token(request.getSession(true)));
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest body) {
        if (body == null || body.email == null || body.password == null) return unauthorized();
        String normalized = body.email.strip().toLowerCase(Locale.ROOT);
        String key = request.getRemoteAddr() + "|" + normalized;
        if (!throttle.allowed(key)) {
            gateway.recordRejectedAttempt(normalized);
            return Response.status(429).entity(Map.of("error", "Authentication temporarily unavailable")).build();
        }
        char[] secret = body.password.toCharArray();
        body.password = null;
        try {
            request.changeSessionId();
            AuthenticationStatus status = security.authenticate(request, response, AuthenticationParameters.withParams().credential(new UsernamePasswordCredential(body.email, new Password(secret))));
            if (status != AuthenticationStatus.SUCCESS) {
                throttle.failed(key);
                return unauthorized();
            }
            throttle.succeeded(key);
            return Response.ok(callers.currentUser()).build();
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @GET
    @Path("/me")
    public CurrentUserView me() {
        requireCaller();
        return callers.currentUser();
    }

    @POST
    @Path("/logout")
    public Response logout() {
        requireCaller();
        callers.recordLogout();
        try {
            request.logout();
        } catch (ServletException e) {
            throw new InternalServerErrorException();
        }
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Response.noContent().build();
    }

    private void requireCaller() {
        if (security.getCallerPrincipal() == null) throw new NotAuthorizedException("Authentication required");
    }

    private static Response unauthorized() {
        return Response.status(401).entity(Map.of("error", "Invalid credentials")).build();
    }
}

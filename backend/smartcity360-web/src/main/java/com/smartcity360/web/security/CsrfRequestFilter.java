package com.smartcity360.web.security;

import com.smartcity360.web.energy.MeterIngestionBoundary;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.*;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class CsrfRequestFilter implements ContainerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");
    @Inject
    CsrfTokenService tokens;
    @Context
    HttpServletRequest request;
    @Context
    UriInfo uri;

    public void filter(ContainerRequestContext context) {
        if (SAFE.contains(context.getMethod())) return;
        if (MeterIngestionBoundary.isMachineRequest(context.getMethod(), context.getUriInfo().getPath(), context.getHeaderString("Cookie")))
            return;
        HttpSession session = request.getSession(false);
        if (!tokens.valid(session, context.getHeaderString("X-CSRF-Token")) || !sameOrigin(context.getHeaderString("Origin"), context.getHeaderString("Referer")))
            context.abortWith(Response.status(403).entity(Map.of("error", "CSRF validation failed")).type(MediaType.APPLICATION_JSON).build());
    }

    private boolean sameOrigin(String origin, String referer) {
        String expected = origin(uri.getBaseUri());
        if (origin != null) return expected.equals(origin);
        if (referer != null) try {
            return expected.equals(origin(URI.create(referer)));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    private static String origin(URI u) {
        int port = u.getPort();
        return u.getScheme() + "://" + u.getHost() + (port < 0 ? "" : ":" + port);
    }
}

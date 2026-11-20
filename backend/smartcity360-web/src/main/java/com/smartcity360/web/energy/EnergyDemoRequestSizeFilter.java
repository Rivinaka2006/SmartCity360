package com.smartcity360.web.energy;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class EnergyDemoRequestSizeFilter implements ContainerRequestFilter {
    static final int MAX_REQUEST_BYTES = 1_024;
    private static final Set<String> DEMO_PATHS = Set.of(
            "energy/streams/demo",
            "energy/streams/demo/process-reading");

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        if (!"POST".equals(request.getMethod())
                || !DEMO_PATHS.contains(request.getUriInfo().getPath())) {
            return;
        }
        if (request.getLength() > MAX_REQUEST_BYTES) {
            request.abortWith(requestTooLarge());
            return;
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        InputStream input = request.getEntityStream();
        byte[] buffer = new byte[256];
        int total = 0;
        int bytesRead;
        try {
            while ((bytesRead = input.read(buffer)) != -1) {
                total += bytesRead;
                if (total > MAX_REQUEST_BYTES) {
                    request.abortWith(requestTooLarge());
                    return;
                }
                body.write(buffer, 0, bytesRead);
            }
        } catch (IOException exception) {
            request.abortWith(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid demonstration request"))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
            return;
        }
        request.setEntityStream(new ByteArrayInputStream(body.toByteArray()));
    }

    private static Response requestTooLarge() {
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .entity(Map.of("error", "Demonstration request is too large"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

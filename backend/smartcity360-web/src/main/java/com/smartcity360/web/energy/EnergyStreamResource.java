package com.smartcity360.web.energy;

import com.smartcity360.common.energy.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Path("/energy/streams")
@Produces(MediaType.APPLICATION_JSON)
public class EnergyStreamResource {
    private static final String CANONICAL_PATH = "/api/v1/energy/streams/process";
    @EJB
    EnergyStreamService streams;

    @POST
    @Path("/process")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response process(ProcessBody body,
                            @HeaderParam("X-Meter-Id") String meterCode,
                            @HeaderParam("X-Credential-Version") String version,
                            @HeaderParam("X-Request-Timestamp") String timestamp,
                            @HeaderParam("X-Request-Nonce") String nonce,
                            @HeaderParam("X-Meter-Signature") String signature) {
        try {
            if (body == null || body.payloadBase64 == null) return meterUnauthorized();
            byte[] payload = Base64.getDecoder().decode(body.payloadBase64);
            byte[] mac = Base64.getDecoder().decode(signature == null ? "" : signature);
            var command = new EnergyProcessCommand(meterCode, Integer.parseInt(version), Instant.parse(timestamp), nonce,
                    "POST", CANONICAL_PATH, mac, payload, body.layers);
            EnergyProcessResult result = streams.process(command);
            return Response.ok(new ProcessResponse(result.runId(), Base64.getEncoder().encodeToString(result.output()),
                    result.layers(), result.inputBytes(), result.outputBytes())).build();
        } catch (EnergyStreamServiceException exception) {
            if (exception.code().startsWith("KEY_CONFIGURATION"))
                return Response.status(503).entity(Map.of("error", "Energy stream keys unavailable")).build();
            if (exception.code().startsWith("METER_")) return meterUnauthorized();
            return Response.status(400).entity(Map.of("error", "Energy stream request rejected", "code", exception.code())).build();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return meterUnauthorized();
        }
    }

    @POST
    @Path("/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response verify(VerifyBody body) {
        try {
            byte[] plain = streams.verify(Base64.getDecoder().decode(body.envelopeBase64));
            return Response.ok(Map.of("payloadBase64", Base64.getEncoder().encodeToString(plain))).build();
        } catch (EnergyStreamServiceException | IllegalArgumentException | NullPointerException e) {
            return Response.status(400).entity(Map.of("error", "Protected payload could not be verified")).build();
        }
    }

    @GET
    @Path("/demo/status")
    @RolesAllowed("ADMIN")
    public DemoAvailability demoAvailability() {
        return new DemoAvailability(demoEnabled());
    }

    @GET
    @Path("/demo/process-reading/status")
    @RolesAllowed("ADMIN")
    public IntegratedDemoAvailability integratedDemoAvailability() {
        boolean enabled = integratedDemoEnabled();
        return new IntegratedDemoAvailability(enabled, enabled && streams.integratedDemoPrepared());
    }

    @POST
    @Path("/demo/process-reading")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response processIntegratedDemoReading(DemoBody body) {
        if (!integratedDemoEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Integrated demo unavailable", "code", "DEMO_DISABLED"))
                    .build();
        }
        if (body == null || body.consumptionKwh == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "A valid consumption value is required", "code", "DEMO_INPUT_INVALID"))
                    .build();
        }
        if (!streams.integratedDemoPrepared()) {
            return demoPreparationRequired();
        }
        try {
            return Response.ok(streams.processIntegratedDemoReading(body.consumptionKwh)).build();
        } catch (EnergyStreamServiceException exception) {
            return switch (exception.code()) {
                case "DEMO_INPUT_INVALID" -> Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "A valid consumption value is required", "code", exception.code()))
                        .build();
                case "DEMO_RATE_LIMITED" -> Response.status(429)
                        .entity(Map.of("error", "Please wait before processing another demo reading", "code", exception.code()))
                        .build();
                case "DEMO_PREPARATION_REQUIRED" -> demoPreparationRequired();
                case "DEMO_DISABLED" -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Integrated demo unavailable", "code", exception.code()))
                        .build();
                default -> Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("error", "Demo processing could not be completed", "code", "DEMO_PROCESSING_FAILED"))
                        .build();
            };
        }
    }

    @POST
    @Path("/demo")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response demonstrate(DemoBody body) {
        if (!demoEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Demonstration unavailable"))
                    .build();
        }
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "A valid demonstration input is required"))
                    .build();
        }
        try {
            return Response.ok(streams.demonstrate(body.consumptionKwh)).build();
        } catch (EnergyStreamServiceException exception) {
            if ("DEMO_INPUT_INVALID".equals(exception.code())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "A valid demonstration input is required"))
                        .build();
            }
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "The demonstration could not be completed"))
                    .build();
        }
    }

    @GET
    @Path("/runs")
    public List<EnergyRunView> runs(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("limit") @DefaultValue("50") int limit) {
        return streams.listRuns(Math.max(0, offset), limit);
    }

    @GET
    @Path("/runs/{id}")
    public EnergyRunView run(@PathParam("id") UUID id) throws NotFoundException {
        try {
            return streams.findRun(id);
        } catch (EnergyStreamServiceException e) {
            throw new NotFoundException();
        }
    }

    private static Response meterUnauthorized() {
        return Response.status(401).entity(Map.of("error", "Meter authentication failed")).build();
    }

    public static final class ProcessBody {
        public String payloadBase64;
        public List<String> layers = List.of("AUTHENTICATION", "COMPRESSION", "ENCRYPTION");
    }

    public static final class VerifyBody {
        public String envelopeBase64;
    }

    public static final class DemoBody {
        public String consumptionKwh;
    }

    public record DemoAvailability(boolean available) {
    }

    public record IntegratedDemoAvailability(boolean enabled, boolean prepared) {
    }

    public record ProcessResponse(UUID runId, String protectedPayloadBase64, List<String> layers, int inputBytes,
                                  int outputBytes) {
    }

    private boolean demoEnabled() {
        return demoEnabled(name -> ConfigProvider.getConfig()
                .getOptionalValue(name, String.class)
                .orElse(null));
    }

    private boolean integratedDemoEnabled() {
        return demoEnabled(name -> ConfigProvider.getConfig()
                .getOptionalValue(name, String.class)
                .orElse(null), "smartcity360.energy.stream.demo.integrated.enabled");
    }

    static boolean demoEnabled(Function<String, String> configuration) {
        return "true".equalsIgnoreCase(configuration.apply("smartcity360.energy.stream.demo.enabled"));
    }

    static boolean demoEnabled(Function<String, String> configuration, String propertyName) {
        return "true".equalsIgnoreCase(configuration.apply(propertyName));
    }

    private static Response demoPreparationRequired() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of(
                        "error", "Demo environment preparation required",
                        "code", "DEMO_PREPARATION_REQUIRED"))
                .build();
    }
}

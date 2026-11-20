package com.smartcity360.web.streetlights;

import com.smartcity360.common.streetlights.StreetlightServiceException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

import java.util.Map;

@Provider
public class StreetlightServiceExceptionMapper implements ExceptionMapper<StreetlightServiceException> {
    @Override
    public Response toResponse(StreetlightServiceException exception) {
        return Response.status(statusFor(exception.code())).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Streetlight request rejected", "code", exception.code())).build();
    }

    static int statusFor(String code) {
        return switch (code) {
            case "STREETLIGHT_NOT_FOUND", "PROFILE_NOT_FOUND", "ZONE_NOT_FOUND" -> 404;
            case "FORBIDDEN", "CALLER_NOT_FOUND", "CALLER_INACTIVE" -> 403;
            case "PROFILE_CODE_EXISTS", "ASSET_TAG_EXISTS", "PROFILE_IN_USE", "PROFILE_INACTIVE",
                 "PROFILE_VERSION_LIMIT", "ZONE_INACTIVE", "ZONE_NOT_STREET", "STREETLIGHT_UNAVAILABLE",
                 "STREETLIGHT_STATE_INVALID" -> 409;
            default -> 400;
        };
    }
}

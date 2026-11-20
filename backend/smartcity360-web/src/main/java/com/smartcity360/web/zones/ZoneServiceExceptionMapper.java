package com.smartcity360.web.zones;

import com.smartcity360.common.zones.ZoneServiceException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

import java.util.Map;

@Provider
public class ZoneServiceExceptionMapper implements ExceptionMapper<ZoneServiceException> {
    @Override
    public Response toResponse(ZoneServiceException exception) {
        int status = switch (exception.code()) {
            case "ZONE_NOT_FOUND" -> 404;
            case "ZONE_CODE_CONFLICT", "INVALID_ZONE_PARENT", "ZONE_CYCLE", "ACTIVE_DESCENDANTS_EXIST",
                 "ACTIVE_METERS_EXIST",
                 "OVERLAPPING_ENERGY_READINGS", "ZONE_INACTIVE" -> 409;
            case "CALLER_NOT_FOUND" -> 403;
            default -> 400;
        };
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Zone request rejected", "code", exception.code())).build();
    }
}

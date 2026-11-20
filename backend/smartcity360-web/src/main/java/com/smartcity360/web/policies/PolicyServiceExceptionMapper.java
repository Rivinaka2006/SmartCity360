package com.smartcity360.web.policies;

import com.smartcity360.common.policies.PolicyServiceException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

import java.util.Map;

@Provider
public class PolicyServiceExceptionMapper implements ExceptionMapper<PolicyServiceException> {
    @Override
    public Response toResponse(PolicyServiceException exception) {
        return Response.status(statusFor(exception.code())).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Policy request rejected", "code", exception.code())).build();
    }

    static int statusFor(String code) {
        return switch (code) {
            case "POLICY_NOT_FOUND", "VERSION_NOT_FOUND", "ACTIVE_VERSION_NOT_FOUND" -> 404;
            case "FORBIDDEN", "CALLER_NOT_FOUND", "CALLER_INACTIVE" -> 403;
            case "SOURCE_LENGTH_LIMIT", "CONTEXT_PAYLOAD_LIMIT" -> 413;
            case "POLICY_CODE_EXISTS", "POLICY_INACTIVE", "POLICY_NOT_ACTIVE", "VERSION_NOT_VALID", "VERSION_LIMIT" ->
                    409;
            default -> 400;
        };
    }
}

package com.smartcity360.web.complaints;

import com.smartcity360.common.complaints.ComplaintServiceException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

import java.util.Map;

@Provider
public class ComplaintServiceExceptionMapper implements ExceptionMapper<ComplaintServiceException> {
    @Override
    public Response toResponse(ComplaintServiceException exception) {
        int status = statusFor(exception.code());
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Complaint request rejected", "code", exception.code())).build();
    }

    static int statusFor(String code) {
        return switch (code) {
            case "COMPLAINT_NOT_FOUND", "ZONE_NOT_FOUND", "ASSIGNEE_NOT_FOUND" -> 404;
            case "FORBIDDEN", "CALLER_NOT_FOUND", "CALLER_INACTIVE" -> 403;
            case "COMPLAINT_ALREADY_ASSIGNED", "COMPLAINT_NOT_ASSIGNED_TO_CALLER", "COMPLAINT_TERMINAL",
                 "INVALID_STATUS_TRANSITION", "STALE_COMPLAINT_STATE", "ESCALATION_LIMIT_REACHED",
                 "COMPLAINT_UNASSIGNED", "ASSIGNEE_INACTIVE", "ASSIGNEE_ROLE_INVALID", "ZONE_INACTIVE" -> 409;
            default -> 400;
        };
    }
}

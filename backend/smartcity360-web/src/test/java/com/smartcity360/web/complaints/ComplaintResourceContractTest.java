package com.smartcity360.web.complaints;

import com.smartcity360.web.energy.MeterIngestionBoundary;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComplaintResourceContractTest {
    @Test
    void resourceUsesExplicitStaticRoutesAndRoleBoundaries() throws Exception {
        Class<?> type = Class.forName("com.smartcity360.web.complaints.ComplaintResource");
        assertEquals("/complaints", type.getAnnotation(Path.class).value());

        Method mine = type.getMethod("mine", int.class, int.class);
        assertEquals("/my", mine.getAnnotation(Path.class).value());
        assertEquals(Set.of("RESIDENT"), Set.of(mine.getAnnotation(RolesAllowed.class).value()));

        Method queue = type.getMethod("queue", int.class, int.class);
        assertEquals("/queue", queue.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"),
                Set.of(queue.getAnnotation(RolesAllowed.class).value()));

        Method submit = type.getMethod("submit", ComplaintResource.SubmitBody.class);
        assertNotNull(submit.getAnnotation(POST.class));
        assertEquals(Set.of("RESIDENT"), Set.of(submit.getAnnotation(RolesAllowed.class).value()));

        Method escalate = type.getMethod("escalate", java.util.UUID.class, ComplaintResource.EscalateBody.class);
        assertNotNull(escalate.getAnnotation(POST.class));
    }

    @Test
    void complaintWritesAreNeverClassifiedAsMachineCsrfExempt() {
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "complaints", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "complaints/id/escalate", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "complaints/id/resolve", null));
    }

    @Test
    void malformedComplaintErrorsMapToControlledClientResponses() {
        assertEquals(400, ComplaintServiceExceptionMapper.statusFor("ZONE_REQUIRED"));
    }
}

package com.smartcity360.web.energy;

import com.smartcity360.common.energy.EnergyStreamDemoResult;
import com.smartcity360.common.energy.EnergyStreamIntegratedDemoResult;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnergyStreamDemoResourceContractTest {
    @Test
    void exposesDemoStatusAndExecutionOnlyToAdmins() throws Exception {
        Class<?> resource = EnergyStreamResource.class;
        assertEquals("/energy/streams", resource.getAnnotation(Path.class).value());

        Method status = resource.getMethod("demoAvailability");
        assertNotNull(status.getAnnotation(GET.class));
        assertEquals("/demo/status", status.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN"), Set.of(status.getAnnotation(RolesAllowed.class).value()));

        Method demonstrate = resource.getMethod("demonstrate", EnergyStreamResource.DemoBody.class);
        assertNotNull(demonstrate.getAnnotation(POST.class));
        assertEquals("/demo", demonstrate.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN"), Set.of(demonstrate.getAnnotation(RolesAllowed.class).value()));

        Method integratedStatus = resource.getMethod("integratedDemoAvailability");
        assertNotNull(integratedStatus.getAnnotation(GET.class));
        assertEquals("/demo/process-reading/status", integratedStatus.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN"), Set.of(integratedStatus.getAnnotation(RolesAllowed.class).value()));

        Method integratedProcess = resource.getMethod("processIntegratedDemoReading", EnergyStreamResource.DemoBody.class);
        assertNotNull(integratedProcess.getAnnotation(POST.class));
        assertEquals("/demo/process-reading", integratedProcess.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMIN"), Set.of(integratedProcess.getAnnotation(RolesAllowed.class).value()));
    }

    @Test
    void demoFlagIsDisabledForMissingFalseOrUnrecognizedConfiguration() {
        String property = "smartcity360.energy.stream.demo.enabled";

        assertFalse(EnergyStreamResource.demoEnabled(ignored -> null));
        assertFalse(EnergyStreamResource.demoEnabled(ignored -> "false"));
        assertFalse(EnergyStreamResource.demoEnabled(ignored -> "yes"));
        assertTrue(EnergyStreamResource.demoEnabled(name -> {
            assertEquals(property, name);
            return "true";
        }));

        String integratedProperty = "smartcity360.energy.stream.demo.integrated.enabled";
        assertFalse(EnergyStreamResource.demoEnabled(ignored -> null, integratedProperty));
        assertFalse(EnergyStreamResource.demoEnabled(ignored -> "yes", integratedProperty));
        assertTrue(EnergyStreamResource.demoEnabled(name -> {
            assertEquals(integratedProperty, name);
            return "true";
        }, integratedProperty));
    }

    @Test
    void demoRouteRemainsWithinBrowserCsrfAndSameOriginBoundary() {
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "energy/streams/demo", null));
        assertFalse(MeterIngestionBoundary.isMachineRequest("POST", "energy/streams/demo/process-reading", null));
    }

    @Test
    void responseDtoContainsNoKeySignatureNonceOrCredentialFields() {
        assertFalse(Arrays.stream(EnergyStreamDemoResult.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(name -> name.contains("key")
                        || name.contains("signature")
                        || name.contains("nonce")
                        || name.contains("credential")
                        || name.contains("secret")));
        assertFalse(Arrays.stream(EnergyStreamIntegratedDemoResult.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(name -> name.contains("key")
                        || name.contains("signature")
                        || name.contains("nonce")
                        || name.contains("credential")
                        || name.contains("secret")));
        assertEquals(Set.of("consumptionKwh"), Arrays.stream(EnergyStreamResource.DemoBody.class.getFields())
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet()));
    }
}

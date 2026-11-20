
package com.smartcity360.ejb.energy;

import com.smartcity360.common.energy.EnergyProcessCommand;
import com.smartcity360.ejb.security.SecurityAuditService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnergyStreamServiceSecurityContractTest {
    @Test
    void processIsMeterAuthenticatedInternallyWhileHumanOperationsAreRoleProtected() throws Exception {
        assertNotNull(EnergyStreamServiceBean.class
                .getMethod("process", EnergyProcessCommand.class)
                .getAnnotation(PermitAll.class));

        Set<String> verificationRoles = Set.of(EnergyStreamServiceBean.class
                .getMethod("verify", byte[].class)
                .getAnnotation(RolesAllowed.class)
                .value());
        assertEquals(Set.of("ADMIN", "CITY_MANAGER"), verificationRoles);

        Set<String> processingRoles = Set.of(EnergyStreamServiceBean.class
                .getMethod("processIntegratedDemoReading", String.class)
                .getAnnotation(RolesAllowed.class)
                .value());
        assertEquals(Set.of("ADMIN"), processingRoles);

        Set<String> readinessRoles = Set.of(EnergyStreamServiceBean.class
                .getMethod("integratedDemoPrepared")
                .getAnnotation(RolesAllowed.class)
                .value());
        assertEquals(Set.of("ADMIN"), readinessRoles);
        assertEquals(SecurityAuditService.class, EnergyStreamServiceBean.class.getDeclaredField("audit").getType());

        ConfigProperty demoFlag = EnergyStreamServiceBean.class
                .getDeclaredField("integratedDemoEnabled")
                .getAnnotation(ConfigProperty.class);
        assertNotNull(demoFlag);
        assertEquals(EnergyStreamServiceBean.INTEGRATED_DEMO_ENABLED_PROPERTY, demoFlag.name());
        assertEquals("false", demoFlag.defaultValue());
    }
}

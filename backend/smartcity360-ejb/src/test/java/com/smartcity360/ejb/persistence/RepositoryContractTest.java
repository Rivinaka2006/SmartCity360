package com.smartcity360.ejb.persistence;

import jakarta.ejb.Stateless;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryContractTest {
    @Test
    void providesAStatelessRepositoryForEveryEntity() throws Exception {
        List<String> names = List.of("User", "Role", "UserRole", "Zone", "BuildingPowerState",
                "ZoneCommand", "EnergyMeter", "EnergyReading", "EnergyStreamRun", "Complaint",
                "ComplaintEvent", "LightingProfile", "Streetlight", "Policy", "PolicyVersion",
                "PolicyEvaluation", "AuditLog");
        Class<?> base = Class.forName("com.smartcity360.ejb.persistence.repository.AbstractRepository");
        for (String name : names) {
            Class<?> repository = Class.forName("com.smartcity360.ejb.persistence.repository." + name + "Repository");
            assertNotNull(repository.getAnnotation(Stateless.class), repository.getSimpleName());
            assertTrue(base.isAssignableFrom(repository));
        }
    }

    @Test
    void entityManagerUsesTheContainerManagedPersistenceUnit() throws Exception {
        Class<?> base = Class.forName("com.smartcity360.ejb.persistence.repository.AbstractRepository");
        Field field = base.getDeclaredField("entityManager");
        PersistenceContext context = field.getAnnotation(PersistenceContext.class);
        assertNotNull(context);
        assertEquals("smartcityPU", context.unitName());
    }
}

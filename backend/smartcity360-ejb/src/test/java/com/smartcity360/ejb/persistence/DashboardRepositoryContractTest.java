package com.smartcity360.ejb.persistence;

import com.smartcity360.ejb.persistence.repository.*;
import jakarta.ejb.Stateless;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardRepositoryContractTest {

    @Test
    void aggregateRepositoriesAreStatelessAndExposeReadMethods() throws Exception {
        Class<?>[] repositories = {
                ZoneRepository.class,
                BuildingPowerStateRepository.class,
                ComplaintRepository.class,
                StreetlightRepository.class,
                EnergyReadingRepository.class,
                PolicyRepository.class,
        };
        for (Class<?> repository : repositories) {
            assertNotNull(repository.getAnnotation(Stateless.class), repository.getSimpleName());
        }
        assertNotNull(ZoneRepository.class.getMethod("countActiveZones"));
        assertNotNull(ZoneRepository.class.getMethod("countActiveBuildings"));
        assertNotNull(BuildingPowerStateRepository.class.getMethod("countActiveBuildingStates"));
        assertNotNull(ComplaintRepository.class.getMethod("countByStatus"));
        assertNotNull(StreetlightRepository.class.getMethod("countByOperationalState"));
        assertNotNull(EnergyReadingRepository.class.getMethod("sumActiveMeterReadings", java.time.OffsetDateTime.class, java.time.OffsetDateTime.class));
        assertNotNull(PolicyRepository.class.getMethod("countByStatus"));
    }
}

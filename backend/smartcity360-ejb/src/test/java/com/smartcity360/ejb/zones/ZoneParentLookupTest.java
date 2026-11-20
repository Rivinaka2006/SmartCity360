package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.persistence.model.ZoneEntity;
import com.smartcity360.ejb.persistence.repository.ZoneRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZoneParentLookupTest {
    @Test
    void cityLookupReturnsActiveCityMatchesWithoutInvokingWriteOperations() {
        ZoneServiceBean service = new ZoneServiceBean();
        RecordingZoneRepository repository = new RecordingZoneRepository();
        service.zones = repository;

        assertEquals("CITY", service.findParentOptions("CITY", "001", 0, 25).get(0).type());
        assertEquals("001", repository.search);
        assertEquals(0, repository.offset);
        assertEquals(25, repository.limit);
        assertEquals(0, repository.writeCount);
    }

    @Test
    void invalidLookupTypeAndPaginationAreRejectedAsClientErrors() {
        ZoneServiceBean service = new ZoneServiceBean();
        service.zones = new RecordingZoneRepository();

        assertEquals("ZONE_TYPE_INVALID", assertThrows(RuntimeException.class,
                () -> service.findParentOptions("INVALID", "", 0, 25)).getMessage());
        assertEquals("PAGINATION_INVALID", assertThrows(RuntimeException.class,
                () -> service.findParentOptions("CITY", "", -1, 25)).getMessage());
        assertEquals("PAGINATION_INVALID", assertThrows(RuntimeException.class,
                () -> service.findParentOptions("CITY", "", 0, 51)).getMessage());
    }

    private static final class RecordingZoneRepository extends ZoneRepository {
        private String search;
        private int offset;
        private int limit;
        private int writeCount;

        @Override
        public List<ZoneEntity> findActiveByTypeAndSearch(ZoneEntity.ZoneType type,
                                                          String value,
                                                          int firstResult,
                                                          int maxResults) {
            search = value;
            offset = firstResult;
            limit = maxResults;
            ZoneEntity city = new ZoneEntity();
            city.setZoneType(type);
            city.setCode("001");
            city.setName("Colombo");
            city.setStatus(ZoneEntity.Status.ACTIVE);
            return List.of(city);
        }

        @Override
        public ZoneEntity insertZone(ZoneEntity zone) {
            writeCount++;
            return super.insertZone(zone);
        }
    }
}

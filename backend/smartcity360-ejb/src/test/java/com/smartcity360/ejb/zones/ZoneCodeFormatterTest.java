package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.persistence.model.ZoneEntity;
import com.smartcity360.ejb.persistence.repository.ZoneRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZoneCodeFormatterTest {
    @Test
    void formatsNumericCodesWithTheZoneTypePrefix() {
        assertEquals("C001", ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.CITY, "001"));
        assertEquals("D001", ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.DISTRICT, "001"));
        assertEquals("S001", ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.STREET, "001"));
        assertEquals("B001", ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.BUILDING, "001"));
    }

    @Test
    void acceptsOnlyTheCorrectCanonicalPrefix() {
        assertEquals("D001", ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.DISTRICT, "D001"));
        assertEquals("ZONE_CODE_INVALID", assertThrows(ZoneApplicationException.class,
                () -> ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.CITY, "D001")).code());
    }

    @Test
    void rejectsBlankAndMalformedCodes() {
        assertEquals("ZONE_CODE_INVALID", assertThrows(ZoneApplicationException.class,
                () -> ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.CITY, "")).code());
        assertEquals("ZONE_CODE_INVALID", assertThrows(ZoneApplicationException.class,
                () -> ZoneCodeFormatter.normalizeNew(ZoneEntity.ZoneType.CITY, "C-001")).code());
    }

    @Test
    void preservesAnUnchangedLegacyCodeDuringUpdate() {
        assertEquals("001", ZoneCodeFormatter.normalizeUpdate(ZoneEntity.ZoneType.CITY, "001", "001"));
    }

    @Test
    void createRejectsAConflictAfterNormalizingNumericInput() {
        ZoneServiceBean service = new ZoneServiceBean();
        service.zones = new ZoneRepository() {
            @Override
            public Optional<ZoneEntity> findByCode(String code) {
                assertEquals("D001", code);
                return Optional.of(new ZoneEntity());
            }
        };

        assertEquals("ZONE_CODE_CONFLICT", assertThrows(ZoneApplicationException.class,
                () -> service.create(new com.smartcity360.common.zones.ZoneCreateCommand(
                        "DISTRICT", "001", "Central", null))).code());
    }
}

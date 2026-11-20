package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.zones.domain.composite.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneTreeAssemblerTest {
    private final ZoneTreeAssembler assembler = new ZoneTreeAssembler();

    @Test
    void assemblesUnorderedRowsIntoCodeOrderedCompositeTree() {
        UUID city = UUID.randomUUID(), district = UUID.randomUUID(), street = UUID.randomUUID();
        UUID b2 = UUID.randomUUID(), b1 = UUID.randomUUID();
        List<ZoneTreeAssembler.ZoneNode> rows = List.of(
                node(b2, street, ZoneKind.BUILDING, "B-2"), node(city, null, ZoneKind.CITY, "CITY"),
                node(street, district, ZoneKind.STREET, "STREET"), node(b1, street, ZoneKind.BUILDING, "B-1"),
                node(district, city, ZoneKind.DISTRICT, "DISTRICT"));

        ZoneComponent root = assembler.assemble(city, rows);

        assertEquals(ZoneKind.CITY, root.kind());
        ZoneComponent assembledStreet = root.children().get(0).children().get(0);
        assertEquals(List.of("B-1", "B-2"), assembledStreet.children().stream().map(ZoneComponent::code).toList());
    }

    @Test
    void supportsAnEmptyCompositeAndBuildingRoot() {
        UUID street = UUID.randomUUID(), building = UUID.randomUUID();
        assertTrue(assembler.assemble(street, List.of(node(street, UUID.randomUUID(), ZoneKind.STREET, "S"))).children().isEmpty());
        assertInstanceOf(BuildingLeaf.class,
                assembler.assemble(building, List.of(node(building, street, ZoneKind.BUILDING, "B"))));
    }

    @Test
    void rejectsDuplicateCycleInvalidTypeAndDisconnectedRows() {
        UUID city = UUID.randomUUID(), district = UUID.randomUUID(), building = UUID.randomUUID();
        ZoneTreeAssembler.ZoneNode cityNode = node(city, null, ZoneKind.CITY, "C");
        assertEquals("ZONE_TREE_CORRUPT", assertThrows(ZoneTreeException.class,
                () -> assembler.assemble(city, List.of(cityNode, cityNode))).code());
        assertEquals("ZONE_TREE_CORRUPT", assertThrows(ZoneTreeException.class,
                () -> assembler.assemble(city, List.of(cityNode, node(building, city, ZoneKind.BUILDING, "B")))).code());
        assertEquals("ZONE_TREE_CORRUPT", assertThrows(ZoneTreeException.class,
                () -> assembler.assemble(city, List.of(cityNode, node(district, UUID.randomUUID(), ZoneKind.DISTRICT, "D")))).code());
    }

    @Test
    void rejectsMissingRoot() {
        assertEquals("ZONE_NOT_FOUND", assertThrows(ZoneTreeException.class,
                () -> assembler.assemble(UUID.randomUUID(), List.of())).code());
    }

    private static ZoneTreeAssembler.ZoneNode node(UUID id, UUID parent, ZoneKind kind, String code) {
        return new ZoneTreeAssembler.ZoneNode(id, parent, kind, code, code);
    }
}

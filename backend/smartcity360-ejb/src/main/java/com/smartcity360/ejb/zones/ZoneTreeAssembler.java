package com.smartcity360.ejb.zones;

import com.smartcity360.ejb.persistence.model.ZoneEntity;
import com.smartcity360.ejb.persistence.repository.ZoneRepository;
import com.smartcity360.ejb.zones.domain.composite.*;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.*;

@Stateless
public class ZoneTreeAssembler {
    @Inject
    ZoneRepository zones;

    public ZoneComponent load(UUID rootId) {
        List<ZoneNode> nodes = zones.findSubtree(rootId).stream().map(ZoneTreeAssembler::node).toList();
        return assemble(rootId, nodes);
    }

    public ZoneComponent assemble(UUID rootId, List<ZoneNode> rows) {
        Objects.requireNonNull(rootId, "rootId");
        Objects.requireNonNull(rows, "rows");
        Map<UUID, ZoneNode> nodes = new HashMap<>();
        for (ZoneNode row : rows) {
            if (nodes.putIfAbsent(row.id(), row) != null) throw corrupt();
        }
        ZoneNode root = nodes.get(rootId);
        if (root == null) throw new ZoneTreeException("ZONE_NOT_FOUND");

        Map<UUID, ZoneComponent> components = new HashMap<>();
        for (ZoneNode row : rows) {
            ZoneComponent component = row.kind() == ZoneKind.BUILDING
                    ? new BuildingLeaf(row.id(), row.code(), row.name())
                    : new CompositeZone(row.id(), row.code(), row.name(), row.kind());
            components.put(row.id(), component);
        }

        Set<UUID> attached = new HashSet<>();
        attached.add(rootId);
        List<ZoneNode> remaining = new ArrayList<>(rows);
        remaining.remove(root);
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            Iterator<ZoneNode> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                ZoneNode row = iterator.next();
                if (!attached.contains(row.parentId())) continue;
                ZoneComponent parent = components.get(row.parentId());
                if (!(parent instanceof CompositeZone composite)) throw corrupt();
                try {
                    composite.add(components.get(row.id()));
                } catch (IllegalArgumentException exception) {
                    throw corrupt();
                }
                attached.add(row.id());
                iterator.remove();
                progressed = true;
            }
            if (!progressed) throw corrupt();
        }
        return components.get(rootId);
    }

    private static ZoneNode node(ZoneEntity entity) {
        return new ZoneNode(entity.getId(), entity.getParentZone() == null ? null : entity.getParentZone().getId(),
                ZoneKind.valueOf(entity.getZoneType().name()), entity.getCode(), entity.getName());
    }

    private static ZoneTreeException corrupt() {
        return new ZoneTreeException("ZONE_TREE_CORRUPT");
    }

    public record ZoneNode(UUID id, UUID parentId, ZoneKind kind, String code, String name) {
        public ZoneNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
        }
    }
}

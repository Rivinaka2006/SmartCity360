package com.smartcity360.ejb.zones;

import com.smartcity360.common.zones.*;
import com.smartcity360.ejb.persistence.model.*;
import com.smartcity360.ejb.persistence.repository.*;
import com.smartcity360.ejb.security.SecurityAuditService;
import com.smartcity360.ejb.zones.domain.composite.*;
import jakarta.annotation.Resource;
import jakarta.annotation.security.*;
import jakarta.ejb.*;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Stateless
@DeclareRoles({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class ZoneServiceBean implements ZoneService {
    @Inject
    ZoneRepository zones;
    @Inject
    BuildingPowerStateRepository powerStates;
    @Inject
    ZoneCommandRepository zoneCommands;
    @Inject
    EnergyMeterRepository meters;
    @Inject
    EnergyReadingRepository readings;
    @Inject
    UserRepository users;
    @Inject
    ZoneTreeAssembler assembler;
    @Inject
    SecurityAuditService audit;
    @Resource
    SessionContext session;

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ZoneView create(ZoneCreateCommand command) {
        if (command == null) throw error("REQUEST_INVALID");
        ZoneEntity.ZoneType type = type(command.type());
        String normalizedCode = ZoneCodeFormatter.normalizeNew(type, command.code());
        validateText(command.name(), 150, "ZONE_NAME_INVALID");
        if (zones.findByCode(normalizedCode).isPresent()) throw error("ZONE_CODE_CONFLICT");
        ZoneEntity parent = parentFor(type, command.parentId(), null);
        ZoneEntity entity = new ZoneEntity();
        entity.setZoneType(type);
        entity.setCode(normalizedCode);
        entity.setName(command.name().strip());
        entity.setParentZone(parent);
        entity.setStatus(ZoneEntity.Status.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        zones.insertZone(entity);
        audit.recordApplicationEvent("ZONE_CREATED", actor().getId(), "ZONE", entity.getId(), "{\"status\":\"SUCCESS\"}");
        return view(entity);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ZoneView get(UUID id) {
        return view(requireZone(id));
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneView> list(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw error("PAGINATION_INVALID");
        return zones.listOrdered(offset, limit).stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneView> findParentOptions(String value, String search, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 50) throw error("PAGINATION_INVALID");
        ZoneEntity.ZoneType type = type(value);
        String normalizedSearch = search == null ? "" : search.strip();
        return zones.findActiveByTypeAndSearch(type, normalizedSearch, offset, limit)
                .stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
    public List<ComplaintLocationView> complaintLocations(String search, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 50) {
            throw error("PAGINATION_INVALID");
        }
        String normalizedSearch = search == null ? "" : search.strip();
        return zones.findActiveBySearch(normalizedSearch, offset, limit)
                .stream()
                .map(zone -> new ComplaintLocationView(zone.getId(), zone.getName(), zone.getCode(), zone.getZoneType().name()))
                .toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneView> path(UUID id) {
        requireZone(id);
        return zones.findAncestorPath(id).stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public ZoneTreeView tree(UUID id) {
        return treeView(loadTree(id));
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ZoneView update(UUID id, ZoneUpdateCommand command) {
        if (command == null) throw error("REQUEST_INVALID");
        ZoneEntity entity = requireZone(id);
        String normalizedCode = ZoneCodeFormatter.normalizeUpdate(entity.getZoneType(), command.code(), entity.getCode());
        validateText(command.name(), 150, "ZONE_NAME_INVALID");
        zones.findByCode(normalizedCode).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw error("ZONE_CODE_CONFLICT");
                });
        ZoneEntity parent = parentFor(entity.getZoneType(), command.parentId(), id);
        entity.setCode(normalizedCode);
        entity.setName(command.name().strip());
        entity.setParentZone(parent);
        entity.setUpdatedAt(OffsetDateTime.now());
        audit.recordApplicationEvent("ZONE_UPDATED", actor().getId(), "ZONE", id, "{\"status\":\"SUCCESS\"}");
        return view(entity);
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ZoneView deactivate(UUID id) {
        ZoneEntity entity = requireZone(id);
        if (entity.getStatus() == ZoneEntity.Status.INACTIVE) return view(entity);
        if (zones.countActiveDescendants(id) > 0) throw error("ACTIVE_DESCENDANTS_EXIST");
        if (meters.countActiveInSubtree(id) > 0) throw error("ACTIVE_METERS_EXIST");
        entity.setStatus(ZoneEntity.Status.INACTIVE);
        entity.setUpdatedAt(OffsetDateTime.now());
        audit.recordApplicationEvent("ZONE_DEACTIVATED", actor().getId(), "ZONE", id, "{\"status\":\"SUCCESS\"}");
        return view(entity);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PowerOperationView shutdown(UUID id) {
        return power(id, ZoneCommandEntity.Command.SHUTDOWN);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PowerOperationView restore(UUID id) {
        return power(id, ZoneCommandEntity.Command.RESTORE);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public UsageView usage(UUID id, OffsetDateTime from, OffsetDateTime to) {
        try {
            new UsageQuery(from, to, (building, start, end) -> null);
        } catch (RuntimeException exception) {
            throw error("INVALID_REPORTING_PERIOD");
        }
        ZoneEntity target = requireZone(id);
        ZoneComponent root = loadTree(id);
        Set<UUID> buildings = buildingIds(root);
        List<EnergyMeterEntity> meterRows = meters.findByBuildings(buildings);
        Map<UUID, Integer> meterCounts = meterRows.stream().collect(Collectors.groupingBy(
                meter -> meter.getBuildingZone().getId(), Collectors.summingInt(ignored -> 1)));
        List<UsageReadingAggregator.ReadingSlice> slices = readings.findFullyContainedByBuildings(buildings, from, to).stream()
                .map(reading -> new UsageReadingAggregator.ReadingSlice(reading.getMeter().getBuildingZone().getId(),
                        reading.getMeter().getId(), reading.getPeriodStartAt(), reading.getPeriodEndAt(), reading.getConsumptionKwh()))
                .toList();
        Map<UUID, UsageReport> byBuilding;
        try {
            byBuilding = UsageReadingAggregator.aggregate(buildings, slices, meterCounts, from, to);
        } catch (ZoneOperationException exception) {
            throw error(exception.code());
        }
        UsageReport report = root.reportUsage(new UsageQuery(from, to, (building, start, end) -> byBuilding.get(building)));
        return new UsageView(target.getId(), from, to, report.totalKwh(), report.readingCount(), report.meterCount(),
                report.buildingsWithReadings(), report.buildingsWithoutReadings(),
                "FULLY_CONTAINED_[FROM,TO);_OVERLAPS_REJECTED");
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ZoneCommandView> commands(UUID id, int offset, int limit) {
        requireZone(id);
        if (offset < 0 || limit < 1 || limit > 200) throw error("PAGINATION_INVALID");
        return zoneCommands.findByTargetZone(id, offset, limit).stream().map(this::commandView).toList();
    }

    private PowerOperationView power(UUID id, ZoneCommandEntity.Command command) {
        ZoneEntity target = requireZone(id);
        if (target.getStatus() != ZoneEntity.Status.ACTIVE) throw error("ZONE_INACTIVE");
        ZoneComponent root = loadTree(id);
        Set<UUID> buildingIds = buildingIds(root);
        List<UUID> orderedIds = buildingIds.stream().sorted().toList();
        Map<UUID, ZoneEntity> lockedZones = zones.lockByIds(orderedIds).stream()
                .collect(Collectors.toMap(ZoneEntity::getId, zone -> zone));
        if (lockedZones.size() != orderedIds.size()) throw error("ZONE_TREE_CORRUPT");
        Map<UUID, BuildingPowerStateEntity> existing = powerStates.findByZoneIdsForUpdate(orderedIds).stream()
                .collect(Collectors.toMap(BuildingPowerStateEntity::getZoneId, state -> state));
        UserEntity actor = actor();
        OffsetDateTime now = OffsetDateTime.now();
        PowerOperationContext context = new PowerOperationContext((buildingId, desired) -> {
            BuildingPowerStateEntity state = existing.get(buildingId);
            PowerState before = state == null ? PowerState.ON : PowerState.valueOf(state.getPowerState().name());
            if (state == null) {
                state = new BuildingPowerStateEntity();
                state.setZoneId(buildingId);
                state.setZone(lockedZones.get(buildingId));
                state.setPowerState(BuildingPowerStateEntity.PowerState.valueOf(desired.name()));
                state.setUpdatedBy(actor);
                state.setUpdatedAt(now);
                powerStates.persist(state);
                existing.put(buildingId, state);
            } else if (before != desired) {
                state.setPowerState(BuildingPowerStateEntity.PowerState.valueOf(desired.name()));
                state.setUpdatedBy(actor);
                state.setUpdatedAt(now);
            }
            return before != desired;
        });
        PowerOperationResult result = command == ZoneCommandEntity.Command.SHUTDOWN ? root.shutdown(context) : root.restore(context);
        ZoneCommandEntity history = new ZoneCommandEntity();
        history.setTargetZone(target);
        history.setIssuedBy(actor);
        history.setCommand(command);
        history.setExecutionStatus(ZoneCommandEntity.ExecutionStatus.SUCCEEDED);
        history.setAffectedBuildings(result.changedBuildings());
        String message = command + " changed " + result.changedBuildings() + " of " + result.targetedBuildings() + " buildings";
        history.setMessage(message);
        history.setIssuedAt(now);
        zoneCommands.persist(history);
        audit.recordApplicationEvent("ZONE_" + command.name(), actor.getId(), "ZONE", id,
                "{\"status\":\"SUCCESS\",\"changedBuildings\":" + result.changedBuildings() + "}");
        return new PowerOperationView(history.getId(), id, command.name(), result.targetedBuildings(),
                result.changedBuildings(), message, now);
    }

    private ZoneEntity parentFor(ZoneEntity.ZoneType type, UUID parentId, UUID movingId) {
        if (type == ZoneEntity.ZoneType.CITY) {
            if (parentId != null) throw error("INVALID_ZONE_PARENT");
            return null;
        }
        if (parentId == null) throw error("INVALID_ZONE_PARENT");
        if (movingId != null && zones.findSubtree(movingId).stream().anyMatch(zone -> zone.getId().equals(parentId)))
            throw error("ZONE_CYCLE");
        ZoneEntity parent = requireZone(parentId);
        ZoneEntity.ZoneType expected = switch (type) {
            case DISTRICT -> ZoneEntity.ZoneType.CITY;
            case STREET -> ZoneEntity.ZoneType.DISTRICT;
            case BUILDING -> ZoneEntity.ZoneType.STREET;
            case CITY -> throw new IllegalStateException();
        };
        if (parent.getZoneType() != expected || parent.getStatus() != ZoneEntity.Status.ACTIVE)
            throw error("INVALID_ZONE_PARENT");
        return parent;
    }

    private ZoneComponent loadTree(UUID id) {
        try {
            return assembler.load(id);
        } catch (ZoneTreeException e) {
            throw error(e.code());
        }
    }

    private ZoneEntity requireZone(UUID id) {
        if (id == null) throw error("ZONE_NOT_FOUND");
        return zones.findById(id).orElseThrow(() -> error("ZONE_NOT_FOUND"));
    }

    private UserEntity actor() {
        return users.findByEmail(session.getCallerPrincipal().getName()).orElseThrow(() -> error("CALLER_NOT_FOUND"));
    }

    private static ZoneEntity.ZoneType type(String value) {
        try {
            return ZoneEntity.ZoneType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw error("ZONE_TYPE_INVALID");
        }
    }

    private static void validateText(String value, int max, String code) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw error(code);
    }

    private static ZoneServiceException error(String code) {
        return new ZoneApplicationException(code);
    }

    private static Set<UUID> buildingIds(ZoneComponent root) {
        Set<UUID> ids = new LinkedHashSet<>();
        Deque<ZoneComponent> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ZoneComponent node = stack.pop();
            if (node.kind() == ZoneKind.BUILDING) ids.add(node.id());
            else node.children().forEach(stack::push);
        }
        return ids;
    }

    private ZoneView view(ZoneEntity z) {
        return new ZoneView(z.getId(), z.getParentZone() == null ? null : z.getParentZone().getId(), z.getZoneType().name(), z.getCode(), z.getName(), z.getStatus().name(), z.getCreatedAt(), z.getUpdatedAt());
    }

    private static ZoneTreeView treeView(ZoneComponent z) {
        return new ZoneTreeView(z.id(), z.kind().name(), z.code(), z.name(), z.children().stream().map(ZoneServiceBean::treeView).toList());
    }

    private ZoneCommandView commandView(ZoneCommandEntity c) {
        return new ZoneCommandView(c.getId(), c.getTargetZone().getId(), c.getIssuedBy().getId(), c.getCommand().name(), c.getExecutionStatus().name(), c.getAffectedBuildings(), c.getMessage(), c.getIssuedAt());
    }
}

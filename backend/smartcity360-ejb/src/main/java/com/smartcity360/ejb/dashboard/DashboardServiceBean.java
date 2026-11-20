package com.smartcity360.ejb.dashboard;

import com.smartcity360.common.dashboard.*;
import com.smartcity360.ejb.persistence.model.*;
import com.smartcity360.ejb.persistence.repository.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.*;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Stateless
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class DashboardServiceBean implements DashboardService {
    @Inject
    private ZoneRepository zones;
    @Inject
    private BuildingPowerStateRepository powerStates;
    @Inject
    private ComplaintRepository complaints;
    @Inject
    private StreetlightRepository streetlights;
    @Inject
    private EnergyReadingRepository readings;
    @Inject
    private PolicyRepository policies;

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public DashboardSummaryView summary() {
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
        Map<BuildingPowerStateEntity.PowerState, Long> powerCounts = enumCounts(
                powerStates.countActiveBuildingStates(), BuildingPowerStateEntity.PowerState.class);
        Map<ComplaintEntity.Status, Long> complaintCounts = enumCounts(
                complaints.countByStatus(), ComplaintEntity.Status.class);
        Map<StreetlightEntity.OperationalState, Long> streetlightCounts = enumCounts(
                streetlights.countByOperationalState(), StreetlightEntity.OperationalState.class);
        Map<PolicyEntity.Status, Long> policyCounts = enumCounts(
                policies.countByStatus(), PolicyEntity.Status.class);
        OffsetDateTime from = asOf.minusHours(24);
        Object[] energyResult = readings.sumActiveMeterReadings(from, asOf);
        long readingCount = ((Number) energyResult[0]).longValue();
        BigDecimal consumption = energyResult[1] == null ? null : (BigDecimal) energyResult[1];
        return DashboardSummaryAssembler.assemble(
                asOf,
                zones.countActiveZones(),
                zones.countActiveBuildings(),
                powerCounts,
                complaintCounts,
                streetlightCounts,
                policyCounts,
                new DashboardSummaryAssembler.EnergyAggregate(consumption, readingCount));
    }

    private static <E extends Enum<E>> Map<E, Long> enumCounts(
            List<Object[]> rows,
            Class<E> enumType) {
        return rows.stream().collect(Collectors.toMap(
                row -> enumValue(row[0], enumType),
                row -> ((Number) row[1]).longValue(),
                Long::sum,
                () -> new EnumMap<>(enumType)));
    }

    private static <E extends Enum<E>> E enumValue(Object value, Class<E> enumType) {
        if (enumType.isInstance(value)) return enumType.cast(value);
        return Enum.valueOf(enumType, value.toString());
    }
}

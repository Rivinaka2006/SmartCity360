package com.smartcity360.ejb.dashboard;

import com.smartcity360.common.dashboard.DashboardSummaryView;
import com.smartcity360.ejb.persistence.model.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

final class DashboardSummaryAssembler {

    private DashboardSummaryAssembler() {
    }

    static DashboardSummaryView assemble(
            OffsetDateTime asOf,
            long totalZones,
            long totalBuildings,
            Map<BuildingPowerStateEntity.PowerState, Long> powerStates,
            Map<ComplaintEntity.Status, Long> complaints,
            Map<StreetlightEntity.OperationalState, Long> streetlights,
            Map<PolicyEntity.Status, Long> policies,
            EnergyAggregate energy) {
        long powerOn = count(powerStates, BuildingPowerStateEntity.PowerState.ON);
        long powerOff = count(powerStates, BuildingPowerStateEntity.PowerState.OFF);
        long powerUnknown = Math.max(0, totalBuildings - powerOn - powerOff);
        return new DashboardSummaryView(
                asOf,
                new DashboardSummaryView.ZoneSummary(totalZones, totalBuildings),
                new DashboardSummaryView.PowerSummary(powerOn, powerOff, powerUnknown),
                new DashboardSummaryView.ComplaintSummary(
                        total(complaints),
                        count(complaints, ComplaintEntity.Status.OPEN),
                        count(complaints, ComplaintEntity.Status.IN_PROGRESS),
                        count(complaints, ComplaintEntity.Status.ESCALATED),
                        count(complaints, ComplaintEntity.Status.RESOLVED),
                        count(complaints, ComplaintEntity.Status.REJECTED)),
                new DashboardSummaryView.StreetlightSummary(
                        total(streetlights),
                        count(streetlights, StreetlightEntity.OperationalState.ON),
                        count(streetlights, StreetlightEntity.OperationalState.OFF),
                        count(streetlights, StreetlightEntity.OperationalState.FAULT),
                        count(streetlights, StreetlightEntity.OperationalState.MAINTENANCE)),
                new DashboardSummaryView.EnergySummary(
                        asOf.minusHours(24), asOf, energy.consumptionKwh(), energy.readingCount()),
                new DashboardSummaryView.PolicySummary(
                        total(policies),
                        count(policies, PolicyEntity.Status.DRAFT),
                        count(policies, PolicyEntity.Status.ACTIVE),
                        count(policies, PolicyEntity.Status.INACTIVE)));
    }

    private static <T> long count(Map<T, Long> values, T key) {
        return values.getOrDefault(key, 0L);
    }

    private static <T> long total(Map<T, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
    }

    record EnergyAggregate(BigDecimal consumptionKwh, long readingCount) {
    }
}

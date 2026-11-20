package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.EnergyMeterEntity;
import com.smartcity360.ejb.persistence.model.EnergyReadingEntity;
import jakarta.ejb.Stateless;

import java.time.OffsetDateTime;
import java.util.*;

@Stateless
public class EnergyReadingRepository extends AbstractRepository<EnergyReadingEntity, UUID> {
    public EnergyReadingRepository() {
        super(EnergyReadingEntity.class);
    }

    public List<EnergyReadingEntity> findHistory(UUID meterId, OffsetDateTime from, OffsetDateTime to, int offset, int limit) {
        return entityManager.createQuery("SELECT r FROM EnergyReadingEntity r WHERE r.meter.id=:id AND r.periodEndAt>=:from AND r.periodEndAt<:to ORDER BY r.periodEndAt DESC", EnergyReadingEntity.class).setParameter("id", meterId).setParameter("from", from).setParameter("to", to).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<EnergyReadingEntity> findFullyContainedByBuildings(Collection<UUID> buildingIds, OffsetDateTime from, OffsetDateTime to) {
        if (buildingIds.isEmpty()) return List.of();
        return entityManager.createQuery("SELECT r FROM EnergyReadingEntity r JOIN FETCH r.meter m JOIN FETCH m.buildingZone WHERE m.buildingZone.id IN :ids AND r.periodStartAt>=:from AND r.periodEndAt<=:to ORDER BY m.id,r.periodStartAt,r.periodEndAt", EnergyReadingEntity.class).setParameter("ids", buildingIds).setParameter("from", from).setParameter("to", to).getResultList();
    }

    public Object[] sumActiveMeterReadings(OffsetDateTime from, OffsetDateTime to) {
        return entityManager.createQuery("""
                        SELECT count(r), sum(r.consumptionKwh)
                        FROM EnergyReadingEntity r
                        JOIN r.meter m
                        WHERE m.status=:status
                        AND r.periodStartAt>=:from
                        AND r.periodEndAt<=:to
                        """, Object[].class)
                .setParameter("status", EnergyMeterEntity.Status.ACTIVE)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }
}

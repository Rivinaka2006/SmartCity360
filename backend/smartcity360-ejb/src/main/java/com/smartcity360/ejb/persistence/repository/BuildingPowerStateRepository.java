package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.BuildingPowerStateEntity;
import com.smartcity360.ejb.persistence.model.ZoneEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.LockModeType;

import java.util.*;

@Stateless
public class BuildingPowerStateRepository extends AbstractRepository<BuildingPowerStateEntity, UUID> {
    public BuildingPowerStateRepository() {
        super(BuildingPowerStateEntity.class);
    }

    public List<BuildingPowerStateEntity> findByZoneIdsForUpdate(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return entityManager.createQuery("SELECT s FROM BuildingPowerStateEntity s WHERE s.zoneId IN :ids ORDER BY s.zoneId",
                BuildingPowerStateEntity.class).setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    public List<Object[]> countActiveBuildingStates() {
        return entityManager.createQuery("""
                        SELECT s.powerState, count(s)
                        FROM BuildingPowerStateEntity s
                        JOIN s.zone z
                        WHERE z.status=:status AND z.zoneType=:type
                        GROUP BY s.powerState
                        """, Object[].class)
                .setParameter("status", ZoneEntity.Status.ACTIVE)
                .setParameter("type", ZoneEntity.ZoneType.BUILDING)
                .getResultList();
    }
}

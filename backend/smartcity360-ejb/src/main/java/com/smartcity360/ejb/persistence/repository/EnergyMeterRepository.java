package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.EnergyMeterEntity;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class EnergyMeterRepository extends AbstractRepository<EnergyMeterEntity, UUID> {
    public EnergyMeterRepository() {
        super(EnergyMeterEntity.class);
    }

    public Optional<EnergyMeterEntity> findByMeterCode(String code) {
        return entityManager.createQuery("SELECT m FROM EnergyMeterEntity m WHERE m.meterCode=:code", EnergyMeterEntity.class).setParameter("code", code).getResultStream().findFirst();
    }

    public Optional<EnergyMeterEntity> findByMeterCodeWithBuildingZone(String code) {
        return entityManager.createQuery(
                        "SELECT m FROM EnergyMeterEntity m JOIN FETCH m.buildingZone WHERE m.meterCode=:code",
                        EnergyMeterEntity.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst();
    }

    public List<EnergyMeterEntity> findByBuilding(UUID id) {
        return entityManager.createQuery("SELECT m FROM EnergyMeterEntity m WHERE m.buildingZone.id=:id ORDER BY m.meterCode", EnergyMeterEntity.class).setParameter("id", id).getResultList();
    }

    public List<EnergyMeterEntity> findByBuildings(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return entityManager.createQuery("SELECT m FROM EnergyMeterEntity m JOIN FETCH m.buildingZone WHERE m.buildingZone.id IN :ids ORDER BY m.buildingZone.id,m.meterCode", EnergyMeterEntity.class).setParameter("ids", ids).getResultList();
    }

    public long countActiveInSubtree(UUID rootId) {
        return ((Number) entityManager.createNativeQuery("WITH RECURSIVE subtree AS (SELECT id FROM zones WHERE id=?1 UNION ALL SELECT z.id FROM zones z JOIN subtree s ON z.parent_zone_id=s.id) SELECT count(*) FROM energy_meters m JOIN subtree s ON s.id=m.building_zone_id WHERE m.status='ACTIVE'").setParameter(1, rootId).getSingleResult()).longValue();
    }
}

package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.EnergyStreamRunEntity;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class EnergyStreamRunRepository extends AbstractRepository<EnergyStreamRunEntity, UUID> {
    public EnergyStreamRunRepository() {
        super(EnergyStreamRunEntity.class);
    }

    public List<EnergyStreamRunEntity> findByMeter(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT r FROM EnergyStreamRunEntity r WHERE r.meter.id=:id ORDER BY r.executedAt DESC", EnergyStreamRunEntity.class).setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

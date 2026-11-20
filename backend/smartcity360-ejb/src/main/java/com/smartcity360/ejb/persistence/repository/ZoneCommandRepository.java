package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.ZoneCommandEntity;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class ZoneCommandRepository extends AbstractRepository<ZoneCommandEntity, UUID> {
    public ZoneCommandRepository() {
        super(ZoneCommandEntity.class);
    }

    public List<ZoneCommandEntity> findByTargetZone(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT c FROM ZoneCommandEntity c WHERE c.targetZone.id=:id ORDER BY c.issuedAt DESC", ZoneCommandEntity.class).setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

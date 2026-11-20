package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.AuditLogEntity;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class AuditLogRepository extends AbstractRepository<AuditLogEntity, UUID> {
    public AuditLogRepository() {
        super(AuditLogEntity.class);
    }

    public List<AuditLogEntity> findByEntity(String type, UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT a FROM AuditLogEntity a WHERE a.entityType=:type AND a.entityId=:id ORDER BY a.occurredAt DESC", AuditLogEntity.class).setParameter("type", type).setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

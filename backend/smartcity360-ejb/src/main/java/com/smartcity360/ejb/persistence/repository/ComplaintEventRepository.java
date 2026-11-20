package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.ComplaintEventEntity;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class ComplaintEventRepository extends AbstractRepository<ComplaintEventEntity, UUID> {
    public ComplaintEventRepository() {
        super(ComplaintEventEntity.class);
    }

    public List<ComplaintEventEntity> findByComplaint(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT e FROM ComplaintEventEntity e WHERE e.complaint.id=:id ORDER BY e.occurredAt,e.id", ComplaintEventEntity.class).setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

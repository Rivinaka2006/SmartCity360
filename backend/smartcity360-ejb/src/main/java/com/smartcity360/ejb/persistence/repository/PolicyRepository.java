package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.PolicyEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;

import java.util.*;

@Stateless
public class PolicyRepository extends AbstractRepository<PolicyEntity, UUID> {
    public PolicyRepository() {
        super(PolicyEntity.class);
    }

    public Optional<PolicyEntity> findByPolicyCode(String code) {
        return entityManager.createQuery("SELECT p FROM PolicyEntity p WHERE p.policyCode=:code", PolicyEntity.class)
                .setParameter("code", code).getResultStream().findFirst();
    }

    public Optional<PolicyEntity> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(PolicyEntity.class, id, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 5000)));
    }

    public Optional<PolicyEntity> findByIdForRead(UUID id) {
        return Optional.ofNullable(entityManager.find(PolicyEntity.class, id, LockModeType.PESSIMISTIC_READ,
                Map.of("jakarta.persistence.lock.timeout", 5000)));
    }

    public void lockPolicyCode(String code) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, "policy:" + code).getSingleResult();
    }

    public List<PolicyEntity> listOrdered(boolean activeOnly, int offset, int limit) {
        String jpql = "SELECT p FROM PolicyEntity p" + (activeOnly ? " WHERE p.status=:status" : "")
                + " ORDER BY p.createdAt DESC, p.id";
        var query = entityManager.createQuery(jpql, PolicyEntity.class);
        if (activeOnly) query.setParameter("status", PolicyEntity.Status.ACTIVE);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<Object[]> countByStatus() {
        return entityManager.createQuery("SELECT p.status, count(p) FROM PolicyEntity p GROUP BY p.status", Object[].class)
                .getResultList();
    }
}

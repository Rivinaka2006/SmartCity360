package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.PolicyVersionEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.LockModeType;

import java.util.*;

@Stateless
public class PolicyVersionRepository extends AbstractRepository<PolicyVersionEntity, UUID> {
    public PolicyVersionRepository() {
        super(PolicyVersionEntity.class);
    }

    public List<PolicyVersionEntity> findByPolicy(UUID id) {
        return findByPolicy(id, 0, 200);
    }

    public List<PolicyVersionEntity> findByPolicy(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT v FROM PolicyVersionEntity v WHERE v.policy.id=:id ORDER BY v.versionNo DESC", PolicyVersionEntity.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public Optional<PolicyVersionEntity> findActive(UUID id) {
        return entityManager.createQuery("SELECT v FROM PolicyVersionEntity v WHERE v.policy.id=:id AND v.active=true", PolicyVersionEntity.class)
                .setParameter("id", id).getResultStream().findFirst();
    }

    public Optional<PolicyVersionEntity> findByPolicyAndId(UUID policyId, UUID versionId) {
        return entityManager.createQuery("SELECT v FROM PolicyVersionEntity v WHERE v.policy.id=:policyId AND v.id=:versionId", PolicyVersionEntity.class)
                .setParameter("policyId", policyId).setParameter("versionId", versionId).getResultStream().findFirst();
    }

    public int nextVersionNumber(UUID policyId) {
        Integer current = entityManager.createQuery("SELECT MAX(v.versionNo) FROM PolicyVersionEntity v WHERE v.policy.id=:id", Integer.class)
                .setParameter("id", policyId).getSingleResult();
        if (current == null) return 1;
        if (current == Integer.MAX_VALUE) throw new IllegalStateException("Policy version limit reached");
        return current + 1;
    }

    public List<PolicyVersionEntity> findActiveForUpdate(UUID policyId) {
        return entityManager.createQuery("SELECT v FROM PolicyVersionEntity v WHERE v.policy.id=:id AND v.active=true ORDER BY v.versionNo", PolicyVersionEntity.class)
                .setParameter("id", policyId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    public void flushChanges() {
        entityManager.flush();
    }
}

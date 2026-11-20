package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.PolicyEvaluationEntity;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class PolicyEvaluationRepository extends AbstractRepository<PolicyEvaluationEntity, UUID> {
    public PolicyEvaluationRepository() {
        super(PolicyEvaluationEntity.class);
    }

    public List<PolicyEvaluationEntity> findByPolicyVersion(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT e FROM PolicyEvaluationEntity e WHERE e.policyVersion.id=:id ORDER BY e.evaluatedAt DESC, e.id DESC", PolicyEvaluationEntity.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<PolicyEvaluationEntity> findByPolicy(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT e FROM PolicyEvaluationEntity e JOIN FETCH e.policyVersion v WHERE v.policy.id=:id ORDER BY e.evaluatedAt DESC, e.id DESC", PolicyEvaluationEntity.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

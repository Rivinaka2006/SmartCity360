package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.LightingProfileEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.LockModeType;

import java.util.*;

@Stateless
public class LightingProfileRepository extends AbstractRepository<LightingProfileEntity, UUID> {
    public LightingProfileRepository() {
        super(LightingProfileEntity.class);
    }

    public Optional<LightingProfileEntity> findByProfileCode(String code) {
        return entityManager.createQuery("SELECT p FROM LightingProfileEntity p WHERE p.profileCode=:code", LightingProfileEntity.class)
                .setParameter("code", code).getResultStream().findFirst();
    }

    public Optional<LightingProfileEntity> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(LightingProfileEntity.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public void lockProfileCode(String code) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, "lighting-profile-code:" + code).getSingleResult();
    }

    public List<LightingProfileEntity> listOrdered(boolean activeOnly, int offset, int limit) {
        String jpql = "SELECT p FROM LightingProfileEntity p" + (activeOnly ? " WHERE p.active=true" : "")
                + " ORDER BY p.profileCode,p.profileVersion,p.id";
        return entityManager.createQuery(jpql, LightingProfileEntity.class)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

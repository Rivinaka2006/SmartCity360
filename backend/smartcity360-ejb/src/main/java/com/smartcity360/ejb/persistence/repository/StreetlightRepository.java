package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.StreetlightEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.LockModeType;

import java.util.*;

@Stateless
public class StreetlightRepository extends AbstractRepository<StreetlightEntity, UUID> {
    public StreetlightRepository() {
        super(StreetlightEntity.class);
    }

    public Optional<StreetlightEntity> findByAssetTag(String tag) {
        return entityManager.createQuery("SELECT s FROM StreetlightEntity s WHERE s.assetTag=:tag", StreetlightEntity.class)
                .setParameter("tag", tag).getResultStream().findFirst();
    }

    public Optional<StreetlightEntity> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(StreetlightEntity.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public void lockAssetTag(String assetTag) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, "streetlight-asset-tag:" + assetTag).getSingleResult();
    }

    public List<StreetlightEntity> listJoined(UUID streetZoneId, int offset, int limit) {
        String jpql = "SELECT s FROM StreetlightEntity s JOIN FETCH s.profile JOIN FETCH s.streetZone"
                + (streetZoneId == null ? "" : " WHERE s.streetZone.id=:zone") + " ORDER BY s.assetTag,s.id";
        var query = entityManager.createQuery(jpql, StreetlightEntity.class);
        if (streetZoneId != null) query.setParameter("zone", streetZoneId);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<StreetlightEntity> findByStreetZone(UUID id, int offset, int limit) {
        return listJoined(id, offset, limit);
    }

    public List<StreetlightEntity> findByProfile(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT s FROM StreetlightEntity s WHERE s.profile.id=:id ORDER BY s.assetTag", StreetlightEntity.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long countByProfile(UUID profileId) {
        return entityManager.createQuery("SELECT count(s) FROM StreetlightEntity s WHERE s.profile.id=:id", Long.class)
                .setParameter("id", profileId).getSingleResult();
    }

    public long countAll() {
        return entityManager.createQuery("SELECT count(s) FROM StreetlightEntity s", Long.class).getSingleResult();
    }

    public long countDistinctActiveProfiles() {
        return entityManager.createQuery(
                        "SELECT count(DISTINCT s.profile.id) FROM StreetlightEntity s WHERE s.profile.active=true", Long.class)
                .getSingleResult();
    }

    public List<Object[]> countByOperationalState() {
        return entityManager.createQuery(
                        "SELECT s.operationalState, count(s) FROM StreetlightEntity s GROUP BY s.operationalState",
                        Object[].class)
                .getResultList();
    }
}

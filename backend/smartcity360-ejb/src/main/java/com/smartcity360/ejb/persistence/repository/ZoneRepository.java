package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.ZoneEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.LockModeType;

import java.util.*;

@Stateless
public class ZoneRepository extends AbstractRepository<ZoneEntity, UUID> {
    public ZoneRepository() {
        super(ZoneEntity.class);
    }

    public Optional<ZoneEntity> findByCode(String code) {
        return entityManager.createQuery("SELECT z FROM ZoneEntity z WHERE z.code=:code", ZoneEntity.class)
                .setParameter("code", code).getResultStream().findFirst();
    }

    public ZoneEntity insertZone(ZoneEntity zone) {
        if (zone.getId() == null) zone.setId(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO zones(id, parent_zone_id, zone_type, code, name, status, created_at, updated_at)
                        VALUES(CAST(?1 AS uuid), CAST(?2 AS uuid), ?3, ?4, ?5, ?6, ?7, ?8)
                        """)
                .setParameter(1, zone.getId().toString())
                .setParameter(2, zone.getParentZone() == null ? null : zone.getParentZone().getId().toString())
                .setParameter(3, zone.getZoneType().name())
                .setParameter(4, zone.getCode())
                .setParameter(5, zone.getName())
                .setParameter(6, zone.getStatus().name())
                .setParameter(7, zone.getCreatedAt())
                .setParameter(8, zone.getUpdatedAt())
                .executeUpdate();
        return zone;
    }

    public List<ZoneEntity> findChildren(UUID parentId) {
        return entityManager.createQuery("SELECT z FROM ZoneEntity z WHERE z.parentZone.id=:id ORDER BY z.code", ZoneEntity.class)
                .setParameter("id", parentId).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<ZoneEntity> findAncestorPath(UUID id) {
        return entityManager.createNativeQuery("""
                WITH RECURSIVE ancestry AS (
                    SELECT z.*, 0 AS depth
                    FROM zones z
                    WHERE z.id = ?1
                    UNION ALL
                    SELECT parent.*, ancestry.depth + 1
                    FROM zones parent
                    JOIN ancestry ON ancestry.parent_zone_id = parent.id
                )
                SELECT id, parent_zone_id, zone_type, code, name, status, created_at, updated_at
                FROM ancestry
                ORDER BY depth DESC
                """, ZoneEntity.class)
                .setParameter(1, id)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<ZoneEntity> findSubtree(UUID rootId) {
        return entityManager.createNativeQuery("""
                WITH RECURSIVE subtree AS (
                    SELECT z.*, ARRAY[z.id] AS traversal_path, false AS traversal_cycle
                    FROM zones z WHERE z.id = ?1
                    UNION ALL
                    SELECT child.*, subtree.traversal_path || child.id,
                           child.id = ANY(subtree.traversal_path)
                    FROM zones child
                    JOIN subtree ON child.parent_zone_id = subtree.id
                    WHERE NOT subtree.traversal_cycle
                )
                SELECT id, parent_zone_id, zone_type, code, name, status, created_at, updated_at
                FROM subtree
                ORDER BY code, id
                """, ZoneEntity.class).setParameter(1, rootId).getResultList();
    }

    public List<ZoneEntity> lockByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return entityManager.createQuery("SELECT z FROM ZoneEntity z WHERE z.id IN :ids ORDER BY z.id", ZoneEntity.class)
                .setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    public long countActiveDescendants(UUID rootId) {
        return ((Number) entityManager.createNativeQuery("""
                WITH RECURSIVE descendants AS (
                    SELECT id, status FROM zones WHERE parent_zone_id = ?1
                    UNION ALL
                    SELECT child.id, child.status FROM zones child
                    JOIN descendants parent ON child.parent_zone_id = parent.id
                ) SELECT count(*) FROM descendants WHERE status = 'ACTIVE'
                """).setParameter(1, rootId).getSingleResult()).longValue();
    }

    public List<ZoneEntity> listOrdered(int offset, int limit) {
        return entityManager.createQuery("SELECT z FROM ZoneEntity z LEFT JOIN FETCH z.parentZone ORDER BY z.code", ZoneEntity.class)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<ZoneEntity> findActiveByTypeAndSearch(ZoneEntity.ZoneType type,
                                                       String search,
                                                       int offset,
                                                       int limit) {
        return entityManager.createQuery("""
                        SELECT z FROM ZoneEntity z
                        WHERE z.status = :status
                          AND z.zoneType = :type
                          AND (LOWER(z.code) LIKE :search ESCAPE '\\'
                               OR LOWER(z.name) LIKE :search ESCAPE '\\')
                        ORDER BY z.name, z.code
                        """, ZoneEntity.class)
                .setParameter("status", ZoneEntity.Status.ACTIVE)
                .setParameter("type", type)
                .setParameter("search", "%" + escapeLike(search) + "%")
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<ZoneEntity> findActiveBySearch(String search, int offset, int limit) {
        return entityManager.createQuery("""
                        SELECT z FROM ZoneEntity z
                        WHERE z.status = :status
                          AND (LOWER(z.code) LIKE :search ESCAPE '\\'
                               OR LOWER(z.name) LIKE :search ESCAPE '\\')
                        ORDER BY z.name, z.code
                        """, ZoneEntity.class)
                .setParameter("status", ZoneEntity.Status.ACTIVE)
                .setParameter("search", "%" + escapeLike(search) + "%")
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .toLowerCase(Locale.ROOT);
    }

    public long countActiveZones() {
        return entityManager.createQuery(
                        "SELECT count(z) FROM ZoneEntity z WHERE z.status=:status", Long.class)
                .setParameter("status", ZoneEntity.Status.ACTIVE)
                .getSingleResult();
    }

    public long countActiveBuildings() {
        return entityManager.createQuery(
                        "SELECT count(z) FROM ZoneEntity z WHERE z.status=:status AND z.zoneType=:type", Long.class)
                .setParameter("status", ZoneEntity.Status.ACTIVE)
                .setParameter("type", ZoneEntity.ZoneType.BUILDING)
                .getSingleResult();
    }
}

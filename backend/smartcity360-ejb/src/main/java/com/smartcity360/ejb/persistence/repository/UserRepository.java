package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.UserEntity;
import jakarta.ejb.Stateless;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Stateless
public class UserRepository extends AbstractRepository<UserEntity, UUID> {
    public UserRepository() {
        super(UserEntity.class);
    }

    public Optional<UserEntity> findByEmail(String email) {
        return entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE lower(u.email)=lower(:email)", UserEntity.class)
                .setParameter("email", email)
                .setHint("jakarta.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS)
                .setHint("jakarta.persistence.cache.storeMode", CacheStoreMode.REFRESH)
                .getResultStream()
                .findFirst();
    }

    public List<UserEntity> findByStatus(UserEntity.Status status, int offset, int limit) {
        return entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.status=:status ORDER BY u.createdAt DESC", UserEntity.class)
                .setParameter("status", status)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<UserEntity> findBySearch(String search, int offset, int limit) {
        String normalizedSearch = search == null ? "" : search.strip().toLowerCase();
        return entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE lower(u.email) LIKE :search OR lower(u.fullName) LIKE :search ORDER BY u.createdAt DESC",
                        UserEntity.class)
                .setParameter("search", "%" + normalizedSearch + "%")
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public long countActiveUsersWithRole(String roleCode) {
        return entityManager.createQuery(
                        "SELECT COUNT(ur) FROM UserRoleEntity ur JOIN ur.user u WHERE ur.id.roleCode = :roleCode AND u.status = :status",
                        Long.class)
                .setParameter("roleCode", roleCode)
                .setParameter("status", UserEntity.Status.ACTIVE)
                .getSingleResult();
    }

    public void lockActiveAdminInvariant() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended('smartcity360.active-admin', 0))")
                .getSingleResult();
    }

    public void flush() {
        entityManager.flush();
    }
}

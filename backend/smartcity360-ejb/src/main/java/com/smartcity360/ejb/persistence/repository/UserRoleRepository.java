package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.*;
import jakarta.ejb.Stateless;

import java.util.*;

@Stateless
public class UserRoleRepository extends AbstractRepository<UserRoleEntity, UserRoleId> {
    public UserRoleRepository() {
        super(UserRoleEntity.class);
    }

    public List<UserRoleEntity> findByUserId(UUID id) {
        return entityManager.createQuery("SELECT ur FROM UserRoleEntity ur WHERE ur.id.userId=:id ORDER BY ur.id.roleCode", UserRoleEntity.class).setParameter("id", id).getResultList();
    }

    public List<UserRoleEntity> findByRoleCode(String code, int offset, int limit) {
        return entityManager.createQuery("SELECT ur FROM UserRoleEntity ur WHERE ur.id.roleCode=:code ORDER BY ur.assignedAt DESC", UserRoleEntity.class).setParameter("code", code).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public boolean hasRole(UUID userId, String role) {
        return !entityManager.createQuery("SELECT ur.id FROM UserRoleEntity ur WHERE ur.id.userId=:id AND ur.id.roleCode=:role", UserRoleId.class).setParameter("id", userId).setParameter("role", role).setMaxResults(1).getResultList().isEmpty();
    }

    public void removeAllForUser(UUID userId) {
        entityManager.createQuery("DELETE FROM UserRoleEntity ur WHERE ur.id.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}

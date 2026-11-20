package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.RoleEntity;
import jakarta.ejb.Stateless;

import java.util.List;

@Stateless
public class RoleRepository extends AbstractRepository<RoleEntity, String> {
    public RoleRepository() {
        super(RoleEntity.class);
    }

    public List<RoleEntity> findAll() {
        return entityManager.createQuery("SELECT r FROM RoleEntity r ORDER BY r.code", RoleEntity.class).getResultList();
    }
}

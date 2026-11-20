package com.smartcity360.ejb.persistence.repository;

import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.*;

import java.util.List;
import java.util.Optional;

@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public abstract class AbstractRepository<T, ID> {
    @PersistenceContext(unitName = "smartcityPU")
    protected EntityManager entityManager;
    private final Class<T> entityType;

    protected AbstractRepository(Class<T> entityType) {
        this.entityType = entityType;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    public Optional<T> findById(ID id) {
        return Optional.ofNullable(entityManager.find(entityType, id));
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T update(T entity) {
        return entityManager.merge(entity);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void remove(T entity) {
        entityManager.remove(entityManager.contains(entity) ? entity : entityManager.merge(entity));
    }

    public List<T> list(int offset, int limit) {
        if (offset < 0 || limit < 1) throw new IllegalArgumentException("offset must be >= 0 and limit must be > 0");
        return entityManager.createQuery("SELECT e FROM " + entityType.getSimpleName() + " e", entityType).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}

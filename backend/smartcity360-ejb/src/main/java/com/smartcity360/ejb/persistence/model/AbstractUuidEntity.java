package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
public abstract class AbstractUuidEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    protected UUID id;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public final boolean equals(Object other) {
        return this == other || other != null && getClass() == other.getClass()
                && id != null && id.equals(((AbstractUuidEntity) other).id);
    }

    @Override
    public final int hashCode() {
        return id == null ? System.identityHashCode(this) : Objects.hash(getClass(), id);
    }
}

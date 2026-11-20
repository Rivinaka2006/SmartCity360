package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_roles")
public class UserRoleEntity {
    @EmbeddedId
    private UserRoleId id;
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @MapsId("roleCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private RoleEntity role;
    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    public UserRoleId getId() {
        return id;
    }

    public void setId(UserRoleId v) {
        id = v;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity v) {
        user = v;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity v) {
        role = v;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(OffsetDateTime v) {
        assignedAt = v;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof UserRoleEntity e && id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}

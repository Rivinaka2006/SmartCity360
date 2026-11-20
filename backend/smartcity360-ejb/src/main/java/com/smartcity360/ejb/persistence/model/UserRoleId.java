package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserRoleId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "role_code", nullable = false, length = 30)
    private String roleCode;

    public UserRoleId() {
    }

    public UserRoleId(UUID userId, String roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID v) {
        userId = v;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String v) {
        roleCode = v;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof UserRoleId k && Objects.equals(userId, k.userId) && Objects.equals(roleCode, k.roleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleCode);
    }
}

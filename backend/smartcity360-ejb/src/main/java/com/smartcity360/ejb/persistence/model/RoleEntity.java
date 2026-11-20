package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    @Column(name = "code", nullable = false, length = 30)
    private String code;
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        code = v;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String v) {
        displayName = v;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof RoleEntity r && code != null && code.equals(r.code);
    }

    @Override
    public int hashCode() {
        return code == null ? System.identityHashCode(this) : code.hashCode();
    }
}

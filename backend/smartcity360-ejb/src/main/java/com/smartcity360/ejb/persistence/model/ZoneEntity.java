package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "zones")
public class ZoneEntity extends AbstractUuidEntity {
    public enum ZoneType {CITY, DISTRICT, STREET, BUILDING}

    public enum Status {ACTIVE, INACTIVE}

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_zone_id")
    private ZoneEntity parentZone;
    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 12)
    private ZoneType zoneType;
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.ACTIVE;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ZoneEntity getParentZone() {
        return parentZone;
    }

    public void setParentZone(ZoneEntity v) {
        parentZone = v;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public void setZoneType(ZoneType v) {
        zoneType = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        status = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        updatedAt = v;
    }
}

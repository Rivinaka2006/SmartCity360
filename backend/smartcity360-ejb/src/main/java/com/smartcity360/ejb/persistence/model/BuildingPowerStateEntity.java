package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "building_power_states")
public class BuildingPowerStateEntity {
    public enum PowerState {ON, OFF}

    @Id
    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private ZoneEntity zone;
    @Enumerated(EnumType.STRING)
    @Column(name = "power_state", nullable = false, length = 8)
    private PowerState powerState = PowerState.ON;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID v) {
        zoneId = v;
    }

    public ZoneEntity getZone() {
        return zone;
    }

    public void setZone(ZoneEntity v) {
        zone = v;
    }

    public PowerState getPowerState() {
        return powerState;
    }

    public void setPowerState(PowerState v) {
        powerState = v;
    }

    public UserEntity getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UserEntity v) {
        updatedBy = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        updatedAt = v;
    }
}

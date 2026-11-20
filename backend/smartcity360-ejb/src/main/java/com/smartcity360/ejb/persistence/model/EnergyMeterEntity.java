package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "energy_meters")
public class EnergyMeterEntity extends AbstractUuidEntity {
    public enum Status {ACTIVE, INACTIVE}

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_zone_id", nullable = false)
    private ZoneEntity buildingZone;
    @Column(name = "meter_code", nullable = false, unique = true, length = 50)
    private String meterCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.ACTIVE;
    @Column(name = "credential_key_id", length = 128)
    private String credentialKeyId;
    @Column(name = "credential_key_version")
    private Integer credentialKeyVersion;
    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();
    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    public ZoneEntity getBuildingZone() {
        return buildingZone;
    }

    public void setBuildingZone(ZoneEntity v) {
        buildingZone = v;
    }

    public String getMeterCode() {
        return meterCode;
    }

    public void setMeterCode(String v) {
        meterCode = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        status = v;
    }

    public String getCredentialKeyId() {
        return credentialKeyId;
    }

    public void setCredentialKeyId(String v) {
        credentialKeyId = v;
    }

    public Integer getCredentialKeyVersion() {
        return credentialKeyVersion;
    }

    public void setCredentialKeyVersion(Integer v) {
        credentialKeyVersion = v;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(OffsetDateTime v) {
        registeredAt = v;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(OffsetDateTime v) {
        lastSeenAt = v;
    }
}

package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "streetlights")
public class StreetlightEntity extends AbstractUuidEntity {
    public enum OperationalState {ON, OFF, FAULT, MAINTENANCE}

    @Column(name = "asset_tag", nullable = false, unique = true, length = 50)
    private String assetTag;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private LightingProfileEntity profile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "street_zone_id", nullable = false)
    private ZoneEntity streetZone;
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "address_label", length = 255)
    private String addressLabel;
    @Enumerated(EnumType.STRING)
    @Column(name = "operational_state", nullable = false, length = 16)
    private OperationalState operationalState = OperationalState.OFF;
    @Column(name = "brightness_override_pct")
    private Short brightnessOverridePct;
    @Column(name = "installed_on")
    private LocalDate installedOn;
    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String v) {
        assetTag = v;
    }

    public LightingProfileEntity getProfile() {
        return profile;
    }

    public void setProfile(LightingProfileEntity v) {
        profile = v;
    }

    public ZoneEntity getStreetZone() {
        return streetZone;
    }

    public void setStreetZone(ZoneEntity v) {
        streetZone = v;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal v) {
        latitude = v;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal v) {
        longitude = v;
    }

    public String getAddressLabel() {
        return addressLabel;
    }

    public void setAddressLabel(String v) {
        addressLabel = v;
    }

    public OperationalState getOperationalState() {
        return operationalState;
    }

    public void setOperationalState(OperationalState v) {
        operationalState = v;
    }

    public Short getBrightnessOverridePct() {
        return brightnessOverridePct;
    }

    public void setBrightnessOverridePct(Short v) {
        brightnessOverridePct = v;
    }

    public LocalDate getInstalledOn() {
        return installedOn;
    }

    public void setInstalledOn(LocalDate v) {
        installedOn = v;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(OffsetDateTime v) {
        lastSeenAt = v;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(OffsetDateTime v) {
        registeredAt = v;
    }
}

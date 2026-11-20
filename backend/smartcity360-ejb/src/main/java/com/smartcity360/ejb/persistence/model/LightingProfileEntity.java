package com.smartcity360.ejb.persistence.model;

import com.smartcity360.ejb.persistence.converter.JsonbConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lighting_profiles")
public class LightingProfileEntity extends AbstractUuidEntity {
    @Column(name = "profile_code", nullable = false, unique = true, length = 50)
    private String profileCode;
    @Column(name = "manufacturer", nullable = false, length = 80)
    private String manufacturer;
    @Column(name = "model", nullable = false, length = 100)
    private String model;
    @Column(name = "wattage", nullable = false, precision = 8, scale = 2)
    private BigDecimal wattage;
    @Column(name = "default_brightness_pct", nullable = false)
    private short defaultBrightnessPct;
    @Column(name = "controller_type", nullable = false, length = 40)
    private String controllerType;
    @Column(name = "firmware_family", length = 50)
    private String firmwareFamily;
    @Convert(converter = JsonbConverter.class)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson = "{}";
    @Column(name = "profile_version", nullable = false)
    private int profileVersion = 1;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getProfileCode() {
        return profileCode;
    }

    public void setProfileCode(String v) {
        profileCode = v;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String v) {
        manufacturer = v;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String v) {
        model = v;
    }

    public BigDecimal getWattage() {
        return wattage;
    }

    public void setWattage(BigDecimal v) {
        wattage = v;
    }

    public short getDefaultBrightnessPct() {
        return defaultBrightnessPct;
    }

    public void setDefaultBrightnessPct(short v) {
        defaultBrightnessPct = v;
    }

    public String getControllerType() {
        return controllerType;
    }

    public void setControllerType(String v) {
        controllerType = v;
    }

    public String getFirmwareFamily() {
        return firmwareFamily;
    }

    public void setFirmwareFamily(String v) {
        firmwareFamily = v;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String v) {
        configJson = v;
    }

    public int getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(int v) {
        profileVersion = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        active = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }
}

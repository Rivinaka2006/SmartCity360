package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "energy_readings", uniqueConstraints = @UniqueConstraint(columnNames = {"meter_id", "period_start_at", "period_end_at"}))
public class EnergyReadingEntity extends AbstractUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id", nullable = false)
    private EnergyMeterEntity meter;
    @Column(name = "period_start_at", nullable = false)
    private OffsetDateTime periodStartAt;
    @Column(name = "period_end_at", nullable = false)
    private OffsetDateTime periodEndAt;
    @Column(name = "consumption_kwh", nullable = false, precision = 14, scale = 4)
    private BigDecimal consumptionKwh;
    @Column(name = "voltage_v", precision = 10, scale = 2)
    private BigDecimal voltageV;
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    public EnergyMeterEntity getMeter() {
        return meter;
    }

    public void setMeter(EnergyMeterEntity v) {
        meter = v;
    }

    public OffsetDateTime getPeriodStartAt() {
        return periodStartAt;
    }

    public void setPeriodStartAt(OffsetDateTime v) {
        periodStartAt = v;
    }

    public OffsetDateTime getPeriodEndAt() {
        return periodEndAt;
    }

    public void setPeriodEndAt(OffsetDateTime v) {
        periodEndAt = v;
    }

    public BigDecimal getConsumptionKwh() {
        return consumptionKwh;
    }

    public void setConsumptionKwh(BigDecimal v) {
        consumptionKwh = v;
    }

    public BigDecimal getVoltageV() {
        return voltageV;
    }

    public void setVoltageV(BigDecimal v) {
        voltageV = v;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime v) {
        receivedAt = v;
    }
}

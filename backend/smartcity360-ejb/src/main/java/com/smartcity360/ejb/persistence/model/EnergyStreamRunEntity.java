package com.smartcity360.ejb.persistence.model;

import com.smartcity360.ejb.persistence.converter.JsonbConverter;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "energy_stream_runs")
public class EnergyStreamRunEntity extends AbstractUuidEntity {
    public enum Status {SUCCESS, FAILED}

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id", nullable = false)
    private EnergyMeterEntity meter;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private UserEntity initiatedBy;
    @Convert(converter = JsonbConverter.class)
    @Column(name = "decorator_chain", nullable = false, columnDefinition = "jsonb")
    private String decoratorChain = "[]";
    @Column(name = "input_bytes", nullable = false)
    private int inputBytes;
    @Column(name = "output_bytes")
    private Integer outputBytes;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;
    @Column(name = "duration_ms")
    private Integer durationMs;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt = OffsetDateTime.now();

    public EnergyMeterEntity getMeter() {
        return meter;
    }

    public void setMeter(EnergyMeterEntity v) {
        meter = v;
    }

    public UserEntity getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(UserEntity v) {
        initiatedBy = v;
    }

    public String getDecoratorChain() {
        return decoratorChain;
    }

    public void setDecoratorChain(String v) {
        decoratorChain = v;
    }

    public int getInputBytes() {
        return inputBytes;
    }

    public void setInputBytes(int v) {
        inputBytes = v;
    }

    public Integer getOutputBytes() {
        return outputBytes;
    }

    public void setOutputBytes(Integer v) {
        outputBytes = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        status = v;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer v) {
        durationMs = v;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String v) {
        errorCode = v;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime v) {
        executedAt = v;
    }
}

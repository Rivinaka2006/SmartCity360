package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "zone_commands")
public class ZoneCommandEntity extends AbstractUuidEntity {
    public enum Command {SHUTDOWN, RESTORE}

    public enum ExecutionStatus {SUCCEEDED, FAILED}

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_zone_id", nullable = false)
    private ZoneEntity targetZone;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false)
    private UserEntity issuedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "command", nullable = false, length = 12)
    private Command command;
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 12)
    private ExecutionStatus executionStatus;
    @Column(name = "affected_buildings", nullable = false)
    private int affectedBuildings;
    @Column(name = "message", columnDefinition = "text")
    private String message;
    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt = OffsetDateTime.now();

    public ZoneEntity getTargetZone() {
        return targetZone;
    }

    public void setTargetZone(ZoneEntity v) {
        targetZone = v;
    }

    public UserEntity getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(UserEntity v) {
        issuedBy = v;
    }

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command v) {
        command = v;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(ExecutionStatus v) {
        executionStatus = v;
    }

    public int getAffectedBuildings() {
        return affectedBuildings;
    }

    public void setAffectedBuildings(int v) {
        affectedBuildings = v;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String v) {
        message = v;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(OffsetDateTime v) {
        issuedAt = v;
    }
}

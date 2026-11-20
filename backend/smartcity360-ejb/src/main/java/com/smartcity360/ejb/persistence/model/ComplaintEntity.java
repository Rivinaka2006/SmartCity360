package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "complaints")
public class ComplaintEntity extends AbstractUuidEntity {
    public enum Severity {LOW, MEDIUM, HIGH, CRITICAL}

    public enum Status {OPEN, IN_PROGRESS, ESCALATED, RESOLVED, REJECTED}

    public enum HandlerLevel {LOCAL_STAFF, SUPERVISOR, CITY_MANAGER}

    @Column(name = "reference_no", nullable = false, unique = true, length = 32)
    private String referenceNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private UserEntity resident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private ZoneEntity zone;
    @Column(name = "title", nullable = false, length = 180)
    private String title;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 12)
    private Severity severity;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.OPEN;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private UserEntity assignedToUser;
    @Enumerated(EnumType.STRING)
    @Column(name = "current_handler_level", length = 16)
    private HandlerLevel currentHandlerLevel;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String v) {
        referenceNo = v;
    }

    public UserEntity getResident() {
        return resident;
    }

    public void setResident(UserEntity v) {
        resident = v;
    }

    public ZoneEntity getZone() {
        return zone;
    }

    public void setZone(ZoneEntity v) {
        zone = v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        title = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity v) {
        severity = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        status = v;
    }

    public UserEntity getAssignedToUser() {
        return assignedToUser;
    }

    public void setAssignedToUser(UserEntity v) {
        assignedToUser = v;
    }

    public HandlerLevel getCurrentHandlerLevel() {
        return currentHandlerLevel;
    }

    public void setCurrentHandlerLevel(HandlerLevel v) {
        currentHandlerLevel = v;
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

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime v) {
        resolvedAt = v;
    }
}

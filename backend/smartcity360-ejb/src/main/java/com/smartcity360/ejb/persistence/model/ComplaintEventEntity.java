package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "complaint_events")
public class ComplaintEventEntity extends AbstractUuidEntity {
    public enum Action {SUBMITTED, ASSIGNED, ESCALATED, COMMENTED, RESOLVED, REOPENED, REJECTED}

    public enum HandlerLevel {LOCAL_STAFF, SUPERVISOR, CITY_MANAGER}

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private ComplaintEntity complaint;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserEntity actorUser;
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private Action action;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_level", length = 16)
    private HandlerLevel fromLevel;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_level", length = 16)
    private HandlerLevel toLevel;
    @Column(name = "note", columnDefinition = "text")
    private String note;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public ComplaintEntity getComplaint() {
        return complaint;
    }

    public void setComplaint(ComplaintEntity v) {
        complaint = v;
    }

    public UserEntity getActorUser() {
        return actorUser;
    }

    public void setActorUser(UserEntity v) {
        actorUser = v;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action v) {
        action = v;
    }

    public HandlerLevel getFromLevel() {
        return fromLevel;
    }

    public void setFromLevel(HandlerLevel v) {
        fromLevel = v;
    }

    public HandlerLevel getToLevel() {
        return toLevel;
    }

    public void setToLevel(HandlerLevel v) {
        toLevel = v;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String v) {
        note = v;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime v) {
        occurredAt = v;
    }
}

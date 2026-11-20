package com.smartcity360.ejb.persistence.model;

import com.smartcity360.ejb.persistence.converter.JsonbConverter;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity extends AbstractUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserEntity actorUser;
    @Column(name = "action", nullable = false, length = 80)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    @Column(name = "entity_id", columnDefinition = "uuid")
    private UUID entityId;
    @Convert(converter = JsonbConverter.class)
    @Column(name = "details_json", nullable = false, columnDefinition = "jsonb")
    private String detailsJson = "{}";
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public UserEntity getActorUser() {
        return actorUser;
    }

    public void setActorUser(UserEntity v) {
        actorUser = v;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String v) {
        action = v;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String v) {
        entityType = v;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID v) {
        entityId = v;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String v) {
        detailsJson = v;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime v) {
        occurredAt = v;
    }
}

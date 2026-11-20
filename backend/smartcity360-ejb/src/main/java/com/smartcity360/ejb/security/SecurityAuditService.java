package com.smartcity360.ejb.security;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.OffsetDateTime;
import java.util.UUID;

@Stateless
public class SecurityAuditService {
    @PersistenceContext(unitName = "smartcityPU")
    private EntityManager entityManager;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void record(String action, UUID actorId, String outcome, String subjectHash) {
        String details = "{\"outcome\":\"" + json(outcome) + "\""
                + (subjectHash == null ? "" : ",\"subjectHash\":\"" + json(subjectHash) + "\"") + "}";

        // EclipseLink binds a null UUID as VARCHAR with PostgreSQL. Explicit casts keep nullable
        // security-audit fields type-safe without changing the datasource or database schema.
        entityManager.createNativeQuery("""
                        INSERT INTO audit_logs
                            (id, actor_user_id, action, entity_type, entity_id, details_json, occurred_at)
                        VALUES
                            (CAST(?1 AS uuid), CAST(?2 AS uuid), ?3, ?4, CAST(?5 AS uuid),
                             CAST(?6 AS jsonb), ?7)
                        """)
                .setParameter(1, UUID.randomUUID().toString())
                .setParameter(2, actorId == null ? null : actorId.toString())
                .setParameter(3, action)
                .setParameter(4, "SECURITY")
                .setParameter(5, actorId == null ? null : actorId.toString())
                .setParameter(6, details)
                .setParameter(7, OffsetDateTime.now())
                .executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void recordApplicationEvent(String action, UUID actorId, String entityType, UUID entityId,
                                       String detailsJson) {
        entityManager.createNativeQuery("""
                        INSERT INTO audit_logs
                            (id, actor_user_id, action, entity_type, entity_id, details_json, occurred_at)
                        VALUES
                            (CAST(?1 AS uuid), CAST(?2 AS uuid), ?3, ?4, CAST(?5 AS uuid),
                             CAST(?6 AS jsonb), ?7)
                        """)
                .setParameter(1, UUID.randomUUID().toString())
                .setParameter(2, actorId == null ? null : actorId.toString())
                .setParameter(3, action)
                .setParameter(4, entityType)
                .setParameter(5, entityId == null ? null : entityId.toString())
                .setParameter(6, detailsJson == null ? "{}" : detailsJson)
                .setParameter(7, OffsetDateTime.now())
                .executeUpdate();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

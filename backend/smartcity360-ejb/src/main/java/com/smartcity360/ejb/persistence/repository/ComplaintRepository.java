package com.smartcity360.ejb.persistence.repository;

import com.smartcity360.ejb.persistence.model.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.LockModeType;

import java.util.*;

@Stateless
public class ComplaintRepository extends AbstractRepository<ComplaintEntity, UUID> {
    public ComplaintRepository() {
        super(ComplaintEntity.class);
    }

    public Optional<ComplaintEntity> findByReferenceNo(String ref) {
        return entityManager.createQuery("SELECT c FROM ComplaintEntity c WHERE c.referenceNo=:ref", ComplaintEntity.class).setParameter("ref", ref).getResultStream().findFirst();
    }

    public ComplaintEntity insertComplaint(ComplaintEntity c) {
        if (c.getId() == null) c.setId(UUID.randomUUID());
        entityManager.createNativeQuery("INSERT INTO complaints(id,reference_no,resident_id,zone_id,title,description,severity,status,assigned_to_user_id,current_handler_level,created_at,updated_at,resolved_at) VALUES(CAST(?1 AS uuid),?2,CAST(?3 AS uuid),CAST(?4 AS uuid),?5,?6,?7,?8,CAST(?9 AS uuid),?10,?11,?12,CAST(?13 AS timestamptz))").setParameter(1, c.getId().toString()).setParameter(2, c.getReferenceNo()).setParameter(3, c.getResident().getId().toString()).setParameter(4, c.getZone().getId().toString()).setParameter(5, c.getTitle()).setParameter(6, c.getDescription()).setParameter(7, c.getSeverity().name()).setParameter(8, c.getStatus().name()).setParameter(9, c.getAssignedToUser() == null ? null : c.getAssignedToUser().getId().toString()).setParameter(10, c.getCurrentHandlerLevel() == null ? null : c.getCurrentHandlerLevel().name()).setParameter(11, c.getCreatedAt()).setParameter(12, c.getUpdatedAt()).setParameter(13, c.getResolvedAt()).executeUpdate();
        return c;
    }

    public Optional<ComplaintEntity> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(ComplaintEntity.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public List<ComplaintEntity> findByResident(UUID id, int offset, int limit) {
        return entityManager.createQuery("SELECT c FROM ComplaintEntity c WHERE c.resident.id=:id ORDER BY c.createdAt DESC,c.id", ComplaintEntity.class).setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<ComplaintEntity> findQueue(Collection<ComplaintEntity.HandlerLevel> levels, UUID assignee, int offset, int limit) {
        if (levels.isEmpty()) return List.of();
        return entityManager.createQuery("SELECT c FROM ComplaintEntity c WHERE c.currentHandlerLevel IN :levels AND c.status NOT IN :terminal AND (c.assignedToUser IS NULL OR c.assignedToUser.id=:actor) ORDER BY c.createdAt,c.id", ComplaintEntity.class).setParameter("levels", levels).setParameter("terminal", List.of(ComplaintEntity.Status.RESOLVED, ComplaintEntity.Status.REJECTED)).setParameter("actor", assignee).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<ComplaintEntity> findAllFiltered(ComplaintEntity.Status status, ComplaintEntity.Severity severity, int offset, int limit) {
        String q = "SELECT c FROM ComplaintEntity c WHERE 1=1" + (status == null ? "" : " AND c.status=:status") + (severity == null ? "" : " AND c.severity=:severity") + " ORDER BY c.createdAt DESC,c.id";
        var query = entityManager.createQuery(q, ComplaintEntity.class);
        if (status != null) query.setParameter("status", status);
        if (severity != null) query.setParameter("severity", severity);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<ComplaintEntity> findByAssigneeAndStatus(UUID userId, ComplaintEntity.Status status, int offset, int limit) {
        return entityManager.createQuery("SELECT c FROM ComplaintEntity c WHERE c.assignedToUser.id=:id AND c.status=:status ORDER BY c.createdAt DESC", ComplaintEntity.class).setParameter("id", userId).setParameter("status", status).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public List<Object[]> countByStatus() {
        return entityManager.createQuery("SELECT c.status, count(c) FROM ComplaintEntity c GROUP BY c.status", Object[].class)
                .getResultList();
    }
}

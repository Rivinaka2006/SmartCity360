package com.smartcity360.ejb.complaints;

import com.smartcity360.common.complaints.*;
import com.smartcity360.ejb.complaints.domain.chain.*;
import com.smartcity360.ejb.persistence.model.*;
import com.smartcity360.ejb.persistence.repository.*;
import com.smartcity360.ejb.security.SecurityAuditService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.*;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.*;

@Stateless
@DeclareRoles({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class ComplaintServiceBean implements ComplaintService {
    private static final Set<ComplaintEntity.Status> TERMINAL =
            EnumSet.of(ComplaintEntity.Status.RESOLVED, ComplaintEntity.Status.REJECTED);

    @Inject
    ComplaintRepository complaints;
    @Inject
    ComplaintEventRepository events;
    @Inject
    UserRepository users;
    @Inject
    UserRoleRepository userRoles;
    @Inject
    ZoneRepository zones;
    @Inject
    SecurityAuditService audit;
    @Resource
    SessionContext session;

    @Override
    @RolesAllowed("RESIDENT")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ComplaintView submit(ComplaintSubmitCommand command) {
        if (command == null) throw error("REQUEST_INVALID");
        UserEntity resident = actor();
        requireRole(resident, "RESIDENT");
        String title = text(command.title(), 180, "TITLE_INVALID");
        String description = text(command.description(), 4000, "DESCRIPTION_INVALID");
        ComplaintSeverity severity = severity(command.severity());
        if (command.zoneId() == null) throw error("ZONE_REQUIRED");
        ZoneEntity zone = zones.findById(command.zoneId()).orElseThrow(() -> error("ZONE_NOT_FOUND"));
        if (zone.getStatus() != ZoneEntity.Status.ACTIVE) throw error("ZONE_INACTIVE");

        RoutingDecision route = new ComplaintChainFactory().standardChain()
                .route(ComplaintRoutingContext.initial(severity));
        if (!route.handled()) throw error("NO_HANDLER_AVAILABLE");
        OffsetDateTime now = OffsetDateTime.now();
        ComplaintEntity entity = new ComplaintEntity();
        entity.setId(UUID.randomUUID());
        entity.setReferenceNo(reference());
        entity.setResident(resident);
        entity.setZone(zone);
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setSeverity(ComplaintEntity.Severity.valueOf(severity.name()));
        entity.setStatus(ComplaintEntity.Status.OPEN);
        entity.setCurrentHandlerLevel(level(route.selectedLevel()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        complaints.insertComplaint(entity);
        ComplaintEntity managed = complaints.findById(entity.getId()).orElseThrow(() -> error("COMPLAINT_PERSIST_FAILED"));
        addEvent(managed, resident, ComplaintEventEntity.Action.SUBMITTED, null,
                managed.getCurrentHandlerLevel(), routingNote(null, route), now);
        audit.recordApplicationEvent("COMPLAINT_SUBMITTED", resident.getId(), "COMPLAINT", managed.getId(),
                "{\"status\":\"SUCCESS\",\"level\":\"" + managed.getCurrentHandlerLevel() + "\"}");
        return view(managed);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
    public ComplaintView get(UUID id) {
        UserEntity actor = actor();
        ComplaintEntity complaint = requireComplaint(id);
        requireReadable(actor, complaint);
        return view(complaint);
    }

    @Override
    @RolesAllowed("RESIDENT")
    public List<ComplaintView> my(int offset, int limit) {
        page(offset, limit);
        UserEntity actor = actor();
        requireRole(actor, "RESIDENT");
        return complaints.findByResident(actor.getId(), offset, limit).stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed("ADMIN")
    public List<ComplaintView> list(String status, String severity, int offset, int limit) {
        page(offset, limit);
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        return complaints.findAllFiltered(status(status), severityEntity(severity), offset, limit)
                .stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<ComplaintView> queue(int offset, int limit) {
        page(offset, limit);
        UserEntity actor = actor();
        Set<ComplaintEntity.HandlerLevel> levels = currentLevels(actor);
        if (levels.isEmpty() && !hasRole(actor, "ADMIN")) throw error("FORBIDDEN");
        if (hasRole(actor, "ADMIN")) levels = EnumSet.allOf(ComplaintEntity.HandlerLevel.class);
        return complaints.findQueue(levels, actor.getId(), offset, limit).stream().map(this::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
    public List<ComplaintEventView> history(UUID id, int offset, int limit) {
        page(offset, limit);
        UserEntity actor = actor();
        ComplaintEntity complaint = requireComplaint(id);
        requireReadable(actor, complaint);
        return events.findByComplaint(id, offset, limit).stream().map(this::eventView).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ComplaintView assign(UUID id, AssignmentCommand command) {
        if (command == null) throw error("REQUEST_INVALID");
        UserEntity actor = actor();
        ComplaintEntity complaint = lock(id);
        requireOpen(complaint);
        requireCurrentLevelRole(actor, complaint);
        UUID assigneeId = command.assigneeId() == null ? actor.getId() : command.assigneeId();
        if (!hasRole(actor, "ADMIN") && !actor.getId().equals(assigneeId)) throw error("FORBIDDEN");
        UserEntity assignee = users.findById(assigneeId).orElseThrow(() -> error("ASSIGNEE_NOT_FOUND"));
        if (assignee.getStatus() != UserEntity.Status.ACTIVE) throw error("ASSIGNEE_INACTIVE");
        if (!hasRole(assignee, complaint.getCurrentHandlerLevel().name())) throw error("ASSIGNEE_ROLE_INVALID");
        if (complaint.getAssignedToUser() != null) {
            if (complaint.getAssignedToUser().getId().equals(assigneeId)) return view(complaint);
            throw error("COMPLAINT_ALREADY_ASSIGNED");
        }
        OffsetDateTime now = OffsetDateTime.now();
        complaint.setAssignedToUser(assignee);
        complaint.setUpdatedAt(now);
        addEvent(complaint, actor, ComplaintEventEntity.Action.ASSIGNED, complaint.getCurrentHandlerLevel(),
                complaint.getCurrentHandlerLevel(), "ASSIGNED_TO:" + assignee.getId(), now);
        audit(complaint, actor, "COMPLAINT_ASSIGNED");
        return view(complaint);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ComplaintView start(UUID id) {
        UserEntity actor = actor();
        ComplaintEntity complaint = lock(id);
        requireOpen(complaint);
        requireCurrentLevelAccess(actor, complaint, true);
        if (complaint.getStatus() == ComplaintEntity.Status.IN_PROGRESS) return view(complaint);
        OffsetDateTime now = OffsetDateTime.now();
        complaint.setStatus(ComplaintEntity.Status.IN_PROGRESS);
        complaint.setUpdatedAt(now);
        addEvent(complaint, actor, ComplaintEventEntity.Action.COMMENTED, complaint.getCurrentHandlerLevel(),
                complaint.getCurrentHandlerLevel(), "STATUS:IN_PROGRESS", now);
        audit(complaint, actor, "COMPLAINT_STARTED");
        return view(complaint);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ComplaintView escalate(UUID id, EscalationCommand command) {
        if (command == null) throw error("REQUEST_INVALID");
        UserEntity actor = actor();
        ComplaintEntity complaint = lock(id);
        requireOpen(complaint);
        ComplaintEntity.HandlerLevel expected = handlerLevel(command.expectedLevel());
        if (expected != complaint.getCurrentHandlerLevel()) throw error("STALE_COMPLAINT_STATE");
        requireCurrentLevelAccess(actor, complaint, true);
        String reason = text(command.reason(), 1000, "ESCALATION_REASON_INVALID");
        HandlerLevel current = HandlerLevel.valueOf(complaint.getCurrentHandlerLevel().name());
        if (current == HandlerLevel.CITY_MANAGER) throw error("ESCALATION_LIMIT_REACHED");
        ComplaintSeverity complaintSeverity = ComplaintSeverity.valueOf(complaint.getSeverity().name());
        RoutingDecision route = new ComplaintChainFactory().standardChain()
                .route(ComplaintRoutingContext.escalating(complaintSeverity, current.next()));
        if (!route.handled()) throw error("NO_HANDLER_AVAILABLE");
        ComplaintEntity.HandlerLevel next = level(route.selectedLevel());
        OffsetDateTime now = OffsetDateTime.now();
        ComplaintEntity.HandlerLevel previous = complaint.getCurrentHandlerLevel();
        complaint.setAssignedToUser(null);
        complaint.setCurrentHandlerLevel(next);
        complaint.setStatus(ComplaintEntity.Status.ESCALATED);
        complaint.setUpdatedAt(now);
        addEvent(complaint, actor, ComplaintEventEntity.Action.ESCALATED, previous, next,
                routingNote(reason, route), now);
        audit(complaint, actor, "COMPLAINT_ESCALATED");
        return view(complaint);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ComplaintView resolve(UUID id, ResolutionCommand command) {
        if (command == null) throw error("REQUEST_INVALID");
        UserEntity actor = actor();
        ComplaintEntity complaint = lock(id);
        requireOpen(complaint);
        requireCurrentLevelAccess(actor, complaint, true);
        if (complaint.getStatus() != ComplaintEntity.Status.IN_PROGRESS) throw error("INVALID_STATUS_TRANSITION");
        String note = text(command.note(), 2000, "RESOLUTION_NOTE_INVALID");
        OffsetDateTime now = OffsetDateTime.now();
        complaint.setStatus(ComplaintEntity.Status.RESOLVED);
        complaint.setResolvedAt(now);
        complaint.setUpdatedAt(now);
        addEvent(complaint, actor, ComplaintEventEntity.Action.RESOLVED, complaint.getCurrentHandlerLevel(),
                complaint.getCurrentHandlerLevel(), note, now);
        audit(complaint, actor, "COMPLAINT_RESOLVED");
        return view(complaint);
    }

    private ComplaintEntity lock(UUID id) {
        if (id == null) throw error("COMPLAINT_NOT_FOUND");
        return complaints.findByIdForUpdate(id).orElseThrow(() -> error("COMPLAINT_NOT_FOUND"));
    }

    private ComplaintEntity requireComplaint(UUID id) {
        if (id == null) throw error("COMPLAINT_NOT_FOUND");
        return complaints.findById(id).orElseThrow(() -> error("COMPLAINT_NOT_FOUND"));
    }

    private UserEntity actor() {
        String principal = session.getCallerPrincipal().getName();
        UserEntity user = users.findByEmail(principal).orElseThrow(() -> error("CALLER_NOT_FOUND"));
        if (user.getStatus() != UserEntity.Status.ACTIVE) throw error("CALLER_INACTIVE");
        return user;
    }

    private void requireReadable(UserEntity actor, ComplaintEntity complaint) {
        if (hasRole(actor, "ADMIN")) return;
        if (hasRole(actor, "RESIDENT") && complaint.getResident().getId().equals(actor.getId())) return;
        if (currentLevels(actor).contains(complaint.getCurrentHandlerLevel())
                && (complaint.getAssignedToUser() == null || complaint.getAssignedToUser().getId().equals(actor.getId())))
            return;
        // Ownership failures are intentionally indistinguishable from missing complaints.
        throw error("COMPLAINT_NOT_FOUND");
    }

    private void requireCurrentLevelAccess(UserEntity actor, ComplaintEntity complaint, boolean requireAssignment) {
        if (hasRole(actor, "ADMIN")) {
            if (requireAssignment && complaint.getAssignedToUser() == null)
                throw error("COMPLAINT_UNASSIGNED");
            return;
        }
        if (!hasRole(actor, complaint.getCurrentHandlerLevel().name())) throw error("FORBIDDEN");
        if (requireAssignment && (complaint.getAssignedToUser() == null
                || !complaint.getAssignedToUser().getId().equals(actor.getId())))
            throw error("COMPLAINT_NOT_ASSIGNED_TO_CALLER");
        if (!requireAssignment && complaint.getAssignedToUser() != null
                && !complaint.getAssignedToUser().getId().equals(actor.getId())) throw error("FORBIDDEN");
    }

    private void requireCurrentLevelRole(UserEntity actor, ComplaintEntity complaint) {
        if (!hasRole(actor, "ADMIN") && !hasRole(actor, complaint.getCurrentHandlerLevel().name()))
            throw error("FORBIDDEN");
    }

    private void requireOpen(ComplaintEntity complaint) {
        if (TERMINAL.contains(complaint.getStatus())) throw error("COMPLAINT_TERMINAL");
    }

    private void addEvent(ComplaintEntity complaint, UserEntity actor, ComplaintEventEntity.Action action,
                          ComplaintEntity.HandlerLevel from, ComplaintEntity.HandlerLevel to,
                          String note, OffsetDateTime when) {
        ComplaintEventEntity event = new ComplaintEventEntity();
        event.setComplaint(complaint);
        event.setActorUser(actor);
        event.setAction(action);
        event.setFromLevel(from == null ? null : ComplaintEventEntity.HandlerLevel.valueOf(from.name()));
        event.setToLevel(to == null ? null : ComplaintEventEntity.HandlerLevel.valueOf(to.name()));
        event.setNote(note);
        event.setOccurredAt(when);
        events.persist(event);
    }

    private void audit(ComplaintEntity complaint, UserEntity actor, String action) {
        audit.recordApplicationEvent(action, actor.getId(), "COMPLAINT", complaint.getId(),
                "{\"status\":\"SUCCESS\",\"level\":\"" + complaint.getCurrentHandlerLevel() + "\"}");
    }

    private boolean hasRole(UserEntity user, String role) {
        return userRoles.hasRole(user.getId(), role);
    }

    private void requireRole(UserEntity user, String role) {
        if (!hasRole(user, role)) throw error("FORBIDDEN");
    }

    private Set<ComplaintEntity.HandlerLevel> currentLevels(UserEntity user) {
        EnumSet<ComplaintEntity.HandlerLevel> result = EnumSet.noneOf(ComplaintEntity.HandlerLevel.class);
        for (ComplaintEntity.HandlerLevel level : ComplaintEntity.HandlerLevel.values())
            if (hasRole(user, level.name())) result.add(level);
        return result;
    }

    private ComplaintView view(ComplaintEntity c) {
        ZoneEntity zone = c.getZone();
        UserEntity assignedUser = c.getAssignedToUser();
        return new ComplaintView(
                c.getId(),
                c.getReferenceNo(),
                c.getResident().getId(),
                zone.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getSeverity().name(),
                c.getStatus().name(),
                assignedUser == null ? null : assignedUser.getId(),
                c.getCurrentHandlerLevel() == null ? null : c.getCurrentHandlerLevel().name(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getResolvedAt(),
                zone.getName(),
                zone.getCode(),
                assignedUser == null ? null : assignedUser.getFullName());
    }

    private ComplaintEventView eventView(ComplaintEventEntity e) {
        return new ComplaintEventView(e.getId(), e.getComplaint().getId(),
                e.getActorUser() == null ? null : e.getActorUser().getId(), e.getAction().name(),
                e.getFromLevel() == null ? null : e.getFromLevel().name(),
                e.getToLevel() == null ? null : e.getToLevel().name(), e.getNote(), e.getOccurredAt());
    }

    private static ComplaintEntity.Severity severityEntity(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ComplaintEntity.Severity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw error("SEVERITY_INVALID");
        }
    }

    private static ComplaintSeverity severity(String value) {
        if (value == null) throw error("SEVERITY_INVALID");
        try {
            return ComplaintSeverity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw error("SEVERITY_INVALID");
        }
    }

    private static ComplaintEntity.Status status(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ComplaintEntity.Status.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw error("STATUS_INVALID");
        }
    }

    private static ComplaintEntity.HandlerLevel handlerLevel(String value) {
        if (value == null) throw error("HANDLER_LEVEL_INVALID");
        try {
            return ComplaintEntity.HandlerLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw error("HANDLER_LEVEL_INVALID");
        }
    }

    private static ComplaintEntity.HandlerLevel level(HandlerLevel level) {
        return ComplaintEntity.HandlerLevel.valueOf(level.name());
    }

    private static String text(String value, int max, String code) {
        if (value == null || value.isBlank()) throw error(code);
        String stripped = value.strip();
        if (stripped.length() > max) throw error(code);
        return stripped;
    }

    private static void page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw error("PAGINATION_INVALID");
    }

    private static String reference() {
        return "SC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 29).toUpperCase(Locale.ROOT);
    }

    private static String routingNote(String prefix, RoutingDecision route) {
        String trace = route.traversedLevels().stream().map(Enum::name)
                .reduce((left, right) -> left + "->" + right).orElse("");
        return (prefix == null || prefix.isBlank() ? "" : prefix + " | ") + "ROUTED:" + trace;
    }

    private static ComplaintApplicationException error(String code) {
        return new ComplaintApplicationException(code);
    }
}

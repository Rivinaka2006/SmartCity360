# Phase 4.3 Complaint Chain Design

## Intent and selected approach

Use a pure Java Chain of Responsibility for severity routing and manual escalation, with a stateless EJB coordinating authorization, pessimistic locking, JPA persistence, events, and audit. SQL-only routing and a single service conditional were rejected because neither demonstrates independent linked handlers.

## Routing

Every route enters `LocalStaffHandler -> SupervisorHandler -> CityManagerHandler`. Severity selects LOW/MEDIUM -> LOCAL_STAFF, HIGH -> SUPERVISOR, CRITICAL -> CITY_MANAGER. Manual escalation supplies a minimum next level, so the same chain forwards past earlier handlers. Decisions include the selected level and traversal trace. An incomplete chain returns an explicit unhandled decision; cycles are rejected while linking.

## Workflow

- Submission is RESIDENT-only, derives the resident from the principal, creates OPEN/unassigned complaint, routes it, and records SUBMITTED atomically.
- Assignment never chooses an arbitrary user. Staff may self-assign at their current queue level; ADMIN may select an active user holding the matching role.
- Start requires assignment and changes OPEN/ESCALATED to IN_PROGRESS. The existing schema has no STARTED event action, so COMMENTED with safe note `STATUS:IN_PROGRESS` records it.
- Escalation advances exactly LOCAL_STAFF -> SUPERVISOR -> CITY_MANAGER, clears the old assignee, sets ESCALATED, and records from/to levels. Clients supply expected current level to reject stale or duplicate concurrent requests.
- Resolution requires IN_PROGRESS and current-level authority, sets RESOLVED/resolved_at, and records RESOLVED. Terminal duplicates are rejected.

## Authorization and concurrency

Residents can access only their own complaints. Staff can access assigned complaints or their current-level queue. ADMIN has oversight. Every mutation locks the complaint row with `PESSIMISTIC_WRITE`; state and event commit in one REQUIRED JTA transaction. Audit application events join that transaction. No failed-success history is written.

## Constraints and non-goals

No migration, new status/action, fake user, automatic assignee, React, Flyweight, or Interpreter work. Descriptions stay in complaint records and are never copied into generic audit details.

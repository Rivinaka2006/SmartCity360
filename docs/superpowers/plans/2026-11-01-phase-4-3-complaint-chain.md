# Phase 4.3 Complaint Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Goal:** Deliver authenticated complaint submission, Chain-of-Responsibility routing, controlled assignment/escalation/status changes, traceable history, and runtime verification without a migration.

**Spec:** `docs/superpowers/specs/2026-11-01-phase-4-3-complaint-chain-design.md`

## Review focus

- Every route enters the first handler and produces an observable delegation trace.
- Concurrent/stale escalation cannot advance two levels accidentally.
- Residents cannot infer or read another resident’s complaint.
- Assignment validates active user and current-level role.
- Business events and state changes share one transaction.

### Task 1: Pure routing chain

- Write failing tests for all severities, delegation, manual next-level routing, unhandled requests, dynamic construction, and cycle rejection.
- Implement handler contract, base handler, three handlers, routing context/decision, enums, and chain factory.
- Run focused tests.

### Task 2: Persistence queries and common contracts

- Add failing contract tests for DTO-only service signatures, role/transaction annotations, locking, pagination, queue and history ordering.
- Extend complaint/event/user-role repositories and create common complaint DTOs/service contract.
- Run repository and contract tests.

### Task 3: Transactional workflow service

- Implement submission, access policy, assignment, start, escalation, resolution, lists, queue, ownership and same-transaction audit.
- Use native UUID casts where EclipseLink nullable UUID binding requires it.
- Verify state-transition and authorization contracts.

### Task 4: REST boundary

- Add failing route/role/CSRF tests.
- Implement non-ambiguous `/my`, `/queue`, `/{id}`, history and mutation endpoints plus safe exception mapping.
- Run web tests.

### Task 5: Live integration and regressions

- Use disposable RESIDENT, LOCAL_STAFF, SUPERVISOR, CITY_MANAGER and ADMIN users, zone and complaints.
- Verify routing for four severities, ownership, queues, assignment, start, multi-level escalation, stale duplicate rejection, resolution, event order, audit, CSRF and concurrent behavior.
- Clean fixtures, verify V001-V005 baseline, run clean package, deploy, health, Decorator, Composite and security regressions, and inspect logs.

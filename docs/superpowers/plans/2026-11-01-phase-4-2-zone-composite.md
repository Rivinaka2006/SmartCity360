# Phase 4.2 Zone Composite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Goal:** Deliver secured hierarchical zone CRUD, recursive power control, usage reporting, and runtime verification through an authentic Composite Pattern.

**Architecture:** Pure EJB-domain Composite assembled from one persisted subtree; common EAR contracts; stateless EJB transaction boundary; JAX-RS adapter; no migration.

**Tech Stack:** Java 17, Jakarta EE 10, JPA 3.1/EclipseLink, PostgreSQL 18, Payara 6, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-01-phase-4-2-zone-composite-design.md`

## Global constraints

Preserve V001-V005, EAR layout, security, CSRF, audit, and Decorator implementation. Do not stage or commit. Every production behavior follows RED -> GREEN.

## Review focus

- Missing power-state rows behave as logical ON and do not inflate changed counts.
- Overlapping readings for one meter are rejected rather than double-counted.
- Competing power commands lock the same buildings in stable UUID order.
- Corrupted/cyclic subtree data cannot recurse forever.
- State-changing zone REST calls cannot bypass existing CSRF/session controls.

### Task 1: Pure Composite domain

**Files:** Create `zones/domain/composite/*`; test `ZoneCompositeTest`.

**Produces:** `ZoneComponent`, `CompositeZone`, `BuildingLeaf`, `PowerOperationContext`, `PowerOperationResult`, `UsageQuery`, `UsageReport`.

- [ ] Write failing polymorphism, nested delegation, idempotence, empty-composite, aggregation, invalid-range, and overlap tests.
- [ ] Run focused tests and confirm missing-symbol/behavior failures.
- [ ] Implement the minimal framework-independent model and rerun focused tests.

### Task 2: Persistence assembly and query foundation

**Files:** Create `ZoneTreeAssembler`; modify zone, power-state, energy-reading, and command repositories; test assembler/query contracts.

**Consumes:** Task 1 domain types. **Produces:** one-query subtree assembly and deterministic locking/query methods.

- [ ] Add failing assembler tests for ordering, empty nodes, bad parent/type, and cycles.
- [ ] Implement one recursive subtree query and validated iterative assembly.
- [ ] Add repository methods for ordered locks, bulk readings, power states, descendants, and command pagination.
- [ ] Run focused and repository contract tests.

### Task 3: Common contracts and transactional service

**Files:** Create `common/zones/*`, `ZoneServiceBean`; modify `SecurityAuditService`.

**Consumes:** Tasks 1-2. **Produces:** local EJB service contract and safe DTOs.

- [ ] Add failing tests for role annotations, transaction attributes, validation, changed counts, usage rules, and no entity leakage.
- [ ] Implement CRUD, conservative deactivation, power transactions, usage, history, and same-transaction audit.
- [ ] Run focused service tests and full EJB tests.

### Task 4: REST boundary

**Files:** Create `ZoneResource`; test `ZoneResourceContractTest`.

**Consumes:** Task 3 common service. **Produces:** `/api/v1/zones` endpoints.

- [ ] Add failing tests for route/role contracts, DTO-only responses, pagination, error mapping, and CSRF non-exemption.
- [ ] Implement resource and exception mapping without changing CSRF exemptions.
- [ ] Run web tests.

### Task 5: PostgreSQL/Payara integration

**Files:** Create `database/tests/Invoke-ZoneCompositeIntegrationTests.ps1`.

- [ ] Build disposable hierarchy, users/roles, meters/readings through a cleanup-safe harness.
- [ ] Verify tree, shutdown, repeat shutdown, restore, persisted state/history, usage aggregation, overlap rejection, forbidden access, CSRF, rollback, and concurrent serialization.
- [ ] Clean fixtures and prove migration baseline reconciliation.
- [ ] Run reactor clean/package, deploy EAR, health and Decorator/security regressions, inspect logs and EAR contents.

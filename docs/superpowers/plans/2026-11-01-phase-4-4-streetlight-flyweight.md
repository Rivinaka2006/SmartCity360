# Phase 4.4 Streetlight Flyweight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure, transactional smart-streetlight management backed by a bounded Java Flyweight cache and measured at 1K, 10K and 50K contexts.

**Architecture:** Pure immutable Flyweight/context classes live in the EJB domain package; an application-scoped bounded factory shares intrinsic profiles. A stateless EJB coordinates repositories, locking, authorization and audit, while the WAR exposes DTO-only REST resources.

**Tech Stack:** Java 17, Jakarta EE 10, EJB/JTA, EclipseLink/JPA, PostgreSQL 18.3, JUnit 5, JOL test tooling.

**Spec:** `docs/superpowers/specs/2026-11-01-phase-4-4-streetlight-flyweight-design.md`

## Global Constraints

- Preserve the existing EAR modules, migrations V001–V005, Jakarta Security, CSRF and prior pattern implementations.
- Do not mutate profile intrinsic fields in place or cache JPA entities.
- Do not stage or commit existing user work.
- No new migration, hardware integration, React, Spring, Redis, or benchmark REST endpoint.

## Review Focus

- Concurrent same-key resolution must return one identical object and count one miss.
- Eviction/invalidation must never return stale profile configuration.
- Null coordinates, bounds, inactive profiles/zones, FAULT/MAINTENANCE, and no-op commands must be controlled.
- Reassignment and commands must lock one streetlight and avoid lost updates.
- Benchmark fixtures must remain in memory and use identical logical inputs.

---

### Task 1: Pure Flyweight domain and bounded factory

**Files:** Create `streetlights/domain/flyweight/*.java`; test `StreetlightFlyweightTest.java` and `LightingProfileFactoryTest.java`.

**Interfaces:** Produces immutable `ProfileKey`, `SharedLightingProfile`, `StreetlightContext`, commands/results, statistics, and `LightingProfileFactory.resolve(ProfileKey,Supplier)`.

- [ ] Write identity, version isolation, immutability, state isolation, command, validation, concurrency, failed-load, eviction and invalidation tests.
- [ ] Run focused tests and confirm missing-class RED.
- [ ] Implement the minimal pure domain and application-scoped 1,024-entry LRU factory.
- [ ] Run focused tests GREEN.

### Task 2: Contracts, repositories and transactional service

**Files:** Create `common/streetlights/*.java`, `ejb/streetlights/*.java`; modify lighting repositories; test `StreetlightServiceContractTest.java`.

**Interfaces:** Produces `StreetlightService` profile/light CRUD, version, deactivation, commands, reassignment, views and statistics.

- [ ] Write failing EJB transaction/role/common-boundary contract tests.
- [ ] Extend repositories with joined pagination/counts and pessimistic locks.
- [ ] Implement validation, immutable version creation, STREET checks, operations, no-op suppression, audit and cache invalidation.
- [ ] Run EJB tests GREEN.

### Task 3: REST resources and error mapping

**Files:** Create `web/streetlights/StreetlightResource.java`, `LightingProfileResource.java`, `StreetlightServiceExceptionMapper.java`; test `StreetlightResourceContractTest.java`.

**Interfaces:** Consumes `StreetlightService`; produces the specified `/streetlights` and `/lighting-profiles` API.

- [ ] Write failing route, role and CSRF-boundary tests.
- [ ] Implement DTO-only resources and controlled 400/404/409 mapping.
- [ ] Run web tests GREEN.

### Task 4: Repeatable benchmark

**Files:** Modify EJB test dependencies; create `StreetlightFlyweightBenchmarkTest.java` and benchmark result writer.

**Interfaces:** Produces CSV measurements for 1K/10K/50K contexts with allocation counts, median initialization time and JOL retained graph estimates.

- [ ] Write failing benchmark correctness test for logical equivalence and identity reuse.
- [ ] Add test-scoped JOL and implement warmup/repeated benchmark harness.
- [ ] Run benchmark and preserve actual target output for the report.

### Task 5: Payara/PostgreSQL integration

**Files:** Create `database/tests/Invoke-StreetlightFlyweightIntegrationTests.ps1`.

**Interfaces:** Exercises final HTTPS API with disposable users, hierarchy, profiles and lights and cleans exact fixture IDs.

- [ ] Verify V001–V005, build EAR, redeploy and health check.
- [ ] Test profile creation/versioning, STREET enforcement, identity statistics, state isolation, idempotence, brightness, reassignment, locking races, authorization, CSRF, JSONB and audit.
- [ ] Run security, Decorator, Composite and Complaint regressions.
- [ ] Confirm cleanup and inspect Payara logs.

### Task 6: Final verification and review

- [ ] Run `mvn -f backend/pom.xml clean package` and verify the EAR contents.
- [ ] Request fresh-context code review; fix Critical/Important findings with RED→GREEN tests.
- [ ] Re-run final deployment, health, integration, benchmark and cleanup evidence.

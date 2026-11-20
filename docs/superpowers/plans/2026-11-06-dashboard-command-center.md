# SmartCity360 Dashboard Command Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a secure aggregate dashboard summary API and a responsive React Smart City Operations Command Center using real persisted data.

**Architecture:** Add explicit dashboard records and a `DashboardService` in common/EJB, backed by grouped repository queries and protected by `@RolesAllowed` in both EJB and REST. Add one typed React Query request and render the response with existing shell, API error, and styling conventions.

**Tech Stack:** Java 17, Jakarta EE 10, JPA 3.1, PostgreSQL, Payara 6, Maven EAR, React, TypeScript, TanStack Query, Vite, existing CSS/Tailwind-compatible design tokens.

**Spec:** `docs/superpowers/specs/2026-11-06-dashboard-command-center-design.md`

## Global Constraints

- Preserve schema, migrations, authentication, CSRF, session cookies, health endpoint, existing modules, and design patterns.
- Do not use or mutate live PostgreSQL data; do not deploy, migrate, stage, or commit.
- Use actual aggregate queries and explicit zero/null semantics; never fabricate statistics.
- Use UTC `[from,to)` energy period for the previous 24 hours and return `asOf`.
- Do not expose PII, credentials, secrets, or internal exceptions.

## Review Focus

- Missing building power rows must be `unknown`, not inferred as `ON`; test in dashboard service aggregation.
- No energy readings must return `consumptionKwh: null` and `readingCount: 0`; test DTO/service mapping.
- UTC boundary readings must be fully contained in `[from,to]` and avoid duplicate counting; test repository query contract and period construction.
- Empty categorical tables must serialize genuine zero counts for every known enum; test summary construction.
- Non-operational roles must fail at both REST and EJB security boundaries; test annotations/contracts.

### Task 1: Define dashboard contracts and aggregate result types

**Files:**
- Create: `backend/smartcity360-common/src/main/java/com/smartcity360/common/dashboard/DashboardService.java`
- Create: `backend/smartcity360-common/src/main/java/com/smartcity360/common/dashboard/DashboardSummaryView.java`
- Test: `backend/smartcity360-common/src/test/java/com/smartcity360/common/dashboard/DashboardSummaryViewTest.java`

**Interfaces:**
- Produces `DashboardService.summary()` returning `DashboardSummaryView`.
- Produces records for zones, power states, complaints, streetlights, energy, and policies with explicit numeric/null fields.

- [ ] Write tests asserting JSON-safe record values, nullable energy consumption, and nested metric accessors.
- [ ] Run the common-module focused test and verify it fails because the contracts do not exist.
- [ ] Implement immutable records with `OffsetDateTime asOf`, UTC energy `from/to`, `BigDecimal consumptionKwh`, and `long readingCount`.
- [ ] Run the focused test and verify it passes.

### Task 2: Add efficient repository aggregate queries and service aggregation

**Files:**
- Create: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/dashboard/DashboardServiceBean.java`
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/ZoneRepository.java`
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/BuildingPowerStateRepository.java`
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/ComplaintRepository.java`
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/StreetlightRepository.java`
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/EnergyReadingRepository.java`
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/PolicyRepository.java`
- Test: `backend/smartcity360-ejb/src/test/java/com/smartcity360/ejb/dashboard/DashboardServiceBeanTest.java`
- Test: `backend/smartcity360-ejb/src/test/java/com/smartcity360/ejb/persistence/DashboardRepositoryContractTest.java`

**Interfaces:**
- Repository methods return grouped enum/count rows or a single aggregate tuple and perform no entity-list scans.
- `DashboardServiceBean.summary()` is `@RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})` and `@TransactionAttribute(SUPPORTS)`.

- [ ] Write service tests for zero-filled enum groups, missing power states, nullable energy, UTC `[from,to]`, and `asOf`.
- [ ] Run focused EJB tests and verify expected failures from absent service/repository methods.
- [ ] Implement grouped aggregate queries for active zones/buildings, power states, complaints, streetlight states, policies, and active-meter readings.
- [ ] Ensure energy aggregation uses one reading per existing unique meter/period key and returns null only when no rows qualify.
- [ ] Implement service mapping with fixed known enum categories and genuine zeroes.
- [ ] Run focused EJB tests and verify they pass.

### Task 3: Expose the secured dashboard REST endpoint

**Files:**
- Create: `backend/smartcity360-web/src/main/java/com/smartcity360/web/dashboard/DashboardResource.java`
- Test: `backend/smartcity360-web/src/test/java/com/smartcity360/web/dashboard/DashboardResourceContractTest.java`

**Interfaces:**
- `GET /dashboard/summary` delegates directly to `DashboardService.summary()`.
- REST and EJB use the same four operational roles; health remains untouched.

- [ ] Write tests for `/dashboard`, `/summary`, JSON production, and exact `RolesAllowed` values.
- [ ] Run the focused web tests and verify failure before the resource exists.
- [ ] Implement the resource with `@Path`, `@Produces`, `@GET`, and role annotation.
- [ ] Run focused web tests and verify they pass.

### Task 4: Add typed frontend API and dashboard UI states

**Files:**
- Create: `frontend/src/features/dashboard/dashboard-api.ts`
- Modify: `frontend/src/features/dashboard/dashboard-page.tsx`
- Modify: `frontend/src/styles/index.css`
- Test: `frontend/src/features/dashboard/dashboard-page.test.tsx`
- Test: `frontend/src/features/dashboard/dashboard-api.test.ts`

**Interfaces:**
- `fetchDashboardSummary(signal?: AbortSignal): Promise<DashboardSummary>` calls `/smartcity360/api/v1/dashboard/summary` through `apiRequest` with same-origin credentials.
- Frontend types mirror all backend fields, including `consumptionKwh: number | null` and ISO timestamps.

- [ ] Write API tests for exact URL and typed response, then run them to observe failure.
- [ ] Write UI tests for loading, populated data, zero/empty categories, null energy, retryable errors, accessible headings, and navigation links.
- [ ] Implement the typed request and dashboard panels using existing Card/Button/LoadingState/ErrorState components and no new dependencies.
- [ ] Add responsive dashboard grid/panel styling within the existing navy/teal visual system.
- [ ] Run focused frontend tests and verify they pass.

### Task 5: Full verification and review

**Files:**
- No additional production files expected.

- [ ] Run backend `mvn test` if Maven is available; otherwise record the environment limitation without substituting a live database.
- [ ] Run `npm run typecheck`.
- [ ] Run `npm run lint`.
- [ ] Run `npm test`.
- [ ] Run `npm run build`.
- [ ] Inspect the final diff/status and confirm no schema, migration, credential, TLS, deployment, or Git staging changes.

## Self-review coverage

All requested metric families map to Task 2, security and endpoint shape map to Task 3, UI states and accessibility map to Task 4, and preservation/build requirements map to Task 5. No live database or browser verification is part of automated work.

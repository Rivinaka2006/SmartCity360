# SmartCity360 Phase 3.3 Persistence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Baseline the verified PostgreSQL schema safely, add concurrency-safe zone invariants, and implement the Jakarta Persistence entity and repository foundation without changing the EAR architecture.

**Architecture:** Canonical SQL migrations remain the source of database structure. A PowerShell migration runner uses PostgreSQL advisory locking, SHA-256 checksums, and a catalog fingerprint to baseline the existing database without replaying schema or role seed DDL. The EJB module maps the schema through Jakarta Persistence 3.1, container-managed `EntityManager`, JTA, and PostgreSQL JSONB attribute conversion.

**Tech Stack:** Java 17, Jakarta EE 10, Jakarta Persistence 3.1, Payara 6.2025.11, PostgreSQL 18.3, Maven 3.9.16, PowerShell.

**Spec:** User-approved Phase 3.3 continuation request dated 2026-10-31.

## Global Constraints

- Preserve all existing tables and the five existing role rows; never drop, truncate, or recreate them.
- Preserve the four-module EAR architecture and `smartcityPU` / `jdbc/smartcity360`.
- Use only `jakarta.*`, container-managed persistence, and JTA.
- Do not use ORM schema generation or expose entities through REST.
- Stop on any catalog fingerprint, role seed, checksum, or unexpected-object discrepancy.
- Do not implement Phase 3.4 workflows or the five assessment design-pattern algorithms.

## Review Focus

- Baseline must distinguish “already structurally identical” from “partially similar” and fail closed.
- Migration locking must serialize concurrent runners and concurrent hierarchy writes.
- Zone reparenting/type changes must not invalidate descendants, meters, or streetlights.
- JSONB writes must bind as PostgreSQL `jsonb`, not `varchar`.
- Integration records must use unique UUIDs and be removed without touching unrelated data.

---

### Task 1: Canonical migrations and baseline runner

**Files:**
- Create: `database/migrations/V001__create_migration_ledger.sql`
- Create: `database/migrations/V002__create_application_schema.sql`
- Create: `database/migrations/V003__seed_initial_roles.sql`
- Create: `database/migrations/V004__enforce_zone_invariants.sql`
- Create: `database/migrations/schema-manifest.json`
- Create: `database/scripts/Invoke-SmartCityMigrations.ps1`

**Interfaces:**
- Consumes: Payara `domain1` JDBC pool configuration and PostgreSQL catalog metadata.
- Produces: checksummed `smartcity360_schema_migrations` entries and an additive invariant trigger installation.

- [ ] Capture exact live DDL and deterministic catalog metadata.
- [ ] Write V001–V003 and a complete schema manifest reconstructed from the live database.
- [ ] Write a fail-closed baseline/apply runner using a session advisory lock and transaction-scoped DDL.
- [ ] Verify every table, column, default, PK, FK, check, unique constraint, and index before baseline registration.
- [ ] Verify the five exact role rows and absence of other data/objects before registering V002/V003.

### Task 2: Zone invariant trigger and PostgreSQL tests

**Files:**
- Modify: `database/migrations/V004__enforce_zone_invariants.sql`
- Create: `database/tests/Invoke-DatabaseInvariantTests.ps1`

**Interfaces:**
- Consumes: canonical zone, meter, and streetlight tables.
- Produces: understandable constraint-trigger errors and serialized hierarchy mutations.

- [ ] Write failing SQL tests for invalid parent type, building children, ancestor cycles, wrong meter/streetlight zone types, and invalidating zone-type changes.
- [ ] Implement one cohesive trigger function plus narrowly scoped triggers.
- [ ] Lock the hierarchy with a transaction advisory lock for relevant writes and test two concurrent transactions.
- [ ] Validate existing rows before installing triggers, apply V004, rerun positive/negative/concurrency tests, and clean test rows.

### Task 3: Jakarta Persistence model

**Files:**
- Modify: `backend/smartcity360-ejb/pom.xml`
- Create: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/model/**`
- Create: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/converter/JsonbConverter.java`
- Test: `backend/smartcity360-ejb/src/test/java/com/smartcity360/ejb/persistence/EntityMappingTest.java`

**Interfaces:**
- Consumes: exact canonical SQL schema.
- Produces: 17 entity mappings, composite `UserRoleId`, enums, UUID/timestamp/numeric/JSONB mappings.

- [ ] Write reflection tests for all table/column/key/relationship/converter mappings.
- [ ] Add PostgreSQL JDBC as a provided compile dependency for provider-neutral `PGobject` conversion.
- [ ] Implement enums, identity-safe base entity support, the JSONB converter, and all 17 entities.
- [ ] Run EJB unit tests and correct only verified mapping mismatches.

### Task 4: Repository foundation

**Files:**
- Create: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/AbstractRepository.java`
- Create: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/persistence/repository/*Repository.java`
- Test: `backend/smartcity360-ejb/src/test/java/com/smartcity360/ejb/persistence/RepositoryContractTest.java`

**Interfaces:**
- Consumes: 17 entity classes and `smartcityPU`.
- Produces: 17 stateless repositories with inherited persistence/find/pagination and indexed domain lookups.

- [ ] Write repository contract/query-shape tests.
- [ ] Implement the transaction-explicit abstract repository.
- [ ] Implement 17 typed repositories with indexed read queries only.
- [ ] Run repository tests.

### Task 5: Build, Payara deployment, and managed-JPA verification

**Files:**
- Temporarily create and then remove: managed integration verification EJB/REST sources.

**Interfaces:**
- Consumes: generated EAR, running `domain1`, existing JDBC resource.
- Produces: evidence for managed insert/read/update/delete and mapping discovery without persistent test endpoints.

- [ ] Run the Java 17 Maven reactor `clean package` and all tests.
- [ ] Inspect EAR contents for the EJB, WAR, common JAR, and persistence descriptor.
- [ ] Start `domain1`, safely redeploy the EAR, and inspect deployment logs.
- [ ] Exercise a temporary development-only managed endpoint that creates, reads, updates, and deletes isolated records including JSONB.
- [ ] Remove the temporary endpoint, rebuild/redeploy the clean EAR, and verify the endpoint is absent.
- [ ] Verify `/smartcity360/api/v1/health` returns HTTP 200 and inspect final logs.

### Task 6: Final verification

**Files:** No new deliverables.

- [ ] Re-run the migration verifier and confirm all checksums/versions.
- [ ] Re-run Maven tests and database invariant tests.
- [ ] Confirm test-data cleanup and unchanged five role rows.
- [ ] Record actual commands, results, files, limitations, and stop before Phase 3.4.

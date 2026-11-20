# Phase 4.1 Energy Decorator Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver secure dynamically composable energy streams using the Decorator Pattern.

**Architecture:** Framework-independent stream decorators live in the EJB domain layer. A Payara-backed key provider and EJB application service connect those classes to JPA, while the WAR owns HTTP DTOs and the precise meter-versus-human security boundary.

**Tech Stack:** Java 17, Jakarta EE 10, MicroProfile Config API, PostgreSQL 18.3, Payara 6.2025.11, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-01-phase-4-1-energy-decorator-security-design.md`

## Global Constraints

- Preserve the four-module EAR and all Phase 3 behavior.
- No permanent secrets, new tables, Spring, JWT, React, commits, or staging.
- Production code follows RED-GREEN TDD; integration fixtures are disposable and cleaned.

## Review Focus

- Same nonce racing in concurrent requests must yield exactly one success.
- An altered payload, method, or path must invalidate the meter MAC.
- Malformed compression/encryption inputs must remain size-bounded and non-sensitive.
- Missing or malformed Payara alias values must fail closed.
- Browser cookies must never authenticate or ambiguously accompany meter ingestion.

---

### Task 1: V005 credential references

**Files:** migration, manifest, migration verification, `EnergyMeterEntity`, entity tests.

- [ ] Make migration contract tests fail for missing V005.
- [ ] Add paired nullable columns, positive-version check, and partial unique index.
- [ ] Update checksums/fingerprints, apply with the existing locked runner, and verify the live catalog.
- [ ] Map the columns in JPA and make mapping tests pass.

### Task 2: Key provider and cryptographic primitives

**Files:** EJB key-provider/domain packages, EJB POM, focused unit tests, local provisioning script.

- [ ] Test missing/malformed/valid 256-bit key behavior first.
- [ ] Implement provider contracts and Payara MicroProfile Config adapter.
- [ ] Test and implement canonical HMAC, bounded replay protection, GZIP limits, and AES-GCM envelope/tamper behavior.
- [ ] Provide an opt-in, non-logging provisioning script that requires explicit apply confirmation.

### Task 3: Decorator hierarchy and pipeline factory

**Files:** EJB energy stream domain classes and unit tests.

- [ ] Test raw behavior, delegation, exact order, combinations, duplicates, empty/oversized inputs, and failures.
- [ ] Implement the component, abstract decorator, three concrete decorators, immutable context/result, and factory.

### Task 4: EJB service and persistence

**Files:** common service DTO/contracts, EJB service, repositories/entity adjustments, tests.

- [ ] Test meter resolution/status/version handling and safe result contracts.
- [ ] Implement production authenticated pipelines, decryption, run queries, and sanitized run persistence.
- [ ] Do not persist energy readings until a reviewed reading format exists.

### Task 5: REST and security boundary

**Files:** WAR resource/DTOs, precise CSRF routing, tests.

- [ ] Test exact meter route selection, cookie rejection, missing proof, response secrecy, and human endpoint protection.
- [ ] Implement process, verify, run-list, and run-detail endpoints with role enforcement.

### Task 6: Full and runtime verification

**Files:** disposable integration tooling only if needed.

- [ ] Run migration, Maven, packaging, and security regression suites.
- [ ] Provision disposable aliases and records without command-line secret exposure, deploy EAR, and verify health/HMAC/AES/JPA/JSONB cases.
- [ ] Remove disposable aliases and records, then confirm the deployed application remains healthy.
- [ ] Self-review the complete change because subagent delegation is disabled.

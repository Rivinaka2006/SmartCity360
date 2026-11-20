# Energy Key-Resolution Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add non-secret, stage-specific server diagnostics for energy-stream key-resolution failures.

**Architecture:** Keep all externally visible error contracts unchanged. Add fixed diagnostic stage/reason logging at the `PayaraKeyProvider` lookup boundary, where the failing source is known.

**Tech Stack:** Java 17, Jakarta EE 10, MicroProfile Config, `java.util.logging`, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-11-08-energy-key-resolution-diagnostics-design.md`

## Global Constraints

- Preserve security, encryption, authorization, service, persistence, and REST behavior.
- Never log key values, encoded material, credentials, meter identifiers, key identifiers, or configuration values.
- Do not modify the database, Payara configuration, integration script, unrelated features, or deployment state.
- Do not execute live integration tests or commit changes.

## Review Focus

- Missing meter HMAC values and missing AES values must not share the same diagnostic stage.
- A missing current AES key identifier must be distinguishable from a missing AES key value.
- Existing `KEY_CONFIGURATION_MISSING` and `KEY_CONFIGURATION_INVALID` exception codes must remain unchanged.
- Supplied key identifiers and values must never appear in diagnostic records.
- Both encryption and envelope-decryption AES identifier paths must use an accurate fixed stage.

---

### Task 1: Key-resolution stage diagnostics

**Files:**
- Modify: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/energy/stream/PayaraKeyProvider.java`
- Modify: `backend/smartcity360-ejb/src/test/java/com/smartcity360/ejb/energy/stream/PayaraKeyProviderTest.java`

**Interfaces:**
- Consumes: Existing `KeyProvider` methods and `EnergyStreamException` codes.
- Produces: Fixed `java.util.logging` records containing only a diagnostic stage and reason.

- [ ] **Step 1: Write failing regression tests**

Add tests that capture `PayaraKeyProvider` log records and require distinct fixed stages for missing meter HMAC value, current AES identifier, and AES value. Assert the existing exception code and absence of supplied identifiers/values.

- [ ] **Step 2: Run the focused test and verify RED**

Run the EJB `PayaraKeyProviderTest` through Maven 3.9.16.

Expected: FAIL because no stage-specific diagnostic records exist.

- [ ] **Step 3: Implement minimal provider logging**

Add fixed stage/reason labels immediately before existing `KEY_CONFIGURATION_MISSING` exceptions. Pass explicit stage labels through identifier validation and decoding without changing lookup order or returned key objects.

- [ ] **Step 4: Run focused and relevant backend tests**

Run `PayaraKeyProviderTest`, `EnergyStreamPipelineTest`, the EJB test suite, and the backend reactor test suite.

Expected: PASS with unchanged exception contracts and no test regressions.

- [ ] **Step 5: Review the diff and report evidence**

Confirm only the provider, its focused test, and these diagnostic workflow documents changed. Report static findings separately from runtime-confirmed root cause; do not infer live Payara state.

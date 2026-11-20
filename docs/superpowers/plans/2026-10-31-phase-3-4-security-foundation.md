# SmartCity360 Phase 3.4 Security Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add database-backed Jakarta Security authentication, Payara sessions, CSRF, EJB role authorization, audit logging, bounded login throttling, and controlled initial-admin provisioning while preserving the EAR architecture.

**Architecture:** The WAR owns HTTP authentication/session/CSRF concerns and delegates credential validation to an EJB through common local contracts. The EJB owns PBKDF2, repositories, audit transactions, authenticated-profile lookup, and `@RolesAllowed` boundaries. Payara’s enabled default principal-to-role mapping maps IdentityStore groups to application roles.

**Tech Stack:** Java 17, Jakarta EE/Security 10/3.0, Payara 6.2025.11, PostgreSQL 18.3, Maven.

**Spec:** User Phase 3.4 request dated 2026-10-31.

## Global Constraints

- Keep the four-module EAR; no JWT, OAuth, Spring Security, or custom session tokens.
- No schema migration is needed; preserve five roles, four migration entries, and all existing data.
- Authentication must establish a container principal and database-derived roles.
- Passwords, hashes, cookies, and CSRF secrets never enter logs or responses.
- Provisioning is implemented but not executed without explicit approval.

## Review Focus

- Payara session registration and WAR-to-EJB caller/role propagation.
- Login/logout CSRF ordering and session fixation prevention.
- Failed authentication audit persistence outside rolled-back work.
- PBKDF2 format parsing limits and constant-time verification.
- Disposable privileged fixture cleanup after integration testing.

---

### Task 1: Password and EJB security services
- [ ] Write failing PBKDF2 and authentication-service tests.
- [ ] Implement versioned PBKDF2-HMAC-SHA256 at 600,000 iterations with upgrade detection.
- [ ] Implement database authentication, role lookup, audit `REQUIRES_NEW`, current-profile lookup, and central authorization service.

### Task 2: Jakarta Security and REST/session layer
- [ ] Write failing mechanism, CSRF, throttling, and response-safety tests.
- [ ] Implement IdentityStore, HttpAuthenticationMechanism with session registration, CSRF service/filter, bounded throttling, exception mapping, and `/auth` endpoints.
- [ ] Add role declarations and secure session-cookie descriptors.

### Task 3: Provisioning and build verification
- [ ] Implement a local-only, interactive initial-admin provisioner without executing it.
- [ ] Run all unit tests and Maven reactor packaging; inspect the EAR.

### Task 4: Payara integration verification
- [ ] Create a disposable ACTIVE user with a unique secret supplied only in process memory and assign ADMIN.
- [ ] Redeploy the EAR and verify CSRF, login, principal, roles, WAR-to-EJB authorization, 401, 403, session fixation, logout, audit rows, and throttling.
- [ ] Remove the disposable user, role assignment, audit rows, and any temporary probe endpoint; rebuild/redeploy the clean EAR.
- [ ] Verify health, database cleanup, role/migration preservation, and clean Payara logs.

# SmartCity360 Jakarta EE EAR Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and verify the minimal Java 17/Jakarta EE 10 SmartCity360 multi-module EAR foundation with a REST-to-EJB health check.

**Architecture:** A thin root reactor includes a backend parent reactor. Shared contracts are compiled into one common JAR in `EAR/lib`; the EJB and WAR use that JAR with provided scope, and the EAR assembles all deployable modules with an explicit Jakarta EE 10 descriptor.

**Tech Stack:** Java 17, Jakarta EE 10 API 10.0.0, Jakarta REST, Jakarta Enterprise Beans, Jakarta Persistence 3.1, Maven, JUnit Jupiter, Payara 6.2025.11.

**Spec:** `docs/superpowers/specs/2026-10-31-jakarta-ee-ear-foundation-design.md`

## Global Constraints

- Use Java 17 and Jakarta EE 10 namespaces only.
- Produce an EAR; do not implement a standalone backend WAR.
- Use persistence unit `smartcityPU` and JTA data source `jdbc/smartcity360` without credentials or schema mutation.
- Preserve current staged/unstaged work and do not commit on the unborn branch.
- Do not deploy, implement business features, authentication, or the five assessment patterns.

## Review Focus

- Duplicate shared classes: archive inspection must find common contracts only in `EAR/lib/smartcity360-common.jar`.
- Container API leakage: dependency and archive inspection must find no bundled Jakarta EE API/implementation JARs.
- Wrong web path: descriptor and annotations must compose to `/smartcity360/api/v1/health`.
- Database mutation: `persistence.xml` must explicitly set schema-generation database action to `none`.
- Invalid platform descriptor: XML parsing and archive inspection must confirm Jakarta EE 10 namespace/schema/version.

---

### Task 1: Maven reactor and packaging configuration

**Files:**
- Modify: `pom.xml`
- Create: `backend/pom.xml`
- Create: `backend/smartcity360-common/pom.xml`
- Create: `backend/smartcity360-ejb/pom.xml`
- Create: `backend/smartcity360-web/pom.xml`
- Create: `backend/smartcity360-ear/pom.xml`
- Create: `backend/smartcity360-ear/src/main/application/META-INF/application.xml`
- Create: `backend/smartcity360-ejb/src/main/resources/META-INF/persistence.xml`

**Interfaces:**
- Consumes: existing root coordinates `com.smartcity360:smartcity360:1.0-SNAPSHOT` and Java 17.
- Produces: a four-module backend reactor and EAR assembly contract used by Tasks 2-3.

- [ ] Create the root/backend reactor POMs and child packaging POMs with pinned compatible plugins.
- [ ] Configure the EJB and WAR to use common as `provided`, and the EAR to package common under `lib`.
- [ ] Add explicit Jakarta EE 10 `application.xml` and the Persistence 3.0 configuration schema used by Jakarta Persistence 3.1, with schema generation disabled.
- [ ] Run `mvn -f backend/pom.xml validate` and require `BUILD SUCCESS`.

### Task 2: Health contract and EJB service (RED-GREEN)

**Files:**
- Create: `backend/smartcity360-common/src/main/java/com/smartcity360/common/health/HealthService.java`
- Create: `backend/smartcity360-common/src/main/java/com/smartcity360/common/health/HealthStatus.java`
- Create: `backend/smartcity360-ejb/src/test/java/com/smartcity360/ejb/health/HealthServiceBeanTest.java`
- Create: `backend/smartcity360-ejb/src/main/java/com/smartcity360/ejb/health/HealthServiceBean.java`

**Interfaces:**
- Consumes: Jakarta EJB API and the common module dependency from Task 1.
- Produces: `HealthService.currentStatus(): HealthStatus` and its `@Stateless` local implementation.

- [ ] Write `HealthServiceBeanTest.currentStatusReportsApplicationUp()` asserting literal status `UP` and service `smartcity360`.
- [ ] Run the EJB test and verify compilation fails because the contract/implementation does not exist.
- [ ] Add the minimal common contract/record and stateless EJB implementation.
- [ ] Re-run the EJB test and require one passing test.

### Task 3: Jakarta REST endpoint (RED-GREEN)

**Files:**
- Create: `backend/smartcity360-web/src/test/java/com/smartcity360/web/health/HealthResourceTest.java`
- Create: `backend/smartcity360-web/src/main/java/com/smartcity360/web/RestApplication.java`
- Create: `backend/smartcity360-web/src/main/java/com/smartcity360/web/health/HealthResource.java`

**Interfaces:**
- Consumes: `HealthService.currentStatus(): HealthStatus` from Task 2.
- Produces: Jakarta REST application path `/api/v1` and `GET /health` returning the shared status DTO.

- [ ] Write `HealthResourceTest.healthDelegatesToEjbContract()` using a real in-memory contract implementation and asserting the returned literal DTO values.
- [ ] Run the WAR test and verify compilation fails because `HealthResource` does not exist.
- [ ] Add the REST application and resource with local EJB injection plus a test seam.
- [ ] Re-run the WAR test and require one passing test.

### Task 4: Full build and EAR inspection

**Files:**
- Inspect: `backend/smartcity360-ear/target/smartcity360-ear-1.0-SNAPSHOT.ear`
- Inspect: module dependency trees and effective descriptors.

**Interfaces:**
- Consumes: all artifacts from Tasks 1-3.
- Produces: verified Phase 3.1 EAR artifact and implementation report evidence.

- [ ] Run the full clean package reactor with JDK 17 and require all modules to succeed.
- [ ] List the EAR and nested WAR/EJB/common contents; assert one common JAR in `lib`, no duplicate common classes, and no bundled Jakarta EE API implementations.
- [ ] Parse packaged `META-INF/application.xml`; assert Jakarta EE namespace, `application_10.xsd`, version `10`, EJB/WAR module names, `/smartcity360`, and `lib`.
- [ ] Run Maven dependency trees and confirm no circular dependencies or runtime Jakarta EE API packaging.
- [ ] Inspect Payara read-only state and report the stopped domain and existing `jdbc/smartcity360` resource without deployment or mutation.

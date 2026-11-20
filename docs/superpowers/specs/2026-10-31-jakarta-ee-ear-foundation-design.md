# SmartCity360 Jakarta EE EAR Foundation Design

## Intent

Establish Phase 3.1 as a conventional Maven multi-module Jakarta EE 10 EAR application for Payara 6 while preserving the existing IntelliJ project metadata and PostgreSQL schema. The phase proves WAR-to-EJB communication with a health endpoint and stops before deployment, database access, authentication, or business-pattern implementation.

## Goals

- Keep the repository root as a thin Maven reactor and use `backend/pom.xml` as the backend parent/aggregator.
- Build separate common JAR, EJB, WAR, and EAR modules on Java 17.
- Package the common contracts once in `EAR/lib`, with provided Jakarta EE APIs.
- Expose `GET /smartcity360/api/v1/health` through Jakarta REST and a local EJB contract.
- Include Jakarta EE 10 `application.xml` and the Persistence 3.0 configuration descriptor used by Jakarta Persistence 3.1.
- Verify the reactor build and inspect the EAR contents without deploying it.

## Non-goals

- No deployment or redeployment.
- No database schema changes or credential configuration.
- No assessment design patterns, business features, frontend changes, or authentication.

## Constraints and preservation

- The existing repository is on an unborn `master` branch with staged IntelliJ files and an unstaged POM edit; implementation must not commit, unstage, or overwrite unrelated work.
- Existing `src/` directories are empty and reusable only as historical project scaffolding; they remain untouched.
- IntelliJ already targets JDK 17 and has a PostgreSQL data source for `smartcity360`; those files remain untouched.
- Payara 6.2025.11 is installed, `domain1` is stopped, and its offline configuration already declares `jdbc/smartcity360`; no server resources will be changed.
- Maven is not on `PATH`; verification will use the installed Maven 3.9.16 executable with `JAVA_HOME` set to the detected JDK 17 home for the command.

## Approach

The selected approach is a root reactor containing one backend reactor. The backend parent centralizes Java, Jakarta EE, test dependency, and plugin versions. Common contains only application-owned cross-module contracts. EJB and WAR compile against common with `provided` scope because the EAR supplies the common JAR in `lib`; neither module embeds it. The EAR declares all three artifacts explicitly and owns the application descriptor and context root.

Alternatives were rejected because making the root POM the backend parent would not satisfy the required `backend/pom.xml` parent boundary, while a standalone WAR or WAR-embedded EJB classes would violate the mandatory EAR architecture and duplicate shared classes.

## Module boundaries

- `smartcity360-common`: local EJB interface and health DTO.
- `smartcity360-ejb`: stateless health service and the `smartcityPU` persistence unit using `jdbc/smartcity360` with schema generation disabled.
- `smartcity360-web`: Jakarta REST application at `/api/v1` and `/health` resource delegating to the local EJB contract.
- `smartcity360-ear`: assembly of EJB JAR, WAR, and common JAR plus Jakarta EE 10 `application.xml`.

## Verification

Unit tests exercise service and resource behavior before implementation. A clean Maven package must pass all modules. Archive inspection must prove the expected WAR/EJB/common paths, one common JAR only, a valid Jakarta EE 10 descriptor, and absence of Jakarta EE implementation JARs.

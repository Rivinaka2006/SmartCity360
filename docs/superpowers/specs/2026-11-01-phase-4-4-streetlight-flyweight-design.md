# Phase 4.4 Streetlight Flyweight Design

## Intent

Implement a real Java Flyweight model for large streetlight populations while preserving the Jakarta EE EAR, existing database schema, security, CSRF, audit, and previous pattern modules.

## Constraints and non-goals

- Java 17, Jakarta EE 10, Payara 6.2025.11, EclipseLink and PostgreSQL remain unchanged.
- No schema migration, real hardware control, React work, distributed cache, Spring, or mass database benchmark data.
- JPA entities never enter the long-lived Flyweight cache.
- Existing profile rows are never updated in place when intrinsic configuration changes.

## Chosen approach

`SharedLightingProfile` is an immutable Flyweight containing profile ID/version, manufacturer, model, wattage, default brightness, controller type, firmware family, and canonical JSON text. `StreetlightContext` is immutable extrinsic state containing light ID, STREET zone, coordinates/address, operational state, and brightness override. `StreetlightFlyweight` applies commands to a context and returns a replacement context plus operation metadata.

`LightingProfileFactory` is an application-scoped, process-local, synchronized access-order LRU with capacity 1,024. It keys entries by `ProfileKey(profileId, profileVersion)`, constructs once while resident, rejects duplicate concurrent creation, inserts only successfully built values, exposes aggregate hit/miss/eviction counts, and supports key/profile invalidation. Identity reuse is guaranteed while an entry remains resident; eviction permits later reconstruction.

## Profile lifecycle

The schema has a unique `profile_code` rather than a logical profile-family foreign key. Safe versioning is nevertheless additive: creating a version copies or replaces intrinsic fields into a new row with a new UUID, caller-supplied unique code, and `source.profile_version + 1`. The old row and its streetlight references remain unchanged. The limitation is that lineage is represented by the version-creation operation and audit record, not a persisted parent-profile column.

Intrinsic rows are never mutated. Deactivation is allowed only when no streetlight references the profile, then its cache entries are invalidated. Profile reassignment locks the light, requires an active target profile, changes only the FK, and resolves the new Flyweight.

## Operations

- `ON` and `OFF` are idempotent.
- Brightness overrides are 0–100 and may be stored while OFF for the next activation.
- FAULT and MAINTENANCE contexts reject ON/OFF/brightness commands.
- Effective brightness is the override when present, otherwise the shared default.
- State changes use pessimistic row locking; unchanged commands do not write or audit.

## Service, API, and authorization

`StreetlightServiceBean` is the CMT boundary. ADMIN creates/versions/deactivates profiles, registers/reassigns streetlights, and operates lights. CITY_MANAGER may operate lights. ADMIN, CITY_MANAGER, SUPERVISOR and LOCAL_STAFF may read profiles/lights/statistics. RESIDENT has no streetlight access. Every mutating HTTP route remains under the existing session and CSRF filter.

The API uses `/lighting-profiles` and `/streetlights` with static `/stats` declared separately from `/{id}`. DTOs live in common; no entity is exposed.

## Benchmark

A test-only repeatable harness creates logically identical 1,000, 10,000 and 50,000-light datasets with ten profiles. It warms up, repeats initialization timing, counts configuration allocations exactly, proves reference identity, and uses JOL graph traversal for retained-object-graph estimates. JOL values are reported as estimates, not precise JVM heap savings. No benchmark data is persisted or exposed over HTTP.

## Alternatives considered

- In-place profile mutation: rejected because it invalidates historical references and cached intrinsic state.
- Unbounded `ConcurrentHashMap`: rejected because profile churn could grow without limit.
- External/distributed cache: rejected as unnecessary infrastructure for the single-node assessment environment.

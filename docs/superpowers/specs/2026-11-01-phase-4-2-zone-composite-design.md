# Phase 4.2 Smart Zone Composite Design

## Intent

Implement CITY -> DISTRICT -> STREET -> BUILDING operations through a real Composite domain model while preserving the EAR, V004 triggers, Jakarta Security, CSRF, JTA, and existing Decorator feature.

## Approaches considered

1. **Pure Composite plus persistence ports and a one-query assembler (selected).** `CompositeZone` recursively delegates to `ZoneComponent`; `BuildingLeaf` invokes injected operation/usage ports. A PostgreSQL recursive query loads only the requested subtree. This keeps the pattern unit-testable and JPA-free.
2. Entity-backed composite objects. Rejected because lazy JPA associations would couple the pattern to Payara and invite N+1 queries.
3. SQL-only recursive operations. Rejected because it would not demonstrate polymorphic Composite delegation.

## Domain rules

- `ZoneComponent` exposes shutdown, restore, and usage reporting uniformly.
- CITY, DISTRICT, and STREET become `CompositeZone`; BUILDING becomes `BuildingLeaf`.
- A missing `building_power_states` row means logical ON. The first command materializes the row. `affectedBuildings` counts logical state changes, not merely touched rows.
- Composite results sum targeted and changed buildings; repeated commands are idempotent.
- Usage includes readings whose full half-open interval is contained by `[from,to)`: `start >= from && end <= to`. Boundary-overlapping readings are excluded, not prorated. Overlapping included readings for one meter make the report fail rather than double-count.
- A building with no included readings is distinct from measured zero by building/readings counters.

## Persistence and transactions

- `ZoneTreeAssembler` uses one recursive native query mapped to `ZoneEntity`, validates root uniqueness/type/parent links/cycles, and builds a deterministically code-ordered tree.
- Power commands run in one `ZoneServiceBean` REQUIRED transaction. Building zone rows are locked in UUID order before state writes, serializing competing shutdown/restore operations and reducing deadlock risk. State changes, SUCCEEDED command history, and application audit commit together.
- Validation occurs before mutation. Unexpected failures roll back the entire operation. No independent failed-command record is written because it could be misleading without a committed state transition.
- CRUD relies on V004 as the final concurrency-safe hierarchy authority and also returns early, understandable application errors.
- Deactivation is conservative: it is rejected while active descendants or active referenced meters exist. Zones are never physically deleted.

## Security

- ADMIN: create/update/deactivate and power operations.
- CITY_MANAGER: power operations and reporting/read access.
- SUPERVISOR and LOCAL_STAFF: hierarchy, history, and usage read access.
- RESIDENT: no zone administration access.
- EJB `@RolesAllowed` is authoritative; REST methods also declare roles. Existing CSRF processing protects every state-changing zone route.

## Non-goals

No hardware control, new tables, new migration, caching, React, or Phase 4.3 patterns. Energy readings are never invented or modified.

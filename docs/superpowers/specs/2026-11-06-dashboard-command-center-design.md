# SmartCity360 Dashboard Command Center Design

## Intent

Replace the health/profile-only dashboard with an operational command center backed by one secure read-only summary response. The dashboard must represent persisted city data, preserve the existing session/CSRF/security architecture, and remain useful when any dataset is empty.

## Goals

- Add `GET /smartcity360/api/v1/dashboard/summary` through REST → EJB → JPA.
- Aggregate zones, buildings, building power states, complaints, streetlights, energy, and policies without N+1 queries.
- Authorize the endpoint for operational roles at both REST and EJB boundaries.
- Return explicit metric names, an `asOf` timestamp, and UTC energy period metadata.
- Render responsive, accessible React summaries with loading, empty, retryable error, and unavailable states.

## Non-goals and constraints

- No schema, migration, credential, TLS, Payara, authentication, or CSRF changes.
- No live database access, data mutation, deployment, Git staging, or commits.
- The public health endpoint remains unchanged.
- No complaint titles, descriptions, resident identities, meter credentials, or internal exceptions are returned.

## Metric definitions

- `zones.total`: active rows in `zones` across all zone types.
- `buildings.total`: active rows in `zones` where `zone_type = BUILDING`.
- `powerStates`: counts of active buildings joined to `building_power_states`; buildings without a state are reported as `unknown`, not inferred as `ON`.
- `complaints.total`: all complaint rows; `byStatus` includes every persisted status, with genuine zeroes for absent statuses.
- `streetlights.total`: all rows in `streetlights`; `byOperationalState` includes ON, OFF, FAULT, and MAINTENANCE.
- `energy`: sum of distinct persisted readings for active meters whose complete reading periods are contained in the UTC reporting interval `[from, to]`, where `to` is the current UTC instant and `from` is 24 hours earlier. The existing unique constraint `(meter_id, period_start_at, period_end_at)` and a single meter join prevent duplicate readings; no overlap splitting or double counting is introduced. `readingCount` and nullable `consumptionKwh` distinguish no measurements from a measured zero.
- `policies.total`: all policy rows; `byStatus` includes DRAFT, ACTIVE, and INACTIVE.
- `asOf`: application-observed UTC timestamp after the aggregate reads; it is not advertised as an atomic database snapshot.

## Approaches

1. Browser composition of existing endpoints: rejected because it requires multiple requests, duplicate aggregation, pagination work, and inconsistent observation times.
2. One dashboard EJB backed by repository aggregate queries: selected because it provides one response contract, centralized authorization, efficient grouping, and a stable UI boundary.

## Backend shape

Create dashboard DTOs in the common module, a dashboard service contract and stateless implementation in the EJB module, repository aggregate methods using grouped JPQL/native-safe queries, and a role-protected REST resource in the web module. The service uses `SUPPORTS` read-only transaction semantics consistent with existing reporting services.

## Frontend shape

Add a typed dashboard summary API client and replace the dashboard body with KPI cards and operational panels. Existing shell, navigation, API error handling, React Query, visual tokens, and health query remain in use. The UI displays unavailable energy as “No readings” rather than zero and gives each empty categorical breakdown a meaningful empty state.

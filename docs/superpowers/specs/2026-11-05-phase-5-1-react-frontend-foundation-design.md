# Phase 5.1 React Frontend Foundation Design

## Intent

Create a standalone `frontend/` development workspace for SmartCity360 that gives authenticated users a secure, accessible application shell and proves integration with the existing Jakarta EE session-authentication contract. The frontend must preserve Payara sessions, CSRF protections, role enforcement, the `/smartcity360` cookie path, and the existing EAR boundary. Phase 5.1 stops at foundation and placeholder module views; it does not implement the five business dashboards or bundle React into the EAR.

## Verified current state

- Microsoft OpenJDK 17 is installed at `C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot`; `java` and `javac` report 17.0.16.
- Payara Server 6.2025.11 build 43 is installed at `E:\Java Institute\IdeaProjects\payara6`; `domain1` is running.
- `server-config` listener `http-listener-2` is HTTPS-enabled on port 8181 and currently uses certificate alias `s1as`.
- The domain key store is `E:\Java Institute\IdeaProjects\payara6\glassfish\domains\domain1\config\keystore.p12`, a PKCS12 store. Existing aliases must be preserved.
- The certificate currently presented by `localhost:8181` is valid from 2025-11-04 to 2035-11-02 and has SHA-256 fingerprint `1F:86:48:B8:C3:81:3B:60:7A:98:8C:D4:46:79:E3:C5:92:42:53:1C:E9:45:51:33:73:7D:24:C5:B1:51:F8:0E`. It has `CN=localhost` but no Subject Alternative Name, so standards-compliant hostname verification fails.
- No `frontend/` workspace or JavaScript lockfile exists. Node 24.13.0 and npm 11.6.2 are installed.
- The backend baseline is healthy: the Maven reactor previously completed with 84 tests passing and produced the EAR without changing module structure.
- Authentication uses a Payara-managed `JSESSIONID` cookie with `Path=/smartcity360`, `Secure`, `HttpOnly`, and `SameSite=Strict`.
- The exact auth contract is `{email,password}` for login, `{id,displayName,email,roles}` for current user, `{token}` for CSRF, header `X-CSRF-Token`, and an empty HTTP 204 logout response. Payara may return HTML for an unauthenticated `/auth/me` request.

## Goals

- Use React, TypeScript, Vite, React Router, Tailwind CSS, shadcn/ui-style local components, Lucide React, TanStack Query, React Hook Form, and Zod.
- Run Vite at `https://localhost:5173` with a separately trusted certificate and key outside the repository.
- Proxy unchanged `/smartcity360/api/v1` paths to `https://localhost:8181` with explicit Payara certificate trust and TLS verification enabled.
- Implement a typed API client, in-memory CSRF management, real session initialization, login, logout, protected routes, role-aware navigation, session-expiry handling, and safe non-JSON error handling.
- Provide an accessible, responsive enterprise shell, theme support, dashboard foundation using only real health/session data, and clearly labelled placeholder module views.
- Cover security-relevant browser behavior with isolated tests, then distinguish those results from live HTTPS/Payara integration evidence.

## Non-goals and anti-goals

- No JWT, bearer token, browser-stored session identifier, mock login, permissive CORS, database access, backend schema change, EAR restructuring, React-in-EAR packaging, or Phase 5.2 feature implementation.
- No `secure: false`, `NODE_TLS_REJECT_UNAUTHORIZED=0`, broad Origin rewriting, cookie attribute rewriting, or automatic Windows trust-store mutation.
- No fabricated dashboard statistics and no public UI for the machine-authenticated energy-meter ingestion operation.
- No permanent administrator or permanent integration credential.

## Approaches considered

1. **Selected: HTTPS Vite with explicit certificate files and an explicitly trusted Payara certificate.** The browser talks only to `https://localhost:5173`; Vite forwards the unchanged `/smartcity360/api/v1` path to Payara. A dedicated HTTPS agent trusts only configured Payara certificate material and retains `rejectUnauthorized: true`. This preserves the secure cookie path and avoids CORS.
2. **System-wide/current-user trust with Node system CA lookup.** Rejected as the primary design because it makes development depend on workstation trust-store state and persistent environment behavior that the project cannot validate or reproduce safely.
3. **HTTP Vite, disabled verification, or direct browser CORS.** Rejected because these approaches weaken TLS or require backend security changes and are incompatible with the approved constraints.

## TLS setup and validation boundary

Persistent Payara changes remain manual and separately approved. The administrator will back up `keystore.p12` and `domain.xml`, create a new alias rather than replacing `s1as`, include `DNS:localhost`, `IP:127.0.0.1`, and IPv6 loopback SANs plus `serverAuth` EKU, set only `server-config.network-config.protocols.protocol.http-listener-2.ssl.cert-nickname`, restart, and verify the certificate actually presented on 8181. Passwords are entered interactively; they are never arguments, repository values, logs, or environment files.

The Vite certificate and private key live outside the repository under a user-controlled directory and are configured through:

- `SMARTCITY360_VITE_CERT_FILE`
- `SMARTCITY360_VITE_KEY_FILE`
- `SMARTCITY360_PAYARA_CA_FILE`

A reusable Node TLS configuration module validates that all three variables name absolute readable files, parses the frontend certificate, checks validity and `localhost` SAN, verifies the private key matches the certificate by creating a secure context, and constructs a Payara HTTPS agent with explicit CA material and `rejectUnauthorized: true`. A pre-development TLS probe connects to `localhost:8181` with SNI `localhost`, fails on chain or hostname errors, and prints only non-secret certificate identity, validity, and fingerprint information. Vite startup fails before listening when frontend material is missing or invalid. Live proxy requests also fail closed if Payara cannot be authenticated.

The repository contains only an environment-variable name template and setup guidance. `.env.local`, certificate files, key files, and local TLS directories are ignored.

## Development proxy and cookie behavior

Vite listens on `localhost:5173` over HTTPS and proxies the `/smartcity360/api` prefix to `https://localhost:8181` without rewriting paths. Proxy configuration uses `secure: true`, the explicit HTTPS agent, and `changeOrigin: false`.

Keeping `changeOrigin: false` is deliberate. The browser sends `Origin: https://localhost:5173`, Vite preserves the original Host and Origin headers, and the existing Jakarta REST `UriInfo` therefore reconstructs the same origin for CSRF comparison. No Origin/Referer translation is needed. Browser requests remain under `/smartcity360`, so Payara's cookie path matches and its `Secure`, `HttpOnly`, and `SameSite=Strict` attributes remain unchanged. API calls use relative URLs and `credentials: "same-origin"`.

## Frontend structure

The workspace is feature-oriented and contains only Phase 5.1 responsibilities:

- `src/app/`: providers, router, top-level error boundary, and application bootstrap.
- `src/lib/api/`: typed fetch client, error model, endpoint paths, and auth/health contracts.
- `src/features/auth/`: login form, authentication provider/hooks, route guards, and auth queries/mutations.
- `src/features/dashboard/`: real health/session dashboard foundation.
- `src/components/ui/`: focused shadcn/ui-compatible primitives checked into the application.
- `src/components/layout/`: sidebar, header, responsive navigation, user menu, theme control, and content shell.
- `src/components/common/`: loading, error, empty, unauthorized, not-found, and placeholder states.
- `src/routes/`: route metadata and role policy used by both router guards and navigation.
- `src/styles/`: Tailwind theme tokens and reduced-motion/accessibility foundations.
- Co-located tests exercise public behavior rather than implementation details.

Module placeholders are created for zones, complaints, streetlights, energy, and policies only where routing needs a concrete view. They state that feature UI is planned and do not issue speculative API calls or show mock metrics.

## API client and error handling

The API client accepts a relative path, method, optional JSON body, `AbortSignal`, and a CSRF requirement. It always sends `Accept: application/json`, adds `Content-Type` only when a JSON body exists, uses same-origin credentials, and handles 204 without parsing. Response parsing is content-type-aware: JSON error bodies may contribute a safe `error` and `code`; HTML, plain text, malformed JSON, and 5xx bodies are never rendered verbatim.

Normalized errors retain status and a safe category for 400, 401, 403, 404, 409, 429, and 500-class failures. A 401 invalidates auth-related TanStack Query state and clears the in-memory CSRF token. Request cancellation remains distinguishable from server failure.

Handwritten TypeScript contracts match the inspected Java DTOs. OpenAPI generation is not used because the current document omits precise authentication response schemas and the WAR context root.

## Authentication and CSRF lifecycle

At startup, a TanStack Query-backed auth provider calls `/smartcity360/api/v1/auth/me`. Protected content remains behind a loading boundary until the result is known. A 401 means anonymous, including Payara's HTML 401 page; other failures produce a retryable connection state.

The CSRF token is held only in module memory. Login first obtains `/auth/csrf`, then sends `{email,password}` plus `X-CSRF-Token`. Successful login stores no credential data; it updates the current-user query from the response and leaves the browser to manage `JSESSIONID`. Logout sends the current/refreshed CSRF token, accepts only 204 success, then clears user, CSRF, and feature-query state. Login failures are generic; passwords are never logged or persisted.

On a later 401, the client clears authenticated state and redirects to `/login` while preserving an internal safe return path. The return path must be a local `/app` route, preventing open redirects. The user sees an expired-session notice rather than stale protected content.

## Routing and role-aware navigation

- `/login`: anonymous login page; authenticated users go to `/app`.
- `/app`: available to every authenticated backend role.
- `/app/complaints`: available to all five roles because backend behavior is ownership/role-specific.
- `/app/zones`, `/app/streetlights`, `/app/policies`: visible to `ADMIN`, `CITY_MANAGER`, `SUPERVISOR`, and `LOCAL_STAFF`, matching existing read boundaries.
- `/app/energy`: visible to `ADMIN`, `CITY_MANAGER`, and `SUPERVISOR`, matching energy-run read boundaries. It does not expose meter ingestion.
- Direct navigation by an authenticated user without an allowed role renders an unauthorized view; unknown routes render not found.

These guards are usability controls only. All requests still rely on backend `@RolesAllowed` and current database-derived roles.

## Application shell and visual system

The shell uses a restrained civic blue/teal design with neutral surfaces, clear focus rings, semantic landmarks, and light/dark tokens. Desktop uses a fixed sidebar plus header; mobile uses an accessible shadcn dialog/sheet opened by a labelled menu button. The header shows the real caller display name and roles, theme control, and logout. Navigation uses Lucide icons and route metadata rather than duplicated role checks.

The dashboard shows SmartCity360 identity, the authenticated user's safe profile, backend health response, and links to modules the caller can access. It does not display invented counts. Toasts report transient mutations; persistent failures use inline accessible error states. Motion is limited and disabled under `prefers-reduced-motion`.

## State boundaries

- TanStack Query owns server state: current user, health, and later module data.
- A small auth context exposes query-derived session status and login/logout actions; it does not become a general application store.
- Component state owns navigation and theme UI.
- React Hook Form and Zod own login form state and client-side validation.
- No Redux or token-persistence layer is introduced.

## Testing strategy

Tests follow red-green-refactor. Vitest, JSDOM, Testing Library, and user-event exercise observable behavior. Fetch is substituted only at the external network boundary with complete response fixtures.

Required tests cover:

- JSON success, empty 204, cancellation, safe HTML 401, malformed/non-JSON errors, and normalized status handling.
- Exact login JSON shape and absence of invented fields.
- CSRF retrieval and `X-CSRF-Token` forwarding on login/logout.
- `credentials: "same-origin"` on every API request; HttpOnly cookie content is never accessed by JavaScript.
- Session initialization loading boundary, anonymous state, authenticated state, and 401 expiry cleanup.
- Protected-route redirect with safe return path, authenticated forbidden view, and not-found handling.
- Role-derived navigation for each backend role group.
- Logout accepting an empty 204 response and clearing authenticated state.
- Responsive navigation opening, keyboard dismissal, focus behavior, and accessible labelling.
- TLS configuration validation for absent variables, missing files, invalid PEM, mismatched key/certificate, expired/not-yet-valid certificates, missing localhost SAN, and Payara probe failure. Fixtures contain disposable test certificates only and no production secret.

Isolated frontend tests do not prove Payara integration. Live verification requires the SAN-correct Payara certificate, a trusted frontend certificate, and an approved disposable user.

## Verification and completion boundary

Implementation verification runs npm type checking, ESLint, normal Vitest tests, production Vite build, and the existing Maven reactor using Microsoft JDK 17 and Maven 3.9.16. It checks that the EAR contents and migration ledger remain unchanged and verifies the backend health endpoint.

Once certificates are manually provisioned, live checks start Vite over HTTPS and verify strict TLS, proxy health, CSRF retrieval, HTML 401 handling, approved disposable-user login, cookie persistence, authenticated `/auth/me`, protected navigation, logout, and post-logout 401. Temporary users and associated test records are cleaned only through already approved safe mechanisms. If no disposable user is approved, authenticated runtime checks remain explicitly pending.

## Builder decisions left open

- Select exact compatible patch versions within the approved major-version set and retain `package-lock.json`; avoid newly released incompatible majors.
- Choose the smallest shadcn primitives necessary for login and shell rather than generating the whole catalog.
- Organize test helpers around real response shapes without adding production-only hooks for tests.
- Keep certificate provisioning manual; implementation may validate and document it but must not mutate Payara, keystores, or Windows trust automatically.

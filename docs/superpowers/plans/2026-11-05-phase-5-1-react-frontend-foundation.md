# Phase 5.1 React Frontend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone, tested React/TypeScript foundation that securely integrates with SmartCity360's existing Payara session and CSRF contracts through a strictly verified HTTPS Vite proxy.

**Architecture:** `frontend/` is an independent Vite workspace. Browser requests remain relative under `/smartcity360/api/v1`; Vite proxies them without path rewriting or Host/Origin replacement and verifies Payara through explicit CA material. TanStack Query owns server state, an auth provider coordinates session lifecycle, and route metadata supplies both guards and role-aware navigation.

**Tech Stack:** Node 24.13.0, npm 11.6.2, React 19.2.0, TypeScript 5.9.3, Vite 7.3.7, React Router 7.18.5, Tailwind CSS 4.1.17, TanStack Query 5.90.10, React Hook Form 7.66.1, Zod 4.6.7, Lucide React 0.553.0, Vitest 4.1.12, Testing Library 16.3.0, JSDOM 27.2.0.

**Spec:** `docs/superpowers/specs/2026-11-05-phase-5-1-react-frontend-foundation-design.md`

## Global Constraints

- Preserve the Jakarta EE common/EJB/WAR/EAR modules and all backend source, migrations, data, authentication, CSRF, and role enforcement.
- Do not modify the Payara keystore, listener alias, Windows trust store, or permanent users.
- Use `https://localhost:5173`, target `https://localhost:8181`, relative `/smartcity360/api/v1` paths, `changeOrigin: false`, `secure: true`, and explicit CA trust.
- Never use `secure: false`, `NODE_TLS_REJECT_UNAUTHORIZED=0`, cookie rewriting, JWT, local/session storage for credentials or CSRF, or wildcard credentialed CORS.
- Keep certificate/key files and machine paths outside Git; fail closed on missing or invalid TLS configuration.
- Do not implement Phase 5.2 business pages or bundle the frontend into the EAR.
- Do not stage or commit files; each task ends with a verification checkpoint instead.

## Review Focus

- A Payara HTML 401 body must become a generic typed 401 error without HTML reaching the UI (Task 2).
- A malicious external return path must fall back to `/app`, never become an open redirect (Task 4).
- A session-expiry 401 during protected use must clear user, CSRF, and cached protected data before redirecting (Task 3).
- Missing, unreadable, mismatched, expired, SAN-invalid, or untrusted TLS material must prevent development startup or the Payara probe from succeeding (Task 1).
- Mobile navigation must be keyboard-operable, close on selection/Escape, and restore focus (Task 5).

---

### Task 1: Workspace, dependency lock, and strict TLS configuration

**Files:**
- Create: `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig.json`, `frontend/tsconfig.app.json`, `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`, `frontend/eslint.config.js`, `frontend/components.json`, `frontend/index.html`, `frontend/.env.example`
- Create: `frontend/config/tls.mjs`, `frontend/config/tls.test.ts`, `frontend/scripts/verify-tls.mjs`
- Create: `frontend/src/test/setup.ts`, `frontend/src/vite-env.d.ts`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `loadTlsConfiguration(env): { viteCertificate, vitePrivateKey, payaraCa, payaraAgent }`; `verifyPayaraTls(options): Promise<VerifiedPeer>`; Vite proxy at `/smartcity360/api` with unchanged path, `changeOrigin:false`, `secure:true`.

- [ ] Write failing TLS configuration tests for missing variables/files, malformed certificate/key, mismatched key, invalid validity/SAN, and failed strict Payara handshake using disposable fixtures.
- [ ] Run `npm test -- config/tls.test.ts` and verify failure because the TLS loader does not exist.
- [ ] Create the npm workspace with the pinned dependencies and lockfile; configure TypeScript, ESLint, Vitest/JSDOM, Tailwind Vite plugin, and test setup.
- [ ] Implement `config/tls.mjs`, the strict pre-development probe, and `vite.config.ts`; never log private key contents or create trust material.
- [ ] Run the focused TLS tests and confirm they pass.
- [ ] Run `npm run typecheck` and `npm run lint`; correct configuration errors without weakening rules.

### Task 2: Typed API contracts and safe HTTP client

**Files:**
- Create: `frontend/src/lib/api/contracts.ts`, `frontend/src/lib/api/api-error.ts`, `frontend/src/lib/api/client.ts`, `frontend/src/lib/api/client.test.ts`
- Create: `frontend/src/lib/api/paths.ts`, `frontend/src/lib/api/csrf-store.ts`, `frontend/src/lib/api/csrf-store.test.ts`

**Interfaces:**
- Produces: `CurrentUser`, `CsrfResponse`, `LoginRequest`, `HealthStatus`; `ApiError`; `apiRequest<T>(path, options): Promise<T>`; `getCsrfToken(): Promise<string>`; `clearCsrfToken(): void`.

- [ ] Write failing tests for JSON success, exact login body, same-origin credentials, request cancellation, CSRF forwarding, status normalization, safe HTML/malformed 401, and empty 204.
- [ ] Run `npm test -- src/lib/api/client.test.ts src/lib/api/csrf-store.test.ts` and verify failures are caused by missing client behavior.
- [ ] Implement exact Java DTO contracts, content-type-aware parsing, safe errors, AbortSignal forwarding, and 204 handling.
- [ ] Implement single-flight in-memory CSRF retrieval and clearing; never expose storage persistence.
- [ ] Run focused tests and then the full frontend test suite.

### Task 3: Session initialization, login, logout, and expiry

**Files:**
- Create: `frontend/src/app/query-client.ts`, `frontend/src/app/providers.tsx`
- Create: `frontend/src/features/auth/auth-api.ts`, `frontend/src/features/auth/auth-context.tsx`, `frontend/src/features/auth/use-auth.ts`
- Create: `frontend/src/features/auth/login-schema.ts`, `frontend/src/features/auth/login-page.tsx`
- Test: `frontend/src/features/auth/auth-context.test.tsx`, `frontend/src/features/auth/login-page.test.tsx`

**Interfaces:**
- Consumes: Task 2 API client/contracts/CSRF store.
- Produces: `AuthProvider`; `useAuth(): {status,user,login,logout}`; `LoginPage`.

- [ ] Write failing tests for unresolved-session loading, HTML-401 anonymous initialization, authenticated initialization, login validation/contract, generic failure, 204 logout, and expiry cleanup.
- [ ] Run the focused auth tests and verify expected behavior failures.
- [ ] Implement auth query/mutations, query-cache cleanup, in-memory CSRF cleanup, and password-safe form behavior with React Hook Form/Zod.
- [ ] Implement accessible login UI using the minimum local shadcn-compatible controls.
- [ ] Run focused and full frontend tests.

### Task 4: Protected routing and role policy

**Files:**
- Create: `frontend/src/routes/route-policy.ts`, `frontend/src/routes/route-policy.test.ts`
- Create: `frontend/src/routes/protected-route.tsx`, `frontend/src/routes/protected-route.test.tsx`, `frontend/src/routes/router.tsx`
- Create: `frontend/src/components/common/unauthorized-view.tsx`, `frontend/src/components/common/not-found-view.tsx`, `frontend/src/components/common/module-placeholder.tsx`

**Interfaces:**
- Consumes: Task 3 auth state.
- Produces: canonical route metadata and `canAccessRoute(roles, route): boolean`; protected router with safe local return path.

- [ ] Write failing role-table tests for all five roles plus tests for loading, anonymous redirect, safe return navigation, forbidden direct access, and not found.
- [ ] Run focused route tests and verify they fail because guards/policies are absent.
- [ ] Implement route metadata and guards; use the same metadata for later navigation and reject external return paths.
- [ ] Add clearly labelled placeholder views without mock data or speculative requests.
- [ ] Run focused and full frontend tests.

### Task 5: Responsive shell, theme, dashboard, and accessible states

**Files:**
- Create: `frontend/src/components/ui/button.tsx`, `frontend/src/components/ui/card.tsx`, `frontend/src/components/ui/input.tsx`, `frontend/src/components/ui/label.tsx`, `frontend/src/components/ui/sheet.tsx`, `frontend/src/components/ui/dropdown-menu.tsx`
- Create: `frontend/src/components/layout/app-shell.tsx`, `frontend/src/components/layout/sidebar.tsx`, `frontend/src/components/layout/header.tsx`, `frontend/src/components/layout/theme-toggle.tsx`
- Create: `frontend/src/components/common/loading-state.tsx`, `frontend/src/components/common/error-state.tsx`, `frontend/src/components/common/empty-state.tsx`, `frontend/src/components/common/app-error-boundary.tsx`
- Create: `frontend/src/features/dashboard/dashboard-page.tsx`, `frontend/src/features/dashboard/health-api.ts`
- Create: `frontend/src/lib/utils.ts`, `frontend/src/styles/index.css`, `frontend/src/main.tsx`
- Test: `frontend/src/components/layout/app-shell.test.tsx`, `frontend/src/features/dashboard/dashboard-page.test.tsx`

**Interfaces:**
- Consumes: Tasks 2-4 APIs, auth state, and route metadata.
- Produces: responsive application shell and dashboard using only current user and real health response.

- [ ] Write failing tests for role-filtered links, real user display, logout action, mobile menu accessibility/Escape/focus restoration, loading/error health states, and absence of fabricated metrics.
- [ ] Run focused shell/dashboard tests and verify expected failures.
- [ ] Implement focused shadcn-compatible primitives, theme provider/control, responsive shell, common states, Sonner notifications, and reduced-motion/focus styles.
- [ ] Implement health-backed dashboard and wire the router/bootstrap.
- [ ] Run focused tests, full tests, typecheck, lint, and `npm run build`.

### Task 6: TLS/operator guidance and complete verification

**Files:**
- Create: `frontend/README.md`
- Modify only if needed for verified frontend behavior: frontend files from Tasks 1-5
- Do not modify: Payara keystore/listener, backend source, migrations, application data

**Interfaces:**
- Consumes: complete frontend, actual domain status, explicit certificate paths.
- Produces: reproducible manual certificate procedure and evidence-separated Phase 5.1 verification.

- [ ] Document the actual Microsoft JDK/Payara paths, interactive-password keystore backup/new-alias/listener procedure, separate Vite certificate, environment variables, and strict verification commands.
- [ ] Run `npm run typecheck`, `npm run lint`, `npm test -- --run`, and `npm run build`; capture totals and failures.
- [ ] Run Maven 3.9.16 with `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot` against `backend/pom.xml`; verify tests and EAR structure.
- [ ] Verify migration ledger read-only and confirm no backend/database changes were made.
- [ ] Run `npm run verify:tls`; expect a documented failure until the manually provisioned SAN-correct Payara and trusted Vite certificates exist.
- [ ] If and only if certificate verification passes and an approved disposable user exists, start Vite and verify health, CSRF, HTML 401, login, cookie persistence, `/auth/me`, protected navigation, logout, and post-logout 401; otherwise report these exact checks as pending.
- [ ] Inspect Git status and report every created/modified file without staging or committing.

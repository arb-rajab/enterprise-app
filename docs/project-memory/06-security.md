# Security

## Authentication
- BCrypt password hashing (`BCryptPasswordEncoder`, Spring Security default strength).
- Stateless JWT (HS256), issued by `JwtService`, validated per-request by
  `JwtAuthenticationFilter`. See `adr/0002-jwt-based-authentication.md` for the full rationale
  and accepted trade-offs (no server-side revocation, no refresh-token rotation yet).
- The signing secret (`app.security.jwt.secret` / `JWT_SECRET` env var) is required, checked to
  be ≥ 256 bits at `JwtService` construction, and the app **fails to start** without it outside
  the `dev` profile default. There is no hardcoded production secret anywhere in the codebase.
- **OIDC/SSO login** (Spring Security `oauth2Login`, against a local Keycloak container) exists
  as a second, additional way to reach an authenticated session - not a replacement for the two
  points above. `OidcAuthenticationSuccessHandler` provisions/links a local user (by email; see
  `adr/0005-oidc-sso-identity-linking.md` for the full identity-linking decision and its
  trade-offs) and mints the *same* JWT `JwtService` issues for password logins, so everything
  downstream of login - authorization, row-level checks, the token format itself - is identical
  regardless of which path was used. The OIDC handshake runs on its own `SecurityFilterChain`
  (`oidcFilterChain`, session-based, matching only `/oauth2/**` and `/login/**`); the JWT API
  chain (`apiFilterChain`) is untouched and stays fully stateless.

## Authorization
- Method-level `@PreAuthorize` on controllers for role checks (`hasRole(...)` /
  `hasAnyRole(...)`).
- Data-scoped checks inside services where a role alone can't express the rule — e.g.
  `RequisitionService.requireOwner()` ensures only the requisition's own requester (or an Admin)
  can submit/cancel it, and `decide()` checks the acting user's roles against the specific
  pending step's required role, not just "any approver role."
- **Known gap, accepted for this demo:** `GET` list endpoints for requisitions
  (`/api/v1/requisitions`) return *all* requisitions to any authenticated user, not just the
  caller's own or their department's. Write actions are correctly scoped (see above); read
  visibility is not row-level restricted. Tracked in `09-backlog.md`. This is disclosed here
  deliberately rather than silently shipped as if it were full row-level security.

## Password / credential handling
- Minimum password length enforced at registration (8 characters, `RegisterRequest` Bean
  Validation).
- Demo/seed accounts (`V2__seed_reference_data.sql`) all share one throwaway password
  (`Password123!`), documented in the login screen itself and in `README.md`. These are
  **not real credentials for anything** — the repository, its seed data, and its JWT signing
  secret in the `dev` Spring profile are all meant to be cloned and run locally by anyone.

## Transport & headers
- The nginx layer in front of the built frontend sets `X-Frame-Options: SAMEORIGIN`,
  `X-Content-Type-Options: nosniff`, and `Referrer-Policy: strict-origin-when-cross-origin`
  (`frontend/nginx.conf`).
- TLS termination is explicitly out of scope for this repo — it assumes a platform-level load
  balancer/ingress handles TLS in any real deployment (see `08-ops.md`); no cleartext-HTTP-only
  assumption is baked into the app logic itself (cookies aren't used at all; the JWT is sent as a
  header, not a cookie, so there's no `Secure`/`SameSite` cookie attribute story to get wrong).

## Input validation & injection
- All request DTOs use Jakarta Bean Validation (`@NotBlank`, `@Email`, `@DecimalMin`, etc.);
  `GlobalExceptionHandler` converts validation failures into a structured 400 response.
- All database access goes through Spring Data JPA / JPQL derived queries and `@Query`-free
  repositories — no string-concatenated SQL anywhere, so classic SQL injection is not a
  realistic risk in the current codebase.
- Flyway migrations are the only place raw SQL exists, and they are static, reviewed, versioned
  files, not built from runtime input.

## Dependency & container hygiene
- Both Dockerfiles are multi-stage (build stage discarded from the final image) and run the
  application as a **non-root user** (`procureflow` in the backend image, the stock `nginx` user
  in the frontend image, deliberately not `root`).
- Dependabot is enabled for this repository (see the PR that introduced it and
  `12-session-handoff.md` for the verification status of that specific check at merge time —
  dependency-vulnerability status is a point-in-time fact that belongs in the handoff log, not
  duplicated here).

## Secrets management
- No secret is committed to the repository. `.env` is git-ignored; `.env.example` documents the
  variable names only, with placeholder values. `docker-compose.yml`'s `backend` service uses
  Compose's `${VAR:?error message}` syntax for `JWT_SECRET` specifically so that forgetting to
  set it is a loud, immediate failure, not a silent fallback to an insecure default.
- The Keycloak client secret (`OIDC_CLIENT_SECRET`) is the one deliberate exception: it has a
  fixed, committed dev-only default (`procureflow-dev-secret`, matching
  `keycloak/procureflow-realm.json`), because it's a secret shared between this repo's own two
  local services, not a real credential for anything - see `keycloak/README.md`. It follows the
  same "never a real production default" rule as the seed-data password in the point above.

## What a real production hardening pass would still need to add
(Explicitly deferred — see `09-backlog.md` for the full list with reasoning per item.)
Rate limiting on `/api/v1/auth/**`, account lockout after repeated failed logins, JWT revocation
(deny-list or short-TTL + refresh rotation), row-level read authorization on list endpoints,
structured audit-log export/retention policy, and a real secrets manager (Vault/AWS Secrets
Manager/etc.) instead of environment variables.

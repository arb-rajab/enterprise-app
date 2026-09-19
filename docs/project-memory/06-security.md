# Security

## Authentication
- BCrypt password hashing (`BCryptPasswordEncoder`, Spring Security default strength).
- Stateless JWT access tokens (HS256), issued by `JwtService`, validated per-request by
  `JwtAuthenticationFilter` with no DB lookup. See `adr/0002-jwt-based-authentication.md` for the
  full rationale.
- **Refresh-token rotation and revocation**: `login`/`register` also issue an opaque, single-use
  refresh token (only its SHA-256 hash is persisted, in `refresh_tokens`); `POST
  /api/v1/auth/refresh` validates, revokes, and replaces it with a new pair, and `POST
  /api/v1/auth/logout` revokes it outright. See `adr/0006-jwt-refresh-token-rotation-and-revocation.md`
  for the design and its accepted residual limitation: an already-issued access token that hasn't
  expired yet is still not revocable, since validating it stays a stateless, no-DB-lookup
  operation by design (ADR-0002). That residual window is bounded by the access-token TTL
  (`app.security.jwt.access-token-ttl-minutes`, default 30 minutes).
- The signing secret (`app.security.jwt.secret` / `JWT_SECRET` env var) is required, checked to
  be ≥ 256 bits at `JwtService` construction, and the app **fails to start** without it outside
  the `dev` profile default. There is no hardcoded production secret anywhere in the codebase.

## Authorization
- Method-level `@PreAuthorize` on controllers for role checks (`hasRole(...)` /
  `hasAnyRole(...)`).
- Data-scoped checks inside services where a role alone can't express the rule — e.g.
  `RequisitionService.requireOwner()` ensures only the requisition's own requester (or an Admin)
  can submit/cancel it, and `decide()` checks the acting user's roles against the specific
  pending step's required role, not just "any approver role." For the `ROLE_DEPARTMENT_MANAGER`
  step specifically, `decide()` also requires the approver's own department to match the
  requisition's department — see `adr/0007-department-scoped-approval-authorization.md` for the
  cross-department approval bypass this closed (a Department Manager from any department could
  previously approve/reject any other department's requisitions) and why
  `ROLE_PROCUREMENT_OFFICER`/`ROLE_FINANCE_APPROVER` are deliberately excluded from this
  restriction.
- **Row-level read scoping** on requisitions and purchase orders: `ROLE_ADMIN`,
  `ROLE_PROCUREMENT_OFFICER`, and `ROLE_FINANCE_APPROVER` can read every row (matching the
  org-wide approval authority `decide()` already grants them); `ROLE_DEPARTMENT_MANAGER` is
  scoped to their own department; everyone else sees only rows they requested themselves.
  Implemented once in `ReadScopePolicy` and applied by both services'
  `findVisibleTo()`/`findVisibleById()`. See `adr/0005-row-level-read-scoping.md` for the design
  question this resolved and why the previous "any authenticated user sees everything" behavior
  was disclosed as an accepted gap rather than shipped silently.

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
- **Dependabot version updates** are configured via `.github/dependabot.yml` (added in the PR
  that introduced this bullet's correction — previously this doc claimed Dependabot was already
  enabled and cited "the PR that introduced it," but no such PR or config file existed; that was
  inaccurate and has been fixed rather than left standing). It covers all four ecosystems present
  in this repo: `maven` (`/backend`), `npm` (`/frontend`), `docker` (both `/backend` and
  `/frontend` Dockerfiles), and `github-actions` (`/`), each on a weekly schedule.
- **Dependabot security alerts** (the repository Settings → Security toggle that scans existing
  dependencies for known CVEs) is a distinct, admin-only setting — not something a committed file
  controls, and not something verifiable or changeable via the tooling available in a worker
  session. It needs to be confirmed/enabled by a repository admin in GitHub Settings; see
  `12-session-handoff.md` for the current verification status of that specific check.

## Secrets management
- No secret is committed to the repository. `.env` is git-ignored; `.env.example` documents the
  variable names only, with placeholder values. `docker-compose.yml`'s `backend` service uses
  Compose's `${VAR:?error message}` syntax for `JWT_SECRET` specifically so that forgetting to
  set it is a loud, immediate failure, not a silent fallback to an insecure default.

## What a real production hardening pass would still need to add
(Explicitly deferred — see `09-backlog.md` for the full list with reasoning per item.)
Rate limiting on `/api/v1/auth/**`, account lockout after repeated failed logins, silent
background access-token renewal in the frontend (the refresh endpoint exists and is used on
explicit logout, but the SPA doesn't yet call it proactively before an access token expires),
scheduled cleanup of expired/revoked `refresh_tokens` rows, structured audit-log export/retention
policy, and a real secrets manager (Vault/AWS Secrets Manager/etc.) instead of environment
variables.

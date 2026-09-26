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
- **OIDC/SSO login** (Spring Security `oauth2Login`, against a local Keycloak container) exists
  as a second, additional way to reach an authenticated session - not a replacement for the two
  points above. `OidcAuthenticationSuccessHandler` provisions/links a local user (by email; see
  `adr/0008-oidc-sso-identity-linking.md` for the full identity-linking decision and its
  trade-offs) and mints the *same* JWT `JwtService` issues for password logins, so everything
  downstream of login - authorization, row-level checks, the token format itself - is identical
  regardless of which path was used. The OIDC handshake runs on its own `SecurityFilterChain`
  (`oidcFilterChain`, session-based, matching only `/oauth2/**` and `/login/**`); the JWT API
  chain (`apiFilterChain`) is untouched and stays fully stateless.
- **Deactivated accounts are blocked on every login/renewal path, not just password login.**
  Password login blocks a deactivated user via `AuthenticationManager` ->
  `UserPrincipal.isEnabled()` -> `user.isActive()`. OIDC/SSO login now runs the same check (via
  Spring Security's own `AccountStatusUserDetailsChecker`, not a second hand-rolled test) before
  minting tokens, and refresh-token rotation (`RefreshTokenService.rotate()`) now re-checks the
  token owner's active status on every renewal, not just at initial login - both were real,
  previously-open gaps; see `adr/0008-oidc-sso-identity-linking.md`'s and
  `adr/0006-jwt-refresh-token-rotation-and-revocation.md`'s amendments for the full account of each
  and the regression tests proving them closed.

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

## Rate limiting
`POST /api/v1/auth/login`, every other REST endpoint, and the gRPC listener are all now rate
limited - previously none of them were. In-memory `bucket4j` token buckets (no Redis/shared cache
exists in this stack); see `adr/0010-rate-limiting.md` for the full mechanism, thresholds, and the
reasoning behind each. Account lockout (a distinct, persistent-per-account mechanism) remains
deferred - see `09-backlog.md`.

## Transport & headers
- The nginx layer in front of the built frontend sets `X-Frame-Options: SAMEORIGIN`,
  `X-Content-Type-Options: nosniff`, and `Referrer-Policy: strict-origin-when-cross-origin`
  (`frontend/nginx.conf`).
- TLS termination is explicitly out of scope for this repo — it assumes a platform-level load
  balancer/ingress handles TLS in any real deployment (see `08-ops.md`); no cleartext-HTTP-only
  assumption is baked into the app logic itself (cookies aren't used at all; the JWT is sent as a
  header, not a cookie, so there's no `Secure`/`SameSite` cookie attribute story to get wrong).
- **The gRPC listener (`app.grpc.port`, ADR-0009) is plaintext** (`ServerBuilder.forPort(...)`, no
  TLS credentials configured) - checked directly, not assumed, and **deliberately deferred rather
  than fixed in this pass**, unlike REST's TLS story above which at least has an explicit
  "platform-level ingress handles it" answer on record. gRPC has no equivalent documented answer
  yet: nothing here says a real deployment's ingress/mesh terminates TLS in front of port 9090 the
  way it's assumed to for `server.port`. This matters more for gRPC than it might otherwise, since
  the bearer token `GrpcAuthInterceptor` reads from call metadata is exactly the kind of credential
  cleartext transport would expose in transit. Left open because a correct fix needs a real
  decision this pass didn't have the scope to make (mTLS between services vs. TLS terminated by an
  ingress/mesh in front of 9090, and how a local/dev environment without either still works) rather
  than a rushed `ServerBuilder.useTransportSecurity(...)` call with a self-signed dev cert wired in
  ad hoc. Tracked as an explicit, named gap in `09-backlog.md`, not silently left for a future
  session to rediscover.

## Input validation & injection
- All request DTOs use Jakarta Bean Validation (`@NotBlank`, `@Email`, `@DecimalMin`, etc.);
  `GlobalExceptionHandler` converts validation failures into a structured 400 response.
- All database access goes through Spring Data JPA / JPQL derived queries and `@Query`-free
  repositories — no string-concatenated SQL anywhere, so classic SQL injection is not a
  realistic risk in the current codebase.
- Flyway migrations are the only place raw SQL exists, and they are static, reviewed, versioned
  files, not built from runtime input.

## Spring Boot / Spring Security version and CVE applicability
Investigated directly (dependency tree resolved and read, not assumed from a changelog) rather
than upgraded blindly or dismissed without checking:

- **Before this pass:** `spring-boot-starter-parent` 3.3.4 (Sept 2024) -> Spring Security 6.3.3,
  Spring Web/WebMVC 6.1.13. Two flagged CVEs assessed against this app's actual code, not just its
  dependency versions:
  - **CVE-2024-38827** (Spring Security authorization bypass for case-sensitive comparisons -
    `String.toLowerCase()`/`toUpperCase()` without `Locale.ROOT` inside Spring Security's own
    internal case-folding, affecting 6.3.0-6.3.4). In range at 6.3.3. **Applicability to this app:**
    low-but-nonzero as actually deployed - this app defines no custom, case-insensitive
    `RequestMatcher`s and does no locale-dependent case folding of its own in any
    authorization-relevant comparison (`SecurityConfig`'s `authorizeHttpRequests` uses plain string
    path patterns and `hasRole`/`authenticated()`/`permitAll()`; `UserService`'s own
    `.toLowerCase()` calls are on email *storage*, not an authorization decision). The residual
    risk is that JVM default locale is a deployment-environment fact (`-Duser.language`/container
    locale), not something this app's code pins - so "not exploitable under this app's current
    deployment locale" is not the same guarantee as "not exploitable, full stop." Fixed anyway via
    the version bump below - a same-branch patch bump made this a zero-cost, no-behavior-change fix
    rather than one worth leaving as an accepted risk.
  - **CVE-2025-22235** (`EndpointRequest.to()` builds the wrong matcher for an actuator endpoint
    that isn't exposed, potentially authorizing more than intended). **Not applicable** - confirmed
    directly by grepping this codebase for `EndpointRequest`: zero matches. `SecurityConfig`
    permits `/actuator/health/**`/`/actuator/info` as plain string path patterns, never via
    `EndpointRequest.to(...)`, so the vulnerable API is never called here. Fixed by the version
    bump below anyway (defense in depth; costs nothing).
  - **CVE-2024-38816** (spring-webmvc `RouterFunctions`-served static resource path traversal,
    affects up to 6.1.11) - checked and found **already not applicable even before this pass**:
    this app was already on spring-webmvc 6.1.13 (pulled in transitively by `spring-boot-starter-
    parent` 3.3.4's own dependency management), past the 6.1.12 fix, and serves no static resources
    via `RouterFunctions` regardless.
- **Upgraded:** `spring-boot-starter-parent` 3.3.4 -> **3.3.13** - the last version published to
  Maven Central for the 3.3.x line (checked directly against Central's metadata, not assumed);
  Spring Security is pulled to 6.3.10 (well past the 6.3.5 fix for CVE-2024-38827) and
  spring-webmvc to 6.1.21. A same-minor-line patch bump, not a major-version jump: no breaking
  changes expected or encountered, and none were - `mvn spotless:apply` and the full `mvn test`
  suite (all backend unit tests, including the real-Spring-context
  `ProcureFlowApplicationContextTest`) pass unchanged after the bump. Integration tests
  (Docker-gated, see `07-testing.md`) are CI-verified, same caveat as every other change in this
  project.
- **What this doesn't fix, named rather than left implicit: the 3.3.x branch itself is now fully
  end-of-life for open-source users** (checked directly against Maven Central - 3.3.13 is the
  newest version Central has for this line, and multiple 2026 CVEs, e.g. CVE-2026-40973/40974,
  already have fixes published only as far as commercial-only 3.3.19 builds this project has no
  access to). Bumping within 3.3.x closes the two CVEs actually flagged for this pass, but does not
  put this app on a branch that will keep receiving open-source security patches going forward.
  Migrating to Spring Boot 4.x (the actively-supported open-source line as of this pass) is a
  major-version jump with real, unassessed breaking-change surface (Spring Framework 7 baseline,
  possible Jakarta/dependency-floor changes) that this session's scope and priority order (the
  OIDC/refresh-token fix and rate limiting both ranked above this) did not allow room to also
  investigate and validate safely. Tracked explicitly in `09-backlog.md` as the real follow-up this
  patch bump does not substitute for - not shipped as a rushed partial upgrade.

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
- The Keycloak client secret (`OIDC_CLIENT_SECRET`) is the one deliberate exception: it has a
  fixed, committed dev-only default (`procureflow-dev-secret`, matching
  `keycloak/procureflow-realm.json`), because it's a secret shared between this repo's own two
  local services, not a real credential for anything - see `keycloak/README.md`. It follows the
  same "never a real production default" rule as the seed-data password in the point above.

## What a real production hardening pass would still need to add
(Explicitly deferred — see `09-backlog.md` for the full list with reasoning per item.)
Account lockout after repeated failed logins (rate limiting itself is now built - see "Rate
limiting" above), TLS on the gRPC listener, silent background access-token renewal in the frontend
(the refresh endpoint exists and is used on explicit logout, but the SPA doesn't yet call it
proactively before an access token expires), scheduled cleanup of expired/revoked `refresh_tokens`
rows, structured audit-log export/retention policy, a real secrets manager (Vault/AWS Secrets
Manager/etc.) instead of environment variables, and migrating off the now fully end-of-life Spring
Boot 3.3.x branch to Spring Boot 4.x.

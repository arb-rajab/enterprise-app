# Session Handoff

## Session 6 — OIDC deactivation-check bypass fix, rate limiting (REST + gRPC), Spring Boot/Security CVE investigation

**Starting state verified:** `git fetch origin main` was run before any change. The pre-existing
branch `claude/oidc-deactivation-bypass-fix-b7mx40` was found to be 17 commits behind `origin/main`
(missing, among others, PR #4's OIDC/SSO work and PR #5's gRPC API - it had never been rebased past
the initial scaffold) - the same failure mode `13-divergent-history-incident.md` describes. Fixed
by resetting the branch to `origin/main` before any new work (`git checkout -B
claude/oidc-deactivation-bypass-fix-b7mx40 origin/main`), since no PR had been opened for it yet -
there was nothing of this branch's own to preserve.

**What was built, in priority order:**
1. **Fixed a real, live OIDC deactivation-check bypass.** `OidcAuthenticationSuccessHandler` never
   routed through `AuthenticationManager` (no password to check for SSO), so it never ran the
   `user.isActive()` check password login gets for free - a deactivated user could still complete
   OIDC login and get a valid token pair. Compounded: `RefreshTokenService.rotate()` also never
   checked the token owner's active status, so an already-issued refresh token (from either login
   path) kept renewing after deactivation. Reproduced first (both bugs verified to actually cause
   the failing tests below to fail against the pre-fix code, by temporarily reverting the fix and
   re-running them - not assumed from reading the code alone), then fixed: the OIDC handler now
   runs the linked user through Spring Security's own `AccountStatusUserDetailsChecker` (the same
   component `DaoAuthenticationProvider` uses internally, not a second hand-rolled check) before
   minting tokens; `rotate()` now also rejects once the token's user is deactivated. See
   `adr/0008-oidc-sso-identity-linking.md` and `adr/0006-jwt-refresh-token-rotation-and-revocation.md`'s
   amendments for the full account.
2. **Added rate limiting to the gRPC service and the REST API - both had none before this.** A
   general per-IP limit across the whole REST API, two extra tighter limits on `POST
   /api/v1/auth/login` (per-credential-and-IP, per-IP-alone), and a per-caller-remote-address limit
   on the gRPC listener, running ahead of `GrpcAuthInterceptor` the same way the REST login checks
   run ahead of body parsing. In-memory `bucket4j` buckets - no Redis/shared cache exists anywhere
   else in this stack. See `adr/0010-rate-limiting.md` for the full mechanism and threshold
   reasoning, including the honest caveat that these thresholds were sized by inspecting the
   Docker-gated IT suite's source rather than by executing it (this sandbox still can't run
   Testcontainers - see "Real blockers" below).
3. **Investigated the Spring Boot/Spring Security version and two flagged CVEs, rather than
   upgrading blindly or dismissing without checking.** CVE-2024-38827 (spring-security, in range at
   the pinned 6.3.3) and CVE-2025-22235 (actuator `EndpointRequest.to()`, confirmed **not
   applicable** - this codebase never calls that API) were both assessed against this app's actual
   code (dependency tree resolved and read, `SecurityConfig` inspected directly), then fixed anyway
   via a same-branch patch bump: `spring-boot-starter-parent` 3.3.4 -> 3.3.13 (confirmed directly
   against Maven Central to be the newest version published for the 3.3.x line - the whole branch
   is now fully open-source end-of-life). `mvn spotless:apply` and the full unit test suite pass
   unchanged after the bump. A full Spring Boot 4.x migration was investigated and explicitly
   **not** attempted - real, unassessed breaking-change surface that this session's priority order
   (ranked below the fix and rate limiting) didn't leave room to also validate safely. See
   `06-security.md`'s new "Spring Boot / Spring Security version and CVE applicability" section and
   `09-backlog.md`.
4. **gRPC TLS: deferred explicitly, not attempted.** Lower priority than the three items above per
   this session's own instructions, and correctly fixing it needs a real decision (mTLS vs.
   ingress/mesh-terminated TLS, and how local/dev still works) this session's remaining scope didn't
   have room for. Documented as a named, disclosed gap (not a silent one) in `06-security.md`'s
   Transport & headers section and `09-backlog.md`.

### Branch / PR
- Branch: `claude/oidc-deactivation-bypass-fix-b7mx40`
- PR: [#6](https://github.com/arb-rajab/enterprise-app/pull/6)
- Merge status: open, not yet merged as of this entry

### CI status per check
_Filled in below once the PR's CI has actually run — not claimed in advance._

### Test counts (as run directly, not just claimed)
- Backend unit tests: **52/52 passing** (`mvn test`, run directly in this session's sandbox), up
  from 42 at Session 5's `main` state. New: `OidcAuthenticationSuccessHandlerTest` (2 - the
  regression proof for the OIDC bypass, both verified to fail against the pre-fix handler by
  temporarily reverting it and re-running), `RefreshTokenServiceTest
  .rotateRejectsATokenWhoseUserHasSinceBeenDeactivated` (1, same fail-before/pass-after
  verification), `RateLimitFilterTest` (7), `GrpcRateLimitInterceptorTest` (1 - fully in-process,
  no Docker, proves the gRPC interceptor ordering directly).
- Backend integration tests: **written (2 new methods in the existing `AuthControllerIT`:
  `loginOfADeactivatedUserIsRejected` as the password-login baseline this fix must not disturb, and
  `refreshFailsOnceTheUsersAccountIsDeactivated`) but not executed in this session's sandbox** -
  same Docker-registry restriction as every prior session. Run in CI; see CI status above.
- Backend format check (Spotless): **passing** (`mvn spotless:apply` then `mvn test`, run directly,
  both before and after the Spring Boot version bump).
- `mvn dependency:tree`, resolved and read directly (not assumed) to confirm the CVE-relevant
  before/after versions recorded in `06-security.md`.

### Real blockers hit this session
1. **Docker registry access is still blocked in this sandbox** (same restriction as every prior
   session) - the new `AuthControllerIT` methods and the full `mvn verify` (Spotless `check`,
   Failsafe integration tests) could not be executed directly here. Verified in CI instead. This is
   also why `adr/0010-rate-limiting.md`'s thresholds were sized by inspecting the IT suite's source
   rather than by executing it and observing real call volume - a real, disclosed gap in how those
   numbers were validated, not a hidden one; if CI produces spurious 429s from legitimate IT
   traffic, the fix is raising those thresholds, not disabling a limiter.
2. `flexcatalog`/`bookslot` (this task's named reference for "this portfolio's established rate-
   limiting pattern") are separate repositories, not part of this session's default scope - added
   read access for this session specifically to study `bookslot`'s own rate-limiting decision
   (`D-0063` in that repo's decision log) before designing this repo's `adr/0010-rate-limiting.md`.
   That repo is Laravel (Laravel's own cache-backed `RateLimiter`/`throttle:` middleware, not
   `bucket4j`), so the *mechanism* isn't directly portable - what carried over is the *shape*: a
   two-tier (per-credential+IP, per-IP-alone) login limiter, a separate limiter for the other
   real abuse surface, a response shape matching this codebase's own existing error convention
   rather than the framework default, and stating threshold-validation honestly rather than
   presenting an unverified number as proven.

### What's left in the backlog for a future session
1. Confirm this PR's CI (in particular the new `AuthControllerIT` methods and whether the new rate
   limiters' default thresholds survive the full IT suite without a spurious 429 - see "Real
   blockers" above) actually goes green on a real Docker-enabled runner.
2. `adr/0010-rate-limiting.md`'s own follow-up: if this app is ever horizontally scaled, its
   in-memory rate limiters need to move to a shared/distributed backing store.
3. gRPC TLS (see `06-security.md`/`09-backlog.md`) and the Spring Boot 4.x migration (see
   `06-security.md`/`09-backlog.md`) - both explicitly deferred by this session, not overlooked.
4. Everything already listed in Session 5's list below, unchanged by this session's work.

## Session 5 — gRPC purchase-order status API, sharing REST's JWT auth and read-scoping

**Starting state verified:** `git fetch origin main` was run before any change. The pre-existing
local/remote branch `claude/grpc-api-shared-auth-v1spgw` was found to be 14 commits behind
`origin/main` (missing, among others, PR #3's approval-bypass fix, PR #4's OIDC/SSO work, and the
ADRs/migrations both introduced) — the exact failure mode `13-divergent-history-incident.md`
describes. Fixed by `git rebase origin/main` before any new work, rather than building on the
stale base and discovering the divergence later (as Session 4 did).

**What was built:** a read-only gRPC API, `PurchaseOrderGrpcService` (unary `GetPurchaseOrder`,
server-streaming `ListPurchaseOrders`), the first non-REST API surface in this project. See
`adr/0009-grpc-purchase-order-api.md` for the full rationale, in particular why this narrow,
read-heavy slice was chosen over mirroring every REST endpoint, and why authorization is shared
rather than reimplemented: a new `GrpcAuthInterceptor` validates the bearer token with the same
`JwtService` REST's `JwtAuthenticationFilter` uses, and `PurchaseOrderGrpcServiceImpl` calls the
same `PurchaseOrderService.findVisibleById`/`findVisibleTo` (and therefore the same
`ReadScopePolicy`, ADR-0005) the REST controller calls — no gRPC-only scoping logic exists to
drift out of sync with REST's.

### Branch / PR
- Branch: `claude/grpc-api-shared-auth-v1spgw`
- PR: [#5](https://github.com/arb-rajab/enterprise-app/pull/5)
- Merge status: open, not yet merged as of this entry

### CI status per check
_Filled in below once the PR's CI has actually run — not claimed in advance._

### Test counts (as run directly, not just claimed)
- Backend unit tests: **42/42 passing** (`mvn test`, run directly in this session's sandbox after
  the rebase, unchanged count from Session 4's `main` state — this session added no new
  `*Test.java`, only the new integration test below), including
  `ProcureFlowApplicationContextTest`, which proves the new gRPC beans (`GrpcServerLifecycle`,
  `GrpcAuthInterceptor`, `PurchaseOrderGrpcServiceImpl`) wire into the real Spring context cleanly
  without a Testcontainers Postgres.
- Backend integration tests: **written (1 new class, `PurchaseOrderGrpcAuthorizationIT`, 1 test
  method covering 8 distinct assertions) but not executed in this session's sandbox** — same
  Docker-registry restriction as every prior session. Uses a real `ManagedChannel` and the actual
  generated gRPC stub against `GrpcServerLifecycle`'s real bound port (not MockMvc, not a
  hand-rolled protocol stand-in) to prove: an unauthenticated call is rejected
  (`UNAUTHENTICATED`); the requester, their department manager, and Procurement (org-wide) can all
  read the purchase order via both RPCs; and a Department Manager from a *different* department is
  rejected (`PERMISSION_DENIED`) on `GetPurchaseOrder` and never appears in that manager's
  `ListPurchaseOrders` stream — the read-path sibling of ADR-0007's write-path (approval) bypass
  fix, now proven blocked on gRPC too. Run in CI on GitHub-hosted runners; see CI status above for
  the actual result.
- Backend format check (Spotless): **passing** (`mvn spotless:apply` then `mvn -q spotless:check`
  and `mvn test`, run directly).
- `mvn -B package -DskipTests` (proto codegen via `protobuf-maven-plugin` + compile + packaging,
  matching `backend/Dockerfile`'s build stage exactly): **succeeds**, run directly.

### Real blockers hit this session
1. **Docker registry access is still blocked in this sandbox** (same restriction as every prior
   session) — the new gRPC integration test and `mvn verify`'s Spotless `check`/Failsafe phases
   could not be executed directly here. Verified in CI instead.
2. **grpc-bom's transitive `protobuf-java` (3.25.5) didn't match the protoc version used to
   generate code (4.28.2)**, causing a real local compile failure (`RuntimeVersion` class missing,
   `Descriptors.FileDescriptor` API mismatches) the first time `mvn compile` was run after adding
   the dependencies. Fixed by pinning `protobuf-java` to the same version as the `protoc` artifact
   directly in `pom.xml` (Maven's "nearest wins" then overrides the BOM's transitive version) —
   caught and fixed locally before ever pushing, not discovered via CI.
3. **`io.grpc.stub.MetadataUtils.attachHeaders(stub, headers)`**, the convenience method assumed
   from older grpc-java documentation/examples, **does not exist in grpc-java 1.68.1** — only
   `newAttachHeadersInterceptor(Metadata)` (returning a `ClientInterceptor` for
   `stub.withInterceptors(...)`) remains. Also caught locally via `mvn test-compile`, not CI.
4. The pre-existing "starting branch already diverged from `origin/main`" incident (see "Starting
   state verified" above) — this is the third time this exact failure mode has hit a session
   (Sessions 3 and 4 also hit it, per their own entries above); this session's fix was simply to
   check for and rebase past it *before* writing any new code, which Session 4's entry already
   recommended as the fix for future sessions.

### What's left in the backlog for a future session
1. Confirm PR #5's CI (in particular the new `PurchaseOrderGrpcAuthorizationIT`, which spins up a
   real gRPC server/client pair alongside the existing Postgres Testcontainers container) actually
   goes green on a real Docker-enabled runner — this session could only get it to a
   "should work, unverified locally" state, same caveat every prior session's integration tests
   carried.
2. Everything already listed in Session 4's list above, unchanged by this session's work.

## Session 3 — Cross-department approval bypass fix, Dependabot correction, divergent-history root cause

_Numbered "Session 3" because PR #2 (`15e37b2`/`06c9e2c`/`81b4f29` — PO-number race condition,
row-level read scoping, JWT refresh/revocation) clearly happened as a second, distinct session's
work but left no handoff entry of its own here; see `13-divergent-history-incident.md`, which
this session wrote after noticing the same "patch around it, don't write down why" pattern in
that session's unresolved merge conflict._

**Starting state verified:** `git fetch origin main` was run before any change; the branch
`claude/approval-bypass-fix-82u8ke` did not yet exist on the remote, and the previously-checked-out
local branch was found to be behind `origin/main` (missing PR #2's commits), so it was reset to
`origin/main` before starting work — see `13-divergent-history-incident.md` for why that check
matters in this repo specifically.

**What was built:**
1. Fixed a cross-department approval bypass: `RequisitionService.decide()` checked only that the
   approver held the role required by the pending step, never that a `ROLE_DEPARTMENT_MANAGER`
   approver belonged to the requisition's own department. Any Department Manager could
   approve/reject any other department's requisitions. See
   `adr/0007-department-scoped-approval-authorization.md` for the fix and why
   `ROLE_PROCUREMENT_OFFICER`/`ROLE_FINANCE_APPROVER` are deliberately excluded (org-wide by
   design, per ADR-0004/ADR-0005).
2. Verified the `06-security.md` claim "Dependabot is enabled for this repository" was false —
   no `.github/dependabot.yml` and no PR introducing one existed anywhere in git history. Added
   `.github/dependabot.yml` (maven/backend, npm/frontend, docker x2, github-actions — weekly).
   Corrected the doc to distinguish that committable config (now fixed) from Dependabot security
   alerts, which is a repository Settings toggle this session's tooling cannot verify or change.
3. Root-caused the repeated divergent-history incidents — see `13-divergent-history-incident.md`.

### Branch / PR
- Branch: `claude/approval-bypass-fix-82u8ke`
- PR: [#3](https://github.com/arb-rajab/enterprise-app/pull/3)
- Merge status: open, not yet merged as of this entry

### CI status per check
_Filled in below once the PR's CI has actually run — not claimed in advance._

### Dependabot status
- **Version updates:** previously false — no config existed despite the doc's claim. Fixed by
  adding `.github/dependabot.yml` in PR #3.
- **Security alerts (Settings → Security toggle):** unverifiable with this session's tooling (no
  repository-admin API access to `GET /repos/{owner}/{repo}/vulnerability-alerts` or an
  equivalent was available). Needs a repo admin to confirm/enable directly in GitHub Settings.

### Test counts (as run directly, not just claimed)
- Backend unit tests: **38/38 passing** (`mvn test`, run directly in this session's sandbox),
  including the new regression test
  `RequisitionServiceTest.managerFromAnotherDepartmentCannotDecideOnAStep`, confirmed to fail
  against the pre-fix code before the fix was applied.
- Backend format (Spotless): `mvn spotless:apply` run before tests, per this repo's standing
  practice.
- Integration tests / Docker builds: not executed in this session's sandbox, same Docker-registry
  network restriction documented in Session 1's entry below — verified by CI instead.

### Real blockers hit this session
1. Same Docker-registry sandbox restriction as Session 1 — integration tests and Docker builds
   are CI-verified, not locally run.
2. No tooling access to the GitHub repository-admin API needed to verify the Dependabot
   security-alerts toggle or to change repository Settings (squash-merge, branch protection) —
   both documented for admin follow-up rather than guessed at or silently skipped.

## Session 1 — Initial architecture and core scaffold

**Starting state verified:** the repository was confirmed genuinely empty (no commits, no files
beyond an empty working tree) before any work began — this was checked directly (`git status`,
`git log`, directory listing) rather than assumed from the task description, per this session's
standing instructions.

**What was built:** see `10-release-notes.md` (v0.1.0) for the full feature list. In short: a
complete Spring Boot + Angular + PostgreSQL procurement/approval-workflow application with JWT
RBAC auth, Docker packaging, GitHub Actions CI, and this documentation set.

### Branch / PR
- Branch: `claude/enterprise-app-scaffold-gfsyzq`
- PR: _filled in below once opened_
- Merge status: _filled in below once resolved_

### CI status per check
_Filled in below once the PR's CI has actually run — not claimed in advance._

### Dependabot status
_Filled in below — verified via the tooling available in this session, or stated as
unverifiable if that tooling could not enumerate alerts. Never reported as "none found" without
that verification actually having happened._

### Test counts (as run directly, not just claimed)
- Backend unit tests: **20/20 passing** (`mvn test`, run directly in this session's sandbox).
- Backend integration tests: **written (2 classes, 5 test methods) but not executed in this
  session's sandbox** — the sandbox's network policy blocks the Docker registry pulls
  Testcontainers needs (confirmed by directly attempting `docker pull postgres:16-alpine`,
  which returned `429`/`403` from every registry tried). These run in CI on GitHub-hosted
  runners, which have normal registry access; see the CI status above for their actual result.
- Frontend unit tests: **46/46 passing** (`ng test`, run directly in this session's sandbox using
  the pre-installed Playwright Chromium as `CHROME_BIN`).
- Frontend lint: **0 errors/warnings** (`ng lint`, run directly).
- Backend format check (Spotless): **passing** (`mvn spotless:check`, run directly).

### Docker builds
**Not executed in this session's sandbox** — same network restriction as above (`docker build`
for both images needs base-image pulls: `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre-alpine`,
`node:22-alpine`, `nginx:1.27-alpine`, all blocked in-sandbox). The Dockerfiles were written and
reviewed carefully but their actual buildability is verified by the `docker` job in
`.github/workflows/ci.yml`, not by a claim made here.

### ADRs of note
- `adr/0001-monorepo-and-project-layout.md` — monorepo, feature-packaged backend, Flyway-owned
  schema.
- `adr/0002-jwt-based-authentication.md` — stateless JWT vs. sessions vs. OIDC.
- `adr/0003-frontend-backend-integration.md` — same-origin relative API path + reverse proxy,
  not cross-origin CORS, as the shipped integration pattern.
- `adr/0004-tiered-approval-thresholds.md` — the amount-based, sequential approval chain design.

### Real blockers hit this session
1. **Docker registry access is blocked in this sandbox** (see above) — affects integration-test
   execution and Docker image build verification. Not a code problem; disclosed rather than
   worked around by, e.g., skipping the tests or faking a result.
2. Everything else (Maven Central, npm registry, GitHub) was reachable normally.

### What's left in the backlog for a future session
See `09-backlog.md` for the full, categorized list. Highest-value next items, in the order a
follow-up session should probably tackle them:
1. Row-level read scoping on requisition list endpoints (`06-security.md` R2).
2. Fix the PO-number-generation race condition with a DB sequence (`08-ops.md`).
3. Verify the CI `docker` job and integration tests actually go green on a real runner (this
   session could only get them to a "should work, unverified" state).
4. Frontend e2e tests (Playwright) as a follow-up to the current unit-test-only frontend
   coverage.

## Session 4 — OIDC/SSO login (Keycloak), alongside existing JWT auth

_Numbered "Session 4" following Session 3's numbering convention above (Session 3 itself follows
Session 1 in this file only because the PR #2 session, "Session 2," left no handoff entry of its
own — see Session 3's note). This session's own branch was created before Session 3's PR (#3)
merged; see "Real blockers" below for what that caused and how it was fixed._

**What was built:** a second, additional login path — Spring Security `oauth2Login` against a
real local Keycloak container (`docker-compose.yml`'s `keycloak` service, seeded from
`keycloak/procureflow-realm.json`) — without touching the existing custom JWT login/register path.
See `adr/0008-oidc-sso-identity-linking.md` for the identity-linking decision (link by email onto
an existing JWT-registered account) and its trade-offs, and `06-security.md`'s new OIDC
subsection. Frontend: a "Sign in with SSO" button on the login page and a new `/sso/callback`
route that completes the handshake. Same-origin proxying for `/oauth2/**` and `/login/**` added to
`frontend/nginx.conf` and `frontend/proxy.conf.json`, consistent with ADR-0003.

### Branch / PR
- Branch: `claude/oidc-sso-spring-security-gj33gg`
- PR: [#4](https://github.com/arb-rajab/enterprise-app/pull/4)
- Merge status: open, not yet merged as of this entry; `mergeable_state: clean` (no conflicts), no
  open review threads, nothing outstanding on this session's side.

### CI status per check
As of commit `5d738c0` (workflow run 35487866430, the third CI attempt on this PR - see "Real
blockers" for the two real bugs the first two attempts caught and this session fixed):
- **Backend (build, test, lint)** — ✅ success. This is `mvn verify`: Surefire unit tests, Failsafe
  integration tests (including the real-Postgres and real-Keycloak-container ones this sandbox
  couldn't run), and the Spotless format check, all in one job.
- **Frontend (build, test, lint)** — ✅ success.
- **Docker image builds** — ✅ success (both Dockerfiles actually build on a real Docker-enabled
  runner).

The first two CI attempts on this PR (runs for commits `7154625` and `11b667f`) failed for real
reasons this session found and fixed - see "Real blockers hit this session" below for both. The
first ever attempt (before the merge in this session) also hit one transient Maven Central `429`
unrelated to this PR's code, resolved with one re-run per the repo's flake-handling convention.

### Test counts (as run directly, not just claimed)
- Backend unit tests: **42/42 passing** (`mvn test`, run directly in this session's sandbox, after
  merging `origin/main` — see "Real blockers" below), up from 38 as of Session 3's `main` state —
  2 new ones are `UserServiceOidcProvisioningTest`, covering the create-vs-link decision in
  isolation; 2 more are `ProcureFlowApplicationContextTest`, which boots the real Spring context
  against a throwaway in-memory H2 (no Docker) and is what actually caught the circular-dependency
  and application.yml-shadowing bugs below — `UserServiceOidcProvisioningTest`'s mocked unit tests
  alone never would have.
- Backend integration tests: **written (2 new classes: `OidcRedirectIT`, 2 methods;
  `OidcLoginProvisioningIT`, 2 methods) but not executed in this session's sandbox** — same Docker
  registry restriction as Session 1 (confirmed again, not just assumed carried over). These spin
  up a real Keycloak container (`testcontainers-keycloak`, pinned to a version whose transitive
  `keycloak-admin-client` is `26.0.0`, matching the `quay.io/keycloak/keycloak:26.0` image used
  everywhere else) and exercise real Keycloak-issued tokens through
  `UserService.findOrProvisionForOidc` — this is the regression proof that both auth paths coexist
  without interfering (`OidcLoginProvisioningIT.oidcLoginLinksAnExistingJwtAccountWithoutBreakingItsPasswordLogin`
  specifically re-asserts the seeded JWT account's password login succeeds before *and* after an
  OIDC login links onto it). Run in CI on GitHub-hosted runners; see CI status above for the
  actual result.
- Frontend unit tests: **50/50 passing** (`ng test`, run directly, after the merge), up from 46 at
  Session 1 — this session's 4 new ones (`Auth.completeSsoLogin`, `SsoCallback` token-present/
  token-missing/token-without-refresh-token paths) plus PR #2's refresh-token-handling tests
  already on `main`.
- Frontend lint: **0 errors/warnings** (`ng lint`, run directly).
- Frontend production build: **succeeds** (`ng build --configuration production`, run directly).
- Backend format check (Spotless): **passing** (`mvn spotless:apply` then `mvn test`, run
  directly — `mvn verify`'s Spotless `check` goal itself needs the same Docker-gated `verify`
  phase as the integration tests above, so it's confirmed in CI, not locally).

### Real blockers hit this session
1. **Docker registry access is still blocked in this sandbox** (same restriction as Session 1) —
   the new Keycloak-backed integration tests, the `keycloak` docker-compose service, and the
   Spotless `verify`-phase check could not be executed directly here. Verified in CI instead.
2. **Two networking subtleties specific to OIDC-behind-Docker**, resolved by design rather than
   left as an open risk: (a) the browser and the backend container reach Keycloak by different
   hostnames under docker-compose (`localhost:8180` vs. the `keycloak` service name) — handled by
   configuring each OAuth2 provider endpoint URI individually instead of one `issuer-uri`, see the
   comment in `application.yml`; (b) reconstructing the backend's own public base URL (used as the
   registered OAuth2 `redirect_uri`) from the inbound request depends on nginx forwarding the
   `Host` header *with its port* — `frontend/nginx.conf`'s new `/oauth2/`/`/login/` blocks use
   `$http_host`, not `$host`, specifically for this. Neither could be exercised end-to-end in this
   sandbox (needs the full Compose stack); flagged here for the first real run to confirm.
3. **This session hit `13-divergent-history-incident.md`'s exact failure mode a third time**: the
   branch handed to this session (`claude/oidc-sso-spring-security-gj33gg`) already existed on the
   remote, created against `main` from *before* PR #2 and PR #3 merged (JWT refresh/revocation as
   ADR-0006, row-level read scoping as ADR-0005, the approval-bypass fix as ADR-0007). This
   session's own `git fetch origin main` at the very start correctly fetched the *current* `main`,
   but the pre-existing branch had already diverged from it, and this went unnoticed through this
   session's entire build-and-test pass (it never re-checked the branch against fresh
   `origin/main` mid-session) — surfacing only when the opened PR came back `mergeable_state:
   dirty` and no CI had triggered. Two consequences of the base having moved: (a) this session's
   new ADR and Flyway migration had picked the same numbers (`0005`, `V3`) that PR #2/#3 had
   already claimed on `main` for unrelated work — resolved by merging `origin/main` into this
   branch and renumbering this session's files to the next free slots (`adr/0008-...`,
   `V5__add_oidc_identity.sql`); (b) the JWT login path had grown refresh-token rotation
   (ADR-0006) between this branch's creation and its merge, which `OidcAuthenticationSuccessHandler`
   didn't originally know about — fixed by having it also mint a `RefreshTokenService` refresh
   token, so an OIDC-originated session is revocable exactly like a password one (see ADR-0008's
   updated Consequences). Both fixes are in the merge commit on this branch. Worth an explicit
   callout for `13-divergent-history-incident.md`'s own admin-only recommendations (disabling
   squash-merge, branch protection): neither would have prevented *this* specific incident, since
   the cause here was a stale pre-created branch, not a squash-orphaned one — a repo-level "PRs
   must be up to date with base before merge" branch-protection rule would have, though, by
   forcing this merge before CI could even run.
4. **Two real bugs, both only surfaced by CI actually booting the app** — confirming this
   session's own local `mvn test` genuinely could not have caught either, since unit tests mock
   their collaborators and never build the real Spring context:
   - **A circular dependency.** `SecurityConfig` constructor-injects `OidcAuthenticationSuccessHandler`
     (needed by `oidcFilterChain`), which needs `UserService`, which needs a `PasswordEncoder` — but
     `PasswordEncoder` was a `@Bean` method defined inside `SecurityConfig` itself, so Spring
     couldn't finish constructing `SecurityConfig` before running its own factory method. Fixed by
     moving that bean to a new `PasswordEncoderConfig` class.
   - **`backend/src/test/resources/application.yml` entirely shadows
     `backend/src/main/resources/application.yml` during any test run** (a pre-existing repo fact,
     not something this session introduced - Maven puts `target/test-classes` ahead of
     `target/classes` on the test classpath, so Spring Boot's config-data loading finds the test
     one first and never merges in the main one; this is *why* `AbstractIntegrationTest` has to
     supply datasource config via `@DynamicPropertySource` instead of relying on
     `application.yml`'s defaults). This session's new OIDC client-registration config, added only
     to the main file, was therefore invisible in every test - not just the OIDC-specific ones,
     since `SecurityConfig.oidcFilterChain()` unconditionally calls `.oauth2Login(...)`, so *every*
     test's Spring context failed to start. Fixed by adding the equivalent static registration
     config to the test-scoped file too, with a comment explaining why it's there.
   - Both were root-caused **locally**, without Docker, by writing a throwaway diagnostic (kept as
     the permanent `ProcureFlowApplicationContextTest`) that boots the real Spring context against
     an in-memory H2 database instead of Testcontainers Postgres — proving this class of bug
     doesn't actually require Docker to catch fast, and should have been in place from the start
     rather than only added reactively after two failed CI runs.
5. Everything else (Maven Central — including the new `spring-boot-starter-oauth2-client` and
   `testcontainers-keycloak` dependencies, confirmed resolvable — and the npm registry) was
   reachable normally.

### What's left in the backlog for a future session
1. Confirm the two docker-compose networking points above actually work end-to-end on a real
   Docker host (this session could only verify them by design review, not execution).
2. An unlink-SSO-identity flow — see `adr/0008-oidc-sso-identity-linking.md` consequences.
3. Everything already listed in Session 1's list above, unchanged by this session's work.

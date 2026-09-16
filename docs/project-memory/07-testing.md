# Testing

## Backend

### Unit tests (JUnit 5 + Mockito + AssertJ), run by `mvn test`
No database, no Spring context — pure business logic against mocked repositories/collaborators.
20 tests across:

- `ApprovalWorkflowPolicyTest` — every threshold boundary (999.99 / 1000.00 / 1000.01 / 10000.00
  / 10000.01) resolves to the exact expected approval chain.
- `RequisitionServiceTest` — total computation from line items, approval-chain construction on
  submit, sequential-step enforcement (wrong role can't decide, only the current pending step is
  actionable), owner-only submit/cancel, approve-to-completion and reject-halts-workflow paths.
- `JwtServiceTest` — token round-trips its claims, a token signed with a different secret is
  rejected, missing/too-short secrets fail fast at construction.
- `DepartmentServiceTest`, `VendorServiceTest` — duplicate-code rejection, state-transition
  guards (e.g. can't approve a vendor that isn't `PENDING_APPROVAL`).

### Integration tests (JUnit 5 + Spring Boot Test + Testcontainers), run by `mvn verify`
Real HTTP requests via `MockMvc`, a **real PostgreSQL 16** container (Testcontainers), and the
actual Flyway migrations/seed data — not mocks, not H2. `AbstractIntegrationTest` provides the
shared `@Testcontainers` Postgres container and wires its JDBC URL into the Spring context via
`@DynamicPropertySource`.

- `AuthControllerIT` — register → login round trip issues a working JWT; duplicate email is
  rejected (409); wrong password is rejected (401).
- `RequisitionWorkflowIT` — the full lifecycle against the seeded demo accounts: employee creates
  a multi-line-item requisition, submits it, the department manager approves, the procurement
  officer approves (both required at this amount — see ADR-0004), the requisition reaches
  `APPROVED`, gets converted to a Purchase Order, and an unrelated role (an employee approving
  their own requisition) is correctly rejected with 409.

**Convention:** unit tests are named `*Test.java` (Surefire, no external dependencies, runs on
every `mvn test`); integration tests are named `*IT.java` (Failsafe, bound to the
`integration-test`/`verify` phases, requires Docker). This split means `mvn test` stays fast and
runnable anywhere, while `mvn verify` — used in CI — gets full-stack confidence.

**Sandbox caveat, disclosed honestly:** the integration tests could not be executed inside the
agent sandbox this project was built in — the sandbox's network policy blocks the Docker registry
pulls Testcontainers needs (`docker pull postgres:16-alpine` returns `403`/`429` from every
registry tried: Docker Hub, an ECR public mirror). They **are** exercised by
`.github/workflows/ci.yml`'s `backend` job on GitHub-hosted runners, which have unrestricted
registry access; that CI run is the actual verification of record for these tests, not a claim
made in this document. `mvn test` (unit tests) *was* run directly in the sandbox and all 20 pass.

### Format/lint
`mvn verify` also runs Spotless (Google Java Format) in `check` mode — the build fails on
unformatted code, not just warns. `mvn spotless:apply` reformats in place.

## Frontend

### Unit tests (Jasmine + Karma), run by `ng test`
46 tests, headless Chrome. Notable coverage beyond the generated boilerplate:

- `Auth` — login/logout persist and clear the token+user (and `localStorage`), `hasAnyRole`
  checks against the current user's roles.
- `authGuard` / `roleGuard` — both branches of each (allowed vs. redirected), including the
  "route declares no required roles" pass-through case.
- `authInterceptor` — attaches `Authorization: Bearer <token>` when a token exists, leaves the
  request untouched when it doesn't.
- Feature components (`RequisitionCreate`, `RequisitionDetail`, `RequisitionList`, `DepartmentList`,
  `VendorList`, `CatalogList`, `PurchaseOrderList`, `InvoiceList`, `Login`, `Register`, `Dashboard`,
  `Nav`) — form validity rules, role-gated action visibility/availability (e.g. `canDecide` is
  true only for the user holding the *current pending step's* role), and HTTP interactions via
  `HttpClientTestingModule`'s `HttpTestingController` (no real network calls in tests).

A small `core/testing/auth-test-utils.ts` helper (`asAuthInternals`) exists purely so specs can
seed a fake signed-in user into `Auth`'s private signals without `as any` casts, which the
project's ESLint config (`@typescript-eslint/no-explicit-any`) forbids everywhere else too.

Run locally: `CHROME_BIN=<path-to-chromium> npx ng test --no-watch --browsers=ChromeHeadlessCI`
(a `ChromeHeadlessCI` launcher with `--no-sandbox` is defined in `karma.conf.js` specifically
because both this sandbox and typical CI runners execute as root, and Chrome refuses to sandbox
itself as root without that flag). CI resolves `CHROME_BIN` via the `browser-actions/setup-chrome`
action. All 46 tests were run and pass directly in the sandbox that built this project (Playwright's
bundled Chromium at `/opt/pw-browsers/chromium` was reused for this — no extra browser download
was needed).

### Lint
`ng lint` (`angular-eslint` + `@typescript-eslint`, config generated by `ng add angular-eslint`)
passes with zero errors/warnings.

## What "done" means for a CI run on this repo
`.github/workflows/ci.yml` — `backend` job green (`mvn verify`: unit + integration + format),
`frontend` job green (`ng lint` + `ng test` + production `ng build`), `docker` job green (both
Dockerfiles actually build). See `12-session-handoff.md` for the actual CI status of the PR that
introduced this codebase.

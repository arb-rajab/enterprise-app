# Release Notes

## v0.2.0 — OIDC/SSO login (Keycloak), alongside existing JWT auth

**Backend**
- Spring Security `oauth2Login` against a real, local, open-source Keycloak instance
  (`docker-compose.yml`'s `keycloak` service, seeded from `keycloak/procureflow-realm.json`) — an
  *additional* login path, not a replacement for the existing JWT register/login. See
  `adr/0008-oidc-sso-identity-linking.md` for the identity-linking decision (an OIDC login with an
  email matching an existing JWT-registered account links onto it, by design, rather than creating
  a second disconnected identity) and `06-security.md` for the updated security posture.
- A successful OIDC login mints the same app JWT the password path issues, via the same
  `JwtService` — the two paths are indistinguishable to everything downstream of login
  (authorization, row-level checks, the token format itself).
- New `oidcFilterChain` (session-based, `/oauth2/**` + `/login/**` only) added alongside the
  existing, unchanged, fully stateless `apiFilterChain`.
- 2 new unit tests (`UserServiceOidcProvisioningTest`) and 2 new integration test classes (4 test
  methods total) against a real Testcontainers-provisioned Keycloak, including a direct regression
  test that linking an OIDC identity onto an existing account doesn't break that account's
  password login.

**Frontend**
- A "Sign in with SSO" button on the login page and a new `/sso/callback` route completing the
  handshake; 3 new unit tests.

**Platform**
- `docker-compose.yml` gains a `keycloak` service; `frontend/nginx.conf` and
  `frontend/proxy.conf.json` proxy `/oauth2/**` and `/login/**` to the backend, same same-origin
  pattern as `/api/**` (ADR-0003).

### Known limitations introduced by this release
See `adr/0008-oidc-sso-identity-linking.md`'s Consequences section and the updated
`09-backlog.md`: no unlink-SSO-identity flow, and the link-by-email assumption doesn't support two
real people legitimately sharing one email address.

### Verification status
See `12-session-handoff.md` Session 4 for the actual CI outcome and test counts as verified (or
explicitly not verifiable in-sandbox) at merge time for this release.

## v0.1.0 — Initial architecture and core scaffold

First release. Establishes the full stack end-to-end rather than one slice at a time:

**Backend (Spring Boot 3 / Java 21)**
- JWT-based authentication (register/login), BCrypt password hashing, five-role RBAC enforced via
  `@PreAuthorize` plus data-scoped service-layer checks.
- Domain model: Users, Departments, Vendors, Catalog Items, Purchase Requisitions (with line
  items and an ordered, sequential Approval Step chain), Purchase Orders, Invoices, and an
  append-only Audit Log.
- Full requisition lifecycle: draft → submit → amount-tiered multi-step approval → conversion to
  a Purchase Order → invoice recording → invoice approval/payment.
- Flyway-owned schema (two migrations: schema + seed reference data — five demo accounts, four
  departments, three vendors, five catalog items).
- OpenAPI documentation via springdoc (`/swagger-ui.html`).
- 20 unit tests (JUnit 5/Mockito/AssertJ) and 2 integration test classes (5 test methods) against
  a real Testcontainers-provisioned Postgres.

**Frontend (Angular 20, standalone components + signals)**
- Login/registration, role-aware navigation, and the full requisition workflow UI: create with
  dynamic line items, submit, approve/reject, cancel, convert to PO.
- Admin/procurement/finance screens: departments, vendors (with approve/deactivate), catalog
  (read), purchase orders (acknowledge/fulfill), invoices (record/approve/pay/dispute).
- 46 unit tests (Jasmine/Karma), zero ESLint findings.

**Platform**
- Multi-stage, non-root Dockerfiles for both apps; `docker-compose.yml` for one-command local
  runs (Postgres + backend + nginx-served frontend).
- GitHub Actions CI: backend build/test/integration-test/format-check, frontend
  build/lint/test, and a Docker-image-build verification job for both containers.

**Documentation**
- Full `docs/project-memory/` SDLC set (this file's directory) plus four Architecture Decision
  Records covering the repo layout/schema-ownership, the auth approach, the frontend/backend
  integration pattern, and the approval-workflow design.

### Known limitations at this release
See `06-security.md` and `09-backlog.md` for the full, itemized list. Headline items: read
endpoints aren't row-scoped, JWTs can't be revoked before expiry, and PO number generation has an
unresolved (low-likelihood) race condition.

### Verification status
See `12-session-handoff.md` for the actual CI outcome, Dependabot status, and test counts as
verified (or explicitly not verifiable) at merge time for this release.

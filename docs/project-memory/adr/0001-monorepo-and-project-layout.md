# ADR-0001: Monorepo, feature-based backend packaging, and Flyway-owned schema

## Status
Accepted

## Context
Three related structural decisions had to be made before writing any feature code: whether the
backend and frontend live in one repository or two, how the backend's Java packages are
organized, and who owns the database schema (Hibernate auto-DDL or explicit migrations).

## Decision

### One repository
Backend and frontend live in a single repo with `backend/` and `frontend/` at the root, each a
self-contained, independently buildable/deployable project (own `pom.xml` / `package.json`, own
Dockerfile, own CI job).

**Alternatives considered:** two repositories (one per app), coordinated by nothing more than a
documented API contract.

**Why the monorepo wins here:** this project exists to be read and evaluated as a whole. A
reviewer should be able to clone one thing, run `docker compose up`, and see the entire system.
Splitting it would force them to clone twice, cross-reference two READMEs, and reconstruct the
integration story themselves. The downside of a monorepo — coupling two teams' release cadences —
doesn't apply; there is one author and one release cadence. If this were a real multi-team
product, the calculus would likely flip toward separate repos with a published OpenAPI contract
as the integration point (the contract already exists here — `/v3/api-docs` — so that migration
path stays open).

### Feature-based (not layer-based) backend packages
Backend Java code is organized as `user/`, `department/`, `vendor/`, `catalog/`, `requisition/`,
`purchaseorder/`, `invoice/`, `audit/` — each containing its own entity, repository, service,
controller, and `dto/` subfolder — rather than top-level `controllers/`, `services/`,
`repositories/`, `entities/` packages.

**Why:** the requisition workflow is the interesting part of this codebase, and everything about
it (entity, workflow state machine in the service, REST surface) should be readable as one unit.
Layer-based packaging optimizes for "find all controllers" at the cost of "understand this one
feature," which is the wrong trade-off for a system meant to teach itself to a reader feature by
feature.

**Trade-off accepted:** cross-feature reuse requires importing across package boundaries (e.g.
`requisition` imports `catalog` and `department`), and Java's package-private visibility can't be
used to hide feature internals from other features. At this project's size, that's an acceptable
cost.

### Flyway owns the schema; Hibernate `ddl-auto` is `validate`-only
All schema (tables, indexes, constraints, seed reference data) is defined in versioned SQL under
`backend/src/main/resources/db/migration/`. `spring.jpa.hibernate.ddl-auto=validate` means the
app refuses to start if the JPA mapping and the actual schema disagree — Hibernate never
auto-generates or alters schema at runtime.

**Why:** auto-DDL is convenient for a throwaway prototype but wrong for anything meant to
demonstrate production practice: it can't be code-reviewed line by line, it can silently choose a
column type or index the author didn't intend, and it offers no path to a controlled production
migration. Flyway migrations are reviewable diffs, run identically in dev/CI/prod, and are the
same mechanism used to seed the demo reference data (`V2__seed_reference_data.sql`) — one
mechanism for both jobs, not two.

## Consequences
- Every entity field change requires a new Flyway migration in the same PR — there's no shortcut.
- Local `docker compose up` and CI integration tests always start from a known, versioned schema.
- A future split into separate repos is possible without redesigning either app, since the API
  contract (OpenAPI) is already the seam between them.

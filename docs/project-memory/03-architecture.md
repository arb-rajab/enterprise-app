# Architecture

## Repository layout

```
enterprise-app/
├── backend/                 Spring Boot 3 / Java 21, Maven
├── frontend/                Angular 20, standalone components
├── docker-compose.yml       Local dev: postgres + backend + frontend
└── docs/project-memory/     This SDLC documentation set
```

A monorepo (single repo, two independently deployable apps) was chosen over separate repos
because this is a portfolio artifact meant to be read end-to-end by one reviewer; splitting it
would scatter the story across repos for no operational benefit at this scale. See
`adr/0001-monorepo-and-project-layout.md`.

## Backend: layered package structure

`backend/src/main/java/com/enterpriseapp/procureflow/` is organized **by domain feature**, each
with its own layers, rather than by technical layer across the whole app:

```
<feature>/
  <Feature>.java              JPA entity
  <Feature>Repository.java    Spring Data repository
  <Feature>Service.java       Business logic, transactional boundary
  <Feature>Controller.java    REST endpoints, request/response mapping
  dto/                        Request/response records (never expose entities directly)
```

Cross-cutting concerns live in `common/` (base entity, exceptions, global error handler),
`config/` (Spring `@Configuration` classes, including the two `SecurityFilterChain`s in
`SecurityConfig`), and `security/` (JWT issuance/validation, the `UserDetailsService` adapter, and
the OIDC success/failure handlers that finish an SSO login by minting the same JWT — see
`adr/0008-oidc-sso-identity-linking.md`). `audit/` is a small cross-feature service every
workflow-mutating method calls into.

This structure was chosen (over a strict `controller/`, `service/`, `repository/` layering
across the whole app) because it keeps everything about one concept — e.g. a Purchase
Requisition — in one place, which matters more for a demo meant to be read feature-by-feature
than for a codebase optimized for enforcing layer boundaries with module visibility.

**DTOs are Java records**, kept in a `dto/` sub-package per feature, with static `from(entity)`
factory methods on the response records. Entities are never serialized directly to JSON — this
avoids accidentally leaking lazy-loading proxies, password hashes, or internal-only fields, and
decouples the wire format from the persistence model.

## Data model

Core entities and their relationships:

```
User ─┬─< user_roles (ElementCollection<RoleName>)
      └─> Department (optional)

Department ─< User (via department_id)

Vendor ─< CatalogItem
Vendor ─< PurchaseOrder

PurchaseRequisition ─< RequisitionLineItem (→ optional CatalogItem)
PurchaseRequisition ─< ApprovalStep
PurchaseRequisition ─  PurchaseOrder   (one-to-one, created on conversion)

PurchaseOrder ─< Invoice

AuditLogEntry            (polymorphic: entityType + entityId, no FK - append-only by design)
```

Every entity extends `BaseEntity` (`id`, `createdAt`, `updatedAt` via Spring Data JPA
auditing). Schema is owned by Flyway migrations (`backend/src/main/resources/db/migration/`),
**not** Hibernate `ddl-auto` — `ddl-auto` is set to `validate`, so the running JPA mapping must
always match a migration exactly, and schema evolution is reviewable, ordered SQL. See
`adr/0001-monorepo-and-project-layout.md` for related tooling choices and `06-security.md` for
why `user_roles` is a plain string-enum join table rather than a full `Role` entity with its own
permission graph (deliberately out of scope — see `09-backlog.md`).

## Backend request flow

```
HTTP request
  → JwtAuthenticationFilter (reads Authorization: Bearer <token>, populates SecurityContext)
  → Spring Security filter chain (method-level @PreAuthorize on controller methods)
  → Controller (validates @RequestBody via Bean Validation, maps DTO → service call)
  → Service (@Transactional boundary, business rules, calls AuditService)
  → Repository (Spring Data JPA)
  → GlobalExceptionHandler (@RestControllerAdvice) converts domain exceptions to a uniform
    ApiError JSON body with the right HTTP status
```

Authorization is deliberately checked in **two places** that must agree:
1. Coarse-grained, declarative `@PreAuthorize` on controller methods (e.g. "must be
   `PROCUREMENT_OFFICER` or `ADMIN` to create a Vendor").
2. Fine-grained, imperative checks inside services for rules that depend on request *data*, not
   just the caller's role (e.g. "only the requisition's own requester may submit it" — a role
   check alone cannot express this).

## Frontend: feature-module-free standalone Angular

The frontend uses Angular 20's standalone component model (no `NgModule`s) with lazy-loaded
routes per feature:

```
frontend/src/app/
  core/            Singletons: services (HTTP clients), guards, the JWT interceptor, models
  shared/          Reusable, presentation-only pieces (currently: the nav bar)
  features/        One folder per screen area, each independently lazy-loaded via
                   loadComponent() in app.routes.ts
```

State that must survive navigation (the signed-in user, the JWT) lives in a single injectable,
`Auth`, exposed as Angular **signals** (`currentUser`, `isAuthenticated`) rather than a global
NgRx-style store — the app's state surface is small enough that a heavier state library would add
indirection without paying for itself. Per-screen data (requisition lists, vendor lists) is
fetched directly by each feature component via its `HttpClient`-based service and held in local
signals; nothing here needs cross-screen caching.

Routes that require a role check declare it declaratively via route `data: { roles: [...] }`,
enforced by a single functional `roleGuard`, so the authorization rule for a screen lives next to
its route definition rather than scattered through component logic.

## Frontend/backend integration

See `adr/0003-frontend-backend-integration.md` for the full reasoning. In short: the Angular app
always calls a **same-origin, relative** `/api/...` path. In local dev, the Angular CLI dev
server proxies `/api` to `localhost:8080` (`frontend/proxy.conf.json`); in the Docker Compose
setup and any real deployment, nginx (serving the built Angular bundle) proxies `/api` to the
backend container. The Angular app therefore never needs to know the backend's real
hostname/port, and CORS is only a concern for the dev-server case (`app.cors.allowed-origins` in
`application.yml`), not for the containerized deployment path.

## Why Spring Boot 3 / Java 21 and Angular 20

Both are the current LTS/latest-stable major versions as of this project's creation, chosen to
demonstrate familiarity with current, not legacy, tooling: Java 21 (virtual threads available,
though not used here — no I/O-bound concurrency pattern in this app needs them), records for
DTOs, and Angular's standalone-component + signals model (the framework's now-default,
post-`NgModule` idiom).

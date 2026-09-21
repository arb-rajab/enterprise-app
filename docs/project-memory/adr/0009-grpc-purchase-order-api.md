# ADR-0009: gRPC purchase-order status API, sharing REST's auth and read-scoping

## Status
Accepted

## Context
The API surface has been REST-only since the project's first commit. That's a real, disclosed gap
for a portfolio project meant to demonstrate breadth: nothing here shows a typed-RPC / service-to-
service style API, which is a common enterprise integration pattern REST-only demos don't cover.

Two things made "just add a second copy of the REST controllers as gRPC" the wrong shape for this
change:

1. **Which slice.** Mechanically mirroring every REST endpoint as gRPC would double the surface
   area for no real benefit - most of this API (creating requisitions, deciding approval steps,
   converting to a PO) is transactional, form-driven, and already well served by REST/JSON from a
   browser SPA. The one place a typed, streamable RPC contract earns its keep is a narrower,
   read-heavy, integration-shaped slice: **purchase-order status queries**, the kind of thing a
   downstream service (a finance system, a vendor-status dashboard, another internal service)
   would poll or subscribe to repeatedly, and would benefit from a generated, strongly-typed client
   for instead of hand-parsing JSON.
2. **Auth must not fork.** This project now has two authentication paths (JWT, ADR-0002; OIDC/SSO,
   ADR-0008) and a real, previously-buggy authorization surface: read visibility is row-level
   scoped by department/ownership (`ReadScopePolicy`, ADR-0005), and a **cross-department read or
   write bypass is not hypothetical here** - ADR-0007 fixed exactly that class of bug on the
   *write* (approval) path after ADR-0005 had already fixed its *read*-path sibling. A gRPC surface
   that re-implemented "is this caller allowed to see this row" from scratch would be a third place
   for that rule to drift out of sync, on top of the two ADR-0005/0007 already had to reconcile.

## Decision
**Exposed:** a single gRPC service, `PurchaseOrderGrpcService`
(`backend/src/main/proto/purchase_order_service.proto`), with two RPCs mapping onto the existing
read endpoints of `PurchaseOrderController`:

- `GetPurchaseOrder(GetPurchaseOrderRequest) returns (PurchaseOrderStatusResponse)` - unary,
  equivalent to `GET /api/v1/purchase-orders/{id}`.
- `ListPurchaseOrders(ListPurchaseOrdersRequest) returns (stream PurchaseOrderStatusResponse)` -
  server-streaming, equivalent to `GET /api/v1/purchase-orders`. Streaming (rather than returning a
  single repeated field) is the real reason this slice benefits from gRPC over REST/JSON: a
  long-running consumer (a status dashboard, a sync job) gets each visible order as it's
  serialized, without waiting for - or holding in memory - the full list, and without polling.

Real generated code, not a hand-rolled substitute: `protobuf-maven-plugin` (with the `os-maven-
plugin` extension for the platform-specific `protoc`/`protoc-gen-grpc-java` binaries) generates the
actual gRPC server base class and message types from the `.proto` file at build time
(`target/generated-sources/protobuf`), the same way any real gRPC service would.

**Authorization is shared, not reimplemented, in two layers, matching how REST already splits
identity from scoping:**

1. **Identity (who is calling).** `GrpcAuthInterceptor` is the transport-level analog of
   `JwtAuthenticationFilter`: it reads the `authorization` gRPC metadata entry and validates the
   bearer token with the **exact same `JwtService`** bean REST uses (`parseClaims`/`extractEmail`).
   It does not parse or trust anything about the token itself beyond what `JwtService` already
   decides; an invalid, missing, or expired token gets `UNAUTHENTICATED` before the call reaches
   any service logic, same as REST's `SecurityConfig.apiFilterChain` rejecting an unauthenticated
   request to `/api/v1/purchase-orders/**`. This only covers the JWT path (OIDC/SSO issues the same
   kind of access token via the same `JwtService` post-login, per ADR-0008, so no separate OIDC-
   aware gRPC logic is needed or added).
2. **Scoping (what they're allowed to see).** `PurchaseOrderGrpcServiceImpl` looks up the caller's
   `User` by the email `GrpcAuthInterceptor` put in gRPC `Context` - the same
   `userService.findByEmail(authentication.getName())` call `PurchaseOrderController` makes - and
   then calls **the same `PurchaseOrderService.findVisibleById`/`findVisibleTo`** the REST
   controller calls. Nothing about "org-wide roles see everything / a department manager sees their
   department / everyone else sees their own" (`ReadScopePolicy`) is re-expressed here. A future
   change to that rule (or another ADR-0007-style bug fix to it) automatically applies to both
   surfaces because there's exactly one implementation of it.

Neither JWT issuance/validation internals nor OIDC provisioning were touched - both are used
exactly as REST already uses them.

## Alternatives considered
1. **Mirror every REST endpoint (requisitions, approvals, users, ...) as gRPC.** Rejected: most of
   that surface is form-driven CRUD/workflow that REST/JSON already serves well from the Angular
   SPA; duplicating it as gRPC would be surface area for its own sake, not something that
   "genuinely benefits from typed RPC" as this change was scoped to find.
2. **`net.devh:grpc-server-spring-boot-starter` (the community Spring/gRPC integration) instead of
   a plain `io.grpc` server wired up as a small Spring `SmartLifecycle` bean.** That starter's own
   Spring Security integration is built around token formats/flows (typically OAuth2 resource-
   server JWTs validated via a `JwtDecoder`) that don't match this project's hand-rolled
   `JwtService`/`Claims` model without adapter code of its own - at that point it's an extra
   third-party dependency whose version compatibility with Spring Boot 3.3.x has to be tracked,
   for a feature (auto-registering `@GrpcService` beans, config-driven port binding) this project's
   single service doesn't need. A ~60-line `ServerInterceptor` plus a ~60-line `SmartLifecycle`
   bean, both calling the project's own `JwtService`/`PurchaseOrderService`, is less code, fewer
   moving parts, and easier for a reviewer to verify does not diverge from REST's auth than
   configuring a general-purpose starter to bend around a bespoke token format would be.
3. **Re-validate the JWT independently inside the gRPC interceptor (parse the secret/claims shape
   again).** Rejected outright per this change's own scope: `GrpcAuthInterceptor` calls the
   existing `JwtService` bean directly; there is no second JWT-parsing implementation to drift out
   of sync with the first.
4. **Scope gRPC reads by role/department directly inside `GrpcAuthInterceptor` or
   `PurchaseOrderGrpcServiceImpl` (e.g. checking `viewer.getDepartment()` inline).** Rejected: that
   is precisely the duplication this ADR is about avoiding. Scoping stays exclusively in
   `PurchaseOrderService`/`ReadScopePolicy`.

## Consequences
- `backend/pom.xml` gains `io.grpc:grpc-{netty-shaded,protobuf,stub,testing}`, an explicit
  `com.google.protobuf:protobuf-java` pin (grpc-bom 1.68.1's transitive `protobuf-java` predates
  the protoc version this module generates code with), the `os-maven-plugin` build extension, and
  the `protobuf-maven-plugin` build plugin. REST's dependencies, filter chain, and endpoints are
  unchanged.
- New package `com.enterpriseapp.procureflow.grpc`: `GrpcAuthContext` (the gRPC `Context.Key` that
  carries the authenticated email across the interceptor boundary), `GrpcAuthInterceptor`,
  `GrpcServerLifecycle` (starts/stops the gRPC listener alongside the servlet container; port is
  `app.grpc.port`, default `9090`, independent of REST's `server.port`), and
  `PurchaseOrderGrpcServiceImpl`.
- `docker-compose.yml`/`backend/Dockerfile` expose port `9090` alongside `8080`; this is additive,
  REST is unaffected.
- New regression test `PurchaseOrderGrpcAuthorizationIT` (real gRPC client over a real
  `ManagedChannel`, against `GrpcServerLifecycle`'s actual bound port) proves: an unauthenticated
  call is rejected (`UNAUTHENTICATED`); the requester, their department manager, and an org-wide
  role can all read a purchase order via gRPC; and - the specific regression this change must not
  reintroduce - a Department Manager from a **different** department than the requisition's is
  rejected (`PERMISSION_DENIED`) on both `GetPurchaseOrder` and `ListPurchaseOrders`. That's the
  read-path cross-department violation ADR-0005 fixed for REST and ADR-0007 describes as the same
  class of bug as the write-path (approval) bypass it fixed; this test is the gRPC-path proof that
  fix was never bypassed by adding a second entry point.
- No change to `RequisitionService`, `PurchaseOrderService`, `ReadScopePolicy`, `JwtService`, or
  any OIDC/JWT provisioning code - this ADR's entire point is that none of that needed to change to
  add this surface safely.

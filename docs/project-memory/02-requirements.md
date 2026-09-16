# Requirements

## Functional requirements

### Authentication & authorization
- FR-1: Users can register (self-service) and are assigned `ROLE_EMPLOYEE` by default.
- FR-2: Users can log in with email + password and receive a signed, time-limited JWT.
- FR-3: An administrator can change a user's role set.
- FR-4: Every API endpoint enforces role checks server-side; the frontend hiding a control is a
  UX convenience only, never the actual access boundary.

### Reference data
- FR-5: Admins manage Departments (code, name, cost center, manager).
- FR-6: Procurement Officers/Admins manage Vendors, including a `PENDING_APPROVAL → ACTIVE`
  approval step and an `ACTIVE → INACTIVE` deactivation step.
- FR-7: Procurement Officers/Admins manage Catalog Items, each tied to one Vendor.

### Requisition workflow
- FR-8: Any authenticated employee can create a Draft requisition with one or more line items.
- FR-9: A requisition cannot be submitted with zero line items.
- FR-10: On submission, the system computes the required approval chain from the requisition's
  total amount (see ADR-0004) and creates ordered `ApprovalStep`s.
- FR-11: Only the user matching the *current* pending step's role may approve or reject it; steps
  are strictly sequential.
- FR-12: A rejection at any step immediately sets the requisition to `REJECTED` and halts the
  workflow.
- FR-13: A requisition only reaches `APPROVED` once every step is individually approved.
- FR-14: Only the requester (or an Admin) may submit or cancel their own requisition.
- FR-15: An `APPROVED` requisition can be converted to a Purchase Order (choosing a vendor) by a
  Procurement Officer or Admin; this is one-way (`CONVERTED`) and idempotent-guarded (a
  requisition can produce at most one purchase order).

### Purchase orders & invoices
- FR-16: A Purchase Order moves `ISSUED → ACKNOWLEDGED → FULFILLED`, or can be cancelled.
- FR-17: Invoices are recorded against a Purchase Order with a unique invoice number.
- FR-18: A Finance Approver (or Admin) approves, pays, or disputes a received invoice.

### Audit
- FR-19: Every workflow transition (submit, approve, reject, cancel, convert, PO/invoice status
  changes) is recorded in an append-only audit log with actor, action, and timestamp.

## Non-functional requirements

- NFR-1 (**Security**): Passwords are stored as BCrypt hashes only; JWTs are signed with HS256
  using a secret of at least 256 bits, supplied via environment variable, never hardcoded or
  committed. See `06-security.md`.
- NFR-2 (**Testability**): Business logic (the approval-chain policy, workflow transitions) is
  unit-testable without a database; the full stack is verified with real-Postgres integration
  tests. See `07-testing.md`.
- NFR-3 (**Portability**): The whole stack runs locally with a single `docker compose up`, with
  no dependency on a specific host OS.
- NFR-4 (**API contract clarity**): The REST API is documented via OpenAPI/Swagger UI, generated
  from the code (never hand-maintained separately, to avoid drift).
- NFR-5 (**Observability baseline**): The backend exposes health/readiness/liveness probes
  suitable for container orchestration health checks.
- NFR-6 (**Build reproducibility**): CI builds pin toolchain versions (Java 21, Node 22) and fail
  the build on lint/format violations rather than merely warning.

## Explicit non-requirements (see `09-backlog.md` for why these are deferred, not forgotten)

- Multi-tenancy, SSO/OIDC federation, password reset via email, real payment processing,
  file/attachment uploads on requisitions, notifications, and pagination on list endpoints
  (every `findAll` returns the full result set — acceptable at demo data volumes, tracked as a
  known gap in `09-backlog.md`).

# ADR-0005: Row-level read scoping for requisitions and purchase orders

## Status
Accepted

## Context
`06-security.md` and `09-backlog.md` both flagged a known, deliberately-disclosed gap: `GET
/api/v1/requisitions` (and, less visibly, `GET /api/v1/purchase-orders`) returned every row to any
authenticated user, regardless of role, department, or ownership. Write actions were already
correctly scoped (`RequisitionService.requireOwner()`, the per-step role check in `decide()`), but
read access was not. The backlog entry deferred fixing this because it turns on a real design
question: should a Department Manager see their whole department's requisitions, or only the ones
specifically routed to them for approval? That question needed an answer before writing the fix,
not a quick patch that happened to compile.

## Decision
Read visibility for both `PurchaseRequisition` and `PurchaseOrder` now follows the same three-tier
rule, implemented once in `ReadScopePolicy` and applied by `RequisitionService.findVisibleTo()` /
`findVisibleById()` and `PurchaseOrderService.findVisibleTo()` / `findVisibleById()`:

1. **Organization-wide roles** (`ROLE_ADMIN`, `ROLE_PROCUREMENT_OFFICER`, `ROLE_FINANCE_APPROVER`)
   see every row, with no scoping.
2. **`ROLE_DEPARTMENT_MANAGER`** sees every row belonging to their own department (via
   `User.department`), not just the ones with a pending step assigned to them.
3. **Everyone else** (`ROLE_EMPLOYEE`) sees only rows where they are the requester.

`findById`/`findAll` (unscoped) are kept as the internal, unrestricted accessors used by write
paths (`submit`, `decide`, `cancel`, `markConverted`, `convertFromRequisition`,
`updateStatus`) — those already have their own authorization (owner checks, per-step role checks,
or a controller-level `@PreAuthorize` restricted to procurement/admin) and must keep resolving any
row so, e.g., a Finance Approver can still act on a step routed from a department they don't
belong to. Only the controller-facing read paths call the new scoped methods. A denied read
returns 403 (`AccessDeniedException`, already mapped by `GlobalExceptionHandler`), not 404 — this
project doesn't try to hide the existence of a resource from a same-organization user, only its
contents.

## Alternatives considered

1. **Department Manager sees only requisitions with a step currently routed to them** (i.e.
   identical to the existing `/pending-my-approval` endpoint). Rejected: it would make the general
   list endpoint useless for a manager who wants to see their team's *pending drafts* or
   *already-decided* requisitions, not just the one awaiting their signature right now, and it
   would leave `/pending-my-approval` as a pure subset with no distinct purpose.
2. **Scope Procurement Officer / Finance Approver reads to their own department too**, matching
   the literal wording in the old `06-security.md` gap note ("not just the caller's own or their
   department's"). Rejected as inconsistent with `RequisitionService.decide()`, which already lets
   any holder of these roles act on **any** department's pending step (ADR-0004) — restricting
   their *read* access more tightly than their existing *write* (approval) authority would hide
   from them the very requisitions they're already permitted to approve, breaking the
   pending-approval review workflow rather than securing it.
3. **Row-level security at the database layer (Postgres RLS policies).** More defense-in-depth,
   and the right call for a real multi-tenant product, but adds a second, harder-to-test place the
   rule lives (session variables set per request, policy SQL) for a single-tenant demo where the
   scoping rule is simple enough to state and unit-test in one service method. Noted as a future
   direction if this project ever needed genuine tenant isolation, not implemented here.

## Consequences
- `PurchaseOrderRepository` gained `findByRequisition_Department_Id` /
  `findByRequisition_Requester_Id`; `PurchaseRequisitionRepository` gained `findByDepartmentId`
  (alongside the pre-existing `findByRequesterId`).
- Both services depend on the new `ReadScopePolicy` component so the organization-wide-roles rule
  is defined in exactly one place, rather than copy-pasted between the two services and drifting.
- Covered by `RequisitionServiceTest`/`PurchaseOrderServiceTest` (unit, mocked repositories) and
  `RequisitionReadScopingIT` (integration, real Postgres + real JWTs from seeded and
  freshly-registered accounts) — see `07-testing.md`.
- The `06-security.md` "known gap" disclosure for this item is resolved and removed; the
  corresponding `09-backlog.md` entry is resolved and removed.

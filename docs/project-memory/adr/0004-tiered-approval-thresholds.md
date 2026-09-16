# ADR-0004: Amount-tiered, sequential approval chains

## Status
Accepted

## Context
A requisition needs to be routed to the right approver(s) based on its cost, which is the
central "multi-step workflow" this project exists to demonstrate. The rule needed to be simple
enough to unit-test exhaustively and explain in one paragraph, while still being non-trivial
(more than a single fixed approver).

## Decision
`ApprovalWorkflowPolicy.resolveApprovalChain(BigDecimal totalAmount)` returns an ordered list of
required approver roles:

| Total amount           | Required approval chain                                           |
| ----------------------- | ------------------------------------------------------------------ |
| ≤ 1,000.00              | Department Manager                                                 |
| 1,000.01 – 10,000.00     | Department Manager → Procurement Officer                           |
| > 10,000.00              | Department Manager → Procurement Officer → Finance Approver        |

On submission, `RequisitionService.submit()` materializes this into ordered `ApprovalStep` rows
(`stepOrder` 1..N, `status = PENDING`). `RequisitionService.decide()` only allows action on the
**lowest-`stepOrder` step still `PENDING`** — approvals are strictly sequential, not parallel; a
Finance Approver cannot act before the Department Manager has, even on a large requisition.

## Alternatives considered

1. **A single configurable approver per requisition (chosen by the requester).** Rejected —
   trivial to implement but doesn't demonstrate a real approval-chain data model (`ApprovalStep`
   as its own entity, ordered, independently auditable) or sequential-gating logic, which was the
   point of choosing this domain.

2. **Parallel approval (all required roles notified simultaneously, requisition approved once all
   have acted, in any order).** More realistic for some real organizations, but harder to reason
   about and test (no single "current step"), and the sequential model already demonstrates the
   state-machine and RBAC mechanics this project is showing off. Noted in `09-backlog.md` as a
   plausible extension, not implemented.

3. **A fully configurable, admin-editable threshold table stored in the database.** More
   realistic still, but adds a whole admin UI and data model for a rule that, for this demo,
   only needs to *exist* and be *correct*, not be *reconfigurable*. The thresholds are constants
   (`ApprovalWorkflowPolicy.PROCUREMENT_THRESHOLD`, `FINANCE_THRESHOLD`) precisely so this
   decision is visible and greppable in one file rather than hidden in seed data.

## Consequences
- Changing the thresholds is a code change (and a new unit test expectation), not a runtime
  configuration change — acceptable for a demo, called out explicitly as a backlog item if this
  were to become a real product.
- The sequential-only-current-step rule is enforced in `RequisitionService.decide()` (a step
  other than the current pending one cannot be acted on, and only a user holding that exact role
  can act) and is covered by `RequisitionServiceTest` (unit) and `RequisitionWorkflowIT`
  (integration, against real Postgres) — see `07-testing.md`.
- Because thresholds are amount-only, a requisition with many cheap line items and one with a
  single expensive line item route identically if their totals match — this is intentional
  (the rule is about total financial exposure, not line-item complexity).

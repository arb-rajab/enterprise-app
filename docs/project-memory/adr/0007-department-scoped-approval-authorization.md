# ADR-0007: Department-scoped authorization for the Department Manager approval step

## Status
Accepted

## Context
`RequisitionService.decide()` checked only that the acting user held the role required by the
requisition's next pending `ApprovalStep` (`approver.getRoles().contains(step.getApproverRole())`).
It never checked that a `ROLE_DEPARTMENT_MANAGER` approver actually belonged to the requisition's
own department. Any authenticated Department Manager, from any department, could approve or
reject any other department's requisitions at the `ROLE_DEPARTMENT_MANAGER` step —
`ApprovalWorkflowPolicy.resolveApprovalChain()` always includes that step first, so every
requisition was exposed regardless of amount.

This was undisclosed: `06-security.md`'s Authorization section claimed `decide()` "checks the
acting user's roles against the specific pending step's required role, not just 'any approver
role'" as if that were a complete authorization check, and no backlog entry or ADR flagged the
gap. It is the write-side analog of the read-scoping gap fixed in ADR-0005 (`ReadScopePolicy`) —
that ADR fixed *read* visibility for `ROLE_DEPARTMENT_MANAGER` without noticing the parallel gap
still open on the *write* (approval) side.

`ROLE_PROCUREMENT_OFFICER` and `ROLE_FINANCE_APPROVER` are deliberately **not** in scope for this
fix: ADR-0004 and `ReadScopePolicy`'s own Javadoc establish that those two roles have org-wide
approval authority by design (they can act on any department's pending step), and `ReadScopePolicy`
mirrors that by giving them org-wide read access. Restricting their write authority to their own
department would be a behavior change beyond the reported bug and would break the existing,
intentional org-wide review workflow those roles rely on.

## Decision
`RequisitionService.decide()` now additionally requires, only when
`step.getApproverRole() == ROLE_DEPARTMENT_MANAGER`, that the approver's own `User.department`
equals `requisition.getDepartment()`. A Department Manager from another department now gets a 403
(`AccessDeniedException`, already mapped by `GlobalExceptionHandler`) instead of silently being
allowed to decide the step. `ROLE_PROCUREMENT_OFFICER` and `ROLE_FINANCE_APPROVER` steps are
unaffected and remain org-wide, matching ADR-0004/ADR-0005.

`findById`/`findAll` and the rest of `decide()`'s logic are unchanged; this is purely an
additional authorization check on top of the existing role check, on the same code path already
covered by `RequisitionServiceTest`.

## Consequences
- Regression test `RequisitionServiceTest.managerFromAnotherDepartmentCannotDecideOnAStep`
  reproduces the bypass against the pre-fix code (asserts `decide()` throws; it did not before
  this change) and guards against regressing it.
- `06-security.md`'s Authorization section is corrected to describe the actual (now fixed)
  department-scoped check instead of the previous, inaccurate "role alone is enough" description.
- No API contract change: the failure mode for an unauthorized approver was already
  domain-exception-driven (`InvalidStateTransitionException` for a role mismatch); this adds a
  403 for a same-role-wrong-department mismatch, consistent with how `findVisibleById` in
  ADR-0005 already signals a scoping denial (403, not 404 or a generic 400).

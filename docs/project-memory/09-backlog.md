# Backlog

Deferred work, each with the reason it was deferred rather than a bare TODO.

## Security / auth
- **Row-level read scoping** on `GET /api/v1/requisitions` (currently returns all requisitions to
  any authenticated user; write actions are correctly scoped). Deferred because it interacts with
  a design question — should managers see their whole department's requisitions, or only ones
  routed to them? — that deserves its own decision, not a quick patch. See `06-security.md` R2.
- **JWT revocation / refresh-token rotation.** `app.security.jwt.refresh-token-ttl-days` exists
  as a placeholder config value; no refresh flow is implemented. See ADR-0002.
- **Rate limiting and account lockout** on `/api/v1/auth/**`.
- **OIDC/SSO federation** for a real multi-app enterprise estate, instead of self-issued JWTs —
  see ADR-0002's alternatives-considered section for why this was out of scope for this pass.
- **Role changes take effect only on next login** (roles are baked into the JWT at issuance) —
  would need either short-TTL tokens with mandatory refresh, or a per-request role lookup that
  trades away the "no DB hit per request" property ADR-0002 chose.

## Workflow / domain
- **Parallel (not just sequential) approval chains** — see ADR-0004 alternatives.
- **Admin-configurable approval thresholds** (currently constants in `ApprovalWorkflowPolicy`) —
  see ADR-0004.
- **Multi-vendor purchase orders.** Today, conversion picks a single vendor for the whole PO;
  a requisition whose line items came from different vendors' catalog items can still only
  produce one PO against one chosen vendor. Splitting into multiple POs per vendor is a
  reasonable real-world feature, not implemented here.
- **Partial invoice matching / multiple invoices per PO with amount reconciliation** — invoices
  are recorded and status-tracked, but nothing validates an invoice's amount against the PO total
  or tracks partial fulfillment.

## Platform / non-functional
- **Pagination** on all list endpoints (`findAll()` returns everything; fine at demo data
  volumes, not fine at scale).
- **Purchase order number generation race condition** (`COUNT(*) + 1` under concurrent creation)
  — fix is a Postgres sequence (`CREATE SEQUENCE po_number_seq`) instead of a count query. See
  `08-ops.md`.
- **Structured logging / log aggregation / metrics export** (Actuator exposes `health` and `info`
  only; `prometheus` endpoint and a metrics backend are not wired up).
- **A real deployment target and CD pipeline.** CI proves the app builds, tests, and its Docker
  images build; nothing deploys anywhere. See `11-retirement-plan.md` for why that's an honest
  boundary for this project rather than a gap to apologize for.
- **Full Role/Permission entity model.** Roles today are a fixed enum
  (`user_roles` join table of `RoleName` values), not a database-editable permission graph. This
  was a deliberate simplicity choice (see `03-architecture.md`), revisit if the role set needs to
  grow beyond five fixed values.

## Testing
- **Frontend e2e tests** (Playwright/Cypress) exercising the full stack through a real browser —
  currently the frontend has strong unit-test coverage (46 Jasmine/Karma specs) but no true
  end-to-end browser test; the backend's `RequisitionWorkflowIT` covers the equivalent workflow
  at the API layer instead.
- **Load/performance testing** — none exists; not meaningful at this project's intended scale.

## Process
- Path-filtered CI (`.github/workflows/ci.yml` currently runs both the `backend` and `frontend`
  jobs on every push/PR regardless of which side changed) — worth adding `paths:` filters once
  the repo has enough history that most changes are one-sided, to save CI minutes. Not done now
  because the very first PR touches both sides anyway.

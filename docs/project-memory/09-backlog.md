# Backlog

Deferred work, each with the reason it was deferred rather than a bare TODO.

## Security / auth
- **Silent background access-token renewal in the frontend.** `Auth`/`authInterceptor` still just
  bounce to `/login` on a 401; nothing calls `POST /api/v1/auth/refresh` proactively before the
  access token's short TTL expires, even though the backend refresh flow exists and is used on
  explicit logout. See ADR-0006.
- **Scheduled cleanup of expired/revoked `refresh_tokens` rows** — no retention/sweep job exists;
  fine at demo data volumes.
- **Account lockout** after repeated failed logins on `/api/v1/auth/**` - a distinct, persistent
  mechanism from rate limiting. Rate limiting itself is done, see `adr/0010-rate-limiting.md`.
- **gRPC TLS.** The gRPC listener (`app.grpc.port`, ADR-0009) is plaintext; REST at least has a
  documented "platform-level ingress terminates TLS" answer (`06-security.md`), gRPC doesn't yet.
  Needs a real decision (mTLS between services vs. ingress/mesh-terminated TLS in front of port
  9090, and how local/dev still works without either) before implementing, not a rushed
  self-signed-cert patch.
- **Migrate off Spring Boot 3.3.x.** The whole 3.3 branch is now fully end-of-life for open-source
  users (confirmed directly against Maven Central - 3.3.13 is the newest version published there);
  a patch bump within 3.3.x closed the two CVEs flagged in the pass that found this
  (`06-security.md`), but doesn't put this app back on a branch that keeps receiving open-source
  security patches. Spring Boot 4.x is a major-version jump with real breaking-change surface
  (Spring Framework 7 baseline, possible dependency-floor changes) that needs its own dedicated,
  validated pass - not something to bundle into an unrelated change.
- ~~OIDC/SSO federation~~ — done, see `adr/0008-oidc-sso-identity-linking.md`. What's still
  deferred from that work: an **unlink-SSO-identity flow** (there's no way to clear a user's
  `oidc_provider`/`oidc_subject` and force password-only login again), and revisiting the
  link-by-email assumption if this ever needs to support two real people legitimately sharing one
  email address (see that ADR's alternatives-considered section).
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
- **Admin action needed: disable "Allow squash merging" (or adopt merge/rebase-merge as the
  standing PR strategy) and consider branch protection on `main`.** This repo has hit
  divergent/unrelated-history merge conflicts twice, root-caused in
  `13-divergent-history-incident.md`: an initial double-root-commit condition, compounded by PR #1
  being squash-merged, which orphaned the feature branch's history and caused the second
  incident when the next session branched from the pre-squash tip instead of fresh `main`. Both
  changes are GitHub repository Settings actions, not committable files, so out of scope for a
  worker session — flagged here for a repo admin.

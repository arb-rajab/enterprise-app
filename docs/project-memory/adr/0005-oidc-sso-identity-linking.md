# ADR-0005: OIDC/SSO login, added alongside JWT, linked by email

## Status
Accepted

## Context
ADR-0002 chose stateless, self-issued JWTs for the API and explicitly deferred OIDC/SSO
federation as future work (see its "Alternatives considered" section and `09-backlog.md`). That
gap is now being closed: this project adds a second login path, OIDC via Spring Security's
`oauth2Login`, against a real local Keycloak instance (`docker-compose.yml`'s `keycloak` service,
seeded from `keycloak/procureflow-realm.json` - see `keycloak/README.md`). ADR-0002's own
reasoning for rejecting a *third-party-hosted* IdP stands - this project still isn't going to
depend on a real Google/Okta/Microsoft tenant - but a real, self-hosted, open-source IdP the repo
can stand up itself removes that objection entirely, and demonstrating federated login is valuable
on its own for a portfolio project.

The custom JWT path (`/api/v1/auth/register`, `/api/v1/auth/login`) is not replaced. Requisition
row-level scoping assumptions, the approval workflow, and every existing test all identify a user
by the local `users` row - removing JWT login would mean redesigning all of that for no reason
related to what this change is actually for. OIDC is purely an additional way to end up
authenticated as a local user.

The open question this ADR actually needs to answer: **when someone logs in via Keycloak with an
email that already has a JWT-registered account, is that the same user, or a different one?**

## Decision
**Link by email onto the existing account.** `UserService.findOrProvisionForOidc` looks up the
incoming OIDC email (Keycloak's `email` claim) against `users.email`:

- **Match found** → that row's `oidc_provider`/`oidc_subject` columns (added in
  `V3__add_oidc_identity.sql`) are set/updated to point at this IdP identity. Nothing else about
  the row changes - in particular, its `password_hash` and roles are untouched. The user can now
  sign in with *either* their password or SSO and lands on the same account, same requisitions,
  same approval history.
- **No match** → a new `users` row is provisioned: `roles = {ROLE_EMPLOYEE}` (the same default
  `UserService.register` uses), `active = true`, and a `password_hash` set to
  `passwordEncoder.encode(UUID.randomUUID().toString())` - a real, valid BCrypt hash that is never
  handed back to anyone. This means the JWT login path needs **no** null-password special case for
  an SSO-only account: a password login attempt for it just runs the normal
  `authenticationManager.authenticate(...)` call, `BCryptPasswordEncoder.matches` returns false
  against the random hash, and the caller gets the same 401 "bad credentials" a wrong password for
  any other account gets - no separate error path, no information leak about how the account was
  created.

Mechanically, `OidcAuthenticationSuccessHandler` (registered as the `oauth2Login` success handler
on a dedicated `oidcFilterChain`, see `SecurityConfig`) calls `findOrProvisionForOidc`, mints a
normal app JWT via the *same* `JwtService` the password-login path uses, and redirects the browser
to the SPA with that token in a URL fragment (`#token=...`, never a query param, so it can't end up
in this server's access logs or an outbound `Referer` header). From the frontend's perspective, an
OIDC login and a password login both end with "here is a bearer token and here is `/users/me`" -
there is no OIDC-specific session, cookie, or client-side code path after that point.

## Alternatives considered

1. **Separate identity per login method (no linking).** An OIDC login with an email matching an
   existing JWT account would create a *second*, disconnected `users` row. Rejected: this project's
   authorization model (department assignment, approval-chain membership, "who requested this
   requisition") is keyed entirely off a `users.id`. Two rows for one real person would mean their
   approval history and requisitions silently fork depending on which login method they happened
   to use that day - a worse outcome than the feature not existing, not a neutral one.
2. **Manual merge flow** (create both, let an admin or the user explicitly link them later).
   Handles the case where the same person's work email is deliberately used for two unrelated
   accounts, which linking-by-email would incorrectly merge. Rejected for this pass as real scope
   for a feature this repo doesn't have a use case motivating (there is no scenario in this app
   where two *different* people would ever legitimately share one email) - noted here as the right
   next step if that assumption stops holding, rather than silently ignored.
3. **Treat OIDC as the only source of truth, deprecate JWT registration/login.** Rejected outright
   per this feature's own scope - ADR-0002's stateless-JWT decision and everything built on it
   (row-level scoping, the approval-bypass fix, every existing auth test) stays as-is.

## Consequences
- **Email is trusted as the linking key.** This assumes the IdP verifies email ownership (Keycloak
  does, via `emailVerified`) and that this app's own registration doesn't need to. A user who
  registers a JWT account with an email they don't control, then has someone else's real SSO
  identity happen to share it, would incorrectly link - not a realistic risk for a demo/internal
  app scenario, called out here rather than silently assumed away.
- **No unlink flow.** Once `oidc_provider`/`oidc_subject` are set, there's no UI or endpoint to
  clear them and force password-only login again. Tracked in `09-backlog.md`.
- **SSO-only accounts have an unusable password forever, by design** - not a bug, not something a
  "forgot password" flow (which doesn't exist in this app either) would need to special-case.
- **The OIDC login handshake needs a session; the JWT API still doesn't.** `SecurityConfig` now
  defines two `SecurityFilterChain`s: `oidcFilterChain` (`@Order(1)`, matches only `/oauth2/**` and
  `/login/**`, sessions allowed - required by `oauth2Login` to hold redirect state/nonce) and
  `apiFilterChain` (`@Order(2)`, everything else, unchanged - still `STATELESS`,
  `JwtAuthenticationFilter` only). See `OidcRedirectIT.apiAuthPathIsUnaffectedByTheOidcChainExisting`
  for the regression check that the two don't bleed into each other.
- **Keycloak's browser-facing and backend-facing addresses differ under docker-compose**
  (`localhost:8180` vs. the `keycloak` service name on the compose network), so
  `spring.security.oauth2.client.provider.keycloak` configures each endpoint URI individually
  instead of a single `issuer-uri` - see the comment in `application.yml`. Local dev without
  Compose (`mvn spring-boot:run` + `ng serve`, pointed at a standalone `docker-compose up
  keycloak`) doesn't hit this, since both sides reach Keycloak at the same `localhost:8180`.

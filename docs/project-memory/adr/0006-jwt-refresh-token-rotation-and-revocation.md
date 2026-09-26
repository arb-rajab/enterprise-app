# ADR-0006: Refresh-token rotation and revocation

## Status
Accepted

## Context
ADR-0002 chose stateless, self-validating JWT access tokens specifically to avoid a database hit
on every authenticated request, and explicitly accepted two consequences as deferred work rather
than oversights: **no server-side revocation** (a leaked access token is valid until it expires)
and **no refresh-token rotation** (`app.security.jwt.refresh-token-ttl-days` existed only as
placeholder configuration; nothing issued or accepted a refresh token). Both were tracked in
`09-backlog.md` under "Security / auth."

Real revocation of an already-issued, self-validating access token is not achievable without
either a DB lookup per request (which ADR-0002 explicitly rejected) or an in-memory deny-list that
doesn't survive a restart or scale past one instance. The standard resolution — and the one
ADR-0002 itself pointed at — is to keep access tokens short-lived and stateless, and make the
*renewal* path (not the access token itself) the thing that's checked against the database and can
be revoked.

## Decision
`POST /api/v1/auth/login` and `/register` now return both an access token (unchanged: JWT, HS256,
validated from claims alone) and an opaque **refresh token** (a 256-bit random value, not a JWT).
Only the refresh token's SHA-256 hash is persisted (`refresh_tokens` table, `RefreshToken` entity)
— the raw value is never stored, mirroring why passwords are hashed rather than kept in plaintext,
though a fast hash (not BCrypt) is appropriate here since the input is high-entropy random data,
not a low-entropy secret someone might guess.

Two new endpoints, both under the already-`permitAll()` `/api/v1/auth/**` path:

- `POST /api/v1/auth/refresh { refreshToken }` — validates the token (exists, not expired, not
  revoked), **revokes it**, and issues a brand-new access/refresh pair. This makes every refresh
  token single-use (rotation): if a refresh token is ever replayed after having already been used,
  the replay is rejected, which is what lets a stolen-and-later-reused refresh token be detected
  rather than granting an attacker indefinite renewal.
- `POST /api/v1/auth/logout { refreshToken }` — revokes the token outright, with no replacement
  issued. Idempotent: revoking an already-revoked or unknown token is a no-op, not an error.

Both are implemented once in `RefreshTokenService` (`issue`/`rotate`/`revoke`), used by
`AuthService`, with `InvalidRefreshTokenException` mapped to 401 by `GlobalExceptionHandler`. The
Angular client stores the refresh token alongside the access token and calls `/auth/logout`
best-effort on sign-out (`Auth.logout()`); it does not yet perform silent background refresh
before access-token expiry — see Consequences.

## Alternatives considered

1. **A server-side deny-list of revoked access-token JWT IDs (`jti`), checked per request.**
   Rejected: this is exactly the per-request DB/cache lookup ADR-0002 chose stateless JWTs to
   avoid, and would need a shared store (Redis) to work across horizontally-scaled instances —
   real operational surface area for a demo, per ADR-0002's own reasoning against server-side
   sessions.
2. **Non-rotating refresh tokens (issue once, accept indefinitely until its own long TTL
   expires).** Simpler, but a leaked refresh token would be as good as a permanent credential for
   its full TTL (`refresh-token-ttl-days`, currently 7). Rotation bounds this: each refresh token
   is usable exactly once, so a legitimate client and an attacker racing to use the same stolen
   token will produce a detectable double-use rather than both silently succeeding forever.
3. **Storing the raw refresh token in the database (no hashing).** Rejected for the same reason
   passwords aren't stored in plaintext — a database read (backup leak, SQL injection in some
   future change, admin error) would otherwise hand out live credentials directly.
4. **Shortening `access-token-ttl-minutes` from its current 30-minute default now that renewal is
   cheap.** Deliberately not changed in this pass — it's an orthogonal tuning knob (any value
   still benefits from revocable renewal), and changing it isn't needed to make the feature
   complete. Worth revisiting once the frontend does silent background refresh (see Consequences).

## Consequences
- New table `refresh_tokens` (`V4__refresh_tokens.sql`), indexed on `user_id`. No cleanup job for
  expired/revoked rows exists yet — fine at demo data volumes, a real deployment would want a
  scheduled sweep.
- An access token already issued before a refresh token is revoked or a user is deactivated
  remains valid until its own (short) expiry — this ADR narrows the revocation gap ADR-0002
  accepted, it does not close it to zero. That residual window is the short access-token TTL
  itself, which is unchanged by this ADR (see Alternative 4).
- **Amendment (deactivation-check bypass fix):** `rotate()` originally checked only the *token's*
  own state (not expired, not revoked) — it never checked whether the token's *user* had since been
  deactivated. That meant an already-issued refresh token (from either login path: password, or
  OIDC/SSO once ADR-0008 added it) kept renewing indefinitely after `UserService.setActive(id,
  false)`, even though a brand-new password login for that same user was correctly rejected by
  `AuthenticationManager`. `rotate()` now also rejects (and still consumes/revokes the presented
  token, keeping it single-use either way) once `existing.getUser().isActive()` is false — the
  narrower, already-accepted "an already-issued access token stays valid until its own short expiry"
  gap above is unchanged; this closes the separate, unbounded gap in the *renewal* path
  specifically. See `RefreshTokenServiceTest.rotateRejectsATokenWhoseUserHasSinceBeenDeactivated`
  (this test fails against the pre-fix code, confirmed directly by re-running it with the check
  removed) and `AuthControllerIT.refreshFailsOnceTheUsersAccountIsDeactivated`.
- The Angular client stores and sends the refresh token and calls `/auth/logout` on sign-out, but
  does **not** yet use `/auth/refresh` to renew an access token silently before it expires — today
  a 401 from an expired access token still bounces the user to `/login` (`authInterceptor`), same
  as before this ADR. Wiring up silent renewal is frontend work of its own (interceptor changes,
  concurrent-request handling during a refresh) and is tracked in `09-backlog.md` rather than
  bundled into this backend change.
- Covered by `RefreshTokenServiceTest` (unit: issuance stores only the hash, rotation revokes the
  old token and rejects reuse, expired/revoked/unknown tokens are all rejected, logout is
  idempotent) and `AuthControllerIT` (integration: register issues a refresh token; refresh
  rotates and the old token can't be reused; logout revokes; an unknown token is rejected) — see
  `07-testing.md`.
- The `06-security.md`/`09-backlog.md` "no refresh-token rotation" item is resolved; "no
  server-side revocation" is narrowed (revocation now works for the renewal path) rather than
  fully closed, per the point above, and is updated in both docs to describe the new, narrower
  scope of what's still not revocable (a live, not-yet-expired access token).

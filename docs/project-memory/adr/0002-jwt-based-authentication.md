# ADR-0002: Stateless JWT authentication over server-side sessions

## Status
Accepted. Extended (not superseded) by `adr/0008-oidc-sso-identity-linking.md`, which adds OIDC
login as an additional path alongside everything decided below.

## Context
The API needs to authenticate the Angular SPA and authorize requests against five roles. The two
mainstream options in the Spring Security ecosystem are server-side sessions (a session cookie,
state held in the `HttpSession` / a session store) or stateless signed tokens (JWT) validated
per-request without server-side session state.

## Decision
Use stateless JWTs (HS256-signed, via `io.jsonwebtoken`/jjwt), issued on `/api/v1/auth/login` and
`/api/v1/auth/register`, sent by the client as `Authorization: Bearer <token>`, and validated on
every request by a custom `JwtAuthenticationFilter` that populates the `SecurityContext` directly
from the token's claims (subject = email, plus embedded `uid` and `roles` claims) — **no database
lookup on the authenticated-request path**.

Passwords are hashed with BCrypt (Spring Security's `BCryptPasswordEncoder`); the JWT signing
secret is required configuration (`app.security.jwt.secret`, from the `JWT_SECRET` env var) and
the app fails fast at startup if it is missing or shorter than 256 bits — there is deliberately no
insecure default outside the `dev` profile.

## Alternatives considered

1. **Server-side sessions (`HttpSession` + `JSESSIONID` cookie).** Simpler to reason about
   revocation (delete the session), and avoids the token-can't-be-revoked-before-expiry problem
   below. Rejected because it requires sticky sessions or a shared session store (e.g. Redis) to
   scale horizontally, which is real operational surface area this demo doesn't need to carry, and
   because it couples the API more tightly to being called from a cookie-bearing browser context —
   less natural for a SPA calling a REST API that might, in principle, also serve other clients.

2. **OAuth2/OIDC via an external identity provider (Keycloak, Auth0, Cognito, etc.).** This is
   the right answer for a real multi-application enterprise estate with SSO requirements, and is
   explicitly the direction called out as future work in `09-backlog.md`. Rejected *for this
   project* because standing up and documenting an external IdP would shift the demo's center of
   gravity from "here is how you build the app" to "here is how you configure a third-party
   product," and because self-issued JWT is still the mechanism every OIDC provider hands you
   *after* the login redirect — this project demonstrates that half of the picture directly.

## Consequences

- **No server-side revocation.** A stolen or leaked token is valid until it expires
  (`app.security.jwt.access-token-ttl-minutes`, default 30). This is mitigated by a short TTL,
  not eliminated — see `06-security.md` for the explicit statement of this as an accepted
  limitation, not an oversight.
- **Role changes don't take effect until re-login.** Because roles are embedded in the token at
  issuance, an admin revoking a role via `PUT /api/v1/users/{id}/roles` doesn't affect that user's
  *currently held* token. Tracked in `09-backlog.md`.
- **No refresh-token rotation is implemented.** `app.security.jwt.refresh-token-ttl-days` exists
  in configuration as a placeholder for that future work but nothing issues or accepts a refresh
  token today; a user must log in again after the access token expires. This keeps the auth
  surface small and auditable for a demo, at the cost of session longevity a real product would
  want.
- Horizontal scaling of the backend is trivial from an auth perspective — any instance can
  validate any token with only the shared signing secret, no shared session state.

# Local Keycloak realm (dev/CI only)

`procureflow-realm.json` is imported by the `keycloak` service in `docker-compose.yml` (and by
the `testcontainers-keycloak` module in the backend's OIDC integration tests, from a copy at
`backend/src/test/resources/keycloak/procureflow-realm.json` - keep the two in sync). It defines:

- One confidential client, `procureflow-backend`, with a fixed dev-only secret
  (`procureflow-dev-secret`, matching the default in `application.yml`). It has
  `directAccessGrantsEnabled: true` (the OAuth2 Resource Owner Password Credentials grant) purely
  so the backend's integration tests can obtain a real, Keycloak-signed token without scripting a
  browser through the login form - a real user's browser only ever goes through the standard
  authorization-code redirect (`oauth2Login`), never this grant.
- Two test users, both with the throwaway password `Password123!` (same convention as
  `V2__seed_reference_data.sql`):
  - `sso.newhire@procureflow.test` - has no matching JWT-registered account, so logging in with it
    demonstrates provisioning a brand-new user via SSO.
  - `manager@procureflow.test` - has the same email as the JWT-registered "manager" seed account,
    so logging in with it demonstrates linking an OIDC identity onto an existing password-based
    account (see `docs/project-memory/adr/0005-oidc-sso-identity-linking.md`).

None of this is real. There is no real Google/Okta/Microsoft/Keycloak-hosted tenant anywhere in
this project - `sslRequired: none` and the fixed client secret are only safe because this Keycloak
instance is never meant to be reachable from anywhere but `localhost`/the compose network.

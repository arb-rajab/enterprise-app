-- Adds the columns needed to link a user record to an external OIDC identity (Keycloak). See
-- docs/project-memory/adr/0008-oidc-sso-identity-linking.md for why this links by email onto the
-- existing `users` row rather than modeling a separate identity table.
--
-- `password_hash` stays NOT NULL: a user provisioned purely via OIDC still gets a random,
-- unguessable BCrypt hash at creation time (see UserService.findOrProvisionForOidc), so the
-- existing JWT login path simply reports "bad credentials" for that account instead of needing a
-- null-password special case anywhere on the JWT path.

ALTER TABLE users ADD COLUMN oidc_provider VARCHAR(60);
ALTER TABLE users ADD COLUMN oidc_subject VARCHAR(255);

-- One external identity maps to at most one local user, per provider. Partial index because most
-- rows (password-only users) have no OIDC identity at all.
CREATE UNIQUE INDEX uq_users_oidc_identity ON users (oidc_provider, oidc_subject)
    WHERE oidc_subject IS NOT NULL;

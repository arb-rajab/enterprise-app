-- Backs JWT refresh-token rotation and revocation (see ADR-0006). Access tokens stay stateless
-- (validated from claims alone, no DB lookup per ADR-0002); only the refresh flow touches the
-- database, so revoking a refresh token stops a caller from minting new access tokens without
-- adding a DB hit to every authenticated request.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    -- SHA-256 hex digest of the opaque token handed to the client; the raw token itself is never
    -- persisted, mirroring why passwords are stored hashed rather than in plaintext.
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

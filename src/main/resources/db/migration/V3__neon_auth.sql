-- Authentication moved to Neon Auth (Managed Better Auth).
--
-- The backend no longer stores password hashes, issues its own access tokens, or keeps refresh
-- tokens: it verifies the Neon Auth JWT (EdDSA, against the service's published JWKS) and links
-- each account to its neon_auth user via `auth_subject` = the token's `sub` claim.
--
-- Existing rows keep their id, so every income/expense/investment/investment row that already
-- references a user stays attached to that same user. They are linked lazily: the first
-- authenticated request from that person matches on email and fills `auth_subject` in.

ALTER TABLE users ADD COLUMN auth_subject VARCHAR(255);

-- Partial index so that unlinked rows (auth_subject IS NULL) do not collide with each other.
CREATE UNIQUE INDEX ux_users_auth_subject ON users (auth_subject) WHERE auth_subject IS NOT NULL;

DROP INDEX IF EXISTS ux_users_provider_subject;

ALTER TABLE users DROP COLUMN IF EXISTS provider_subject;
ALTER TABLE users DROP COLUMN IF EXISTS auth_provider;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;

DROP TABLE IF EXISTS refresh_tokens;

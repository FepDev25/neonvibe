-- Refresh-token revocation. Refresh tokens embed this version; logout bumps it
-- so every previously issued refresh token for the user becomes invalid.
ALTER TABLE users ADD COLUMN token_version integer NOT NULL DEFAULT 0;

--liquibase formatted sql

--changeset mymail:security-contract-20261022
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT;
-- Deliberately fail on casing collisions; an operator must resolve ownership before rollout.
UPDATE users SET email = lower(trim(email));
CREATE UNIQUE INDEX IF NOT EXISTS users_canonical_email_unique ON users (lower(email));

ALTER TABLE google_accounts ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp;
ALTER TABLE google_accounts ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE google_accounts ALTER COLUMN access_token TYPE TEXT;
ALTER TABLE google_accounts ALTER COLUMN refresh_token TYPE TEXT;
ALTER TABLE google_accounts ALTER COLUMN refresh_token DROP NOT NULL;
ALTER TABLE google_accounts ADD COLUMN IF NOT EXISTS granted_scope VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE google_accounts ADD COLUMN IF NOT EXISTS reconnect_required BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE google_accounts ADD COLUMN IF NOT EXISTS revocation_pending BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE google_accounts ADD COLUMN IF NOT EXISTS revocation_retry_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp;
CREATE INDEX IF NOT EXISTS google_revocation_retry ON google_accounts(revocation_retry_at) WHERE revocation_pending = true;
CREATE UNIQUE INDEX IF NOT EXISTS google_accounts_user_unique ON google_accounts(user_id);

-- Old bearer-state-only transactions must not survive activation of the authenticated PKCE contract.
DELETE FROM google_oauth_states;
ALTER TABLE google_oauth_states ADD COLUMN IF NOT EXISTS redirect_uri VARCHAR(512) NOT NULL;
ALTER TABLE google_oauth_states ADD COLUMN IF NOT EXISTS code_challenge VARCHAR(43) NOT NULL;
ALTER TABLE google_oauth_states ADD COLUMN IF NOT EXISTS code_challenge_method VARCHAR(4) NOT NULL;
ALTER TABLE google_oauth_states ADD COLUMN IF NOT EXISTS transaction_status VARCHAR(16) NOT NULL DEFAULT 'pending';
ALTER TABLE google_oauth_states ADD CONSTRAINT google_state_method CHECK(code_challenge_method = 'S256');
ALTER TABLE google_oauth_states ADD CONSTRAINT google_state_status CHECK(transaction_status IN ('pending','consumed','cancelled'));
CREATE INDEX IF NOT EXISTS google_state_expiry ON google_oauth_states(expires_at);

CREATE TABLE revoked_access_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX revoked_token_expiry ON revoked_access_tokens(expires_at);
CREATE TABLE authentication_rate_limits (
    bucket CHAR(64) PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL
);

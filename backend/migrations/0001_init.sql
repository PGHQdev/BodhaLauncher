-- Backend exclusions are a contract (#23): no tables or fields for usage timelines,
-- notification history, app inventory, contacts, calendar, or search history.
-- Schema review enforces this list.

CREATE TABLE accounts (
  id TEXT PRIMARY KEY,
  google_sub TEXT NOT NULL UNIQUE,
  email TEXT,
  revenuecat_app_user_id TEXT,
  created_at INTEGER NOT NULL
);

CREATE TABLE devices (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  platform TEXT NOT NULL,
  app_version TEXT,
  last_seen_at INTEGER
);
CREATE INDEX idx_devices_account ON devices(account_id);

-- Convenience mirror only; billing truth stays with Play/RevenueCat (#22).
CREATE TABLE entitlements (
  account_id TEXT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
  entitlement TEXT NOT NULL,
  expires_at INTEGER,
  updated_at INTEGER NOT NULL
);

CREATE TABLE refresh_tokens (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  family_id TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'rotated', 'revoked')),
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

-- Issuance is post-v1 (#20); the table exists so account deletion's cascade contract is complete.
CREATE TABLE connector_tokens (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  connector TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_connector_tokens_account ON connector_tokens(account_id);

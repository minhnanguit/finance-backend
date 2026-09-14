-- Module: identity
CREATE TABLE users
(
  id            UUID PRIMARY KEY,
  email         VARCHAR(254) NOT NULL,
  display_name  VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status        VARCHAR(20)  NOT NULL,
  version       BIGINT       NOT NULL DEFAULT 0,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at    TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX ux_users_email ON users (email) WHERE deleted_at IS NULL;

CREATE TABLE refresh_tokens
(
  id          UUID PRIMARY KEY,
  user_id     UUID         NOT NULL REFERENCES users (id),
  device_id   VARCHAR(128) NOT NULL,
  device_name VARCHAR(100) NOT NULL,
  platform    VARCHAR(20)  NOT NULL,
  token_hash  VARCHAR(128) NOT NULL,
  issued_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  replaced_by UUID,
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at  TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user_device_active ON refresh_tokens (user_id, device_id) WHERE revoked_at IS NULL;

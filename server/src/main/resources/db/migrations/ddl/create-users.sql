--liquibase formatted sql

--changeset mymail:create-users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    idp_id VARCHAR NOT NULL UNIQUE,
    email VARCHAR NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR NOT NUll,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR
)

CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique
ON users(LOWER(email))
WHERE deleted_at IS NULL;
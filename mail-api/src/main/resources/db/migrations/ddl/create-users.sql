--liquibase formatted sql

--changeset mymail:create-users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    idp_id VARCHAR UNIQUE,
    email VARCHAR NOT NULL,
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    personal_id VARCHAR NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR NOT NUll,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR
)

CREATE UNIQUE INDEX IF NOT EXISTS consumers_email_unique
ON consumers(LOWER(email))
WHERE deleted_at IS NULL;
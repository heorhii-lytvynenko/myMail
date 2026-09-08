--liquibase formatted sql

--changeset mymail:create-google-accounts
CREATE TABLE IF NOT EXISTS google_accounts (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    google_user_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,

    access_token VARCHAR(2048) NOT NULL,
    refresh_token VARCHAR(2048) NOT NULL,
    access_token_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(255),
    version BIGINT,

    CONSTRAINT fk_google_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);
--liquibase formatted sql

--changeset mymail:create-google-oauth-states
CREATE TABLE IF NOT EXISTS google_oauth_states (
    id UUID PRIMARY KEY,

    state VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_google_oauth_states_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);
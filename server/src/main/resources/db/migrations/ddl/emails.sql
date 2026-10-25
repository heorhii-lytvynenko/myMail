--liquibase formatted sql

--changeset mymail:emails
CREATE TABLE emails (
    id UUID PRIMARY KEY,

    thread_id VARCHAR(255) NOT NULL,
    from_header TEXT,
    to_header TEXT,
    subject TEXT,
    snippet VARCHAR(4096),
    received_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    version BIGINT,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(255),

    -- Match Email's application limits; unfolded headers may span many physical mail lines.
    CONSTRAINT ck_emails_thread_id_not_blank CHECK (length(btrim(thread_id)) > 0),
    CONSTRAINT ck_emails_from_length CHECK (char_length(from_header) <= 65535),
    CONSTRAINT ck_emails_to_length CHECK (char_length(to_header) <= 65535),
    CONSTRAINT ck_emails_subject_length CHECK (char_length(subject) <= 65535)
);

CREATE INDEX idx_emails_received_at ON emails(received_at, id);

--rollback DROP TABLE emails;

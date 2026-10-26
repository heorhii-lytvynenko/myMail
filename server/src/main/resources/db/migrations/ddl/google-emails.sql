--liquibase formatted sql

--changeset mymail:google-emails
-- JOINED inheritance: shared fields, audit columns and version exist only in emails.
CREATE TABLE google_emails (
    id UUID PRIMARY KEY,
    google_message_id VARCHAR(255) NOT NULL,
    google_thread_id VARCHAR(255) NOT NULL,
    google_account_id UUID NOT NULL,

    CONSTRAINT fk_google_emails_email
        FOREIGN KEY (id) REFERENCES emails(id) ON DELETE CASCADE,
    -- The BEFORE DELETE trigger below removes complete entities, including soft-deleted rows.
    CONSTRAINT fk_google_emails_account
        FOREIGN KEY (google_account_id) REFERENCES google_accounts(id),
    CONSTRAINT uq_google_emails_account_message UNIQUE (google_account_id, google_message_id),
    CONSTRAINT ck_google_emails_message_id_not_blank CHECK (length(btrim(google_message_id)) > 0),
    CONSTRAINT ck_google_emails_thread_id_not_blank CHECK (length(btrim(google_thread_id)) > 0)
);

CREATE INDEX idx_google_emails_account_thread ON google_emails(google_account_id, google_thread_id);

--rollback DROP TABLE google_emails;

--changeset mymail:google-email-account-cleanup-function splitStatements:false
-- A child-only account cascade would orphan rows in emails. ORM cascades also skip rows
-- hidden by SoftDeletable's restriction, so cleanup belongs in this database transaction.
CREATE FUNCTION delete_google_account_emails() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM emails
    WHERE id IN (SELECT id FROM google_emails WHERE google_account_id = OLD.id);
    RETURN OLD;
END;
$$;

--rollback DROP FUNCTION delete_google_account_emails();

--changeset mymail:google-email-account-cleanup-trigger
CREATE TRIGGER trg_google_accounts_delete_emails
    BEFORE DELETE ON google_accounts
    FOR EACH ROW EXECUTE FUNCTION delete_google_account_emails();

--rollback DROP TRIGGER trg_google_accounts_delete_emails ON google_accounts;

package com.mymail.integration.google.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Encrypt existing plaintext rows before application readiness; fail closed without the key. */
@Component
@RequiredArgsConstructor
public class LegacyCredentialMigration implements ApplicationRunner {
    private static final long MIGRATION_LOCK_ID = 77209134;
    private static final String READ_CREDENTIALS_SQL =
            "select id, access_token, refresh_token from google_accounts for update";
    private static final String UPDATE_CREDENTIALS_SQL =
            "update google_accounts set access_token = ?, refresh_token = ? where id = ?";

    private final JdbcTemplate jdbc;
    private final CredentialCipher cipher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.execute("select pg_advisory_xact_lock(" + MIGRATION_LOCK_ID + ")");
        jdbc.query(READ_CREDENTIALS_SQL, this::migrateCredentials);
    }

    private void migrateCredentials(ResultSet row) throws SQLException {
        String access = row.getString("access_token");
        String refresh = row.getString("refresh_token");
        if (isEncrypted(access) && (refresh == null || isEncrypted(refresh))) {
            return;
        }
        jdbc.update(UPDATE_CREDENTIALS_SQL, encryptIfNeeded(access), encryptIfNeeded(refresh), row.getObject("id"));
    }

    private String encryptIfNeeded(String token) {
        return token == null || isEncrypted(token) ? token : cipher.encrypt(token);
    }

    private boolean isEncrypted(String token) {
        return token.startsWith(CredentialCipher.ENCRYPTED_PREFIX);
    }
}

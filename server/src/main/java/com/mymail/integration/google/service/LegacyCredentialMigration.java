package com.mymail.integration.google.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** Encrypt existing plaintext rows before application readiness; fail closed without the key. */
@Component
@RequiredArgsConstructor
public class LegacyCredentialMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final CredentialCipher cipher;
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.execute("select pg_advisory_xact_lock(77209134)");
        jdbc.query("select id, access_token, refresh_token from google_accounts for update", rs -> {
            String access = rs.getString("access_token");
            String refresh = rs.getString("refresh_token");
            if (!access.startsWith("v1:") || (refresh != null && !refresh.startsWith("v1:"))) {
                jdbc.update("update google_accounts set access_token = ?, refresh_token = ? where id = ?",
                        access.startsWith("v1:") ? access : cipher.encrypt(access),
                        refresh == null || refresh.startsWith("v1:") ? refresh : cipher.encrypt(refresh), rs.getObject("id"));
            }
        });
    }
}

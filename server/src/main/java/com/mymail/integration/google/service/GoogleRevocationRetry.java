package com.mymail.integration.google.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoogleRevocationRetry {
    private final JdbcTemplate jdbc;
    private final GoogleAccountService accounts;
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void retry() {
        for (UUID id : jdbc.query("""
                select user_id from google_accounts where revocation_pending = true
                and revocation_retry_at <= current_timestamp order by revocation_retry_at, user_id limit 100
                """,
                (rs, row) -> rs.getObject(1, UUID.class))) {
            try { accounts.retryDisconnect(id); }
            catch (RuntimeException ignored) { /* Durable pending flag keeps credentials unusable; retry next minute. */ }
        }
    }
}

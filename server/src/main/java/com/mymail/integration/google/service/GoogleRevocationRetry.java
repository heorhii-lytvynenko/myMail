package com.mymail.integration.google.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleRevocationRetry {
    private static final long RETRY_INTERVAL_MILLIS = 60_000;
    private static final int RETRY_BATCH_SIZE = 100;
    private static final String PENDING_REVOCATIONS_SQL = """
            select user_id from google_accounts where revocation_pending = true
            and revocation_retry_at <= current_timestamp order by revocation_retry_at, user_id limit ?
            """;

    private final JdbcTemplate jdbc;
    private final GoogleAccountService accounts;

    @Scheduled(fixedDelay = RETRY_INTERVAL_MILLIS, initialDelay = RETRY_INTERVAL_MILLIS)
    public void retry() {
        for (UUID userId : pendingRevocations()) {
            retryDisconnect(userId);
        }
    }

    private List<UUID> pendingRevocations() {
        return jdbc.query(PENDING_REVOCATIONS_SQL, (row, index) -> row.getObject(1, UUID.class), RETRY_BATCH_SIZE);
    }

    private void retryDisconnect(UUID userId) {
        try {
            accounts.retryDisconnect(userId);
        } catch (RuntimeException ignored) {
            // The durable pending flag keeps credentials unusable until a later retry succeeds.
        }
    }
}

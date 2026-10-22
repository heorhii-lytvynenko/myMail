package com.mymail.authentication.service;

import com.mymail.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class AuthenticationRateLimiter {
    private final JdbcTemplate jdbc;
    public void check(String category, String key, int limit) {
        Instant window = Instant.ofEpochSecond(Instant.now().getEpochSecond() / 60 * 60);
        Integer count = jdbc.queryForObject("""
                insert into authentication_rate_limits(bucket, window_start, attempts) values (?, ?, 1)
                on conflict (bucket) do update set
                  attempts = case when authentication_rate_limits.window_start = excluded.window_start
                    then authentication_rate_limits.attempts + 1 else 1 end,
                  window_start = excluded.window_start returning attempts
                """, Integer.class, TokenRevocations.digest(category + ":" + key), Timestamp.from(window));
        if (count != null && count > limit) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
    }
}

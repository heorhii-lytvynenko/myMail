package com.mymail.authentication.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRevocations {
    private final JdbcTemplate jdbc;
    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public boolean revoked(Jwt jwt) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from revoked_access_tokens where token_hash = ?)", Boolean.class,
                digest(jwt.getTokenValue())));
    }
    public void revoke(Jwt jwt) {
        jdbc.update("insert into revoked_access_tokens(token_hash, expires_at) values (?, ?) on conflict do nothing",
                digest(jwt.getTokenValue()), Timestamp.from(jwt.getExpiresAt()));
    }
    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        jdbc.update("delete from revoked_access_tokens where expires_at <= current_timestamp");
        jdbc.update("delete from authentication_rate_limits where window_start < current_timestamp - interval '2 minutes'");
        jdbc.update("delete from google_oauth_states where expires_at <= current_timestamp");
    }
}

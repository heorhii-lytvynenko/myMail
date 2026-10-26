package com.mymail.integration.google.entities;

import com.google.auth.oauth2.AccessToken;
import com.mymail.common.Modifable;
import com.mymail.integration.google.service.EncryptedTokenConverter;
import com.mymail.user.entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "google_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class GoogleAccount extends Modifable {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "google_user_id", nullable = false, unique = true)
    private String googleUserId;

    @Column(name = "email", nullable = false)
    private String email;

    @Convert(converter = EncryptedTokenConverter.class)
    @Column(name = "access_token", nullable = false, columnDefinition = "text")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String accessTokenValue;

    @Convert(converter = EncryptedTokenConverter.class)
    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    @Column(name = "granted_scope", nullable = false)
    private String grantedScope = "";
    @Column(name = "reconnect_required", nullable = false)
    private boolean reconnectRequired;
    @Column(name = "revocation_pending", nullable = false)
    private boolean revocationPending;
    @Column(name = "revocation_retry_at", nullable = false)
    private Instant revocationRetryAt = Instant.now();

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    public AccessToken getAccessToken() {
        return accessTokenValue == null ? null : new AccessToken(accessTokenValue,
                accessTokenExpiresAt == null ? null : Date.from(accessTokenExpiresAt));
    }

    public void setAccessToken(AccessToken accessToken) {
        this.accessTokenValue = accessToken == null ? null : accessToken.getTokenValue();
        this.accessTokenExpiresAt = accessToken == null || accessToken.getExpirationTime() == null
                ? null : accessToken.getExpirationTime().toInstant();
    }
}

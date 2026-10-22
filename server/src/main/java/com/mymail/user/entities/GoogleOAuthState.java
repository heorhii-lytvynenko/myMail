package com.mymail.user.entities;

import com.mymail.common.Immutable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "google_oauth_states")
public class GoogleOAuthState extends Immutable {

    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;
    @Column(name = "code_challenge", nullable = false)
    private String codeChallenge;
    @Column(name = "code_challenge_method", nullable = false)
    private String codeChallengeMethod;
    @Column(name = "transaction_status", nullable = false)
    private String transactionStatus = "pending";

    @Column(name = "state", nullable = false, unique = true)
    private String state;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
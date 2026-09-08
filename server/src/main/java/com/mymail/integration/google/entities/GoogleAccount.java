package com.mymail.integration.google.entities;

import com.mymail.common.Modifable;
import com.mymail.user.entities.User;
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
@Table(name = "google_accounts")
public class GoogleAccount extends Modifable {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "google_user_id", nullable = false, unique = true)
    private String googleUserId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;
}
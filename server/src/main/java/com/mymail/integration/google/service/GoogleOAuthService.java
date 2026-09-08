package com.mymail.integration.google.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
public class GoogleOAuthService {

    private static final List<String> SCOPES = List.of(
            GmailScopes.GMAIL_READONLY
    );

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthService(
            @Value("${google.oauth.client-id}") String clientId,
            @Value("${google.oauth.client-secret}") String clientSecret,
            @Value("${google.oauth.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String createAuthorizationUrl(String state) {
        return new GoogleAuthorizationCodeRequestUrl(
                "https://accounts.google.com/o/oauth2/v2/auth",
                clientId,
                redirectUri,
                SCOPES
        )
                .setAccessType("offline")
                .set("prompt", "consent")
                .set("state", state)
                .build();
    }

    public GoogleTokenResponse exchangeCodeForCredentials(String code) {
        try {
            return new GoogleAuthorizationCodeTokenRequest(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    code,
                    redirectUri
            ).execute();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to exchange Google authorization code",
                    e
            );
        }
    }
}
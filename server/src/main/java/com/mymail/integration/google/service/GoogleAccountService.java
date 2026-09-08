package com.mymail.integration.google.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.model.Profile;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.repository.GoogleAccountRepository;
import com.mymail.user.entities.User;
import com.mymail.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {

    private final GoogleAccountRepository googleAccountRepository;
    private final OAuthStateService oauthStateService;
    private final GmailService gmailService;
    private final UserService userService;

    @Transactional
    public void save(String state, GoogleTokenResponse response) {
        User user = oauthStateService.consumeUser(state);

        Profile profile = gmailService.getProfile(
                response.getAccessToken()
        );

        GoogleAccount account = new GoogleAccount();

        account.setUser(user);
        account.setGoogleUserId(profile.getEmailAddress());
        account.setEmail(profile.getEmailAddress());
        account.setAccessToken(response.getAccessToken());
        account.setRefreshToken(response.getRefreshToken());
        account.setAccessTokenExpiresAt(
                Instant.now().plusSeconds(
                        response.getExpiresInSeconds()
                )
        );

        googleAccountRepository.save(account);
    }

    private GoogleAccount getGoogleAccount(GoogleTokenResponse response) {
        try {
            Credential credential = new Credential.Builder(
                    com.google.api.client.auth.oauth2.BearerToken.authorizationHeaderAccessMethod()
            )
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(GsonFactory.getDefaultInstance())
                    .setClientAuthentication(
                            new com.google.api.client.auth.oauth2.ClientParametersAuthentication(
                                    null,
                                    null
                            )
                    )
                    .build()
                    .setAccessToken(response.getAccessToken())
                    .setRefreshToken(response.getRefreshToken())
                    .setExpirationTimeMilliseconds(
                            System.currentTimeMillis()
                                    + response.getExpiresInSeconds() * 1000
                    );

            Gmail gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
            )
                    .setApplicationName("myMail")
                    .build();

            com.google.api.services.gmail.model.Profile profile =
                    gmail.users()
                            .getProfile("me")
                            .execute();

            GoogleAccount account = new GoogleAccount();

            account.setGoogleUserId(profile.getEmailAddress());
            account.setEmail(profile.getEmailAddress());
            account.setAccessToken(response.getAccessToken());
            account.setRefreshToken(response.getRefreshToken());
            account.setAccessTokenExpiresAt(
                    Instant.now().plusSeconds(response.getExpiresInSeconds())
            );

            return account;

        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to retrieve Google account information",
                    e
            );
        }
    }

    public GoogleAccount getByAuthentication(Authentication authentication) {
        User user = userService.getOrCreateUser(authentication);

        return googleAccountRepository.findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Google account is not connected"
                        )
                );
    }
}
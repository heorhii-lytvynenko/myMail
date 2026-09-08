package com.mymail.integration.google.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class GmailService {

    public Profile getProfile(String accessToken) {
        try {
            Credential credential = new Credential.Builder(
                    BearerToken.authorizationHeaderAccessMethod()
            )
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(GsonFactory.getDefaultInstance())
                    .build()
                    .setAccessToken(accessToken);

            Gmail gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
            )
                    .setApplicationName("myMail")
                    .build();

            return gmail.users()
                    .getProfile("me")
                    .execute();

        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to retrieve Google account profile",
                    e
            );
        }
    }
}
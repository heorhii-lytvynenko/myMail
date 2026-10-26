package com.mymail.integration.google.factory;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.springframework.stereotype.Component;

@Component
public class GmailClientFactory {

    private static final String APPLICATION_NAME = "myMail";
    private static final GsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();

    private final HttpTransport transport;

    public GmailClientFactory()
            throws GeneralSecurityException, IOException {
        this.transport = GoogleNetHttpTransport.newTrustedTransport();
    }

    public Gmail create(AccessToken accessToken) {
        GoogleCredentials credentials =
                GoogleCredentials.create(accessToken);

        return new Gmail.Builder(
                transport,
                JSON_FACTORY,
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
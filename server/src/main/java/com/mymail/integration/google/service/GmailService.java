package com.mymail.integration.google.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.mymail.email.model.EmailMessage;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Service
public class GmailService {

    private static final String APPLICATION_NAME = "myMail";

    public Profile getProfile(String accessToken) {
        try {
            Gmail gmail = createClient(accessToken);

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

    public List<EmailMessage> getEmails(String accessToken) {
        try {
            Gmail gmail = createClient(accessToken);

            ListMessagesResponse response = gmail.users()
                    .messages()
                    .list("me")
                    .execute();

            if (response.getMessages() == null) {
                return List.of();
            }

            return response.getMessages()
                    .stream()
                    .map(message -> getEmail(gmail, message.getId()))
                    .toList();

        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to retrieve Gmail emails",
                    e
            );
        }
    }

    private EmailMessage getEmail(Gmail gmail, String messageId) {
        try {
            Message message = gmail.users()
                    .messages()
                    .get("me", messageId)
                    .setFormat("full")
                    .execute();

            return mapMessage(message);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to retrieve Gmail message",
                    e
            );
        }
    }

    private EmailMessage mapMessage(Message message) {
        MessagePart payload = message.getPayload();

        String from = getHeader(payload, "From");
        String to = getHeader(payload, "To");
        String subject = getHeader(payload, "Subject");

        String body = extractBody(payload);

        Instant receivedAt = Instant.ofEpochMilli(
                message.getInternalDate()
        );

        return EmailMessage.builder()
                .id(message.getId())
                .threadId(message.getThreadId())
                .from(from)
                .to(to)
                .subject(subject)
                .body(body)
                .receivedAt(receivedAt)
                .build();
    }


    private String getHeader(MessagePart payload, String name) {
        if (payload.getHeaders() == null) {
            return null;
        }

        return payload.getHeaders()
                .stream()
                .filter(header -> name.equalsIgnoreCase(header.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractBody(MessagePart part) {
        if (part == null) {
            return null;
        }

        String mimeType = part.getMimeType();

        if ("text/html".equalsIgnoreCase(mimeType)
                || "text/plain".equalsIgnoreCase(mimeType)) {

            if (part.getBody() != null
                    && part.getBody().getData() != null) {

                byte[] decoded = Base64.getUrlDecoder()
                        .decode(part.getBody().getData());

                return new String(
                        decoded,
                        StandardCharsets.UTF_8
                );
            }
        }

        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String body = extractBody(child);

                if (body != null) {
                    return body;
                }
            }
        }

        return null;
    }

    private Gmail createClient(String accessToken)
            throws GeneralSecurityException, IOException {

        Credential credential = new Credential.Builder(
                BearerToken.authorizationHeaderAccessMethod()
        )
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .build()
                .setAccessToken(accessToken);

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void send(
            String accessToken,
            String to,
            String subject,
            String html
    ) {
        try {
            Gmail gmail = createClient(accessToken);

            MimeMessage mimeMessage = new MimeMessage(
                    Session.getDefaultInstance(new Properties())
            );

            mimeMessage.setRecipients(
                    RecipientType.TO,
                    InternetAddress.parse(to)
            );
            mimeMessage.setSubject(
                    subject,
                    StandardCharsets.UTF_8.name()
            );
            mimeMessage.setContent(
                    html,
                    "text/html; charset=UTF-8"
            );

            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            mimeMessage.writeTo(outputStream);

            Message message = new Message();
            message.setRaw(
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(outputStream.toByteArray())
            );

            gmail.users()
                    .messages()
                    .send("me", message)
                    .execute();

        } catch (MessagingException | IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to send Gmail message",
                    e
            );
        }
    }
}
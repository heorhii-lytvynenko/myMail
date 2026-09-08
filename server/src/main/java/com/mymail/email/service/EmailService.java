package com.mymail.email.service;

import com.mymail.email.providers.EmailProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final EmailProvider emailProvider;

    public EmailService(
            @Qualifier("gmailEmailProvider") EmailProvider emailProvider
    ) {
        this.emailProvider = emailProvider;
    }

    public void send(
            String to,
            String subject,
            String html
    ) {
        emailProvider.send(to, subject, html);
    }
}
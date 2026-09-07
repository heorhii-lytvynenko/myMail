package com.mymail.email.service;

import com.mymail.email.providers.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailProvider emailProvider;

    public void send(
            String to,
            String subject,
            String html
    ) {
        emailProvider.send(to, subject, html);
    }
}
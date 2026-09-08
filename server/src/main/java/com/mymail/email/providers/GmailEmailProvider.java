package com.mymail.email.providers;

import com.mymail.email.model.EmailMessage;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.service.GmailService;
import com.mymail.integration.google.service.GoogleAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GmailEmailProvider implements EmailProvider {

    private final GmailService gmailService;
    private final GoogleAccountService googleAccountService;

    @Override
    public void send(
            String to,
            String subject,
            String html
    ) {
        GoogleAccount account = getCurrentGoogleAccount();

        gmailService.send(
                account.getAccessToken(),
                to,
                subject,
                html
        );
    }

    @Override
    public List<EmailMessage> getEmails() {
        GoogleAccount account = getCurrentGoogleAccount();

        return gmailService.getEmails(
                account.getAccessToken()
        );
    }

    private GoogleAccount getCurrentGoogleAccount() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return googleAccountService.getByAuthentication(authentication);
    }
}
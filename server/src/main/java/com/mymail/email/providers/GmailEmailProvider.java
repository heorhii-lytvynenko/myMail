package com.mymail.email.providers;

import com.google.auth.oauth2.AccessToken;
import com.mymail.common.ApiException;
import com.mymail.email.entities.Email;
import com.mymail.email.service.GmailService;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.service.GoogleAccountService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
        throw new ApiException(HttpStatus.FORBIDDEN, "GMAIL_READ_ONLY");
    }

    @Override
    public List<Email> getEmails() {
        GoogleAccount account = getCurrentGoogleAccount();
        AccessToken accessToken = account.getAccessToken();

        return gmailService.getEmails(accessToken);
    }

    private GoogleAccount getCurrentGoogleAccount() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return googleAccountService.getByAuthentication(authentication);
    }
}

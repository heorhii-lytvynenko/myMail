package com.mymail.email.service;

import com.google.api.services.gmail.Gmail;
import com.google.auth.oauth2.AccessToken;
import com.mymail.common.ApiException;
import com.mymail.email.entities.Email;
import com.mymail.email.mappers.GmailMapper;
import com.mymail.email.repository.GoogleEmailRepository;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.factory.GmailClientFactory;
import com.mymail.integration.google.service.GoogleAccountService;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailService implements EmailService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "threadId", "from", "to", "subject", "snippet", "receivedAt");

    private final GmailClientFactory gmailClientFactory;
    private final GmailMapper gmailMapper;
    private final GoogleAccountService googleAccountService;
    private final GoogleEmailRepository googleEmails;

    @Override
    public List<Email> getEmails(AccessToken accessToken) {
        Gmail gmail = gmailClientFactory.create(accessToken);
        try {
            var response = gmail.users().messages().list("me").execute();
            if (response.getMessages() == null) return List.of();
            return response.getMessages().stream()
                    .map(message -> read(gmail, message.getId(), "metadata"))
                    .toList();
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    @Override
    public Email getEmail(AccessToken accessToken, String messageId) {
        return read(gmailClientFactory.create(accessToken), messageId, "full");
    }

    private Email read(Gmail gmail, String messageId, String format) {
        try {
            return gmailMapper.toEmail(gmail.users().messages().get("me", messageId).setFormat(format).execute());
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_MAIL_UNAVAILABLE");
    }

    @Transactional(readOnly = true)
    public Page<Email> getEmails(
            Authentication authentication,
            Pageable pageable
    ) {
        Pageable requestedPage = validatedPage(pageable);
        GoogleAccount account = googleAccountService.getCachedAccountByAuthentication(authentication);
        return googleEmails.findByGoogleAccount_Id(account.getId(), requestedPage)
                .map(email -> (Email) email);
    }

    private Pageable validatedPage(Pageable pageable) {
        if (pageable.isUnpaged()) {
            pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_SORT");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "receivedAt");
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        PageRequest requested = PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
        if (requested.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_PAGE");
        }
        return requested;
    }
}

package com.mymail.email.controller;

import com.mymail.api.GoogleEmailsApi;
import com.mymail.api.model.GetGoogleEmailsResponse;
import com.mymail.email.service.GmailService;
import com.mymail.email.mappers.EmailMapper;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmailController implements GoogleEmailsApi {

    private final GmailService gmailService;
    private final EmailMapper emailMapper;

    @Override
    public ResponseEntity<GetGoogleEmailsResponse> getGmailEmails(
            Pageable pageable
    ) {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(emailMapper.toResponse(gmailService.getEmails(authentication, pageable)));
    }
}

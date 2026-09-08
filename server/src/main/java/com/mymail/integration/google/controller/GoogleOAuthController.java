package com.mymail.integration.google.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.mymail.api.GoogleIntegrationApi;
import com.mymail.integration.google.service.GoogleAccountService;
import com.mymail.integration.google.service.GoogleOAuthService;
import com.mymail.integration.google.service.OAuthStateService;
import com.mymail.user.entities.User;
import com.mymail.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class GoogleOAuthController implements GoogleIntegrationApi {

    private final GoogleOAuthService googleOAuthService;
    private final OAuthStateService oauthStateService;
    private final GoogleAccountService googleAccountService;
    private final UserService userService;

    @Override
    public ResponseEntity<Void> connectGoogle() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = userService.getOrCreateUser(authentication);

        String state = oauthStateService.create(user);

        String authorizationUrl =
                googleOAuthService.createAuthorizationUrl(state);

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(authorizationUrl))
                .build();
    }

    @Override
    public ResponseEntity<Void> googleOAuthCallback(
            String code,
            String state
    ) {
        GoogleTokenResponse response =
                googleOAuthService.exchangeCodeForCredentials(code);

        googleAccountService.save(state, response);

        return ResponseEntity.ok().build();
    }
}
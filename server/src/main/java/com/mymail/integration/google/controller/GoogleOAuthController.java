package com.mymail.integration.google.controller;

import com.mymail.api.GoogleIntegrationApi;
import com.mymail.api.model.*;
import com.mymail.integration.google.service.GoogleAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GoogleOAuthController implements GoogleIntegrationApi {
    private final GoogleAccountService accounts;
    private Authentication authentication() { return SecurityContextHolder.getContext().getAuthentication(); }
    @Override
    public ResponseEntity<GoogleAuthorizationResponse> authorizeGoogle(GoogleAuthorizeRequest request) {
        return ResponseEntity.ok(accounts.authorize(authentication(), request));
    }
    @Override
    public ResponseEntity<GoogleConnectionResponse> completeGoogle(GoogleCompleteRequest request) {
        return ResponseEntity.ok(accounts.complete(authentication(), request));
    }
    @Override
    public ResponseEntity<GoogleConnectionResponse> googleStatus() { return ResponseEntity.ok(accounts.status(authentication())); }
    @Override
    public ResponseEntity<Void> disconnectGoogle() {
        accounts.disconnect(authentication()); return ResponseEntity.noContent().build();
    }
    @Override
    public ResponseEntity<Void> connectGoogle() { return ResponseEntity.status(HttpStatus.GONE).build(); }
    @Override
    public ResponseEntity<Void> googleOAuthCallback() { return ResponseEntity.status(HttpStatus.GONE).build(); }
}

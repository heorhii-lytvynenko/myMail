package com.mymail.authentication.controller;

import com.mymail.api.AuthenticationApi;
import com.mymail.api.model.LoginRequest;
import com.mymail.api.model.LoginResponse;
import com.mymail.api.model.SignInRequest;
import com.mymail.api.model.SignInResponse;
import com.mymail.authentication.service.AuthenticationService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthenticationController implements AuthenticationApi {

    private final AuthenticationService authenticationService;

    // Public endpoint - no authentication required
    @GetMapping("/api/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "message", "This endpoint is public - no authentication required"
        ));
    }

    // Protected endpoint - requires authentication
    @GetMapping("/api/private")
    public ResponseEntity<Map<String, Object>> privateEndpoint(Authentication authentication) {

        return ResponseEntity.ok(Map.of(
                "message", "This endpoint requires authentication",
                "user", authentication.getName(),
                "scopes", authentication.getAuthorities()
        ));
    }


    @Override
    public ResponseEntity<Void> logout() {
        var authentication = (JwtAuthenticationToken)
                SecurityContextHolder.getContext().getAuthentication();
        authenticationService.logout(authentication.getToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest
    ) {
        return ResponseEntity.ok(
                authenticationService.login(loginRequest)
        );
    }

    @Override
    public ResponseEntity<SignInResponse> signIn(
            @Valid @RequestBody SignInRequest signInRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authenticationService.register(signInRequest));
    }
}

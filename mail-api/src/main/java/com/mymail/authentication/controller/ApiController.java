package com.mymail.authentication.controller;

import com.auth0.spring.boot.Auth0AuthenticationToken;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    // Public endpoint - no authentication required
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "message", "This endpoint is public - no authentication required"
        ));
    }

    // Protected endpoint - requires authentication
    @GetMapping("/private")
    public ResponseEntity<Map<String, Object>> privateEndpoint(Authentication authentication) {
        Auth0AuthenticationToken auth0Token = (Auth0AuthenticationToken) authentication;

        return ResponseEntity.ok(Map.of(
                "message", "This endpoint requires authentication",
                "user", authentication.getName(),
                "scopes", auth0Token.getAuthorities()
        ));
    }
}
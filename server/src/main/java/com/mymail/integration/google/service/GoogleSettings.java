package com.mymail.integration.google.service;

import com.mymail.common.ApiException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GoogleSettings {
    public static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String CALLBACK_PATH = "/api/integrations/google/callback";
    private static final String LOCAL_HOST = "localhost";
    private static final int LOCAL_FRONTEND_PORT = 3000;

    private final boolean enabled;
    private final Set<String> redirects;

    public GoogleSettings(@Value("${google.oauth.enabled:false}") boolean enabled,
                          @Value("${google.oauth.allowed-redirect-uris}") String redirects) {
        this.enabled = enabled;
        this.redirects = parseRedirectAllowlist(redirects);
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_DISABLED");
        }
    }

    public void checkRedirect(String uri) {
        if (!redirects.contains(uri)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDIRECT_URI");
        }
    }

    private Set<String> parseRedirectAllowlist(String configuration) {
        Set<String> allowlist = new HashSet<>();
        for (String entry : configuration.split(",")) {
            String exact = entry.strip();
            requireSafeRedirect(exact);
            allowlist.add(exact);
        }
        return allowlist;
    }

    private void requireSafeRedirect(String redirect) {
        URI uri = URI.create(redirect);
        if (!hasAllowedScheme(uri) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null
                || redirect.contains("*") || !CALLBACK_PATH.equals(uri.getPath())) {
            throw new IllegalStateException("Invalid Google redirect allowlist configuration");
        }
    }

    private boolean hasAllowedScheme(URI uri) {
        boolean localDevelopment = "http".equals(uri.getScheme())
                && LOCAL_HOST.equals(uri.getHost()) && uri.getPort() == LOCAL_FRONTEND_PORT;
        return "https".equals(uri.getScheme()) || localDevelopment;
    }
}

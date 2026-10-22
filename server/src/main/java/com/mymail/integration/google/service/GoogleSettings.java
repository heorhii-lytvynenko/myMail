package com.mymail.integration.google.service;

import com.mymail.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class GoogleSettings {
    public static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private final boolean enabled;
    private final Set<String> redirects;
    public GoogleSettings(@Value("${google.oauth.enabled:false}") boolean enabled,
                          @Value("${google.oauth.allowed-redirect-uris}") String redirects) {
        this.enabled = enabled;
        this.redirects = new HashSet<>();
        for (String entry : redirects.split(",")) {
            String exact = entry.strip();
            URI uri = URI.create(exact);
            boolean local = "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost()) && uri.getPort() == 3000;
            if (!("https".equals(uri.getScheme()) || local) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                    || exact.contains("*") || !"/api/integrations/google/callback".equals(uri.getPath()))
                throw new IllegalStateException("Invalid Google redirect allowlist configuration");
            this.redirects.add(exact);
        }
    }
    public void requireEnabled() {
        if (!enabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_DISABLED");
    }
    public void checkRedirect(String uri) {
        if (!redirects.contains(uri)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDIRECT_URI");
    }
}

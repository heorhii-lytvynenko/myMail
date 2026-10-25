package com.mymail.authentication.config;

import com.mymail.authentication.service.*;
import com.mymail.common.ApiException;
import com.mymail.common.ApiErrorResponses;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;

public class SessionSecurityFilter extends OncePerRequestFilter {
    private final TokenRevocations revocations;
    private final AuthenticationRateLimiter limiter;
    private final ApiErrorResponses errors;
    public SessionSecurityFilter(TokenRevocations revocations, AuthenticationRateLimiter limiter, ApiErrorResponses errors) {
        this.revocations = revocations;
        this.limiter = limiter;
        this.errors = errors;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI();
        response.setHeader("Cache-Control", "no-store");
        try {
            if ("POST".equals(request.getMethod()) && (path.equals("/api/authentication/login")
                    || path.equals("/api/authentication/signIn"))) {
                // Do not trust caller-supplied forwarding headers. Also limit by canonical email in the service.
                limiter.check(path, request.getRemoteAddr(), path.endsWith("login") ? 100 : 20);
            }
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
                var jwt = auth.getToken();
                boolean logout = "POST".equals(request.getMethod()) && path.equals("/api/authentication/logout");
                if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())
                        || jwt.getSubject() == null || jwt.getSubject().isBlank() || (!logout && revocations.revoked(jwt))) {
                    errors.write(request, response, 401, "UNAUTHORIZED"); return;
                }
            }
        } catch (ApiException ex) {
            errors.write(request, response, ex.getStatusCode().value(), ex.code()); return;
        } catch (RuntimeException ex) {
            errors.write(request, response, 503, "SERVICE_UNAVAILABLE"); return;
        }
        chain.doFilter(request, response);
    }
}

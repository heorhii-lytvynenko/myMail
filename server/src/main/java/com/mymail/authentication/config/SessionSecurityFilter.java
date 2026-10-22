package com.mymail.authentication.config;

import com.mymail.authentication.service.*;
import com.mymail.common.ApiException;
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
    public SessionSecurityFilter(TokenRevocations revocations, AuthenticationRateLimiter limiter) {
        this.revocations = revocations; this.limiter = limiter;
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
                    reject(response, 401, "UNAUTHORIZED"); return;
                }
            }
        } catch (ApiException ex) {
            if (ex.getStatusCode().value() == 429) response.setHeader("Retry-After", "60");
            reject(response, ex.getStatusCode().value(), ex.code()); return;
        } catch (RuntimeException ex) {
            reject(response, 503, "SERVICE_UNAVAILABLE"); return;
        }
        chain.doFilter(request, response);
    }
    public static void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"Request could not be completed.\"}");
    }
}

package com.mymail.authentication.config;

import com.mymail.authentication.service.*;
import com.mymail.common.ApiErrorResponses;
import jakarta.servlet.DispatcherType;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${auth0.domain}") String domain, @Value("${auth0.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri("https://" + domain + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(validators("https://" + domain + "/", audience));
        return decoder;
    }
    public static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", value -> value != null && value.contains(audience)),
                jwt -> jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now())
                        && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                        ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Invalid application session", null)));
    }
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, TokenRevocations revocations,
                                    AuthenticationRateLimiter limiter, ApiErrorResponses errors) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api/public").permitAll()
                        .requestMatchers(HttpMethod.GET, "/integrations/google/connect", "/integrations/google/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/authentication/login", "/api/authentication/signIn").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHORIZED"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403, "FORBIDDEN")))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHORIZED")))
                .addFilterAfter(new SessionSecurityFilter(revocations, limiter, errors), BearerTokenAuthenticationFilter.class)
                .build();
    }
}

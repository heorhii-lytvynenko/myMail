package com.mymail.integration.google.mappers;

import com.google.auth.oauth2.AccessToken;
import com.mymail.api.model.GoogleAuthorizationResponse;
import com.mymail.api.model.GoogleConnectionResponse;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.integration.google.service.GoogleSettings;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static com.mymail.integration.google.GoogleOAuthConstants.ACCESS_TOKEN;
import static com.mymail.integration.google.GoogleOAuthConstants.EXPIRES_IN;
import static com.mymail.integration.google.GoogleOAuthConstants.TRANSACTION_LIFETIME_SECONDS;

@Mapper(componentModel = "spring")
public interface GoogleAccountMapper {
    @Mapping(target = "status", expression = "java(connectionStatus(account))")
    @Mapping(target = "scopes", expression = "java(scopes(account))")
    GoogleConnectionResponse toResponse(GoogleAccount account);

    default GoogleConnectionResponse disconnected() {
        return new GoogleConnectionResponse().status(GoogleConnectionResponse.StatusEnum.DISCONNECTED).scopes(null);
    }

    @Mapping(target = "authorizationUrl", source = "url")
    @Mapping(target = "expiresIn", constant = "" + TRANSACTION_LIFETIME_SECONDS)
    GoogleAuthorizationResponse toAuthorizationResponse(String state, URI url);

    default void updateToken(Map<?, ?> token, GoogleAccount account) {
        account.setAccessToken(accessToken(token));
    }

    default AccessToken accessToken(Map<?, ?> token) {
        String value = (String) token.get(ACCESS_TOKEN);
        long lifetimeSeconds = ((Number) token.get(EXPIRES_IN)).longValue();
        Instant expiresAt = Instant.now().plusSeconds(lifetimeSeconds);
        return new AccessToken(value, Date.from(expiresAt));
    }

    default GoogleConnectionResponse.StatusEnum connectionStatus(GoogleAccount account) {
        return account.isReconnectRequired() || account.isRevocationPending()
                ? GoogleConnectionResponse.StatusEnum.RECONNECT_REQUIRED : GoogleConnectionResponse.StatusEnum.CONNECTED;
    }

    default List<String> scopes(GoogleAccount account) {
        return GoogleSettings.SCOPE.equals(account.getGrantedScope()) ? List.of(GoogleSettings.SCOPE) : List.of();
    }
}

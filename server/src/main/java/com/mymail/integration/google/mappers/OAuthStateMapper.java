package com.mymail.integration.google.mappers;

import com.mymail.user.entities.GoogleOAuthState;
import com.mymail.user.entities.User;
import java.time.Instant;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static com.mymail.integration.google.GoogleOAuthConstants.PKCE_METHOD;

@Mapper(componentModel = "spring")
public interface OAuthStateMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "state")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "redirectUri", source = "redirect")
    @Mapping(target = "codeChallenge", source = "challenge")
    @Mapping(target = "codeChallengeMethod", constant = PKCE_METHOD)
    @Mapping(target = "expiresAt", source = "expiresAt")
    GoogleOAuthState toEntity(String state, User user, String redirect, String challenge, Instant expiresAt);
}

package com.mymail.authentication.service;

import com.mymail.api.model.LoginRequest;
import com.mymail.api.model.LoginResponse;
import com.mymail.api.model.SignInRequest;
import com.mymail.api.model.SignInResponse;
import com.mymail.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final Auth0Client auth0Client;
    private final UserService userService;

    public LoginResponse login(LoginRequest request) {
        return auth0Client.login(request.getEmail(), request.getPassword());
    }

    public SignInResponse register(SignInRequest request) {
        String idpId = auth0Client.register(request.getEmail(), request.getPassword());
        userService.saveRegisteredUser(idpId, request.getEmail());
        LoginResponse token = auth0Client.login(request.getEmail(), request.getPassword());
        return new SignInResponse().idpId(idpId)
                .accessToken(token.getAccessToken()).tokenType(token.getTokenType());
    }
}

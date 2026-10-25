package com.mymail.authentication.service;

import com.mymail.api.model.LoginRequest;
import com.mymail.api.model.LoginResponse;
import com.mymail.api.model.SignInRequest;
import com.mymail.api.model.SignInResponse;
import com.mymail.authentication.mappers.AuthenticationMapper;
import com.mymail.common.ApiException;
import com.mymail.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final Auth0Client auth0Client;
    private final AuthenticationMapper mapper;
    private final UserService userService;
    private final AuthenticationRateLimiter limiter;
    private final TokenRevocations revocations;
    public void logout(Jwt jwt) { revocations.revoke(jwt); }

    public LoginResponse login(LoginRequest request) {
        String email = IdentityPolicy.email(request.getEmail());
        limiter.check("login-email", email, 10);
        return auth0Client.login(email, request.getPassword());
    }

    public SignInResponse register(SignInRequest request) {
        String email = IdentityPolicy.email(request.getEmail());
        limiter.check("signup-email", email, 5);
        if (request.getPassword() == null || request.getPassword().codePointCount(0, request.getPassword().length()) < 8)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD");
        userService.requireEmailAvailable(email);
        String idpId = auth0Client.register(email, request.getPassword());
        userService.saveRegisteredUser(idpId, email);
        LoginResponse token = auth0Client.login(email, request.getPassword());
        return mapper.toSignInResponse(idpId, token);
    }
}

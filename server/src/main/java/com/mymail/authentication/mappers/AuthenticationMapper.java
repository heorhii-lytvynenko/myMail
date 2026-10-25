package com.mymail.authentication.mappers;

import com.mymail.api.model.LoginResponse;
import com.mymail.api.model.SignInResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthenticationMapper {
    @Mapping(target = "idpId", source = "idpId")
    @Mapping(target = "accessToken", source = "token.accessToken")
    @Mapping(target = "tokenType", source = "token.tokenType")
    @Mapping(target = "expiresIn", source = "token.expiresIn")
    SignInResponse toSignInResponse(String idpId, LoginResponse token);

    @Mapping(target = "accessToken", source = "token")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresIn", source = "remaining")
    LoginResponse toLoginResponse(String token, long remaining);
}

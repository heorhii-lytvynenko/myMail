package com.mymail.authentication.controller;

import com.mymail.api.model.LoginRequest;
import com.mymail.api.model.SignInRequest;
import com.mymail.authentication.service.IdentityPolicy;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice
public class CredentialBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == LoginRequest.class || type == SignInRequest.class;
    }
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
                                Type type, Class<? extends HttpMessageConverter<?>> converter) {
        if (body instanceof LoginRequest request) request.setEmail(IdentityPolicy.email(request.getEmail()));
        if (body instanceof SignInRequest request) request.setEmail(IdentityPolicy.email(request.getEmail()));
        return body;
    }
}

package com.mymail.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiException extends ResponseStatusException {
    private final String code;
    public ApiException(HttpStatus status, String code) {
        super(status, code);
        this.code = code;
    }
    public String code() { return code; }
}

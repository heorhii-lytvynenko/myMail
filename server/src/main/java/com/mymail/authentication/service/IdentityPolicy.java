package com.mymail.authentication.service;

import com.mymail.common.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class IdentityPolicy {
    private IdentityPolicy() {}
    public static String email(String email) {
        if (email == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        return normalized;
    }
}

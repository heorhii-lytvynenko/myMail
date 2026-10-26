package com.mymail.email.controller;

import com.mymail.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Validate raw values before Spring's Pageable resolver can silently normalize them. */
@Component
public class EmailPaginationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        validateInteger(request.getParameterValues("page"), 0);
        validateInteger(request.getParameterValues("size"), 1);
        return true;
    }

    private void validateInteger(String[] values, int minimum) {
        if (values == null) {
            return;
        }
        if (values.length != 1) {
            throw invalidPagination();
        }
        try {
            int value = Integer.parseInt(values[0]);
            if (value < minimum) {
                throw invalidPagination();
            }
        } catch (NumberFormatException ex) {
            throw invalidPagination();
        }
    }

    private ApiException invalidPagination() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_PAGINATION");
    }
}

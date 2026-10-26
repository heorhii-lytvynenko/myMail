package com.mymail.common;

import com.mymail.api.model.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@RequiredArgsConstructor
public class ApiErrors {
    private final ApiErrorResponses errors;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorDto> status(ResponseStatusException ex, HttpServletRequest request) {
        String error = ex instanceof ApiException safe ? safe.code() : "REQUEST_REJECTED";
        return errors.response(request, ex.getStatusCode().value(), error);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<ErrorDto> validation(Exception ex, HttpServletRequest request) {
        return errors.response(request, 400, "VALIDATION_FAILED");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDto> unauthorized(Exception ex, HttpServletRequest request) {
        return errors.response(request, 401, "UNAUTHORIZED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> forbidden(Exception ex, HttpServletRequest request) {
        return errors.response(request, 403, "FORBIDDEN");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> conflict(Exception ex, HttpServletRequest request) {
        return errors.response(request, 409, "ACCOUNT_CONFLICT");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorDto> notFound(Exception ex, HttpServletRequest request) {
        return errors.response(request, 404, "NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDto> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity<ErrorDto> response = errors.response(request, 405, "METHOD_NOT_ALLOWED");
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).headers(ex.getHeaders())
                .body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorDto> unsupportedMediaType(Exception ex, HttpServletRequest request) {
        return errors.response(request, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorDto> unacceptableMediaType(Exception ex, HttpServletRequest request) {
        return errors.response(request, 406, "NOT_ACCEPTABLE");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorDto> unavailable(Exception ex, HttpServletRequest request) {
        return errors.response(request, 503, "SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> unexpected(Exception ex, HttpServletRequest request) {
        return errors.response(request, 500, "INTERNAL_SERVER_ERROR");
    }
}

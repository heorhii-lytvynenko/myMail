package com.mymail.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    public static Map<String, String> body(String code) {
        return Map.of("code", code, "message", "Request could not be completed.");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex) {
        String code = ex instanceof ApiException safe ? safe.code() : "REQUEST_REJECTED";
        var builder = ResponseEntity.status(ex.getStatusCode()).cacheControl(CacheControl.noStore());
        if (ex.getStatusCode().value() == 429) builder.header("Retry-After", "60");
        return builder.body(body(code));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<?> validation(Exception ex) { return error(400, "VALIDATION_FAILED"); }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> unauthorized(Exception ex) { return error(401, "UNAUTHORIZED"); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> forbidden(Exception ex) { return error(403, "FORBIDDEN"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(Exception ex) { return error(409, "ACCOUNT_CONFLICT"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unavailable(Exception ex) { return error(503, "SERVICE_UNAVAILABLE"); }
    private ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body(code));
    }
}

package com.mymail.common;

import com.mymail.api.model.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** One error contract for MVC controllers and security failures before controller dispatch. */
@Component
@RequiredArgsConstructor
public class ApiErrorResponses {
    private final JsonMapper json;

    public ResponseEntity<ErrorDto> response(HttpServletRequest request, int status, String error) {
        return ResponseEntity.status(status).headers(headers(status)).body(body(request, status, error));
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String error)
            throws IOException {
        response.setStatus(status);
        headers(status).forEach((name, values) -> {
            response.setHeader(name, values.getFirst());
            values.stream().skip(1).forEach(value -> response.addHeader(name, value));
        });
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body(request, status, error));
    }

    private static ErrorDto body(HttpServletRequest request, int status, String error) {
        // getRequestURI excludes query parameters, which can contain OAuth codes/state.
        return new ErrorDto(error, status, OffsetDateTime.now(ZoneOffset.UTC), request.getRequestURI())
                .message("Request could not be completed.");
    }

    private static HttpHeaders headers(int status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        if (status == 401) headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (status == 429) headers.set(HttpHeaders.RETRY_AFTER, "60");
        return headers;
    }
}

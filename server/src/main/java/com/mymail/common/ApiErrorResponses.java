package com.mymail.common;

import com.mymail.api.model.ErrorDto;
import com.mymail.common.mappers.ApiErrorMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** One error contract for MVC controllers and security failures before controller dispatch. */
@Component
@RequiredArgsConstructor
public class ApiErrorResponses {
    private final JsonMapper json;
    private final ApiErrorMapper mapper;

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

    private ErrorDto body(HttpServletRequest request, int status, String error) {
        // getRequestURI excludes query parameters, which can contain OAuth codes/state.
        return mapper.toResponse(request.getRequestURI(), status, error);
    }

    private static HttpHeaders headers(int status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        if (status == 401) headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (status == 429) headers.set(HttpHeaders.RETRY_AFTER, "60");
        return headers;
    }
}

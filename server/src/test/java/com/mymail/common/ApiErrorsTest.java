package com.mymail.common;

import com.mymail.api.model.SignInRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiErrorsTest {
    private final JsonMapper json = new JsonMapper();
    private final ApiErrorResponses errors = new ApiErrorResponses(json);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new ApiErrors(errors)).build();
    }

    @Test
    void validationAndMalformedJsonReturnErrorDtoWithoutRejectedValues() throws Exception {
        var invalid = mvc.perform(post("/contract/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"private-marker\"}")).andReturn();
        assertError(invalid, 400, "VALIDATION_FAILED");
        assertFalse(invalid.getResponse().getContentAsString().contains("private-marker"));
        var malformed = mvc.perform(post("/contract/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{")).andReturn();
        assertError(malformed, 400, "VALIDATION_FAILED");
    }

    @Test
    void explicitStatusesDuplicatesDatabaseFailuresAndUnexpectedErrorsRemainDistinct() throws Exception {
        Map<String, Integer> cases = Map.of("policy", 422, "duplicate", 409, "database", 503, "provider", 502, "unexpected", 500);
        Map<String, String> codes = Map.of("policy", "REGISTRATION_REJECTED", "duplicate", "ACCOUNT_CONFLICT",
                "database", "SERVICE_UNAVAILABLE", "provider", "GOOGLE_PROVIDER_FAILURE", "unexpected", "INTERNAL_SERVER_ERROR");
        for (var test : cases.entrySet()) {
            var result = mvc.perform(get("/contract/failure/" + test.getKey()).queryParam("state", "private-marker")).andReturn();
            assertError(result, test.getValue(), codes.get(test.getKey()));
            assertFalse(result.getResponse().getContentAsString().contains("private-marker"));
        }
    }

    @Test
    void unsupportedMediaTypeMissingParameterAndWrongMethodUseHttpStatusFromContract() throws Exception {
        assertError(mvc.perform(post("/contract/validate").contentType(MediaType.TEXT_PLAIN).content("body")).andReturn(),
                415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(mvc.perform(get("/contract/parameter")).andReturn(), 400, "VALIDATION_FAILED");
        var wrongMethod = mvc.perform(post("/contract/failure/policy")).andReturn();
        assertError(wrongMethod, 405, "METHOD_NOT_ALLOWED");
        assertTrue(wrongMethod.getResponse().getHeader("Allow").contains("GET"));
    }

    @Test
    void securityWriterUsesSameDtoAndNeverIncludesQueryParameters() throws Exception {
        var request = new MockHttpServletRequest("GET", "/contract/protected");
        request.setQueryString("code=private-marker&state=private-marker");
        var response = new MockHttpServletResponse();
        errors.write(request, response, 401, "UNAUTHORIZED");
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals(5, body.size());
        assertEquals("UNAUTHORIZED", body.get("error").asText());
        assertEquals(401, body.get("status").asInt());
        assertEquals("/contract/protected", body.get("path").asText());
        assertNotNull(OffsetDateTime.parse(body.get("timestamp").asText()));
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertFalse(response.getContentAsString().contains("private-marker"));
    }

    @Test
    void throttlingPreservesRetryAfterAndNewErrorBody() throws Exception {
        var result = mvc.perform(get("/contract/throttled")).andReturn();
        assertError(result, 429, "RATE_LIMITED");
        assertTrue(Integer.parseInt(result.getResponse().getHeader("Retry-After")) > 0);
    }

    private void assertError(MvcResult result, int status, String code) {
        assertEquals(status, result.getResponse().getStatus());
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(5, body.size());
        assertEquals(code, body.get("error").asText());
        assertEquals(status, body.get("status").asInt());
        assertEquals(result.getRequest().getRequestURI(), body.get("path").asText());
        assertNotNull(OffsetDateTime.parse(body.get("timestamp").asText()));
        assertEquals("Request could not be completed.", body.get("message").asText());
        assertFalse(body.has("code"));
    }

    @RestController
    static class ErrorController {
        @PostMapping(value = "/contract/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validate(@Valid @RequestBody SignInRequest request) { }
        @GetMapping("/contract/parameter")
        void parameter(@RequestParam("page") int page) { }
        @GetMapping("/contract/throttled")
        void throttled() { throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"); }
        @GetMapping("/contract/failure/{kind}")
        void failure(@PathVariable("kind") String kind) {
            switch (kind) {
                case "policy" -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REGISTRATION_REJECTED");
                case "duplicate" -> throw new DataIntegrityViolationException("private-marker");
                case "database" -> throw new TransientDataAccessResourceException("private-marker");
                case "provider" -> throw new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_PROVIDER_FAILURE");
                default -> throw new IllegalStateException("private-marker");
            }
        }
    }
}

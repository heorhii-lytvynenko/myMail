package com.mymail.authentication.controller;

import com.mymail.api.model.LoginResponse;
import com.mymail.api.model.SignInResponse;
import com.mymail.authentication.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationControllerTest {
    @Test
    void registrationReturnsCreatedIdentityAndTokenAsJson() throws Exception {
        var service = mock(AuthenticationService.class);
        when(service.register(any())).thenReturn(new SignInResponse().idpId("auth0|123")
                .accessToken("access-token").tokenType("Bearer"));
        MockMvcBuilders.standaloneSetup(new AuthenticationController(service)).build()
                .perform(post("/api/authentication/signIn").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idpId").value("auth0|123"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginReturnsTokenAsJson() throws Exception {
        var service = mock(AuthenticationService.class);
        when(service.login(any())).thenReturn(new LoginResponse()
                .accessToken("access-token").tokenType("Bearer"));
        MockMvcBuilders.standaloneSetup(new AuthenticationController(service)).build()
                .perform(post("/api/authentication/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}

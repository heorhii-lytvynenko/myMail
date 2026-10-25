package com.mymail.authentication.service;

import com.mymail.authentication.mappers.AuthenticationMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class Auth0ClientConfigurationTest {
    @Test
    void createsAuth0ClientWithBootManagedHttpClient() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withUserConfiguration(Auth0Client.class)
                .withBean(AuthenticationMapper.class,
                        () -> Mappers.getMapper(AuthenticationMapper.class))
                .withBean(JwtDecoder.class,
                        () -> Mockito.mock(JwtDecoder.class))
                .withPropertyValues(
                        "auth0.domain=tenant.auth0.com",
                        "auth0.client-id=test-client",
                        "auth0.client-secret=test-secret",
                        "auth0.audience=test-api",
                        "auth0.connection=database")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RestClient.Builder.class);
                    assertThat(context).hasSingleBean(Auth0Client.class);
                });
    }
}

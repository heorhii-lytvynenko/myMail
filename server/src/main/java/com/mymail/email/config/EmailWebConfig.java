package com.mymail.email.config;

import com.mymail.email.controller.EmailPaginationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class EmailWebConfig implements WebMvcConfigurer {
    private final EmailPaginationInterceptor pagination;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pagination).addPathPatterns("/integrations/google/emails");
    }
}

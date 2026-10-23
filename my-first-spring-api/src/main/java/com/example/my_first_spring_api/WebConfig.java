package com.example.my_first_spring_api;

import com.example.my_first_spring_api.security.ForcePasswordChangeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC wiring for cross-cutting request handling. Currently registers the
 * forced-password-change gate of the manual recovery flow over every /api path.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ForcePasswordChangeInterceptor forcePasswordChangeInterceptor =
            new ForcePasswordChangeInterceptor();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(forcePasswordChangeInterceptor).addPathPatterns("/api/**");
    }
}

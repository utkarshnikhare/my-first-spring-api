package com.example.my_first_spring_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.my_first_spring_api.security.UserSessionAuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            UserSessionAuthorizationFilter userSessionAuthorizationFilter,
                                            Environment environment) throws Exception {
        boolean demo = isDemoEnvironment(environment);
        return http
                .csrf(csrf -> {
                    if (demo) {
                        csrf.disable();
                    } else {
                        // The CSRF token is published as a JS-readable cookie and the
                        // frontend echoes it back in the X-XSRF-TOKEN header (see
                        // api() in common.js). CookieCsrfTokenRepository only WRITES
                        // that cookie once the CsrfToken is resolved, and Spring
                        // defers that resolution by default - which would leave the
                        // browser with no cookie and make every unsafe request fail
                        // with 403 Forbidden. Clearing the request attribute name
                        // opts out of the deferral so the cookie is always issued.
                        CookieCsrfTokenRepository tokenRepository =
                                CookieCsrfTokenRepository.withHttpOnlyFalse();
                        tokenRepository.setCookiePath("/");
                        CsrfTokenRequestAttributeHandler tokenHandler =
                                new CsrfTokenRequestAttributeHandler();
                        tokenHandler.setCsrfRequestAttributeName(null);
                        csrf.csrfTokenRepository(tokenRepository)
                             .csrfTokenRequestHandler(tokenHandler);
                    }
                })
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(userSessionAuthorizationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/demo-login", "/api/seller-app/demo-login").denyAll()
                        .requestMatchers("/h2-console/**")
                            .access((authentication, context) -> new AuthorizationDecision(demo))
                        .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/seller/**", "/api/seller-app/**").hasRole("SELLER")
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/marketplace",
                                "/api/items",
                                "/api/search",
                                "/api/kitchens/**",
                                "/api/products/**",
                                "/api/discovery/**",
                                "/h2-console/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error",
                                "/",
                                "/index.html",
                                "/seller.html",
                                "/admin.html",
                                "/kitchens",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"error\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Authentication required. Please log in.\"}");
                        }))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    public static boolean isDemoEnvironment(Environment environment) {
        return environment.matchesProfiles("!prod & (demo | dev | default | postgres-demo)");
    }

    public static boolean isDirectAuthEnabled(Environment environment) {
        return environment.getProperty("sociomart.demo.direct-auth-enabled", Boolean.class, false)
                && !environment.matchesProfiles("prod");
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

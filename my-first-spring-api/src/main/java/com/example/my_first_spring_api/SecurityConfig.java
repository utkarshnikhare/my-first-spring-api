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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.my_first_spring_api.security.UserSessionAuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            UserSessionAuthorizationFilter userSessionAuthorizationFilter,
                                            Environment environment) throws Exception {
        boolean localTools = environment.matchesProfiles("local");
        // The CSRF token is published as a JS-readable cookie and the
        // frontend echoes it back in the X-XSRF-TOKEN header (see
        // api() in common.js). CookieCsrfTokenRepository only WRITES
        // that cookie once the CsrfToken is resolved, and Spring
        // defers that resolution by default - which would leave the
        // browser with no cookie and make every unsafe request fail
        // with 403 Forbidden. Clearing the request attribute name
        // opts out of the deferral so the cookie is always issued.
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookiePath("/");
        CsrfTokenRequestAttributeHandler tokenHandler = new CsrfTokenRequestAttributeHandler();
        tokenHandler.setCsrfRequestAttributeName(null);
        return http
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(tokenRepository).csrfTokenRequestHandler(tokenHandler);
                    if (localTools) {
                        // The H2 console's own forms cannot send our token (developer tool, local only).
                        csrf.ignoringRequestMatchers("/h2-console/**");
                    }
                })
                .headers(headers -> headers
                        // Framing stays same-origin: the local H2 console is framed by its
                        // own page, while buyer/seller/admin pages cannot be embedded by
                        // an attacker site (clickjacking).
                        .frameOptions(frame -> frame.sameOrigin())
                        // Never let a browser MIME-sniff a response into a script/HTML
                        // type - uploads and API responses must keep their declared type.
                        .contentTypeOptions(contentTypeOptions -> { })
                        // Referrer: send only the origin (never the full URL, which can
                        // contain /kitchen/<slug> or ids) on cross-origin navigations.
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterBefore(userSessionAuthorizationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/demo-login", "/api/seller-app/demo-login").denyAll()
                        // Developer tools: reachable only with the "local" profile, denied everywhere else.
                        .requestMatchers("/h2-console/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .access((authentication, context) -> new AuthorizationDecision(localTools))
                        .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/seller/application/**").hasAnyRole("SELLER", "SELLER_PENDING")
                        .requestMatchers("/api/seller/**", "/api/seller-app/**").hasRole("SELLER")
                        .requestMatchers(
                                "/api/auth/**",
                                "/admin/login",
                                "/api/health",
                                "/api/marketplace",
                                "/api/items",
                                "/api/search",
                                "/api/kitchens/**",
                                "/api/products/**",
                                "/api/discovery/**",
                                "/error",
                                "/",
                                "/index.html",
                                "/seller.html",
                                "/admin.html",
                                "/kitchens",
                                "/kitchen/**",
                                "/homemade-store/**",
                                "/homemade",
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

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Authentication is handled via session-based auth endpoints");
        };
    }
}

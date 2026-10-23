package com.example.my_first_spring_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Phase E (persistent JDBC sessions): once Spring Session is on the classpath
 * the session cookie is written by Spring Session's own id resolver, NOT by the
 * servlet container, so {@code server.servlet.session.cookie.*} no longer reaches
 * the browser. This bean restores exactly the cookie behaviour the app had
 * before - Secure (HTTPS-only; browsers accept it on localhost, so local
 * development over plain http keeps working), SameSite=Lax, HttpOnly and
 * Path=/ - and pins Spring Session's default cookie name explicitly.
 *
 * <p>The 30-minute session timeout stays where it always was
 * ({@code server.servlet.session.timeout}): Spring Boot's session
 * auto-configuration falls back to that value when
 * {@code spring.session.timeout} is unset.
 */
@Configuration
public class SessionConfig {

    public static final String SESSION_COOKIE_NAME = "SESSION";

    @Bean
    CookieSerializer sessionCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE_NAME);
        serializer.setCookiePath("/");
        // HTTPS-only, mirroring server.servlet.session.cookie.secure=true.
        serializer.setUseSecureCookie(true);
        // Not JavaScript-readable - the frontend only ever needs XSRF-TOKEN.
        serializer.setUseHttpOnlyCookie(true);
        // No Base64 transport encoding: the session ids Spring Session generates
        // are cookie-safe already, so the cookie value equals the id in the
        // SPRING_SESSION.SESSION_ID column (and in logs) 1:1.
        serializer.setUseBase64Encoding(false);
        // Mirrors server.servlet.session.cookie.same-site=lax.
        serializer.setSameSite("Lax");
        // No explicit max-age: this stays a browser session cookie, as before.
        return serializer;
    }
}

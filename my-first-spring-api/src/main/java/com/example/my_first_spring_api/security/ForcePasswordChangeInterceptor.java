package com.example.my_first_spring_api.security;

import com.example.my_first_spring_api.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Manual password recovery (V1): when a user authenticates with a one-time
 * temporary password, the session is flagged MUST_CHANGE_PASSWORD and this
 * interceptor blocks every API call except the endpoints needed to complete the
 * change (change-password, logout, config) plus the health probe. The flag lives
 * in the (JDBC-backed) HTTP session, so it survives application restarts.
 */
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    /** Paths a forced-change session may still call. */
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/change-password",
            "/api/auth/logout",
            "/api/auth/config",
            "/api/health");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession session = request.getSession(false);
        boolean forced = session != null
                && Boolean.TRUE.equals(session.getAttribute(PasswordRecoveryService.MUST_CHANGE_PASSWORD_SESSION_ATTR));
        if (!forced) {
            return true;
        }
        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"You must change your password before continuing.\"}");
        return false;
    }
}

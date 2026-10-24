package com.example.my_first_spring_api.security;

import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Translates the authenticated session attribute (BUYER_USER = user id) into
 * Spring Security authorities (ROLE_BUYER / ROLE_SELLER / ROLE_ADMIN /
 * ROLE_SUPER_ADMIN) for the duration of one request. This lets SecurityConfig
 * use centralised hasRole(...) rules without scattering checks, and without
 * changing how login works.
 */
@Component
public class UserSessionAuthorizationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Autowired
    public UserSessionAuthorizationFilter(@Lazy UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object attr = session.getAttribute(BuyerService.BUYER_SESSION_KEY);
                if (attr instanceof Long userId) {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user == null) {
                        SecurityContextHolder.getContext().setAuthentication(null);
                    } else if (user.isBlocked()) {
                        // A blocked account loses every authority immediately - on
                        // the very next request, not just at next login. Protected
                        // calls are refused with 403 and the agreed, non-sensitive
                        // message; the response exposes no account data.
                        SecurityContextHolder.getContext().setAuthentication(null);
                        if (isAuthEscapeHatch(request)) {
                            // /api/auth/** stays reachable (SecurityConfig already
                            // permits it) so the client can read the block state via
                            // /me and invalidate the session with /logout. Re-login
                            // is refused separately in AuthService.login.
                            filterChain.doFilter(request, response);
                            return;
                        }
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"Your account is blocked\"}");
                        return;
                    } else {
                        // Always derive authorities from the DB so role changes
                        // (approval, demotion, ...) apply on the very next request.
                        String role = user.getRole().name();
                        if (user.getRole() == com.example.my_first_spring_api.model.UserRole.SELLER
                                && !user.isApprovedSeller()) {
                            role = "SELLER_PENDING";
                        }
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Auth endpoints are the one path a blocked session keeps: the SPA must be able
     * to ask "who am I" (and be told it is blocked) and to log out cleanly. Every
     * other path is refused with 403 while the block is active, so a blocked
     * account can never act again on the existing session.
     */
    private static boolean isAuthEscapeHatch(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/auth/");
    }
}

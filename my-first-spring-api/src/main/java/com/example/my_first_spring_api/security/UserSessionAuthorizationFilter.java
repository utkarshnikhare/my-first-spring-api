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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Translates the existing OTP session attribute (BUYER_USER = user id) into
 * Spring Security authorities (ROLE_BUYER / ROLE_SELLER / ROLE_ADMIN /
 * ROLE_SUPER_ADMIN) for the duration of one request. This lets SecurityConfig
 * use centralised hasRole(...) rules without scattering checks, and without
 * changing how login works.
 */
@Component
public class UserSessionAuthorizationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Autowired
    public UserSessionAuthorizationFilter(UserRepository userRepository) {
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
                    if (user != null) {
                        // Always derive authorities from the DB so role changes
                        // (approval, demotion, ...) apply on the very next request.
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        SecurityContextHolder.getContext().setAuthentication(null);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

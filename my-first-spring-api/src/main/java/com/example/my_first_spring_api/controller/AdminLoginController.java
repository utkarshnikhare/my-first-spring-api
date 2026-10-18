package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.dto.CredentialLoginRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direct entry points for Admin demo and operational login (/admin/login and /api/admin/login).
 * Delegates to AuthController with admin-level rate limit thresholds.
 */
@RestController
public class AdminLoginController {

    private final AuthController authController;

    @Autowired
    public AdminLoginController(AuthController authController) {
        this.authController = authController;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponseDto> adminLogin(@Valid @RequestBody CredentialLoginRequestDto request,
                                                      HttpServletRequest servletRequest,
                                                      HttpServletResponse response) {
        return authController.login(request, servletRequest, response);
    }
}

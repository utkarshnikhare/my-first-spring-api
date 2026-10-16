package com.example.my_first_spring_api.model;

/**
 * Lifecycle of a manual (Admin-mediated) password reset request.
 * A new request starts PENDING; only a Super Admin may move it to
 * APPROVED (temporary password generated) or REJECTED (closed).
 */
public enum PasswordResetRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

package com.example.my_first_spring_api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One-time, time-limited token used to reset a forgotten password. The token is
 * single-use and expires after {@code RESET_TOKEN_TTL} hours. Stored hashed? No -
 * it is used as a bearer token in the (conceptual) email link and must be
 * stored as-is, so it is protected end-to-end by the same TLS/HTTPS boundary as
 * the rest of the session. The value is short-lived, single-use and never
 * returned to the client except as a single-use reset link.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(User user, String token, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return used;
    }

    public void use() {
        this.used = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isUsedFlag() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}
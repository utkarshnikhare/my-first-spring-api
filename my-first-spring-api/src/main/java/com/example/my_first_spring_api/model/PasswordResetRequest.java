package com.example.my_first_spring_api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row of the manual (Admin-mediated) password recovery flow (V1).
 *
 * <p>The user requests a reset by mobile number (PENDING), a Super Admin reviews
 * it and either rejects it (REJECTED) or generates a one-time temporary password
 * (APPROVED). Only the BCrypt hash of the temporary password is stored; the
 * plaintext is shown to the reviewing Super Admin exactly once. The temporary
 * password is valid for 24 hours or until first use, whichever comes first
 * ({@code temp_password_used} / {@code temp_password_expires_at}).</p>
 */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PasswordResetRequestStatus status = PasswordResetRequestStatus.PENDING;

    /** The Super Admin who generated the temporary password or closed the request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    /** BCrypt hash of the one-time temporary password; null until approval. */
    @Column(name = "temp_password_hash", length = 100)
    private String tempPasswordHash;

    @Column(name = "temp_password_used", nullable = false)
    private boolean tempPasswordUsed = false;

    @Column(name = "temp_password_expires_at")
    private LocalDateTime tempPasswordExpiresAt;

    @Column(name = "action_time")
    private LocalDateTime actionTime;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(User user) {
        this.user = user;
        this.createdAt = LocalDateTime.now();
    }

    /** True when a generated temporary password has passed its 24-hour expiry. */
    public boolean isTempPasswordExpired() {
        return tempPasswordExpiresAt != null && LocalDateTime.now().isAfter(tempPasswordExpiresAt);
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

    public PasswordResetRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PasswordResetRequestStatus status) {
        this.status = status;
    }

    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }

    public String getTempPasswordHash() {
        return tempPasswordHash;
    }

    public void setTempPasswordHash(String tempPasswordHash) {
        this.tempPasswordHash = tempPasswordHash;
    }

    public boolean isTempPasswordUsed() {
        return tempPasswordUsed;
    }

    public void setTempPasswordUsed(boolean tempPasswordUsed) {
        this.tempPasswordUsed = tempPasswordUsed;
    }

    public LocalDateTime getTempPasswordExpiresAt() {
        return tempPasswordExpiresAt;
    }

    public void setTempPasswordExpiresAt(LocalDateTime tempPasswordExpiresAt) {
        this.tempPasswordExpiresAt = tempPasswordExpiresAt;
    }

    public LocalDateTime getActionTime() {
        return actionTime;
    }

    public void setActionTime(LocalDateTime actionTime) {
        this.actionTime = actionTime;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

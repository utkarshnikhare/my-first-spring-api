package com.example.my_first_spring_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Immutable record of one important Admin action.
 *
 * <p>Admin handover section 14 requires that "every important Admin action
 * should create an audit record: who acted, what changed, target
 * account/store/order, old/new state where useful, reason and timestamp".
 *
 * <p>This is the single audit store for the application. It is append-only:
 * there is no setter for {@code createdAt} and the service exposes only
 * {@code record(...)} plus read methods, so an ordinary Admin action can add a
 * row but never rewrite or delete one. Nothing in the application calls
 * {@code delete} on {@link AdminAuditLogRepository}.
 *
 * <p>Rows are intentionally never cascaded from the target entity: suspending
 * or soft-removing a seller must not erase the history of what was done to
 * that seller.
 */
@Entity
@Table(name = "admin_audit_logs", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_target", columnList = "target_type,target_id"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine name, e.g. SELLER_APPROVED, BUYER_BLOCKED, SOCIETY_DISABLED. */
    @Column(name = "action", nullable = false)
    private String action;

    /** Who acted. Null only for system-initiated changes. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name")
    private String actorName;

    /** BUYER | SELLER | STOREFRONT | ORDER | AREA | SOCIETY | PLATFORM */
    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** Human-readable label so the log stays readable after a soft removal. */
    @Column(name = "target_label")
    private String targetLabel;

    @Column(name = "old_state")
    private String oldState;

    @Column(name = "new_state")
    private String newState;

    /** Mandatory for Block / Suspend / Remove and other enforcement actions. */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public AdminAuditLog() {}

    public AdminAuditLog(String action, Long actorId, String actorName,
                         String targetType, Long targetId, String targetLabel,
                         String oldState, String newState, String reason) {
        this.action = action;
        this.actorId = actorId;
        this.actorName = actorName;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.oldState = oldState;
        this.newState = newState;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }
    public String getOldState() { return oldState; }
    public void setOldState(String oldState) { this.oldState = oldState; }
    public String getNewState() { return newState; }
    public void setNewState(String newState) { this.newState = newState; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.AdminAuditLog;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.AdminAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single write path for the Admin audit trail (handover section 14).
 *
 * <p>Every important Admin action funnels through {@link #record}. The service
 * deliberately owns no update/delete operation, so the trail is append-only by
 * construction rather than by convention.
 *
 * <p>Writes use {@code REQUIRES_NEW} so an audit row survives even if the
 * surrounding business transaction later rolls back - an enforcement action that
 * failed must not leave a "suspended" audit row behind, and equally a rejected
 * enforcement attempt is worth keeping. Recording is defensive: an audit
 * failure must never turn a successful Admin action into a 500.
 */
@Service
public class AdminAuditService {

    // ---- Actions required by handover section 14 ----
    public static final String SELLER_APPROVED = "SELLER_APPROVED";
    public static final String SELLER_REJECTED = "SELLER_REJECTED";
    public static final String SELLER_CHANGES_REQUESTED = "SELLER_CHANGES_REQUESTED";
    public static final String SELLER_SUSPENDED = "SELLER_SUSPENDED";
    public static final String STOREFRONT_PAUSED = "STOREFRONT_PAUSED";
    public static final String STOREFRONT_RESUMED = "STOREFRONT_RESUMED";
    public static final String STOREFRONT_REMOVED = "STOREFRONT_REMOVED";
    public static final String BUYER_BLOCKED = "BUYER_BLOCKED";
    public static final String BUYER_UNBLOCKED = "BUYER_UNBLOCKED";
    public static final String AREA_ENABLED = "AREA_ENABLED";
    public static final String AREA_DISABLED = "AREA_DISABLED";
    public static final String SOCIETY_ENABLED = "SOCIETY_ENABLED";
    public static final String SOCIETY_DISABLED = "SOCIETY_DISABLED";
    public static final String ORDER_CORRECTED = "ORDER_CORRECTED";

    private static final int MAX_REASON = 1000;

    private final AdminAuditLogRepository repository;

    @Autowired
    public AdminAuditService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Appends one audit row.
     *
     * @param reason mandatory for enforcement actions; callers validate before
     *               calling, this only truncates an over-long reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, User actor, String targetType, Long targetId,
                       String targetLabel, String oldState, String newState, String reason) {
        try {
            repository.save(new AdminAuditLog(
                    action,
                    actor != null ? actor.getId() : null,
                    actor != null ? actor.getName() : "SYSTEM",
                    targetType, targetId,
                    targetLabel != null && targetLabel.length() > 200 ? targetLabel.substring(0, 200) : targetLabel,
                    oldState, newState,
                    reason != null && reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason));
        } catch (RuntimeException ignored) {
            // An audit write must never break the Admin action it describes.
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recent(int limit) {
        List<AdminAuditLog> rows = repository.findAllByOrderByCreatedAtDesc();
        return rows.stream().limit(Math.max(1, Math.min(limit, 500))).map(this::toRow).toList();
    }

    /** History for one target, newest first - rendered inside Seller/Buyer detail. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forTarget(String targetType, Long targetId) {
        if (targetType == null || targetId == null) return List.of();
        return repository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId)
                .stream().map(this::toRow).toList();
    }

    private Map<String, Object> toRow(AdminAuditLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("action", a.getAction());
        m.put("actorId", a.getActorId());
        m.put("actorName", a.getActorName());
        m.put("targetType", a.getTargetType());
        m.put("targetId", a.getTargetId());
        m.put("targetLabel", a.getTargetLabel());
        m.put("oldState", a.getOldState());
        m.put("newState", a.getNewState());
        m.put("reason", a.getReason());
        m.put("at", a.getCreatedAt());
        return m;
    }
}
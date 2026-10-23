package com.example.my_first_spring_api.repository;

import com.example.my_first_spring_api.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read access to the Admin audit trail.
 *
 * <p>Deliberately exposes no {@code delete} usage anywhere in the application:
 * the audit trail is append-only so an enforcement action can always be
 * explained after the fact.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findAllByOrderByCreatedAtDesc();

    List<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    List<AdminAuditLog> findByActionOrderByCreatedAtDesc(String action);

    /** Audit history for one actor - used by the Seller detail "Admin actions" panel. */
    List<AdminAuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    @Query("select count(a) from AdminAuditLog a where a.createdAt >= :since")
    long countSince(@Param("since") java.time.LocalDateTime since);
}
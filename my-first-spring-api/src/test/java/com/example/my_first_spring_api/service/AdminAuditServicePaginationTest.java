package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.AdminAuditLog;
import com.example.my_first_spring_api.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression for the unbounded audit-log read: {@code recent(limit)} must push
 * the limit into the database via {@link Pageable} (a {@code findTop...} query)
 * instead of loading the entire table and trimming in Java. The audit trail is
 * append-only and grows forever, so a full-table read here degrades without
 * bound.
 */
class AdminAuditServicePaginationTest {

    @Test
    void recentPushesBoundedPageableToRepository() {
        AdminAuditLogRepository repository = Mockito.mock(AdminAuditLogRepository.class);
        when(repository.findTopByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());

        AdminAuditService service = new AdminAuditService(repository);
        service.recent(50);

        Mockito.verify(repository).findTopByOrderByCreatedAtDesc(PageRequest.of(0, 50));
        // The old unbounded read (findAllByOrderByCreatedAtDesc) was removed from
        // the repository entirely, so this asserts that nothing even close to it
        // (a 10k-page query) was ever pushed down to the database.
        Mockito.verify(repository, Mockito.never()).findTopByOrderByCreatedAtDesc(PageRequest.of(0, 10_000));
    }

    @Test
    void recentClampsHugeAndNonPositiveLimits() {
        AdminAuditLogRepository repository = Mockito.mock(AdminAuditLogRepository.class);
        when(repository.findTopByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());

        AdminAuditService service = new AdminAuditService(repository);
        service.recent(1_000_000); // clamped to 500
        service.recent(0);         // clamped up to 1

        Mockito.verify(repository).findTopByOrderByCreatedAtDesc(PageRequest.of(0, 500));
        Mockito.verify(repository).findTopByOrderByCreatedAtDesc(PageRequest.of(0, 1));
    }

    @Test
    void recentStillReturnsRowShapesExpectedByAdminUi() {
        AdminAuditLogRepository repository = Mockito.mock(AdminAuditLogRepository.class);
        AdminAuditLog row = new AdminAuditLog("SELLER_BLOCKED", 1L, "Admin", "SELLER", 9L,
                "Seller", null, "BLOCKED", "test");
        when(repository.findTopByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of(row));

        AdminAuditService service = new AdminAuditService(repository);
        List<java.util.Map<String, Object>> rows = service.recent(10);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "SELLER_BLOCKED")
                .containsKeys("id", "actorId", "actorName", "targetType", "targetId",
                        "targetLabel", "oldState", "newState", "reason", "at");
    }
}
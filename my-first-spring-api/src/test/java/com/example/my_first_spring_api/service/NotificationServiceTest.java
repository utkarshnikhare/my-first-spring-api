package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.NotificationEventDto;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.NotificationEvent;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.NotificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Mock NotificationEventRepository repository;
    @InjectMocks NotificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new User("Buyer", "9876500010", "A-1", UserRole.BUYER);
        user.setId(20L);
    }

    @Test
    void unreadIsLimitedToCurrentUserAndMappedToSafeDto() {
        NotificationEvent event = event(7L, 20L, false);
        when(repository.findByUserIdAndDeliveredFalseOrderByCreatedAtDesc(20L)).thenReturn(List.of(event));
        List<NotificationEventDto> result = service.getUnread(user);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Order update");
        assertThat(result.get(0).getBody()).isEqualTo("Order is ready");
        verify(repository).findByUserIdAndDeliveredFalseOrderByCreatedAtDesc(20L);
    }

    @Test
    void markReadLocksEventAndIsIdempotent() {
        NotificationEvent event = event(7L, 20L, false);
        when(repository.findByIdForUpdate(7L)).thenReturn(event);
        when(repository.save(event)).thenReturn(event);
        var result = service.markRead(7L, user);
        assertThat(result.getDelivered()).isTrue();
        verify(repository).save(event);
        service.markRead(7L, user);
        verify(repository, times(1)).save(event);
    }

    @Test
    void markReadRejectsAnotherUsersEvent() {
        NotificationEvent event = event(7L, 99L, false);
        when(repository.findByIdForUpdate(7L)).thenReturn(event);
        assertThatThrownBy(() -> service.markRead(7L, user))
                .isInstanceOf(SellerNotAuthorizedException.class);
        verify(repository, never()).save(any());
    }

    private NotificationEvent event(Long id, Long ownerId, boolean delivered) {
        NotificationEvent event = new NotificationEvent();
        event.setId(id);
        event.setUserId(ownerId);
        event.setTitle("Order update");
        event.setBody("Order is ready");
        event.setDelivered(delivered);
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}

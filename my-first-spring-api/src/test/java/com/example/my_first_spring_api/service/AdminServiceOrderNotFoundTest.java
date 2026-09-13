package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Regression test for AdminService order-detail 404 handling. */
class AdminServiceOrderNotFoundTest {

    @Mock private OrderRepository orderRepository;
    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AdminService adminService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void orderDetailNotFoundThrowsOrderNotFoundException() {
        when(orderRepository.findById(9999999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.orderDetail(9999999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("9999999");
    }

}
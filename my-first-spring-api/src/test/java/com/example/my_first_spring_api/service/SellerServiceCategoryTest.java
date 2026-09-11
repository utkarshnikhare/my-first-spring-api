package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductUpdateDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerServiceCategoryTest {

    @Mock ProductRepository productRepository;
    @Mock KitchenRepository kitchenRepository;
    @Mock UserRepository userRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock AnalyticsService analyticsService;
    @Mock HttpSession httpSession;

    @InjectMocks SellerService sellerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(approvedKitchen()));
    }

    private User approvedSeller() {
        User seller = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        seller.setId(10L);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return seller;
    }

    private Kitchen approvedKitchen() {
        User seller = approvedSeller();
        Kitchen kitchen = new Kitchen("k", "Kitchen", "d", null, seller);
        kitchen.setId(1L);
        return kitchen;
    }

    @Test
    void createProductWithMultipleCategories() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findBySeller(seller)).thenReturn(List.of(kitchen));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProductCreateDto dto = new ProductCreateDto();
        dto.setName("Poha");
        dto.setPrice(BigDecimal.valueOf(40));
        dto.setCategories(List.of("BREAKFAST", "SNACKS"));
        dto.setOrderWindowStart("08:00");
        dto.setOrderWindowEnd("10:00");

        var result = sellerService.createProduct(1L, dto, seller);
        assertThat(result.getCategory()).isEqualTo("BREAKFAST,SNACKS");
    }

    @Test
    void createProductWithSingleCategory() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen();
        when(kitchenRepository.findBySeller(seller)).thenReturn(List.of(kitchen));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        ProductCreateDto dto = new ProductCreateDto();
        dto.setName("Poha");
        dto.setPrice(BigDecimal.valueOf(40));
        dto.setCategories(List.of("LUNCH"));
        dto.setOrderWindowStart("08:00");
        dto.setOrderWindowEnd("10:00");

        var result = sellerService.createProduct(1L, dto, seller);
        assertThat(result.getCategory()).isEqualTo("LUNCH");
    }

    @Test
    void updateProductWithCategories() {
        User seller = approvedSeller();
        Kitchen kitchen = approvedKitchen();
        Product product = new Product(kitchen, "Poha", "desc", BigDecimal.valueOf(40), null);
        product.setId(1L);
        product.setCategory("BREAKFAST");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(kitchenRepository.findBySeller(seller)).thenReturn(List.of(kitchen));
        when(orderItemRepository.findByProductId(1L)).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setCategories(List.of("BREAKFAST", "LUNCH", "DINNER"));

        var result = sellerService.updateProduct(1L, dto, seller);
        assertThat(result.getCategory()).isEqualTo("BREAKFAST,LUNCH,DINNER");
    }
}

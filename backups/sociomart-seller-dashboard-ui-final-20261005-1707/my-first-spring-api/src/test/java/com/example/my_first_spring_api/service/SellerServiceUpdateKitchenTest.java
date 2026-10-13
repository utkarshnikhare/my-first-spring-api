package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderItemRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.service.FeatureService;
import com.example.my_first_spring_api.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SellerServiceUpdateKitchenTest {

    @Mock private KitchenRepository kitchenRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderService orderService;
    @Mock private FeatureService featureService;
    @Mock private HttpSession httpSession;

    @InjectMocks SellerService sellerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private User seller() {
        User s = new User("Seller", "9100000001", "A-101", UserRole.SELLER);
        s.setId(10L);
        return s;
    }

    private User otherUser() {
        User o = new User("Other", "9100000002", "B-101", UserRole.SELLER);
        o.setId(20L);
        return o;
    }

    private Kitchen ownedKitchen() {
        User seller = seller();
        Kitchen kitchen = new Kitchen("test-kitchen", "Test Kitchen", "desc", null, seller);
        kitchen.setId(1L);
        kitchen.setShortDescription("Homemade");
        kitchen.setSociety("Sunshine Society");
        kitchen.setBuilding("Building B");
        kitchen.setWhatsappLink("+91 9100000001");
        kitchen.setInstagramLink("@testkitchen");
        kitchen.setUpiId("test@okhdfc");
        kitchen.setGalleryImages("url1,url2");
        kitchen.setAvailableToday(true);
        kitchen.setSellerType(SellerType.KITCHEN);
        return kitchen;
    }

    private KitchenUpdateDto validDto() {
        KitchenUpdateDto dto = new KitchenUpdateDto();
        dto.setDisplayName("Updated Kitchen");
        dto.setDescription("Updated description");
        dto.setShortDescription("Updated Speciality");
        dto.setImageUrl("https://example.com/kitchen.jpg");
        dto.setWhatsappLink("+91 9100000099");
        dto.setInstagramLink("@updated");
        dto.setUpiId("updated@okhdfc");
        dto.setGalleryImages("url1,url2,url3");
        dto.setAvailableToday(false);
        dto.setSellerType("KITCHEN");
        return dto;
    }

    @Test
    void updateKitchenSuccessfullyUpdatesAllEditableFields() {
        Kitchen kitchen = ownedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(kitchenRepository.save(any(Kitchen.class))).thenAnswer(inv -> {
            Kitchen k = inv.getArgument(0);
            k.setId(1L);
            return k;
        });

        var result = sellerService.updateKitchen(1L, validDto(), seller());

        assertThat(result.getDisplayName()).isEqualTo("Updated Kitchen");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getShortDescription()).isEqualTo("Updated Speciality");
        assertThat(result.getImageUrl()).isEqualTo("https://example.com/kitchen.jpg");
        assertThat(result.getWhatsappLink()).isEqualTo("+91 9100000099");
        assertThat(result.getInstagramLink()).isEqualTo("@updated");
        assertThat(result.getUpiId()).isEqualTo("updated@okhdfc");
        assertThat(result.getGalleryImages()).isEqualTo("url1,url2,url3");
        assertThat(result.getAvailableToday()).isFalse();
        assertThat(result.getSellerType()).isEqualTo("KITCHEN");
    }

    @Test
    void updateKitchenRejectsSocietyChange() {
        Kitchen kitchen = ownedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        KitchenUpdateDto dto = validDto();
        dto.setSociety("Different Society");

        assertThrows(IllegalArgumentException.class,
                () -> sellerService.updateKitchen(1L, dto, seller()));
    }

    @Test
    void updateKitchenRejectsBuildingChange() {
        Kitchen kitchen = ownedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        KitchenUpdateDto dto = validDto();
        dto.setBuilding("Different Building");

        assertThrows(IllegalArgumentException.class,
                () -> sellerService.updateKitchen(1L, dto, seller()));
    }

    @Test
    void updateKitchenRejectsNameChange() {
        Kitchen kitchen = ownedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        KitchenUpdateDto dto = validDto();
        dto.setName("new-slug");

        assertThrows(IllegalArgumentException.class,
                () -> sellerService.updateKitchen(1L, dto, seller()));
    }

    @Test
    void updateKitchenPersistsGalleryImages() {
        Kitchen kitchen = ownedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(kitchenRepository.save(any(Kitchen.class))).thenAnswer(inv -> {
            Kitchen k = inv.getArgument(0);
            k.setId(1L);
            return k;
        });

        KitchenUpdateDto dto = new KitchenUpdateDto();
        dto.setGalleryImages("photo1.jpg,photo2.jpg,photo3.jpg");

        var result = sellerService.updateKitchen(1L, dto, seller());

        assertThat(result.getGalleryImages()).isEqualTo("photo1.jpg,photo2.jpg,photo3.jpg");
    }

    @Test
    void updateKitchenThrowsWhenNotFound() {
        when(kitchenRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(KitchenNotFoundException.class,
                () -> sellerService.updateKitchen(1L, validDto(), seller()));
    }

    @Test
    void updateKitchenThrowsWhenNotOwner() {
        Kitchen kitchen = ownedKitchen();
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));

        assertThrows(SellerNotAuthorizedException.class,
                () -> sellerService.updateKitchen(1L, validDto(), otherUser()));
    }
}

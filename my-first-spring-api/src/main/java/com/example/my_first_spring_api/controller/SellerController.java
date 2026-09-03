package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.ProductUpdateDto;
import com.example.my_first_spring_api.dto.UpdateOrderStatusRequest;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.SellerService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller")
public class SellerController {

    private final SellerService sellerService;
    private final BuyerService authService;

    @Autowired
    public SellerController(SellerService sellerService, BuyerService authService) {
        this.sellerService = sellerService;
        this.authService = authService;
    }

    private User requireSeller(HttpSession session) {
        User user = authService.getCurrentBuyer(session);
        if (user == null) throw new BuyerNotAuthenticatedException("Authentication required. Please verify your mobile number via OTP.");
        if (user.getRole() != UserRole.SELLER) throw new SellerNotAuthorizedException("Only sellers can perform this action");
        return user;
    }

    @GetMapping("/kitchen")
    public ResponseEntity<KitchenDto> getMyKitchen(HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.getMyKitchen(seller));
    }

    @PostMapping("/kitchen")
    public ResponseEntity<KitchenDto> createKitchen(@Valid @RequestBody KitchenCreateDto dto, HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.createKitchen(dto, seller));
    }

    @PutMapping("/kitchen/{kitchenId}")
    public ResponseEntity<KitchenDto> updateKitchen(@PathVariable Long kitchenId,
                                                    @Valid @RequestBody KitchenUpdateDto dto, HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.updateKitchen(kitchenId, dto, seller));
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getMyProducts(HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.getMyProducts(seller));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDto> createProduct(@RequestParam("kitchenId") Long kitchenId,
                                                    @Valid @RequestBody ProductCreateDto dto, HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.createProduct(kitchenId, dto, seller));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long productId,
                                                    @RequestBody ProductUpdateDto dto, HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.updateProduct(productId, dto, seller));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable Long productId, HttpSession session) {
        User seller = requireSeller(session);
        sellerService.deleteProduct(productId, seller);
        return ResponseEntity.ok(Map.of("message", "Product deleted successfully"));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderDto>> getMyOrders(HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.getMyOrders(seller));
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(@PathVariable Long orderId,
                                                      @Valid @RequestBody UpdateOrderStatusRequest request,
                                                      HttpSession session) {
        User seller = requireSeller(session);
        return ResponseEntity.ok(sellerService.updateOrderStatus(orderId, request.getOrderStatus(), seller));
    }
}

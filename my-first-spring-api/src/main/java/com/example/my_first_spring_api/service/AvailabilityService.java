package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.AppTimeZone;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final ProductRepository productRepository;
    private final KitchenRepository kitchenRepository;

    @Autowired
    public AvailabilityService(ProductRepository productRepository,
                               KitchenRepository kitchenRepository) {
        this.productRepository = productRepository;
        this.kitchenRepository = kitchenRepository;
    }

    public boolean isOrderableNow(Product product, User buyer) {
        if (product == null) return false;
        if (!isProductActive(product)) return false;
        if (!isKitchenEligible(product.getKitchen(), buyer)) return false;
        if (!isServiceDateValid(product)) return false;
        if (!isOrderWindowOpen(product, LocalDate.now(AppTimeZone.ZONE), LocalTime.now(AppTimeZone.ZONE)))
            return false;
        if (isSoldOut(product)) return false;
        return true;
    }

    public boolean isAvailableToday(Product product, User buyer) {
        if (product == null) return false;
        if (!isProductActive(product)) return false;
        if (!isKitchenEligible(product.getKitchen(), buyer)) return false;
        if (!isServiceDateValid(product)) return false;
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalTime now = LocalTime.now(AppTimeZone.ZONE);
        return isOrderWindowOpen(product, today, now) && !isSoldOut(product);
    }

    public boolean isPreorderable(Product product, User buyer) {
        if (product == null) return false;
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        if (!OfferingTiming.isPreorderForOrdering(product, today)) return false;
        if (!isProductActive(product)) return false;
        if (!isKitchenEligible(product.getKitchen(), buyer)) return false;
        return true;
    }

    private boolean isProductActive(Product product) {
        return Boolean.TRUE.equals(product.getAvailableToday())
                && !Boolean.TRUE.equals(product.getOrdersPaused())
                && !isSoldOut(product);
    }

    private boolean isKitchenEligible(Kitchen kitchen, User buyer) {
        if (kitchen == null) return false;
        if (!KitchenVisibility.isPubliclyVisible(kitchen)) return false;
        return KitchenVisibility.isServiceAreaVisible(kitchen, buyer);
    }

    private boolean isServiceDateValid(Product product) {
        LocalDate availableDate = product.getAvailableDate();
        if (availableDate == null) return true;
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        return !availableDate.isBefore(today);
    }

    private boolean isOrderWindowOpen(Product product, LocalDate today, LocalTime now) {
        return OfferingTiming.isWindowOpenNow(product, today, now);
    }

    private boolean isSoldOut(Product product) {
        return product.getRemainingQuantity() != null && product.getRemainingQuantity() <= 0;
    }

    public List<Product> filterOrderableNow(List<Product> products, User buyer) {
        return products.stream()
                .filter(p -> isOrderableNow(p, buyer))
                .collect(Collectors.toList());
    }

    public List<Product> filterAvailableToday(List<Product> products, User buyer) {
        return products.stream()
                .filter(p -> isAvailableToday(p, buyer))
                .collect(Collectors.toList());
    }

    public int countOrderableToday(List<Product> products, User buyer) {
        return (int) products.stream()
                .filter(p -> isOrderableNow(p, buyer))
                .count();
    }

    public void assertOrderable(Product product, User buyer, String context) {
        if (product == null) {
            throw new IllegalArgumentException("Offering not found.");
        }
        if (!isProductActive(product)) {
            throw new IllegalArgumentException(
                    product.getName() + " is not currently available for ordering.");
        }
        if (!isKitchenEligible(product.getKitchen(), buyer)) {
            throw new IllegalArgumentException(
                    product.getName() + " is not available in your area.");
        }
        if (!isServiceDateValid(product)) {
            throw new IllegalArgumentException(
                    product.getName() + " is for a past date and can no longer be ordered.");
        }
        LocalDate today = LocalDate.now(AppTimeZone.ZONE);
        LocalTime now = LocalTime.now(AppTimeZone.ZONE);
        if (!isOrderWindowOpen(product, today, now)) {
            throw new IllegalArgumentException(
                    "The order cutoff for " + product.getName()
                    + " has passed — orders for " + context + " are closed.");
        }
        if (isSoldOut(product)) {
            throw new IllegalArgumentException(
                    product.getName() + " is sold out.");
        }
    }
}
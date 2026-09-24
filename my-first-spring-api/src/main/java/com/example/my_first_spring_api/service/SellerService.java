package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.SellerOrderSummaryRowDto;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.ProductUpdateDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderItemRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final FeatureService featureService;

    @Autowired
    public SellerService(KitchenRepository kitchenRepository,
                         ProductRepository productRepository,
                         OrderItemRepository orderItemRepository,
                         OrderService orderService,
                         FeatureService featureService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.featureService = featureService;
    }

    public KitchenDto createKitchen(KitchenCreateDto dto, User seller) {
        String slug = dto.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (kitchenRepository.findByName(slug).isPresent()) {
            throw new IllegalArgumentException("A kitchen with this URL name already exists.");
        }
        Kitchen kitchen = new Kitchen(slug, dto.getDisplayName(), dto.getDescription(), dto.getImageUrl(), seller);
        kitchen.setShortDescription(dto.getShortDescription());
        kitchen.setSociety(dto.getSociety());
        kitchen.setServiceAreas(dto.getServiceAreas());
        kitchen.setBuilding(dto.getBuilding());
        kitchen.setWhatsappLink(dto.getWhatsappLink());
        kitchen.setInstagramLink(dto.getInstagramLink());
        kitchen.setUpiId(dto.getUpiId());
        kitchen.setAvailableToday(dto.getAvailableToday() != null ? dto.getAvailableToday() : true);
        if (dto.getSellerType() != null && !dto.getSellerType().isBlank()) {
            try { kitchen.setSellerType(com.example.my_first_spring_api.model.SellerType.valueOf(dto.getSellerType().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    public KitchenDto updateKitchen(Long kitchenId, KitchenUpdateDto dto, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        if (dto.getSociety() != null) throw new IllegalArgumentException("Primary Society is a protected field and cannot be changed");
        if (dto.getBuilding() != null) throw new IllegalArgumentException("Primary Building is a protected field and cannot be changed");
        if (dto.getName() != null) throw new IllegalArgumentException("Kitchen URL name is a protected field and cannot be changed");
        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) kitchen.setDisplayName(dto.getDisplayName());
        if (dto.getDescription() != null) kitchen.setDescription(dto.getDescription());
        if (dto.getShortDescription() != null) kitchen.setShortDescription(dto.getShortDescription());
        if (dto.getImageUrl() != null) kitchen.setImageUrl(dto.getImageUrl());
        if (dto.getServiceAreas() != null) kitchen.setServiceAreas(dto.getServiceAreas());
        if (dto.getWhatsappLink() != null) kitchen.setWhatsappLink(dto.getWhatsappLink());
        if (dto.getInstagramLink() != null) kitchen.setInstagramLink(dto.getInstagramLink());
        if (dto.getUpiId() != null) kitchen.setUpiId(dto.getUpiId());
        if (dto.getGalleryImages() != null) kitchen.setGalleryImages(dto.getGalleryImages());
        if (dto.getAvailableToday() != null) kitchen.setAvailableToday(dto.getAvailableToday());
        if (dto.getSellerType() != null && !dto.getSellerType().isBlank()) {
            try { kitchen.setSellerType(com.example.my_first_spring_api.model.SellerType.valueOf(dto.getSellerType().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderDetailForSeller(Long orderId, User seller) {
        return orderService.getOrderDtoForSeller(orderId, seller);
    }

    public KitchenDto pauseKitchen(Long kitchenId, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        kitchen.setAvailableToday(false);
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    public KitchenDto resumeKitchen(Long kitchenId, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        kitchen.setAvailableToday(true);
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    @Transactional(readOnly = true)
    public KitchenDto getMyKitchen(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        return kitchens.isEmpty() ? null : toKitchenDto(kitchens.get(0));
    }

    public ProductDto createProduct(Long kitchenId, ProductCreateDto dto, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        LocalDate offeringDate = dto.getAvailableDate();
        LocalDate today = java.time.LocalDate.now();
        boolean preorder = dto.getIsPreorder() != null
                ? dto.getIsPreorder() : offeringDate != null && offeringDate.isAfter(today);
        if (offeringDate != null && offeringDate.isAfter(today) && !preorder) {
            throw new IllegalArgumentException("Future offerings must be pre-orders.");
        }
        if (preorder && !offeringDate.isAfter(today)) {
            throw new IllegalArgumentException("Pre-orders must be for a future offering date.");
        }
        // Validate authoritative offering input before platform feature gating so
        // malformed requests receive the actionable field error, not a feature error.
        String orderWindowStart = OfferingTiming.normalizeHhmm(dto.getOrderWindowStart(), "Orders Open");
        String orderWindowEnd = OfferingTiming.requireOrdersClose(dto.getOrderWindowEnd());
        String readyByTime = dto.getReadyByTime() == null ? null : dto.getReadyByTime().trim();
        Integer maxQuantity = dto.getMaxQuantity();
        if (maxQuantity != null && maxQuantity < 0) {
            throw new IllegalArgumentException("Quantity Available cannot be negative; leave blank for unlimited.");
        }
        if (maxQuantity != null && maxQuantity == 0) {
            throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
        }
        // Server-side enforcement of the Create Offering timing rules so
        // impossible combinations are never persisted, even if the client
        // validation is bypassed.
        OfferingTiming.validateNewOffering(dto.getAvailableDate(), preorder,
                orderWindowStart, orderWindowEnd, readyByTime);
        assertFeatureCompliance(seller, preorder, offeringDate, dto.getName());
        Product product = new Product(kitchen, dto.getName(), dto.getDescription(), dto.getPrice(), dto.getImageUrl());
        product.setPriceUnit(dto.getPriceUnit());
        product.setAvailableToday(offeringDate.equals(java.time.LocalDate.now()) && !preorder);
        product.setAvailableDate(offeringDate);
        product.setOrderWindowStart(orderWindowStart);
        product.setOrderWindowEnd(orderWindowEnd);
        // Keep the legacy column aligned for old clients/seeded records. Buyer
        // orderability uses orderWindowEnd first and this value only as fallback.
        product.setCutoffTime(orderWindowEnd);
        product.setMaxQuantity(maxQuantity);
        product.setRemainingQuantity(maxQuantity);
        product.setIsPreorder(preorder);
        product.setReadyByTime(readyByTime);
        product.setCategory(joinCategories(dto.getCategories()));
        return toProductDto(productRepository.save(product));
    }

    public ProductDto updateProduct(Long productId, ProductUpdateDto dto, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        boolean effectivePreorder = dto.getIsPreorder() != null
                ? dto.getIsPreorder()
                : (dto.getAvailableDate() != null
                    ? dto.getAvailableDate().isAfter(java.time.LocalDate.now())
                    : Boolean.TRUE.equals(product.getIsPreorder()));
        java.time.LocalDate effectiveDate = dto.getAvailableDate() != null
                ? dto.getAvailableDate() : (product.getAvailableDate() != null
                    ? product.getAvailableDate()
                    : (effectivePreorder ? java.time.LocalDate.now().plusDays(1) : java.time.LocalDate.now()));
        if (effectiveDate != null && effectiveDate.isAfter(java.time.LocalDate.now()) && !effectivePreorder) {
            throw new IllegalArgumentException("Future offerings must be pre-orders.");
        }
        if (effectivePreorder && (effectiveDate == null || !effectiveDate.isAfter(java.time.LocalDate.now()))) {
            throw new IllegalArgumentException("Pre-orders must be for a future offering date.");
        }
        assertFeatureCompliance(seller, effectivePreorder, effectiveDate, product.getName());

        boolean hasOrders = !orderItemRepository.findByProductId(productId).isEmpty();
        String effectiveOpen = dto.getOrderWindowStart() != null
                ? OfferingTiming.normalizeHhmm(dto.getOrderWindowStart(), "Orders Open")
                : product.getOrderWindowStart();
        String effectiveCloseInput = dto.getOrderWindowEnd() != null
                ? dto.getOrderWindowEnd()
                : (dto.getCutoffTime() != null ? dto.getCutoffTime() : product.getOrderWindowEnd());
        String effectiveClose = effectiveCloseInput != null
                ? OfferingTiming.requireOrdersClose(effectiveCloseInput)
                : OfferingTiming.resolveOrdersClose(product);
        String effectiveReady = dto.getReadyByTime() != null
                ? dto.getReadyByTime().trim() : product.getReadyByTime();
        if (dto.getMaxQuantity() != null && dto.getMaxQuantity() < 0) {
            throw new IllegalArgumentException("Quantity Available cannot be negative; leave blank for unlimited.");
        }
        if (dto.getMaxQuantity() != null && dto.getMaxQuantity() == 0) {
            throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
        }
        if (dto.getMaxQuantity() != null && dto.getRemainingQuantity() != null
                && dto.getRemainingQuantity() > dto.getMaxQuantity()) {
            throw new IllegalArgumentException("Remaining quantity cannot exceed Quantity Available.");
        }
        if (dto.getRemainingQuantity() != null && dto.getRemainingQuantity() < 0) {
            throw new IllegalArgumentException("Remaining quantity cannot be negative.");
        }
        boolean timingChanged = dto.getAvailableDate() != null || dto.getOrderWindowStart() != null
                || dto.getOrderWindowEnd() != null || dto.getCutoffTime() != null
                || dto.getReadyByTime() != null || dto.getIsPreorder() != null;
        if (timingChanged) {
            OfferingTiming.validateNewOffering(effectiveDate, effectivePreorder,
                    effectiveOpen, effectiveClose, effectiveReady);
        }

        if (hasOrders) {
            if (dto.getName() != null && !dto.getName().isBlank() && !dto.getName().equals(product.getName()))
                throw new IllegalArgumentException("Cannot rename item after orders exist.");
            if (dto.getPrice() != null && !dto.getPrice().equals(product.getPrice()))
                throw new IllegalArgumentException("Cannot change price after orders exist.");
            if (dto.getPriceUnit() != null && !dto.getPriceUnit().equals(product.getPriceUnit()))
                throw new IllegalArgumentException("Cannot change unit after orders exist.");
            if (dto.getAvailableDate() != null && !dto.getAvailableDate().equals(product.getAvailableDate()))
                throw new IllegalArgumentException("Cannot change availability date after orders exist.");
            if (dto.getOrderWindowStart() != null && !java.util.Objects.equals(effectiveOpen, product.getOrderWindowStart()))
                throw new IllegalArgumentException("Cannot change order window start after orders exist.");
            if ((dto.getOrderWindowEnd() != null || dto.getCutoffTime() != null)
                    && !java.util.Objects.equals(effectiveClose, OfferingTiming.resolveOrdersClose(product)))
                throw new IllegalArgumentException("Cannot change Orders Close after orders exist.");
            if (dto.getReadyByTime() != null && !java.util.Objects.equals(effectiveReady, product.getReadyByTime()))
                throw new IllegalArgumentException("Cannot change ready-by time after orders exist.");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getPriceUnit() != null) product.setPriceUnit(dto.getPriceUnit());
        if (dto.getImageUrl() != null) product.setImageUrl(dto.getImageUrl());
        if (dto.getRemainingQuantity() != null) product.setRemainingQuantity(dto.getRemainingQuantity());
        if (dto.getAvailableDate() != null) {
            product.setAvailableDate(dto.getAvailableDate());
            product.setAvailableToday(dto.getAvailableDate().equals(java.time.LocalDate.now()) && !effectivePreorder);
        }
        if (dto.getOrderWindowStart() != null) product.setOrderWindowStart(effectiveOpen);
        if (dto.getOrderWindowEnd() != null || dto.getCutoffTime() != null) {
            product.setOrderWindowEnd(effectiveClose);
            product.setCutoffTime(effectiveClose);
        }
        if (dto.getReadyByTime() != null) product.setReadyByTime(effectiveReady);
        if (dto.getMaxQuantity() != null) product.setMaxQuantity(dto.getMaxQuantity());
        if (dto.getAvailableToday() != null) product.setAvailableToday(dto.getAvailableToday());
        if (dto.getIsPreorder() != null) product.setIsPreorder(effectivePreorder);
        if (dto.getCategories() != null) product.setCategory(joinCategories(dto.getCategories()));
        return toProductDto(productRepository.save(product));
    }

    public void deleteProduct(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getMyProducts(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (kitchens.isEmpty()) return List.of();
        return kitchens.stream()
                .flatMap(k -> productRepository.findByKitchen(k).stream())
                .map(this::toProductDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SellerOrderSummaryRowDto> getMyOrders(User seller) {
        return orderService.getSellerOrders(seller);
    }

    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus, User seller) {
        return orderService.updateOrderStatus(orderId, newStatus, seller);
    }

    public OrderDto markOrderAsPaid(Long orderId, User seller) {
        return orderService.markOrderAsPaid(orderId, seller);
    }

    public OrderDto acknowledgeOrder(Long orderId, User seller) {
        return orderService.acknowledgeOrder(orderId, seller);
    }

    private Kitchen getOwnedKitchen(Long kitchenId, User seller) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        if (!kitchen.getSeller().getId().equals(seller.getId())) throw new SellerNotAuthorizedException("You do not own this kitchen");
        return kitchen;
    }

    /** Joins category enum names into a comma-separated string for multi-category storage. */
    private String joinCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /** Checks whether a product's category string contains the given category. */
    private boolean hasCategory(Product product, String category) {
        String cats = product.getCategory();
        if (cats == null || category == null) return false;
        String upper = category.toUpperCase();
        for (String part : cats.split(",")) {
            if (part.trim().equals(upper)) return true;
        }
        return false;
    }

    /**
     * Enforces the platform's configurable feature rules for seller offerings:
     *  - preorders: marking a product as pre-order requires the seller to have
     *    the "preorders" feature (free by default; Super Admin can make it paid).
     *  - menu_advance_days: an availableDate further ahead than the seller's
     *    effective limit is rejected (baseline free = today + tomorrow).
     */
    private void assertFeatureCompliance(User seller, boolean isPreorder,
                                         java.time.LocalDate availableDate, String productName) {
        if (isPreorder) {
            featureService.assertSellerCanUse(seller, FeatureService.KEY_PREORDERS);
        }
        if (availableDate != null) {
            long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), availableDate);
            if (daysAhead > 0) {
                if (!featureService.sellerHasAccess(seller, FeatureService.KEY_MENU_ADVANCE_DAYS)) {
                    throw new IllegalStateException(
                            "Publishing menus in advance is currently disabled on the platform.");
                }
                Integer limit = featureService.sellerLimit(seller, FeatureService.KEY_MENU_ADVANCE_DAYS);
                int allowed = limit != null ? limit : 0;
                if (daysAhead > allowed) {
                    throw new IllegalStateException("'" + (productName == null ? "This offering" : productName)
                            + "' is " + daysAhead + " day(s) ahead, but your kitchen can publish menus only "
                            + allowed + " day(s) in advance.");
                }
            }
        }
    }

    private KitchenDto toKitchenDto(Kitchen kitchen) {
        KitchenDto dto = new KitchenDto(kitchen.getId(), kitchen.getName(), kitchen.getDisplayName(),
                kitchen.getDescription(), kitchen.getImageUrl(), kitchen.getRating(),
                kitchen.getAvailableToday(), kitchen.getSeller() != null ? kitchen.getSeller().getId() : null);
        dto.setShortDescription(kitchen.getShortDescription());
        dto.setSociety(kitchen.getSociety());
        dto.setServiceAreas(kitchen.getServiceAreas());
        dto.setBuilding(kitchen.getBuilding());
        dto.setWhatsappLink(kitchen.getWhatsappLink());
        dto.setInstagramLink(kitchen.getInstagramLink());
        dto.setUpiId(kitchen.getUpiId());
        dto.setGalleryImages(kitchen.getGalleryImages());
        dto.setOrderDeadline(kitchen.getOrderDeadline());
        dto.setSellerType(kitchen.getSellerType() != null ? kitchen.getSellerType().name() : null);
        dto.setPaused(KitchenVisibility.isPaused(kitchen));
        return dto;
    }

    private ProductDto toProductDto(Product product) {
        Kitchen kitchen = product.getKitchen();
        ProductDto dto = new ProductDto(product.getId(), kitchen != null ? kitchen.getId() : null,
                kitchen != null ? kitchen.getDisplayName() : null, product.getName(),
                product.getDescription(), product.getPrice(), product.getImageUrl(),
                product.getAvailableToday(), product.getRating());
        dto.setPriceUnit(product.getPriceUnit());
        dto.setAvailableDate(product.getAvailableDate());
        dto.setOrderWindowStart(product.getOrderWindowStart());
        dto.setOrderWindowEnd(product.getOrderWindowEnd());
        dto.setMaxQuantity(product.getMaxQuantity());
        dto.setRemainingQuantity(product.getRemainingQuantity());
        dto.setIsPreorder(product.getIsPreorder());
        dto.setKitchenSlug(kitchen != null ? kitchen.getName() : null);
        dto.setCategory(product.getCategory());
        dto.setCutoffTime(product.getCutoffTime());
        dto.setReadyByTime(product.getReadyByTime());
        dto.setPreorderType(product.getPreorderType() != null ? product.getPreorderType().name() : null);
        dto.setAvailableUntilDate(product.getAvailableUntilDate());
        dto.setTimeSlots(product.getTimeSlots());
        dto.setBookedQuantity(product.getBookedQuantity());
        dto.setSoldOut(product.isSoldOut());
        dto.setOrdersPaused(product.isOrdersPaused());
        return dto;
    }
}

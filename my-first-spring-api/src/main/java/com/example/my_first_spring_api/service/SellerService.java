package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.OrderDto;
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
        if (dto.getDisplayName() != null && !dto.getDisplayName().isBlank()) kitchen.setDisplayName(dto.getDisplayName());
        if (dto.getDescription() != null) kitchen.setDescription(dto.getDescription());
        if (dto.getShortDescription() != null) kitchen.setShortDescription(dto.getShortDescription());
        if (dto.getImageUrl() != null) kitchen.setImageUrl(dto.getImageUrl());
        if (dto.getSociety() != null) kitchen.setSociety(dto.getSociety());
        if (dto.getServiceAreas() != null) kitchen.setServiceAreas(dto.getServiceAreas());
        if (dto.getBuilding() != null) kitchen.setBuilding(dto.getBuilding());
        if (dto.getWhatsappLink() != null) kitchen.setWhatsappLink(dto.getWhatsappLink());
        if (dto.getInstagramLink() != null) kitchen.setInstagramLink(dto.getInstagramLink());
        if (dto.getUpiId() != null) kitchen.setUpiId(dto.getUpiId());
        if (dto.getAvailableToday() != null) kitchen.setAvailableToday(dto.getAvailableToday());
        if (dto.getSellerType() != null && !dto.getSellerType().isBlank()) {
            try { kitchen.setSellerType(com.example.my_first_spring_api.model.SellerType.valueOf(dto.getSellerType().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    @Transactional(readOnly = true)
    public KitchenDto getMyKitchen(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        return kitchens.isEmpty() ? null : toKitchenDto(kitchens.get(0));
    }

    public ProductDto createProduct(Long kitchenId, ProductCreateDto dto, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        assertFeatureCompliance(seller,
                Boolean.TRUE.equals(dto.getIsPreorder()), dto.getAvailableDate(), dto.getName());
        // Normalize optional timing fields: blank strings mean "not provided"
        // (e.g. blank Orders Open = accepting orders immediately), never an
        // invalid empty timestamp.
        String orderWindowStart = normalizedHhmm(dto.getOrderWindowStart(), "orderWindowStart");
        String orderWindowEnd = normalizedHhmm(dto.getOrderWindowEnd(), "orderWindowEnd");
        String cutoffTime = validatedCutoff(normalizeBlank(dto.getCutoffTime()));
        String readyByTime = normalizeBlank(dto.getReadyByTime());
        Integer maxQuantity = dto.getMaxQuantity();
        if (maxQuantity != null && maxQuantity < 0) {
            throw new IllegalArgumentException("Quantity Available cannot be negative.");
        }
        // Server-side enforcement of the Create Offering timing rules so
        // impossible combinations are never persisted, even if the client
        // validation is bypassed.
        validateCreateTiming(dto.getAvailableDate(), orderWindowStart, orderWindowEnd,
                cutoffTime, readyByTime);
        Product product = new Product(kitchen, dto.getName(), dto.getDescription(), dto.getPrice(), dto.getImageUrl());
        product.setPriceUnit(dto.getPriceUnit());
        product.setAvailableToday(dto.getAvailableToday() != null ? dto.getAvailableToday() : true);
        product.setAvailableDate(dto.getAvailableDate());
        product.setOrderWindowStart(orderWindowStart);
        product.setOrderWindowEnd(orderWindowEnd);
        product.setMaxQuantity(maxQuantity);
        product.setRemainingQuantity(maxQuantity);
        product.setIsPreorder(dto.getIsPreorder() != null ? dto.getIsPreorder() : false);
        product.setCutoffTime(cutoffTime);
        product.setReadyByTime(readyByTime);
        product.setCategory(joinCategories(dto.getCategories()));
        return toProductDto(productRepository.save(product));
    }

    public ProductDto updateProduct(Long productId, ProductUpdateDto dto, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        boolean effectivePreorder = dto.getIsPreorder() != null
                ? dto.getIsPreorder() : Boolean.TRUE.equals(product.getIsPreorder());
        java.time.LocalDate effectiveDate = dto.getAvailableDate() != null
                ? dto.getAvailableDate() : product.getAvailableDate();
        assertFeatureCompliance(seller, effectivePreorder, effectiveDate, product.getName());

        boolean hasOrders = !orderItemRepository.findByProductId(productId).isEmpty();
        if (hasOrders) {
            if (dto.getName() != null && !dto.getName().isBlank() && !dto.getName().equals(product.getName()))
                throw new IllegalArgumentException("Cannot rename item after orders exist.");
            if (dto.getPrice() != null && !dto.getPrice().equals(product.getPrice()))
                throw new IllegalArgumentException("Cannot change price after orders exist.");
            if (dto.getPriceUnit() != null && !dto.getPriceUnit().equals(product.getPriceUnit()))
                throw new IllegalArgumentException("Cannot change unit after orders exist.");
            if (dto.getAvailableDate() != null && !dto.getAvailableDate().equals(product.getAvailableDate()))
                throw new IllegalArgumentException("Cannot change availability date after orders exist.");
            if (dto.getOrderWindowStart() != null && !dto.getOrderWindowStart().equals(product.getOrderWindowStart()))
                throw new IllegalArgumentException("Cannot change order window start after orders exist.");
            if (dto.getOrderWindowEnd() != null && !dto.getOrderWindowEnd().equals(product.getOrderWindowEnd()))
                throw new IllegalArgumentException("Cannot change order window end after orders exist.");
            if (dto.getReadyByTime() != null && !dto.getReadyByTime().equals(product.getReadyByTime()))
                throw new IllegalArgumentException("Cannot change ready-by time after orders exist.");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getPriceUnit() != null) product.setPriceUnit(dto.getPriceUnit());
        if (dto.getImageUrl() != null) product.setImageUrl(dto.getImageUrl());
        if (dto.getAvailableToday() != null) {
            product.setAvailableToday(dto.getAvailableToday());
            if (dto.getAvailableToday() && product.getRemainingQuantity() == null && product.getMaxQuantity() != null) {
                product.setRemainingQuantity(product.getMaxQuantity());
            }
        }
        if (dto.getRemainingQuantity() != null) product.setRemainingQuantity(dto.getRemainingQuantity());
        if (dto.getAvailableDate() != null) product.setAvailableDate(dto.getAvailableDate());
        if (dto.getOrderWindowStart() != null) product.setOrderWindowStart(dto.getOrderWindowStart());
        if (dto.getOrderWindowEnd() != null) product.setOrderWindowEnd(dto.getOrderWindowEnd());
        if (dto.getCutoffTime() != null) product.setCutoffTime(validatedCutoff(dto.getCutoffTime()));
        if (dto.getReadyByTime() != null && !dto.getReadyByTime().trim().isEmpty()) product.setReadyByTime(dto.getReadyByTime());
        if (dto.getMaxQuantity() != null) product.setMaxQuantity(dto.getMaxQuantity());
        if (dto.getIsPreorder() != null) product.setIsPreorder(dto.getIsPreorder());
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
    public List<OrderDto> getMyOrders(User seller) {
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

    /** Offering cutoffs are strict deadlines: only 24-hour HH:mm is accepted (e.g. "11:30"). */
    private String validatedCutoff(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (!v.matches("^([01]\\d|2[0-3]):[0-5]\\d$"))
            throw new IllegalArgumentException("cutoffTime must use 24-hour HH:mm format, e.g. 20:30");
        return v;
    }

    /** Trims a value; returns null when the field was left blank. */
    private String normalizeBlank(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    /** Optional HH:mm field: null when blank, 400 when malformed. */
    private String normalizedHhmm(String value, String fieldName) {
        String v = normalizeBlank(value);
        if (v == null) return null;
        if (!v.matches("^([01]\\d|2[0-3]):[0-5]\\d$"))
            throw new IllegalArgumentException(fieldName + " must use 24-hour HH:mm format, e.g. 08:30");
        return v;
    }

    private int hhmmToMinutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /**
     * Business timing rules for a newly created offering:
     *  - Orders Open (optional) must be earlier than Orders Close;
     *  - Orders Close must not be after the cutoff or the Delivery / Ready By time;
     *  - the Delivery / Ready By day (parsed from the existing free-text format,
     *    e.g. "1:00 PM today" / "2:00 PM tomorrow" / "4:00 PM Friday") must not
     *    be earlier than the offering's availability date.
     */
    private void validateCreateTiming(java.time.LocalDate availableDate, String orderWindowStart,
                                      String orderWindowEnd, String cutoffTime, String readyByTime) {
        if (orderWindowStart != null && orderWindowEnd != null
                && hhmmToMinutes(orderWindowStart) >= hhmmToMinutes(orderWindowEnd)) {
            throw new IllegalArgumentException("Orders Open must be earlier than Orders Close.");
        }
        if (orderWindowEnd != null && cutoffTime != null
                && hhmmToMinutes(orderWindowEnd) > hhmmToMinutes(cutoffTime)) {
            throw new IllegalArgumentException("Orders Close must not be after the Cutoff time.");
        }
        if (readyByTime == null) return; // nothing further to validate without a delivery time
        // Parse the delivery day/time from the existing readyByTime text format.
        java.time.LocalDate deliveryDate = null;
        java.time.LocalTime deliveryTime = null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)?\\s*(today|tomorrow|tmr)?\\s*$",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(readyByTime);
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            String ampm = m.group(3);
            if (ampm != null) {
                if (hour < 1 || hour > 12) {
                    throw new IllegalArgumentException("readyByTime must be a valid time, e.g. 1:00 PM today");
                }
                hour = hour % 12 + (ampm.equalsIgnoreCase("PM") ? 12 : 0);
            } else if (hour > 23) {
                throw new IllegalArgumentException("readyByTime must be a valid time, e.g. 1:00 PM today");
            }
            deliveryTime = java.time.LocalTime.of(hour, minute);
            String dayWord = m.group(4);
            if (dayWord == null || dayWord.equalsIgnoreCase("today")) {
                deliveryDate = java.time.LocalDate.now();
            } else {
                deliveryDate = java.time.LocalDate.now().plusDays(1); // tomorrow / tmr
            }
        } else {
            java.util.regex.Matcher wd = java.util.regex.Pattern
                    .compile("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(readyByTime);
            if (wd.find()) {
                java.time.DayOfWeek target = java.time.DayOfWeek.valueOf(wd.group(1).toUpperCase());
                java.time.LocalDate d = java.time.LocalDate.now();
                int delta = (target.getValue() - d.getDayOfWeek().getValue() + 7) % 7;
                if (delta == 0) delta = 7;
                deliveryDate = d.plusDays(delta);
            }
        }
        if (availableDate != null && deliveryDate != null && deliveryDate.isBefore(availableDate)) {
            throw new IllegalArgumentException("Delivery / Ready By date cannot be earlier than the offering date.");
        }
        if (orderWindowEnd != null && deliveryTime != null
                && hhmmToMinutes(orderWindowEnd) > deliveryTime.getHour() * 60 + deliveryTime.getMinute()) {
            throw new IllegalArgumentException("Orders Close must be earlier than the Delivery / Ready By time.");
        }
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
        dto.setOrderDeadline(kitchen.getOrderDeadline());
        dto.setSellerType(kitchen.getSellerType() != null ? kitchen.getSellerType().name() : null);
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
        return dto;
    }
}

package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.exception.*;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public static final String DRAFT_ORDER_SESSION_KEY = "DRAFT_ORDER_ID";
    private static final String BUYER_SESSION_KEY = "BUYER_USER";

    @Autowired
    public OrderService(OrderRepository orderRepository, KitchenRepository kitchenRepository,
                        ProductRepository productRepository, UserRepository userRepository,
                        AnalyticsService analyticsService) {
        this.orderRepository = orderRepository;
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    public OrderDto createOrUpdateDraftOrder(Long kitchenId, List<OrderItemRequest> items, HttpSession session) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        // One-kitchen-at-a-time: hidden / suspended / pending sellers' kitchens
        // cannot be ordered from at all.
        if (!KitchenVisibility.isPubliclyVisible(kitchen)) {
            throw new InvalidKitchenSelectionException("This kitchen is not currently accepting orders.");
        }
        Long draftId = (Long) session.getAttribute(DRAFT_ORDER_SESSION_KEY);
        User buyer = resolveBuyer(session);
        Order draft;
        boolean isNewDraft = false;
        if (draftId == null) {
            draft = new Order(buyer, kitchen);
            draft.setOrderStatus(OrderStatus.DRAFT);
            draft.setOrderNumber(generateOrderNumber());
            isNewDraft = true;
        } else {
            draft = orderRepository.findById(draftId).orElse(null);
            if (draft == null) {
                // Stale session pointer (e.g. previous attempt rolled back) — recover.
                session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
                draft = new Order(buyer, kitchen);
                draft.setOrderStatus(OrderStatus.DRAFT);
                draft.setOrderNumber(generateOrderNumber());
                isNewDraft = true;
            } else if (!draft.getKitchen().getId().equals(kitchenId)) {
                throw new InvalidKitchenSelectionException(
                        "You already have items from " + draft.getKitchen().getDisplayName() +
                                ". You can only order from one kitchen at a time.");
            }
        }
        draft.getItems().clear();
        if (items != null) {
            for (OrderItemRequest itemReq : items) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ProductNotFoundException(itemReq.getProductId()));
                int qty = itemReq.getQuantity() == null ? 0 : itemReq.getQuantity();
                if (qty <= 0) {
                    throw new IllegalArgumentException("Quantity must be at least 1.");
                }
                if (Boolean.FALSE.equals(product.getAvailableToday()) && !Boolean.TRUE.equals(product.getIsPreorder())) {
                    throw new IllegalArgumentException("'" + product.getName() + "' is not available today.");
                }
                if (product.getRemainingQuantity() != null && product.getRemainingQuantity() <= 0) {
                    throw new IllegalArgumentException("'" + product.getName() + "' is sold out.");
                }
                if (product.getRemainingQuantity() != null && qty > product.getRemainingQuantity()) {
                    throw new IllegalArgumentException("Only " + product.getRemainingQuantity() + " left of '" + product.getName() + "'. Please reduce quantity.");
                }
                if (product.getMaxQuantity() != null && qty > product.getMaxQuantity()) {
                    throw new IllegalArgumentException("At most " + product.getMaxQuantity() + " units of '" + product.getName() + "' per order.");
                }
                OrderItem orderItem = new OrderItem(product, qty, product.getPrice());
                applyScheduling(product, itemReq, orderItem);
                draft.addItem(orderItem);
            }
        }
        draft.recalculateTotal();
        Order saved = orderRepository.save(draft);
        // Only publish the session key once the draft validated & persisted (if any
        // validation throws above, the transaction rolls back and no stale key remains).
        if (isNewDraft) session.setAttribute(DRAFT_ORDER_SESSION_KEY, saved.getId());
        return toOrderDto(saved);
    }

    /**
     * Offering-level cutoff & scheduling enforcement (Spec 1.4 / Screen 4A).
     * Today items: must be placed before the offering cutoff time today.
     * Fixed pre-orders: scheduled date is the offering's fixed availability date.
     * Flexible pre-orders: buyer picks a date inside the offering window and a
     * slot from the offering's time slots; cutoff applies the day before pickup.
     */
    private void applyScheduling(Product product, OrderItemRequest req, OrderItem item) {
        boolean preorder = Boolean.TRUE.equals(product.getIsPreorder());
        if (!preorder) {
            enforceCutoff(product, LocalDate.now(), "today");
            return;
        }
        LocalDate earliest = product.getAvailableDate() != null ? product.getAvailableDate() : LocalDate.now().plusDays(1);
        LocalDate latest = product.getAvailableUntilDate() != null ? product.getAvailableUntilDate() : earliest;
        LocalDate scheduled;
        if (req.getScheduledDate() == null || req.getScheduledDate().isBlank()) {
            scheduled = earliest;
        } else {
            try {
                scheduled = LocalDate.parse(req.getScheduledDate());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid date for '" + product.getName() + "'.");
            }
        }
        if (scheduled.isBefore(earliest) || scheduled.isAfter(latest)) {
            throw new IllegalArgumentException("'" + product.getName() + "' can be scheduled between "
                    + earliest + " and " + latest + ".");
        }
        // The strict order deadline is the offering cutoff on the day before fulfilment.
        enforceCutoff(product, scheduled.minusDays(1), "for " + scheduled + " availability");
        if (product.getPreorderType() == PreorderType.FLEXIBLE) {
            List<String> slots = parseSlots(product.getTimeSlots());
            if (!slots.isEmpty()) {
                String slot = req.getScheduledSlot();
                if (slot == null || !slots.contains(slot)) {
                    throw new IllegalArgumentException("Choose a valid time slot for '" + product.getName() + "'.");
                }
                item.setScheduledSlot(slot);
            }
        } else {
            item.setScheduledSlot(null);
        }
        item.setScheduledDate(scheduled);
    }

    /** Cutoffs belong exclusively to offerings — this is where they are enforced. */
    private void enforceCutoff(Product product, LocalDate cutoffDate, String context) {
        String cutoff = product.getCutoffTime();
        if (cutoff == null || cutoff.isBlank()) return;
        LocalTime cutoffTime;
        try {
            cutoffTime = LocalTime.parse(cutoff);
        } catch (Exception e) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (cutoffDate.isBefore(today) || (cutoffDate.equals(today) && LocalTime.now().isAfter(cutoffTime))) {
            throw new IllegalArgumentException("The order cutoff (" + cutoff + ") for '"
                    + product.getName() + "' has passed — orders " + context + " are closed.");
        }
    }

    private List<String> parseSlots(String timeSlots) {
        if (timeSlots == null || timeSlots.isBlank()) return List.of();
        return java.util.Arrays.stream(timeSlots.split(","))
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDto getCurrentDraftOrder(HttpSession session) {
        Long draftId = (Long) session.getAttribute(DRAFT_ORDER_SESSION_KEY);
        if (draftId == null) return null;
        Order draft = orderRepository.findById(draftId).orElse(null);
        if (draft == null) return null;
        return toOrderDto(draft);
    }

    public void clearDraftOrder(HttpSession session) {
        Long draftId = (Long) session.getAttribute(DRAFT_ORDER_SESSION_KEY);
        if (draftId != null) {
            orderRepository.deleteById(draftId);
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
        }
    }

    public OrderDto placeOrder(PaymentStatus paymentStatus, PlaceOrderRequest.BuyerDetails buyerDetails,
                               String customInstructions, HttpSession session) {
        Long draftId = (Long) session.getAttribute(DRAFT_ORDER_SESSION_KEY);
        if (draftId == null) {
            throw new IllegalArgumentException("Your order session has expired. Please add items again.");
        }
        Order order = orderRepository.findById(draftId).orElse(null);
        if (order == null) {
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
            throw new IllegalArgumentException("Your order session has expired. Please add items again.");
        }
        // One-kitchen rule: if the kitchen became unavailable after this draft was
        // created (seller suspended / rejected / hidden), the draft can no longer be
        // placed. Clear it so the buyer starts a fresh selection.
        if (order.getKitchen() == null || !KitchenVisibility.isPubliclyVisible(order.getKitchen())) {
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
            orderRepository.delete(order);
            throw new InvalidKitchenSelectionException(
                    "This kitchen is no longer accepting orders. Your selection was cleared.");
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Your order is empty. Please add items before placing it.");
        }
        User buyer = resolveBuyer(session);
        if (buyer == null) throw new BuyerNotAuthenticatedException("Authentication required to place an order.");
        order.setBuyer(buyer);
        if (buyerDetails != null) updateBuyerDetails(buyer, buyerDetails);
        if (customInstructions != null && !customInstructions.isBlank()) order.setCustomInstructions(customInstructions);
        consumeStock(order);
        // Claim-based UPI flow (Screen 6):
        //  [I HAVE PAID]   → paymentStatus PAID, order CONFIRMED ("Payment pending verification")
        //  [I'LL PAY LATER] → paymentStatus PENDING, order stays ORDERED ("Pending")
        boolean claimedPaid = paymentStatus == PaymentStatus.PAID;
        order.setPaymentStatus(claimedPaid ? PaymentStatus.PAID : PaymentStatus.PENDING);
        order.setOrderStatus(claimedPaid ? OrderStatus.CONFIRMED : OrderStatus.ORDERED);
        order.recalculateTotal();
        orderRepository.save(order);
        session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
        analyticsService.record(AnalyticsService.EV_ORDER_PLACED, buyer.getId(),
                buyer.getMobileNumber(), order.getKitchen() != null ? order.getKitchen().getId() : null,
                order.getOrderNumber());
        return toOrderDto(order);
    }

    @Transactional(readOnly = true)
    public Map<String, List<OrderDto>> getMyOrders(User buyer) {
        List<OrderDto> allOrders = orderRepository.findByBuyerOrderByCreatedAtDesc(buyer).stream()
                .map(this::toOrderDto).collect(Collectors.toList());
        List<OrderDto> active = allOrders.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DELIVERED
                        && o.getOrderStatus() != OrderStatus.CANCELLED
                        && o.getOrderStatus() != OrderStatus.DRAFT)
                .collect(Collectors.toList());
        List<OrderDto> completed = allOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.DELIVERED || o.getOrderStatus() == OrderStatus.CANCELLED)
                .collect(Collectors.toList());
        Map<String, List<OrderDto>> result = new HashMap<>();
        result.put("active", active);
        result.put("completed", completed);
        return result;
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderDetails(Long orderId, User buyer) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) throw new OrderNotFoundException(orderId);
        return toOrderDto(order);
    }

    public OrderDto rateOrder(Long orderId, Integer rating, User buyer) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) throw new OrderNotFoundException(orderId);
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalArgumentException("You can rate this order only after completing the payment.");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("A cancelled order cannot be rated.");
        }
        if (order.getRating() != null) {
            throw new IllegalArgumentException("You have already rated this order.");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        order.setRating(rating);
        order.setOrderStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        Product product = order.getItems().isEmpty() ? null : order.getItems().get(0).getProduct();
        if (product != null) {
            double oldRating = product.getRating() == null ? 0 : product.getRating();
            double newRating = oldRating == 0 ? rating : (oldRating + rating) / 2.0;
            product.setRating(Math.round(newRating * 10.0) / 10.0);
            productRepository.save(product);
        }
        return toOrderDto(order);
    }

    public OrderDto reorder(Long orderId, HttpSession session, User buyer) {
        Order originalOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Kitchen kitchen = originalOrder.getKitchen();
        // One-kitchen rule: do not rebuild a draft from a kitchen that is no longer
        // publicly active (seller suspended / rejected / hidden).
        if (kitchen == null || !KitchenVisibility.isPubliclyVisible(kitchen)) {
            throw new InvalidKitchenSelectionException("This kitchen is no longer accepting orders.");
        }
        Order newDraft = new Order(buyer, kitchen);
        newDraft.setOrderStatus(OrderStatus.DRAFT);
        newDraft.setOrderNumber(generateOrderNumber());
        orderRepository.save(newDraft);
        for (OrderItem item : originalOrder.getItems()) {
            OrderItem newItem = new OrderItem(item.getProduct(), item.getQuantity(), item.getProduct().getPrice());
            newDraft.addItem(newItem);
        }
        newDraft.recalculateTotal();
        orderRepository.save(newDraft);
        session.setAttribute(DRAFT_ORDER_SESSION_KEY, newDraft.getId());
        return toOrderDto(newDraft);
    }

    public OrderDto updatePaymentStatus(Long orderId, PaymentStatus paymentStatus, User buyer) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) throw new OrderNotFoundException(orderId);
        order.setPaymentStatus(paymentStatus);
        if (paymentStatus == PaymentStatus.PAID && order.getOrderStatus() == OrderStatus.ORDERED) {
            order.setOrderStatus(OrderStatus.CONFIRMED);
        }
        orderRepository.save(order);
        return toOrderDto(order);
    }
    // Trigger redeployment

    @Transactional(readOnly = true)
    public List<OrderDto> getSellerOrders(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (kitchens.isEmpty()) return List.of();
        return kitchens.stream()
                .flatMap(k -> orderRepository.findByKitchenOrderByCreatedAtDesc(k).stream())
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT)
                .map(this::toOrderDto)
                .collect(Collectors.toList());
    }

    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus, User seller) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getKitchen() == null || !order.getKitchen().getSeller().getId().equals(seller.getId())) {
            throw new SellerNotAuthorizedException("Not authorized");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("This order is already cancelled and cannot be updated.");
        }
        if (order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("This order is already delivered and cannot be updated.");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("Order status is required.");
        }
        if (newStatus == OrderStatus.DRAFT) {
            throw new IllegalArgumentException("An order cannot be reverted to a draft.");
        }
        if (newStatus == order.getOrderStatus()) {
            throw new IllegalArgumentException("Order is already " + newStatus + ".");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        order.setOrderStatus(newStatus);
        orderRepository.save(order);
        return toOrderDto(order);
    }

        private User resolveBuyer(HttpSession session) {
        // Check session attribute first (reliable for REST), then SecurityContext
        if (session != null) {
            Object attr = session.getAttribute(BUYER_SESSION_KEY);
            if (attr instanceof Long userId) {
                return userRepository.findById(userId).orElse(null);
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long userId) {
            return userRepository.findById(userId).orElse(null);
        }
        return null;
    }

    private OrderDto toOrderDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setCustomInstructions(order.getCustomInstructions());
        Kitchen kitchen = order.getKitchen();
        if (kitchen != null) {
            dto.setKitchen(new OrderDto.KitchenSummary(
                    kitchen.getId(), kitchen.getName(), kitchen.getDisplayName(),
                    kitchen.getImageUrl(), kitchen.getRating()));
        }
        if (order.getBuyer() != null) {
            OrderDto.BuyerSummary buyerSummary = new OrderDto.BuyerSummary();
            buyerSummary.setName(order.getBuyer().getName());
            buyerSummary.setMobileNumber(order.getBuyer().getMobileNumber());
            buyerSummary.setFlatHouseNumber(order.getBuyer().getFlatHouseNumber());
            dto.setBuyer(buyerSummary);
        }
        if (order.getItems() != null) {
            dto.setItems(order.getItems().stream()
                    .map(item -> {
                        OrderItemDto itemDto = new OrderItemDto(item.getId(), item.getProduct().getId(),
                                item.getProduct().getName(), item.getProduct().getImageUrl(),
                                item.getQuantity(), item.getPrice());
                        itemDto.setScheduledDate(item.getScheduledDate());
                        itemDto.setScheduledSlot(item.getScheduledSlot());
                        return itemDto;
                    })
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private String generateOrderNumber() {
        return "SM" + (System.nanoTime() % 10000000000L);
    }

    private void consumeStock(Order order) {
        Map<Long, Integer> totals = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            Long productId = item.getProduct().getId();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            Integer existing = totals.get(productId);
            totals.put(productId, (existing == null ? 0 : existing) + quantity);
        }
        for (Map.Entry<Long, Integer> e : totals.entrySet()) {
            Product product = productRepository.findById(e.getKey()).orElse(null);
            if (product == null) continue;
            if (product.getRemainingQuantity() != null) {
                if (product.getRemainingQuantity() < e.getValue()) {
                    throw new IllegalArgumentException("Only " + product.getRemainingQuantity() + " left of '" +
                            product.getName() + "'. Please reduce quantity and try again.");
                }
                int updated = productRepository.consumeStock(e.getKey(), e.getValue());
                if (updated == 0) {
                    throw new IllegalArgumentException("Not enough stock left for '" + product.getName() + "'. Please reduce quantity.");
                }
            } else {
                product.setBookedQuantity((product.getBookedQuantity() == null ? 0 : product.getBookedQuantity()) + e.getValue());
                productRepository.save(product);
            }
        }
    }

    private void restoreStock(Order order) {
        Map<Long, Integer> totals = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            Long productId = item.getProduct().getId();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            Integer existing = totals.get(productId);
            totals.put(productId, (existing == null ? 0 : existing) + quantity);
        }
        for (Map.Entry<Long, Integer> e : totals.entrySet()) {
            Product product = productRepository.findById(e.getKey()).orElse(null);
            if (product == null || product.getRemainingQuantity() == null) continue;
            Integer max = product.getMaxQuantity();
            int restored = product.getRemainingQuantity() + e.getValue();
            product.setRemainingQuantity(max != null ? Math.min(max, restored) : restored);
            // Reversed action of consumeStock: freeing a cancelled order must also
            // release the booked units so the demand bar returns to its true level.
            int booked = (product.getBookedQuantity() == null ? 0 : product.getBookedQuantity()) - e.getValue();
            product.setBookedQuantity(Math.max(0, booked));
            productRepository.save(product);
        }
    }

    private void updateBuyerDetails(User buyer, PlaceOrderRequest.BuyerDetails buyerDetails) {
        if (buyerDetails.getName() != null && !buyerDetails.getName().isBlank()) {
            buyer.setName(buyerDetails.getName());
        }
        if (buyerDetails.getFlatHouseNumber() != null && !buyerDetails.getFlatHouseNumber().isBlank()) {
            buyer.setFlatHouseNumber(buyerDetails.getFlatHouseNumber());
        }
        if (buyerDetails.getSociety() != null && !buyerDetails.getSociety().isBlank()) {
            buyer.setSociety(buyerDetails.getSociety());
        }
        if (buyerDetails.getBuilding() != null && !buyerDetails.getBuilding().isBlank()) {
            buyer.setBuilding(buyerDetails.getBuilding());
        }
        userRepository.save(buyer);
    }
}


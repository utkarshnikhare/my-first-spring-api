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
import java.time.LocalDateTime;
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
    private final NotificationService notificationService;
    private final RetentionService retentionService;
    /**
     * Recurring engine (V2): occurrence-aware ordering. Injected as a field so
     * the manually-constructed OrderService used by one unit test keeps
     * working (it only exercises one-time offerings, where this stays null).
     */
    @Autowired
    private RecurringScheduleService recurringScheduleService;

    public static final String DRAFT_ORDER_SESSION_KEY = "DRAFT_ORDER_ID";
    private static final String BUYER_SESSION_KEY = "BUYER_USER";
    /** Bounded retries when an order-number candidate is already taken. */
    private static final int MAX_ORDER_NUMBER_ATTEMPTS = 5;

    @Autowired
    public OrderService(OrderRepository orderRepository, KitchenRepository kitchenRepository,
                        ProductRepository productRepository, UserRepository userRepository,
                        AnalyticsService analyticsService, NotificationService notificationService,
                        RetentionService retentionService) {
        this.orderRepository = orderRepository;
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
        this.notificationService = notificationService;
        this.retentionService = retentionService;
    }

    public OrderDto createOrUpdateDraftOrder(Long kitchenId, List<OrderItemRequest> items, HttpSession session) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        User buyer = resolveBuyer(session);
        if (buyer == null) throw new BuyerNotAuthenticatedException("Authentication required to create an order.");
        // Only a BUYER may start an order. The Buyer and Seller apps share one
        // browser session, so a Seller login elsewhere (e.g. the Seller app's
        // demo-login) can leave a SELLER identity in this session. Without this
        // check that identity would silently become the order's buyer.
        if (buyer.getRole() != UserRole.BUYER) {
            throw new BuyerNotAuthenticatedException("Only buyers can place an order. Please log in as a buyer.");
        }
        // One-kitchen-at-a-time: hidden / suspended / pending sellers' kitchens
        // cannot be ordered from at all, and service-area rules apply server-side.
        if (!KitchenVisibility.isPubliclyVisible(kitchen) || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            throw new InvalidKitchenSelectionException("This kitchen is not currently accepting orders in your area.");
        }
        Long draftId = (Long) session.getAttribute(DRAFT_ORDER_SESSION_KEY);
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
            } else if (draft.getBuyer() == null || !draft.getBuyer().getId().equals(buyer.getId())) {
                // Never let a stale or manipulated session pointer update another buyer's draft.
                throw new InvalidKitchenSelectionException("Your current order session is no longer valid.");
            } else if (!draft.getKitchen().getId().equals(kitchenId)) {
                // Kitchen switched — clear stale draft and start fresh.
                // The frontend confirmation modal already ensures this is intentional.
                orderRepository.delete(draft);
                session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
                draft = new Order(buyer, kitchen);
                draft.setOrderStatus(OrderStatus.DRAFT);
                draft.setOrderNumber(generateOrderNumber());
                isNewDraft = true;
            }
        }
        draft.getItems().clear();
        if (items != null) {
            for (OrderItemRequest itemReq : items) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ProductNotFoundException(itemReq.getProductId()));
                if (product.getKitchen() == null || !product.getKitchen().getId().equals(kitchen.getId())) {
                    throw new IllegalArgumentException("The selected offering does not belong to this kitchen.");
                }
                int qty = itemReq.getQuantity() == null ? 0 : itemReq.getQuantity();
                if (qty <= 0) {
                    throw new IllegalArgumentException("Quantity must be at least 1.");
                }
                if (Boolean.TRUE.equals(product.getOrdersPaused())) {
                    throw new IllegalArgumentException("Orders are paused for '" + product.getName() + "'.");
                }
                boolean itemPreorder = Boolean.TRUE.equals(product.getIsPreorder());
                // Recurring offerings are governed by their occurrence rows:
                // today's selling date when one exists, otherwise the next
                // scheduled date (a future-occurrence pre-order, V2 §14). This
                // also releases the one-time "available today" gate, which is
                // date-based and does not describe a repeating schedule.
                OccurrenceDto recurringOcc = recurringSchedulesTarget(product, itemReq);
                boolean recurring = recurringOcc != null;
                if (recurringScheduleService != null && recurringOcc == null
                        && recurringScheduleService.hasSchedule(product.getId())) {
                    // A schedule exists but no live selling date (ended or
                    // exhausted): V2 §12 - nothing further may be ordered.
                    throw new IllegalArgumentException("'" + product.getName() + "' is not currently open for orders.");
                }
                if (Boolean.FALSE.equals(product.getAvailableToday()) && !itemPreorder && !recurring) {
                    throw new IllegalArgumentException("'" + product.getName() + "' is not available today.");
                }
                OrderItem orderItem = new OrderItem(product, qty, product.getPrice());
                if (recurring) {
                    validateRecurringOrder(product, recurringOcc, qty);
                    // The fulfilment date IS the occurrence date, so each
                    // selling date keeps its own order bucket (V2 §4/§13).
                    orderItem.setScheduledDate(recurringOcc.getDate());
                    orderItem.setOccurrence(recurringScheduleService.getOccurrence(recurringOcc.getId()));
                } else {
                    if (product.getRemainingQuantity() != null && product.getRemainingQuantity() <= 0) {
                        throw new IllegalArgumentException("'" + product.getName() + "' is sold out.");
                    }
                    if (product.getRemainingQuantity() != null && qty > product.getRemainingQuantity()) {
                        throw new IllegalArgumentException("Only " + product.getRemainingQuantity() + " left of '" + product.getName() + "'. Please reduce quantity.");
                    }
                    if (product.getMaxQuantity() != null && qty > product.getMaxQuantity()) {
                        throw new IllegalArgumentException("At most " + product.getMaxQuantity() + " units of '" + product.getName() + "' per order.");
                    }
                    applyScheduling(product, itemReq, orderItem);
                }
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

    /** Enforces Offering For date plus Orders Open/Close on both draft and placement. */
    private void enforceCutoff(Product product, LocalDate cutoffDate, String context) {
        OfferingTiming.enforceOrderWindow(product, cutoffDate, context);
    }

    // ==================== RECURRING OCCURRENCE ORDERING (V2) ====================

    /** Target occurrence for an ordering step; null for one-time offerings. */
    private OccurrenceDto recurringSchedulesTarget(Product product, OrderItemRequest request) {
        if (recurringScheduleService == null) return null;
        if (request != null && request.getScheduledDate() != null && !request.getScheduledDate().isBlank()
                && recurringScheduleService.hasSchedule(product.getId())) {
            LocalDate requestedDate;
            try {
                requestedDate = LocalDate.parse(request.getScheduledDate().trim());
            } catch (RuntimeException invalidDate) {
                throw new IllegalArgumentException("Invalid recurring occurrence date for '" + product.getName() + "'.");
            }
            OccurrenceDto selected = recurringScheduleService.resolveOccurrenceForDate(product, requestedDate);
            if (selected == null) {
                throw new IllegalArgumentException("'" + product.getName()
                        + "' is not open for orders on " + requestedDate + ".");
            }
            return selected;
        }
        return recurringScheduleService.resolveTargetOccurrence(product);
    }

    /** Strict HH:mm parse for window text; null when absent or unparseable. */
    private LocalTime parseHhmmOrNull(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (RuntimeException unparseable) {
            return null; // legacy free-text values cannot gate an order
        }
    }

    /**
     * Occurrence-scoped rules for ONE recurring order line (V2 §5/§8/§10):
     * the per-day sold-out / closed flags, the occurrence's own Orders Close
     * override on its selling date, the per-order quantity cap and the
     * per-date bucket total ("14 plates" means 14 plates for that date).
     */
    private void validateRecurringOrder(Product product, OccurrenceDto occ, int qty) {
        LocalDate today = LocalDate.now();
        if (occ.getDate() == null || occ.getDate().isBefore(today)) {
            throw new IllegalArgumentException("'" + product.getName() + "' is no longer scheduled for " + occ.getDate() + ".");
        }
        if (Boolean.TRUE.equals(occ.getSoldOut())) {
            throw new IllegalArgumentException("'" + product.getName() + "' is sold out for " + occ.getDate() + ".");
        }
        if (Boolean.TRUE.equals(occ.getOrdersPaused())) {
            throw new IllegalArgumentException("Orders are closed for '" + product.getName() + "' on " + occ.getDate() + ".");
        }
        // The product's opening time gates both same-day orders and advance
        // recurring pre-orders. The occurrence close time is enforced only on
        // its fulfilment date; future dates remain open until then.
        LocalTime openAt = parseHhmmOrNull(product.getOrderWindowStart());
        if (openAt != null && LocalTime.now().isBefore(openAt)) {
            throw new IllegalArgumentException("Orders for '" + product.getName() + "' have not opened yet.");
        }
        if (occ.getDate().equals(today)) {
            LocalTime closeAt = parseHhmmOrNull(occ.getOrderCloseTime());
            if (closeAt != null && LocalTime.now().isAfter(closeAt)) {
                throw new IllegalArgumentException("Orders for '" + product.getName() + "' are closed for " + occ.getDate() + ".");
            }
        }
        Integer dayQty = occ.getQuantity();
        if (dayQty != null) {
            if (qty > dayQty) {
                throw new IllegalArgumentException("At most " + dayQty + " of '" + product.getName()
                        + "' per order for " + occ.getDate() + ".");
            }
            int booked = bookedPlatesOn(product, occ.getId());
            if (booked + qty > dayQty) {
                throw new IllegalArgumentException("Only " + Math.max(0, dayQty - booked) + " of '"
                        + product.getName() + "' left for " + occ.getDate() + ".");
            }
        }
    }

    /**
     * Plates of this product already committed to one fulfilment date. Orders
     * carrying a scheduledDate bucket by that date; older rows without one
     * bucket by their creation date (the day they were ordered). Drafts and
     * cancelled orders hold no inventory, so they never count. Deliberately
     * simple - the same kitchen-scoped scan the dashboard summary performs.
     */
    private int bookedPlatesOn(Product product, Long occurrenceId) {
        if (product.getKitchen() == null || product.getId() == null || occurrenceId == null) return 0;
        int total = 0;
        for (Order order : orderRepository.findByKitchenOrderByCreatedAtDesc(product.getKitchen())) {
            if (order.getOrderStatus() == OrderStatus.DRAFT || order.getOrderStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null || !product.getId().equals(item.getProduct().getId())) continue;
                if (item.getOccurrence() != null && occurrenceId.equals(item.getOccurrence().getId())) {
                    total += item.getQuantity() == null ? 0 : item.getQuantity();
                }
            }
        }
        return total;
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
        User buyer = resolveBuyer(session);
        if (buyer == null || draft.getBuyer() == null || !draft.getBuyer().getId().equals(buyer.getId())) {
            throw new OrderNotFoundException(draftId);
        }
        return toOrderDto(draft);
    }

    public void clearDraftOrder(HttpSession session) {
        Long draftId = (Long) session.getAttribute(DRAFT_ORDER_SESSION_KEY);
        if (draftId != null) {
            Order draft = orderRepository.findById(draftId).orElse(null);
            User buyer = resolveBuyer(session);
            if (draft != null && buyer != null
                    && (draft.getBuyer() == null || !draft.getBuyer().getId().equals(buyer.getId()))) {
                throw new OrderNotFoundException(draftId);
            }
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
        Order order = orderRepository.findByIdForUpdate(draftId).orElse(null);
        if (order == null) {
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
            throw new IllegalArgumentException("Your order session has expired. Please add items again.");
        }
        if (order.getOrderStatus() != OrderStatus.DRAFT) {
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
            throw new IllegalArgumentException("This order has already been placed.");
        }
        User buyer = resolveBuyer(session);
        if (buyer == null) throw new BuyerNotAuthenticatedException("Authentication required to place an order.");
        if (buyer.getRole() != UserRole.BUYER) {
            // A non-buyer identity (the Seller app's demo-login, or an admin)
            // replaced this shared session. The buyer's draft pointer is now
            // unreachable, so drop it and report an ordinary 401 - the status
            // the frontend already understands as "sign in again" - instead of
            // letting the check fall through to the cross-buyer 404 below,
            // which has no recovery path and dead-ended the checkout.
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
            throw new BuyerNotAuthenticatedException("Only buyers can place an order. Please log in as a buyer.");
        }
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            // Another BUYER's draft. Keep the 404: it refuses the request AND
            // avoids confirming that the order exists at all. This is the
            // deliberate cross-buyer protection and must not be softened.
            throw new OrderNotFoundException(draftId);
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
        order.setBuyer(buyer);
        if (buyerDetails != null) updateBuyerDetails(buyer, buyerDetails);
        if (customInstructions != null && !customInstructions.isBlank()) order.setCustomInstructions(customInstructions);
        // First-order verification: ensure buyer profile is complete before placing an order.
        // Required fields: society, building, flat/house number. Mobile is set at login.
        if (buyer.getSociety() == null || buyer.getSociety().isBlank()
                || buyer.getBuilding() == null || buyer.getBuilding().isBlank()
                || buyer.getFlatHouseNumber() == null || buyer.getFlatHouseNumber().isBlank()) {
            throw new BuyerProfileIncompleteException(
                    "Please complete your profile (society, building, wing, flat/house number) before placing an order.");
        }
        if (!KitchenVisibility.isServiceAreaVisible(order.getKitchen(), buyer)) {
            session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
            orderRepository.delete(order);
            throw new IllegalArgumentException("This kitchen does not serve your selected area. Please choose another kitchen.");
        }
        validateOrderItemsForPlacement(order);
        consumeStock(order);
        // Notify seller if any tracked offering newly sold out as a result of this order.
        notifyNewlySoldOut(order);
        boolean isHomemade = KitchenVisibility.isHomemadeStore(order.getKitchen());
        PaymentStatus effectivePaymentStatus = paymentStatus == null || paymentStatus == PaymentStatus.WILL_PAY_LATER
                ? PaymentStatus.PENDING : paymentStatus;
        if (effectivePaymentStatus == PaymentStatus.PAID && !isHomemade) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setOrderStatus(OrderStatus.CONFIRMED);
        } else {
            order.setPaymentStatus(effectivePaymentStatus);
            order.setOrderStatus(OrderStatus.ORDERED);
        }
        order.recalculateTotal();
        // Server-side authoritative order timestamp: set exactly once when the draft
        // transitions to a finalized order state. This represents the real order creation
        // time — NOT the draft creation time (createdAt). Never overwritten on updates.
        if (order.getOrderTime() == null) {
            order.setOrderTime(LocalDateTime.now());
        }
        orderRepository.save(order);
        session.removeAttribute(DRAFT_ORDER_SESSION_KEY);
        analyticsService.record(AnalyticsService.EV_ORDER_PLACED, buyer.getId(),
                buyer.getMobileNumber(), order.getKitchen() != null ? order.getKitchen().getId() : null,
                orderPlacedDetail(order, buyer));
        // Handover 12: roll the commercial fact into the durable daily aggregate
        // at the moment the order is placed - the only point where it is
        // counted exactly once. Delivery and cancellation deliberately do NOT
        // call this: the upsert is additive, so a second call would double-count.
        retentionService.recordOrderFact(order);
        String productSummary = order.getItems() == null ? "items" : order.getItems().stream()
                .map(item -> item.getProduct() != null ? item.getProduct().getName() : "item")
                .collect(Collectors.joining(", "));
        if (order.getKitchen() != null && order.getKitchen().getSeller() != null) {
            notificationService.sendNewOrderNotification(order.getKitchen().getSeller(), order.getOrderNumber(), productSummary);
        }
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
        Order order = orderRepository.findByIdWithItems(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
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
        if (originalOrder.getBuyer() == null || !originalOrder.getBuyer().getId().equals(buyer.getId())) {
            throw new OrderNotFoundException(orderId);
        }
        Kitchen kitchen = originalOrder.getKitchen();
        // One-kitchen rule: do not rebuild a draft from a kitchen that is no longer
        // publicly active or does not serve this buyer's selected area.
        if (kitchen == null || !KitchenVisibility.isPubliclyVisible(kitchen)
                || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            throw new InvalidKitchenSelectionException("This kitchen is no longer accepting orders in your area.");
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
        if (paymentStatus == null) throw new IllegalArgumentException("Payment status is required.");
        // Keep the existing buyer compatibility endpoint, but normalize the
        // deferred-payment choice to the authoritative PENDING business state.
        PaymentStatus requestedStatus = paymentStatus == PaymentStatus.WILL_PAY_LATER
                ? PaymentStatus.PENDING : paymentStatus;
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) throw new OrderNotFoundException(orderId);
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("A cancelled order cannot change payment status.");
        }
        // Buyers cannot mark an order paid; the seller-only endpoint owns that transition.
        if (requestedStatus == PaymentStatus.PAID && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalArgumentException("Payment status is managed by the seller.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            if (requestedStatus == PaymentStatus.PAID) return toOrderDto(order);
            throw new IllegalArgumentException("A paid order cannot be changed by the buyer.");
        }
        if (requestedStatus == order.getPaymentStatus()) return toOrderDto(order);
        order.setPaymentStatus(requestedStatus);
        orderRepository.save(order);
        return toOrderDto(order);
    }

    public OrderDto cancelOrder(Long orderId, User buyer) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return toOrderDto(order); // idempotent retry; inventory is never restored twice
        }
        if (order.getOrderStatus() == OrderStatus.DRAFT
                || order.getOrderStatus() == OrderStatus.DELIVERED
                || order.getOrderStatus() == OrderStatus.COMPLETED) {
            throw new IllegalArgumentException("This order cannot be cancelled at its current stage.");
        }
        order = cancelOrderLocked(order);
        return toOrderDto(order);
    }

    private Order cancelOrderLocked(Order order) {
        Long orderId = order.getId();
        restoreStock(order);
        // Bulk inventory updates clear the persistence context; reload the order
        // and its items before accessing lazy relationships or building the DTO.
        order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getKitchen() != null && order.getKitchen().getSeller() != null) {
            notificationService.sendOrderCancellationNotification(
                    order.getKitchen().getSeller(), order.getOrderNumber());
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        // Handover 18: capture "order cancelled" while the event occurs, so the
        // aggregate outlives the detailed-order retention purge. Both the buyer's
        // cancel and the seller's cancel route through this one method.
        analyticsService.record(AnalyticsService.EV_ORDER_CANCELLED,
                order.getBuyer() != null ? order.getBuyer().getId() : null,
                order.getBuyer() != null ? order.getBuyer().getMobileNumber() : null,
                order.getKitchen() != null ? order.getKitchen().getId() : null,
                order.getOrderNumber());
        return order;
    }

    public OrderDto markOrderAsPaid(Long orderId, User seller) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getKitchen() == null || order.getKitchen().getSeller() == null
                || !order.getKitchen().getSeller().getId().equals(seller.getId())) {
            throw new SellerNotAuthorizedException("Not authorized");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("This order is already cancelled.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) return toOrderDto(order);
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);
        // Handover 18: payment status is an event worth keeping. Recorded after the
        // PAID short-circuit above, so it fires on the real transition only.
        analyticsService.record(AnalyticsService.EV_PAYMENT_STATUS,
                order.getBuyer() != null ? order.getBuyer().getId() : null,
                order.getBuyer() != null ? order.getBuyer().getMobileNumber() : null,
                order.getKitchen() != null ? order.getKitchen().getId() : null,
                order.getOrderNumber() + " -> PAID");
        if (order.getBuyer() != null) notificationService.sendPaymentReceivedNotification(order.getBuyer(), order.getOrderNumber());
        // Requirement 19: the owning seller is notified on the real transition only.
        // The PAID short-circuit above guarantees repeated Mark as Paid never duplicates.
        if (order.getKitchen() != null && order.getKitchen().getSeller() != null) {
            notificationService.sendPaymentUpdatedNotification(
                    order.getKitchen().getSeller(), order.getOrderNumber());
        }
        return toOrderDto(order);
    }

    public OrderDto acknowledgeOrder(Long orderId, User seller) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getKitchen() == null || !order.getKitchen().getSeller().getId().equals(seller.getId())) {
            throw new SellerNotAuthorizedException("Not authorized");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("This order is already cancelled.");
        }
        if (order.getAcknowledgedAt() != null) {
            throw new IllegalArgumentException("This order has already been acknowledged.");
        }
        order.setAcknowledgedAt(java.time.LocalDateTime.now());
        order.setAcknowledgedBy(seller);
        if (order.getOrderStatus() == OrderStatus.ORDERED) {
            order.setOrderStatus(OrderStatus.CONFIRMED);
        }
        orderRepository.save(order);
        return toOrderDto(order);
    }

    // ==================== DELIVERY COMPLETION (V1) ====================

    /**
     * Records the seller's delivery flag for ONE order.
     *
     * <p>Authorization is derived from the AUTHENTICATED seller, never from
     * anything in the request: the order's own kitchen must belong to
     * {@code seller}. That is what stops a tampered orderId from reaching
     * another seller's order.</p>
     *
     * <p>Payment status, order status, quantities and totals are never touched,
     * so marking an unpaid order Delivered (and vice versa) stays legal.</p>
     *
     * <p>Idempotent: repeating the same request returns the unchanged order and
     * leaves {@code deliveredAt} exactly as it was.</p>
     */
    public OrderDto updateDeliveryStatus(Long orderId, DeliveryStatus target, User seller) {
        if (target == null) throw new IllegalArgumentException("Delivery status is required.");
        if (seller == null) throw new SellerNotAuthorizedException("Not authorized");
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!isOwnedBySeller(order.getKitchen(), seller)) throw new SellerNotAuthorizedException("Not authorized");
        if (!order.isActiveForDelivery()) {
            throw new IllegalArgumentException(
                    order.getOrderStatus() == OrderStatus.CANCELLED
                            ? "A cancelled order cannot be marked as delivered."
                            : "This order is not an active customer order yet.");
        }
        // Backend clock only: the browser's clock is never authoritative.
        boolean changed = order.applyDeliveryStatus(target, seller, LocalDateTime.now());
        orderRepository.save(order);
        if (changed && target == DeliveryStatus.DELIVERED) {
            // Handover 18: capture "order delivered" on the real transition only,
            // so a repeated Mark Delivered never duplicates the analytics record.
            analyticsService.record(AnalyticsService.EV_ORDER_DELIVERED,
                    order.getBuyer() != null ? order.getBuyer().getId() : null,
                    order.getBuyer() != null ? order.getBuyer().getMobileNumber() : null,
                    order.getKitchen() != null ? order.getKitchen().getId() : null,
                    order.getOrderNumber());
        }
        return toOrderDto(order);
    }

    /**
     * Bulk "Mark All Delivered" for every active order of ONE offering on ONE
     * date.
     *
     * <p>Runs in the caller's transaction (the class is {@code @Transactional}),
     * so the whole batch commits or rolls back together and a partial success is
     * never reported as a success.</p>
     *
     * <p>Scope is the offering + date, NOT the seller's current UI filters - the
     * UI says so explicitly, so a filtered view can never be mistaken for the
     * action's scope. Cancelled and draft orders are skipped, and orders already
     * DELIVERED are left untouched so their original {@code delivered_at} is not
     * rewritten and no duplicate record is created. Orders of other offerings
     * and of other kitchens are never loaded.</p>
     */
    public DeliveryProgressDto markAllOfferingOrdersDelivered(Long productId, LocalDate date, User seller) {
        // The kitchen is derived from the OFFERING the seller opened, never from
        // "the seller's first kitchen". A seller may own several storefronts
        // (e.g. a Kitchen and a Homemade one); resolving to kitchens.get(0)
        // silently scoped progress and the bulk write to the wrong storefront,
        // so the second storefront's offering always reported 0 active orders
        // and "Mark All Delivered" did nothing at all.
        Kitchen kitchen = requireOwnedProduct(productId, seller).getKitchen();
        LocalDateTime now = LocalDateTime.now();
        // The scope is measured BEFORE any write: this is the exact set the seller
        // confirmed, and the same set the rows below are drawn from.
        DeliveryProgressDto before = computeDeliveryProgress(productId, date, kitchen);
        int confirmedScope = before.getBulkScopeOrderCount();

        int updated = 0;
        for (Order order : orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen)) {
            if (!orderContainsProductOnDate(order, productId, date)) continue;
            if (!order.isActiveForDelivery()) continue;
            if (order.isDelivered()) continue; // stays Delivered, original timestamp kept
            if (order.applyDeliveryStatus(DeliveryStatus.DELIVERED, seller, now)) {
                orderRepository.save(order);
                updated++;
                // Handover 18: one DELIVERED event per order that actually changed.
                analyticsService.record(AnalyticsService.EV_ORDER_DELIVERED,
                        order.getBuyer() != null ? order.getBuyer().getId() : null,
                        order.getBuyer() != null ? order.getBuyer().getMobileNumber() : null,
                        order.getKitchen() != null ? order.getKitchen().getId() : null,
                        order.getOrderNumber());
            }
        }
        // Progress is recomputed from the SAME rows just written, so the numbers
        // the caller receives always describe committed server state.
        DeliveryProgressDto progress = computeDeliveryProgress(productId, date, kitchen);
        if (updated != confirmedScope) {
            // Defensive: the scope measured BEFORE the write and the rows actually
            // changed must agree. A mismatch would mean the batch was only
            // partially applied, which must never be reported as a clean success -
            // the transaction rolls back, so the seller's view stays unchanged.
            throw new IllegalStateException("Delivery update was only partially applied. Please retry.");
        }
        progress.setAppliedCount(updated);
        return progress;
    }

    /**
     * Delivery progress + bulk scope for one offering on one date, computed from
     * ACTIVE (non-draft, non-cancelled) orders only. This is the single source of
     * the numbers the offering screen shows and the count its confirmation dialog
     * quotes.
     */
    @Transactional(readOnly = true)
    public DeliveryProgressDto getDeliveryProgress(Long productId, LocalDate date, User seller) {
        // Same offering-derived kitchen resolution as the bulk write, so the
        // progress the seller reads and the rows the bulk action changes are
        // always computed over the same storefront.
        Kitchen kitchen = requireOwnedProduct(productId, seller).getKitchen();
        return computeDeliveryProgress(productId, date, kitchen);
    }

    /** Shared counter used by the progress read AND the bulk write. */
    private DeliveryProgressDto computeDeliveryProgress(Long productId, LocalDate date, Kitchen kitchen) {
        DeliveryProgressDto dto = new DeliveryProgressDto();
        dto.setProductId(productId);
        dto.setDate(date);
        int active = 0, delivered = 0;
        for (Order order : orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen)) {
            if (!orderContainsProductOnDate(order, productId, date)) continue;
            if (!order.isActiveForDelivery()) continue;
            active++;
            if (order.isDelivered()) delivered++;
        }
        dto.setActiveOrderCount(active);
        dto.setDeliveredCount(delivered);
        dto.setRemainingCount(active - delivered);
        dto.setBulkAlreadyDeliveredCount(delivered);
        // Only active-and-not-yet-delivered rows will actually change.
        dto.setBulkScopeOrderCount(active - delivered);
        return dto;
    }

    /** Loads the offering and proves the AUTHENTICATED seller owns it. */
    private Product requireOwnedProduct(Long productId, User seller) {
        if (seller == null) throw new SellerNotAuthorizedException("Not authorized");
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!isOwnedBySeller(product.getKitchen(), seller)) {
            throw new SellerNotAuthorizedException("Not your product");
        }
        return product;
    }

    private boolean orderContainsProduct(Order order, Long productId) {
        if (order.getItems() == null) return false;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null && productId.equals(item.getProduct().getId())) return true;
        }
        return false;
    }

    private boolean orderContainsProductOnDate(Order order, Long productId, LocalDate date) {
        if (order.getItems() == null || date == null) return false;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null || !productId.equals(item.getProduct().getId())) continue;
            LocalDate fulfilment = item.getScheduledDate();
            if (fulfilment == null && order.getCreatedAt() != null) fulfilment = order.getCreatedAt().toLocalDate();
            if (date.equals(fulfilment)) return true;
        }
        return false;
    }

    /** True when {@code seller} owns {@code kitchen}; a null kitchen is never owned. */
    private boolean isOwnedBySeller(Kitchen kitchen, User seller) {
        return kitchen != null && kitchen.getSeller() != null
                && kitchen.getSeller().getId() != null
                && kitchen.getSeller().getId().equals(seller.getId());
    }

    /*
     * Deliberately absent: a "first kitchen of the seller" lookup.
     *
     * A seller may own several storefronts, so "the seller's kitchen" is not a
     * well-defined value. Delivery scope is always resolved from the OFFERING the
     * seller opened (see requireOwnedProduct), which is both unambiguous and
     * ownership-checked. Returning kitchens.get(0) silently pointed the Kitchen
     * and Homemade storefronts of one seller at each other's orders, so the
     * second storefront reported 0 deliveries and Mark All Delivered did nothing.
     */

    @Transactional(readOnly = true)
    public List<SellerOrderSummaryRowDto> getSellerOrders(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (kitchens.isEmpty()) return List.of();
        return kitchens.stream().flatMap(k -> orderRepository.findByKitchenOrderByCreatedAtDesc(k).stream())
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT)
                .map(this::toSellerOrderSummaryRowDto).collect(Collectors.toList());
    }

    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus, User seller) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
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
            order = cancelOrderLocked(order);
            return toOrderDto(order);
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

    @Transactional(readOnly = true)
    public OrderDto getOrderDtoForSeller(Long orderId, User seller) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getKitchen() == null || !order.getKitchen().getSeller().getId().equals(seller.getId())) {
            throw new SellerNotAuthorizedException("Not authorized");
        }
        return toOrderDto(order);
    }

    private SellerOrderSummaryRowDto toSellerOrderSummaryRowDto(Order order) {
        OrderDto full = toOrderDto(order);
        SellerOrderSummaryRowDto dto = new SellerOrderSummaryRowDto();
        dto.setId(full.getId()); dto.setOrderNumber(full.getOrderNumber()); dto.setTotalAmount(full.getTotalAmount());
        dto.setPaymentStatus(full.getPaymentStatus()); dto.setOrderStatus(full.getOrderStatus()); dto.setCreatedAt(full.getCreatedAt());
        dto.setOrderTime(full.getOrderTime()); dto.setUpdatedAt(full.getUpdatedAt()); dto.setKitchen(full.getKitchen());
        dto.setItems(full.getItems()); dto.setCustomInstructions(full.getCustomInstructions()); dto.setAcknowledgedAt(full.getAcknowledgedAt());
        dto.setAcknowledgedBySellerId(full.getAcknowledgedBySellerId());
        if (full.getBuyer() != null) {
            SellerOrderSummaryRowDto.BuyerSummary b = new SellerOrderSummaryRowDto.BuyerSummary();
            b.setName(full.getBuyer().getName()); b.setFlatHouseNumber(full.getBuyer().getFlatHouseNumber());
            b.setSociety(full.getBuyer().getSociety()); b.setBuilding(full.getBuyer().getBuilding()); dto.setBuyer(b);
        }
        return dto;
    }

    OrderDto toOrderDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setOrderStatus(order.getOrderStatus());
        // Delivery is read from the SAME Order row for every audience (seller
        // drill-down, seller order detail and the buyer's Orders page). There is
        // no buyer-specific delivery state to fall out of sync, and a legacy
        // order with no delivery history reports NOT_DELIVERED.
        dto.setDeliveryStatus(order.getEffectiveDeliveryStatus().name());
        dto.setDeliveredAt(order.getDeliveredAt());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setOrderTime(order.getOrderTime());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setCustomInstructions(order.getCustomInstructions());
        dto.setAcknowledgedAt(order.getAcknowledgedAt());
        dto.setAcknowledgedBySellerId(order.getAcknowledgedBy() != null ? order.getAcknowledgedBy().getId() : null);
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
                buyerSummary.setSociety(order.getBuyer().getSociety());
                buyerSummary.setBuilding(order.getBuyer().getBuilding());
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
                        itemDto.setOccurrenceId(item.getOccurrence() == null ? null : item.getOccurrence().getId());
                        return itemDto;
                    })
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    /**
     * Generates an order number for a newly created order.
     *
     * <p>Format: {@code SM-} + uppercase hex, matching the convention already used
     * by the seeded demo orders ({@code SM-5050}) and the existing test fixtures
     * ({@code SM-DRAFT-1}, {@code SM-PAID-001}). Order numbers are display- and
     * search-only in this application - nothing parses them - so historical values
     * are never rewritten and both existing shapes keep working.
     *
     * <p>Uniqueness. This previously returned {@code "SM" + System.nanoTime() %
     * 10000000000L}. That modulus makes the value <em>cycle every 10 seconds</em>,
     * so two orders created ten seconds apart were assigned the same number; with
     * the UNIQUE constraint on {@code order_number} the second insert failed and
     * order creation broke. A timestamp is therefore not a uniqueness guarantee.
     *
     * <p>Now a high-entropy candidate is generated and checked against the
     * repository, retrying on the (astronomically unlikely) collision. The database
     * UNIQUE constraint remains the final backstop.
     */
    private String generateOrderNumber() {
        for (int attempt = 0; attempt < MAX_ORDER_NUMBER_ATTEMPTS; attempt++) {
            String candidate = "SM-" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase(Locale.ROOT);
            if (!orderRepository.existsByOrderNumber(candidate)) return candidate;
        }
        // Exhausted the retries: fall back to a timestamp-qualified value so the
        // order can still be created. The UNIQUE constraint remains authoritative.
        return "SM-" + System.nanoTime() + "-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void validateOrderItemsForPlacement(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null || item.getProduct().getId() == null) {
                throw new IllegalArgumentException("One of the selected offerings is no longer available.");
            }
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProduct().getId()));
            if (product.getKitchen() == null || !product.getKitchen().getId().equals(order.getKitchen().getId())) {
                throw new IllegalArgumentException("One of the selected offerings no longer belongs to this kitchen.");
            }
            if (Boolean.TRUE.equals(product.getOrdersPaused())) {
                throw new IllegalArgumentException("Orders are paused for '" + product.getName() + "'.");
            }
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            // Recurring lines re-validate against the occurrence the draft
            // actually targets: the schedule (or its per-day override) may
            // have changed between draft and placement. Everything else about
            // the line was fixed at draft time by the same occurrence rules.
            boolean recurringProduct = recurringScheduleService != null
                    && recurringScheduleService.hasSchedule(product.getId());
            if (recurringProduct) {
                OccurrenceDto target = recurringScheduleService
                        .resolveOccurrenceForDateForUpdate(product, item.getScheduledDate());
                if (target == null) {
                    throw new IllegalArgumentException("'" + product.getName()
                            + "' is no longer scheduled for " + item.getScheduledDate() + ".");
                }
                if (item.getOccurrence() != null && !target.getId().equals(item.getOccurrence().getId())) {
                    throw new IllegalArgumentException("The selected occurrence changed for '"
                            + product.getName() + "'. Refresh and try again.");
                }
                if (item.getOccurrence() == null) {
                    item.setOccurrence(recurringScheduleService.getOccurrence(target.getId()));
                }
                validateRecurringOrder(product, target, quantity);
                continue;
            }
            boolean preorder = Boolean.TRUE.equals(product.getIsPreorder());
            LocalDate orderingDate = LocalDate.now();
            if (preorder) {
                if (item.getScheduledDate() == null) {
                    orderingDate = product.getAvailableDate() != null
                            ? product.getAvailableDate().minusDays(1) : LocalDate.now();
                } else {
                    orderingDate = item.getScheduledDate().minusDays(1);
                }
            }
            // Re-check at final placement: a draft may have been created before
            // Orders Open or before the offering's Orders Close time.
            OfferingTiming.enforceOrderWindow(product, orderingDate, "today");
            if (!preorder && Boolean.FALSE.equals(product.getAvailableToday())) {
                throw new IllegalArgumentException("'" + product.getName() + "' is no longer available today.");
            }
            if (product.getRemainingQuantity() != null && product.getRemainingQuantity() <= 0) {
                throw new IllegalArgumentException("'" + product.getName() + "' is sold out.");
            }
            if (product.getRemainingQuantity() != null && quantity > product.getRemainingQuantity()) {
                throw new IllegalArgumentException("Only " + product.getRemainingQuantity() + " left of '" + product.getName() + "'.");
            }
            if (product.getMaxQuantity() != null && quantity > product.getMaxQuantity()) {
                throw new IllegalArgumentException("At most " + product.getMaxQuantity() + " units of '" + product.getName() + "' per order.");
            }
            if (!preorder) {
                enforceCutoff(product, LocalDate.now(), "today");
            } else {
                LocalDate scheduled = item.getScheduledDate();
                LocalDate earliest = product.getAvailableDate() != null ? product.getAvailableDate() : LocalDate.now().plusDays(1);
                LocalDate latest = product.getAvailableUntilDate() != null ? product.getAvailableUntilDate() : earliest;
                if (scheduled == null || scheduled.isBefore(earliest) || scheduled.isAfter(latest)) {
                    throw new IllegalArgumentException("'" + product.getName() + "' can be scheduled between "
                            + earliest + " and " + latest + ".");
                }
                enforceCutoff(product, scheduled.minusDays(1), "for " + scheduled + " availability");
                if (product.getPreorderType() == PreorderType.FLEXIBLE) {
                    List<String> slots = parseSlots(product.getTimeSlots());
                    if (!slots.isEmpty() && (item.getScheduledSlot() == null || !slots.contains(item.getScheduledSlot()))) {
                        throw new IllegalArgumentException("Choose a valid time slot for '" + product.getName() + "'.");
                    }
                }
            }
        }
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
            if (recurringScheduleService != null && recurringScheduleService.hasSchedule(product.getId())) {
                // Recurring capacity is occurrence/date scoped. Product stock is
                // the one-time bucket and must not let Monday consume Friday.
                continue;
            }
            if (product.getRemainingQuantity() != null) {
                if (product.getRemainingQuantity() < e.getValue()) {
                    throw new IllegalArgumentException("Only " + product.getRemainingQuantity() + " left of '" +
                            product.getName() + "'. Please reduce quantity and try again.");
                }
                int updated = productRepository.consumeStock(e.getKey(), e.getValue());
                if (updated == 0) {
                    throw new IllegalArgumentException("Not enough stock left for '" + product.getName() + "'. Please reduce quantity.");
                }
                Product fresh = productRepository.findById(e.getKey()).orElse(null);
                if (fresh != null && fresh.getRemainingQuantity() != null && fresh.getRemainingQuantity() <= 0) {
                    fresh.setAvailableToday(false);
                    productRepository.save(fresh);
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
            if (item == null || item.getProduct() == null || item.getProduct().getId() == null) continue;
            Long productId = item.getProduct().getId();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) continue;
            Integer existing = totals.get(productId);
            totals.put(productId, (existing == null ? 0 : existing) + quantity);
        }
        for (Map.Entry<Long, Integer> e : totals.entrySet()) {
            int quantity = e.getValue();
            if (quantity <= 0) continue;
            // The cancellation path holds the order row lock, and these database
            // updates are atomic/capped, so concurrent cancellations cannot lose stock.
            Product product = productRepository.findById(e.getKey()).orElse(null);
            if (product == null) continue;
            if (recurringScheduleService != null && recurringScheduleService.hasSchedule(product.getId())) continue;
            if (product.getRemainingQuantity() != null) {
                int restored = productRepository.restoreStock(e.getKey(), quantity);
                if (restored == 0) {
                    throw new IllegalArgumentException("Could not restore inventory for '"
                            + product.getName() + "'. Please try again.");
                }
                productRepository.reopenAfterRestore(e.getKey());
            } else {
                productRepository.decrementBookedQuantity(e.getKey(), quantity);
            }
        }
    }

    /**
     * Sends sold-out notifications to the seller for any product whose tracked remainingQuantity
     * newly dropped to zero as a result of the given order. Only fires on the transition into
     * sold-out state — not on every order placement — preventing notification spam on refresh.
     * Each product is notified at most once per order even when the draft lists it more than once.
     */
    private void notifyNewlySoldOut(Order order) {
        if (order.getKitchen() == null || order.getKitchen().getSeller() == null) return;
        if (order.getItems() == null) return;
        Set<Long> notifiedProductIds = new HashSet<>();
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null || item.getProduct().getId() == null) continue;
            if (!notifiedProductIds.add(item.getProduct().getId())) continue;
            Product fresh = productRepository.findById(item.getProduct().getId()).orElse(null);
            if (fresh != null && fresh.getRemainingQuantity() != null && fresh.getRemainingQuantity() <= 0) {
                notificationService.sendSoldOutNotification(order.getKitchen().getSeller(), fresh.getName());
            }
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

    /**
     * Handover 18: "order_placed with order value, seller/storefront, Area,
     * Society and category". The detail string is stored ON the event row, so
     * aggregate analytics stay correct even after the detailed-order retention
     * purge deletes the order itself.
     *
     * <p>Format: {@code ORDER-123;value=250.00;storefront=4;seller=7;category=KITCHEN;societyId=2;society=Green Park;areaId=1;area=HSR;payment=COD}.
     * Area/society prefer the canonical reference rows and fall back to the
     * free-text values the buyer typed when no reference is attached.</p>
     */
    private static String orderPlacedDetail(Order order, User buyer) {
        StringBuilder sb = new StringBuilder(order.getOrderNumber() == null ? "ORDER" : order.getOrderNumber());
        sb.append(";value=").append(order.getTotalAmount() != null ? order.getTotalAmount() : "0");
        Kitchen kitchen = order.getKitchen();
        if (kitchen != null) {
            sb.append(";storefront=").append(kitchen.getId());
            if (kitchen.getSeller() != null) sb.append(";seller=").append(kitchen.getSeller().getId());
            sb.append(";category=").append(kitchen.getSellerType() != null ? kitchen.getSellerType().name() : "UNKNOWN");
        }
        Society society = buyer.getSocietyRef();
        if (society != null) {
            sb.append(";societyId=").append(society.getId()).append(";society=").append(society.getName());
        } else if (buyer.getSociety() != null && !buyer.getSociety().isBlank()) {
            sb.append(";society=").append(buyer.getSociety());
        }
        Area area = buyer.getAreaRef() != null ? buyer.getAreaRef() : (society != null ? society.getArea() : null);
        if (area != null) {
            sb.append(";areaId=").append(area.getId()).append(";area=").append(area.getName());
        } else if (buyer.getArea() != null && !buyer.getArea().isBlank()) {
            sb.append(";area=").append(buyer.getArea());
        }
        sb.append(";payment=").append(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "UNKNOWN");
        return sb.toString();
    }
}

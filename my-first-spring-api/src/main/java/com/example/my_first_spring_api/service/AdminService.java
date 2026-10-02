package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    public static final String SUPER_ADMIN_MOBILE = "9000000001";
    public static final String ADMIN_MOBILE = "9000000002";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final KitchenRepository kitchenRepository;
    private final EnquiryRepository enquiryRepository;
    private final FavouriteRepository favouriteRepository;
    private final AnalyticsService analyticsService;
    private final SocietyDirectory societyDirectory;
    private final LocationService locationService;
    private final org.springframework.core.env.Environment environment;

    @Autowired
    public AdminService(UserRepository userRepository, AnalyticsService analyticsService,
                        OrderRepository orderRepository, ProductRepository productRepository,
                        KitchenRepository kitchenRepository, EnquiryRepository enquiryRepository,
                        FavouriteRepository favouriteRepository, SocietyDirectory societyDirectory,
                        LocationService locationService,
                        org.springframework.core.env.Environment environment) {
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.kitchenRepository = kitchenRepository;
        this.enquiryRepository = enquiryRepository;
        this.favouriteRepository = favouriteRepository;
        this.societyDirectory = societyDirectory;
        this.locationService = locationService;
        this.environment = environment;
    }

    // ==================== Dashboard ====================

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();

        List<User> allBuyers = userRepository.findByRole(UserRole.BUYER);
        List<User> allSellers = userRepository.findByRole(UserRole.SELLER);
        List<Kitchen> allKitchens = kitchenRepository.findAll();
        List<Product> allProducts = productRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();
        List<Enquiry> allEnquiries = enquiryRepository.findAll();

        long totalBuyers = allBuyers.size();
        long totalSellers = allSellers.size();
        long approvedSellers = allSellers.stream().filter(s -> s.getSellerApprovalStatus() == SellerApprovalStatus.APPROVED).count();
        long pendingSellers = allSellers.stream().filter(s -> s.getSellerApprovalStatus() == SellerApprovalStatus.PENDING).count();
        long rejectedSellers = allSellers.stream().filter(s -> s.getSellerApprovalStatus() == SellerApprovalStatus.REJECTED).count();
        long suspendedSellers = allSellers.stream().filter(s -> s.getSellerApprovalStatus() == SellerApprovalStatus.SUSPENDED).count();

        long totalKitchens = allKitchens.size();
        Set<Long> liveKitchenIds = new HashSet<>();
        long liveOfferings = 0;
        long preorderOfferings = 0;
        long soldOutOfferings = 0;
        long totalOfferings = allProducts.size();

        for (Product p : allProducts) {
            if (isLiveProduct(p)) {
                liveOfferings++;
                liveKitchenIds.add(p.getKitchen().getId());
            }
            if (p.getIsPreorder() != null && p.getIsPreorder()) {
                preorderOfferings++;
            }
            if (p.isSoldOut()) {
                soldOutOfferings++;
            }
        }

        long totalOrders = allOrders.size();
        long ordersToday = allOrders.stream().filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfToday)).count();
        long ordersThisMonth = allOrders.stream().filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfMonth)).count();
        long ordersLast3Days = allOrders.stream().filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfToday.minusDays(2))).count();

        BigDecimal totalOrderValue = allOrders.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal todayOrderValue = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfToday))
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthOrderValue = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfMonth))
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidCount = allOrders.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).count();
        BigDecimal paidValue = allOrders.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long willPayLaterCount = allOrders.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.WILL_PAY_LATER).count();
        BigDecimal willPayLaterValue = allOrders.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.WILL_PAY_LATER)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingPaymentCount = allOrders.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PENDING).count();
        BigDecimal pendingPaymentValue = allOrders.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PENDING)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Order-status breakdown for the Admin dashboard. Counted in memory from the
        // allOrders list this method has ALREADY loaded, so it adds no extra queries.
        // /api/admin/** is Admin-only, so no Buyer/Seller behaviour is affected.
        // The buckets below are mutually exclusive and sum to totalOrders:
        //   awaitingSellerConfirmation = ORDERED   (placed, seller has not confirmed)
        //   inFulfilment               = CONFIRMED + READY
        //   fulfilled                   = DELIVERED + COMPLETED
        //   cancelled                   = CANCELLED
        //   draft                       = DRAFT (not a real order yet)
        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus st : OrderStatus.values()) ordersByStatus.put(st, 0L);
        for (Order o : allOrders) {
            if (o.getOrderStatus() != null) ordersByStatus.merge(o.getOrderStatus(), 1L, Long::sum);
        }
        long ordersAwaitingSellerConfirmation = ordersByStatus.getOrDefault(OrderStatus.ORDERED, 0L);
        long ordersInFulfilment = ordersByStatus.getOrDefault(OrderStatus.CONFIRMED, 0L)
                + ordersByStatus.getOrDefault(OrderStatus.READY, 0L);
        long ordersFulfilled = ordersByStatus.getOrDefault(OrderStatus.DELIVERED, 0L)
                + ordersByStatus.getOrDefault(OrderStatus.COMPLETED, 0L);
        long ordersCancelled = ordersByStatus.getOrDefault(OrderStatus.CANCELLED, 0L);
        long ordersDraft = ordersByStatus.getOrDefault(OrderStatus.DRAFT, 0L);

        long totalEnquiries = allEnquiries.size();
        long openEnquiries = allEnquiries.stream().filter(e -> e.getStatus() == EnquiryStatus.NEW).count();
        long resolvedEnquiries = allEnquiries.stream().filter(e -> e.getStatus() == EnquiryStatus.CONTACTED || e.getStatus() == EnquiryStatus.CLOSED).count();

        long totalFavourites = favouriteRepository.count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalBuyers", totalBuyers);
        out.put("totalSellers", totalSellers);
        out.put("approvedSellers", approvedSellers);
        out.put("pendingSellers", pendingSellers);
        out.put("rejectedSellers", rejectedSellers);
        out.put("suspendedSellers", suspendedSellers);
        out.put("totalKitchens", totalKitchens);
        out.put("liveKitchens", liveKitchenIds.size());
        out.put("kitchensWithZeroLiveOfferings", totalKitchens - liveKitchenIds.size());
        out.put("totalOfferings", totalOfferings);
        out.put("liveOfferings", liveOfferings);
        out.put("preorderOfferings", preorderOfferings);
        out.put("soldOutOfferings", soldOutOfferings);
        out.put("totalOrders", totalOrders);
        out.put("ordersToday", ordersToday);
        out.put("ordersThisMonth", ordersThisMonth);
        out.put("ordersLast3Days", ordersLast3Days);
        out.put("totalOrderValue", totalOrderValue);
        out.put("todayOrderValue", todayOrderValue);
        out.put("monthOrderValue", monthOrderValue);
        out.put("paidCount", paidCount);
        out.put("paidValue", paidValue);
        out.put("willPayLaterCount", willPayLaterCount);
        out.put("willPayLaterValue", willPayLaterValue);
        out.put("pendingPaymentCount", pendingPaymentCount);
        out.put("pendingPaymentValue", pendingPaymentValue);
        out.put("totalEnquiries", totalEnquiries);
        out.put("openEnquiries", openEnquiries);
        out.put("resolvedEnquiries", resolvedEnquiries);
        out.put("totalFavourites", totalFavourites);
        // Additive keys for the Admin order-status breakdown (see comment above).
        out.put("ordersByStatus", ordersByStatus);
        out.put("ordersAwaitingSellerConfirmation", ordersAwaitingSellerConfirmation);
        out.put("ordersInFulfilment", ordersInFulfilment);
        out.put("ordersFulfilled", ordersFulfilled);
        out.put("ordersCancelled", ordersCancelled);
        out.put("ordersDraft", ordersDraft);
        return out;
    }

    private boolean isLiveProduct(Product p) {
        if (p.getAvailableToday() == null || !p.getAvailableToday()) return false;
        if (p.isSoldOut()) return false;
        return true;
    }

    // ==================== Buyers ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buyers() {
        List<User> buyers = userRepository.findByRole(UserRole.BUYER);
        return buyers.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            m.put("mobileNumber", b.getMobileNumber());
            m.put("society", b.getSociety());
            m.put("building", b.getBuilding());
            m.put("flatHouseNumber", b.getFlatHouseNumber());
            List<Order> orders = orderRepository.findByBuyerOrderByCreatedAtDesc(b);
            m.put("orderCount", orders.size());
            BigDecimal total = orders.stream()
                    .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("totalOrderValue", total);
            m.put("favouriteKitchens", favouriteRepository.countByUser(b));
            m.put("createdAt", b.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    // ==================== Sellers ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sellers(SellerApprovalStatus status) {
        List<User> sellers = status != null
                ? userRepository.findByRoleAndSellerApprovalStatus(UserRole.SELLER, status)
                : userRepository.findByRole(UserRole.SELLER);
        return sellers.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("mobileNumber", s.getMobileNumber());
            m.put("sellerApprovalStatus", s.getSellerApprovalStatus());
            m.put("statusReason", s.getSellerStatusReason());
            m.put("approvedAt", s.getApprovedAt());
            m.put("createdAt", s.getCreatedAt());
            List<Kitchen> kitchens = kitchenRepository.findBySeller(s);
            m.put("kitchenCount", kitchens.size());
            if (!kitchens.isEmpty()) {
                Kitchen k = kitchens.get(0);
                m.put("kitchenName", k.getDisplayName());
                m.put("society", k.getSociety());
                m.put("building", k.getBuilding());
                m.put("area", k.getSociety());
                m.put("instagramLink", k.getInstagramLink());
                List<Product> products = productRepository.findByKitchen(k);
                long liveCount = products.stream().filter(this::isLiveProduct).count();
                m.put("liveOfferings", liveCount);
                m.put("totalOfferings", products.size());
            }
            return m;
        }).collect(Collectors.toList());
    }

    // ==================== Kitchens ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> kitchens() {
        return kitchens(null);
    }

    public List<Map<String, Object>> kitchens(String sellerTypeFilter) {
        List<Kitchen> all = kitchenRepository.findAll();
        return all.stream()
                .filter(k -> sellerTypeFilter == null || sellerTypeFilter.isBlank() || sellerTypeFilter.equalsIgnoreCase(k.getSellerType() != null ? k.getSellerType().name() : ""))
                .map(k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", k.getId());
                m.put("name", k.getName());
                m.put("displayName", k.getDisplayName());
                m.put("sellerId", k.getSeller() != null ? k.getSeller().getId() : null);
                m.put("sellerName", k.getSeller() != null ? k.getSeller().getName() : null);
                m.put("sellerType", k.getSellerType() != null ? k.getSellerType().name() : null);
                m.put("society", k.getSociety());
                m.put("building", k.getBuilding());
                m.put("area", k.getSociety());
                m.put("serviceAreas", k.getServiceAreas());
                m.put("servedSocietyIds", k.getServedSocieties() == null
                        ? new java.util.ArrayList<>()
                        : k.getServedSocieties().stream()
                                .map(com.example.my_first_spring_api.model.Society::getId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toList()));
                m.put("availableToday", k.getAvailableToday());
                m.put("imageUrl", k.getImageUrl());
                m.put("instagramLink", k.getInstagramLink());
                List<Product> products = productRepository.findByKitchen(k);
                long liveCount = products.stream().filter(this::isLiveProduct).count();
                m.put("totalOfferings", products.size());
                m.put("liveOfferings", liveCount);
                m.put("hasLiveOfferings", liveCount > 0);
                return m;
            }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> updateKitchenServiceAreas(Long kitchenId, String serviceAreas) {
        return updateKitchenServiceAreas(kitchenId, serviceAreas, null, null);
    }

    /**
     * ID-based coverage update, used by the Admin kitchen coverage editor.
     *
     * <p>When {@code societyIds} is supplied it is the authoritative write: the
     * kitchen's COMPLETE coverage is replaced by those Society records (validated as
     * existing, active, and inside the selected Area) and the denormalised display
     * string is rebuilt from them, so the two representations cannot drift. The
     * legacy name-string argument is still honoured when no IDs are supplied, and
     * both routes funnel through the same {@code LocationService} methods — there is
     * only one persistence path for coverage.</p>
     */
    @Transactional
    public Map<String, Object> updateKitchenServiceAreas(Long kitchenId, String serviceAreas,
                                                         Long areaId, List<Long> societyIds) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        if (societyIds != null) {
            locationService.saveSellerCoverage(kitchen, areaId, societyIds);
        } else if (serviceAreas != null) {
            kitchen.setServiceAreas(societyDirectory.validateAndNormalize(serviceAreas));
            locationService.applyCoverageFromNames(kitchen, kitchen.getServiceAreas());
            kitchenRepository.save(kitchen);
        }
        // Neither field was supplied, so the request carries no intent at all. The
        // Admin editor omits both when no Area is picked (its own comment promises the
        // save "cannot silently clear an existing coverage"), so treat it as a strict
        // no-op. Falling through to the legacy branch here used to normalise null to
        // "" and wipe servedSocieties + serviceAreas - real data loss on a kitchen that
        // already had ID-backed coverage, reachable whenever the persisted Area is not
        // offered in the active coverage options. Clearing coverage stays available by
        // sending an explicit empty societyIds array or an explicit "" serviceAreas.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", kitchen.getId());
        out.put("name", kitchen.getName());
        out.put("displayName", kitchen.getDisplayName());
        out.put("serviceAreas", kitchen.getServiceAreas());
        out.put("society", kitchen.getSociety());
        out.put("servedSocietyIds", kitchen.getServedSocieties() == null
                ? new java.util.ArrayList<>()
                : kitchen.getServedSocieties().stream()
                        .map(com.example.my_first_spring_api.model.Society::getId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList()));
        return out;
    }

    /**
     * Existing societies admins can assign as a kitchen's service areas.
     * Same source as the seller's Manage Kitchen list — no second list.
     */
    @Transactional(readOnly = true)
    public List<String> societies() {
        return societyDirectory.findAllSocieties();
    }

    /**
     * The Area/Society choices for the coverage editor: ACTIVE areas with their
     * ACTIVE societies and the IDs the write path needs. Delegates to the shared
     * {@link LocationService} so the Admin editor and the seller picker are driven
     * by one identical source.
     */
    @Transactional(readOnly = true)
    public List<CoverageOptionDto> coverageOptions() {
        return locationService.getCoverageOptions();
    }

    // ==================== Offerings ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> offerings() {
        List<Product> all = productRepository.findAll();
        return all.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("kitchenId", p.getKitchen() != null ? p.getKitchen().getId() : null);
            m.put("kitchenName", p.getKitchen() != null ? p.getKitchen().getDisplayName() : null);
            m.put("sellerId", p.getKitchen() != null && p.getKitchen().getSeller() != null ? p.getKitchen().getSeller().getId() : null);
            m.put("sellerName", p.getKitchen() != null && p.getKitchen().getSeller() != null ? p.getKitchen().getSeller().getName() : null);
            m.put("price", p.getPrice());
            m.put("priceUnit", p.getPriceUnit());
            m.put("availableToday", p.getAvailableToday());
            m.put("isPreorder", p.getIsPreorder());
            m.put("availableDate", p.getAvailableDate() != null ? p.getAvailableDate().toString() : null);
            m.put("cutoffTime", p.getCutoffTime());
            m.put("maxQuantity", p.getMaxQuantity());
            m.put("remainingQuantity", p.getRemainingQuantity());
            m.put("bookedQuantity", p.getBookedQuantity());
            m.put("soldOut", p.isSoldOut());
            m.put("category", p.getCategory());
            m.put("status", classifyProductStatus(p));
            return m;
        }).collect(Collectors.toList());
    }

    private String classifyProductStatus(Product p) {
        if (p.isSoldOut()) return "SOLD_OUT";
        if (p.getIsPreorder() != null && p.getIsPreorder()) return "PRE_ORDER";
        if (p.getAvailableToday() == null || !p.getAvailableToday()) return "CLOSED";
        return "LIVE";
    }

    // ==================== Orders ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> orders(String filter, String search) {
        List<Order> all = orderRepository.findAll();
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        
        return all.stream()
                .filter(o -> {
                    if ("last3days".equals(filter)) {
                        return o.getCreatedAt() != null && o.getCreatedAt().isAfter(threeDaysAgo);
                    }
                    return true;
                })
                .filter(o -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    String orderNum = o.getOrderNumber() != null ? o.getOrderNumber().toLowerCase() : "";
                    String buyerName = o.getBuyer() != null && o.getBuyer().getName() != null ? o.getBuyer().getName().toLowerCase() : "";
                    String buyerMobile = o.getBuyer() != null && o.getBuyer().getMobileNumber() != null ? o.getBuyer().getMobileNumber().toLowerCase() : "";
                    String kitchenName = o.getKitchen() != null && o.getKitchen().getDisplayName() != null ? o.getKitchen().getDisplayName().toLowerCase() : "";
                    String sellerName = o.getKitchen() != null && o.getKitchen().getSeller() != null && o.getKitchen().getSeller().getName() != null ? o.getKitchen().getSeller().getName().toLowerCase() : "";
                    return orderNum.contains(s) || buyerName.contains(s) || buyerMobile.contains(s) || kitchenName.contains(s) || sellerName.contains(s);
                })
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNumber", o.getOrderNumber());
                    m.put("buyerId", o.getBuyer() != null ? o.getBuyer().getId() : null);
                    m.put("buyerName", o.getBuyer() != null ? o.getBuyer().getName() : null);
                    m.put("buyerMobile", o.getBuyer() != null ? o.getBuyer().getMobileNumber() : null);
                    m.put("sellerId", o.getKitchen() != null && o.getKitchen().getSeller() != null ? o.getKitchen().getSeller().getId() : null);
                    m.put("sellerName", o.getKitchen() != null && o.getKitchen().getSeller() != null ? o.getKitchen().getSeller().getName() : null);
                    m.put("kitchenId", o.getKitchen() != null ? o.getKitchen().getId() : null);
                    m.put("kitchenName", o.getKitchen() != null ? o.getKitchen().getDisplayName() : null);
                    m.put("totalAmount", o.getTotalAmount());
                    m.put("paymentStatus", o.getPaymentStatus() != null ? o.getPaymentStatus().name() : null);
                    m.put("orderStatus", o.getOrderStatus() != null ? o.getOrderStatus().name() : null);
                    m.put("customInstructions", o.getCustomInstructions());
                    m.put("createdAt", o.getCreatedAt());
                    m.put("orderTime", o.getOrderTime());
                    m.put("society", o.getBuyer() != null ? o.getBuyer().getSociety() : null);
                    m.put("building", o.getBuyer() != null ? o.getBuyer().getBuilding() : null);
                    m.put("flatHouseNumber", o.getBuyer() != null ? o.getBuyer().getFlatHouseNumber() : null);
                    List<Map<String, Object>> items = o.getItems().stream().map(it -> {
                        Map<String, Object> im = new LinkedHashMap<>();
                        im.put("productId", it.getProduct() != null ? it.getProduct().getId() : null);
                        im.put("productName", it.getProduct() != null ? it.getProduct().getName() : null);
                        im.put("quantity", it.getQuantity());
                        im.put("price", it.getPrice());
                        im.put("total", it.getPrice() != null && it.getQuantity() != null ? it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())) : BigDecimal.ZERO);
                        return im;
                    }).collect(Collectors.toList());
                    m.put("items", items);
                    return m;
                }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean orderExists(Long id) {
        return orderRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> orderDetail(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.getId());
        m.put("orderNumber", order.getOrderNumber());
        m.put("buyerId", order.getBuyer() != null ? order.getBuyer().getId() : null);
        m.put("buyerName", order.getBuyer() != null ? order.getBuyer().getName() : null);
        m.put("buyerMobile", order.getBuyer() != null ? order.getBuyer().getMobileNumber() : null);
        m.put("buyerSociety", order.getBuyer() != null ? order.getBuyer().getSociety() : null);
        m.put("buyerBuilding", order.getBuyer() != null ? order.getBuyer().getBuilding() : null);
        m.put("buyerFlat", order.getBuyer() != null ? order.getBuyer().getFlatHouseNumber() : null);
        m.put("sellerId", order.getKitchen() != null && order.getKitchen().getSeller() != null ? order.getKitchen().getSeller().getId() : null);
        m.put("sellerName", order.getKitchen() != null && order.getKitchen().getSeller() != null ? order.getKitchen().getSeller().getName() : null);
        m.put("kitchenId", order.getKitchen() != null ? order.getKitchen().getId() : null);
        m.put("kitchenName", order.getKitchen() != null ? order.getKitchen().getDisplayName() : null);
        m.put("kitchenSociety", order.getKitchen() != null ? order.getKitchen().getSociety() : null);
        m.put("kitchenBuilding", order.getKitchen() != null ? order.getKitchen().getBuilding() : null);
        m.put("totalAmount", order.getTotalAmount());
        m.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        m.put("orderStatus", order.getOrderStatus() != null ? order.getOrderStatus().name() : null);
        m.put("customInstructions", order.getCustomInstructions());
        m.put("createdAt", order.getCreatedAt());
        m.put("orderTime", order.getOrderTime());
        List<Map<String, Object>> items = order.getItems().stream().map(it -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("productId", it.getProduct() != null ? it.getProduct().getId() : null);
            im.put("productName", it.getProduct() != null ? it.getProduct().getName() : null);
            im.put("quantity", it.getQuantity());
            im.put("price", it.getPrice());
                         im.put("lineTotal", it.getPrice() != null && it.getQuantity() != null ? it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())) : BigDecimal.ZERO);
                         im.put("total", it.getPrice() != null && it.getQuantity() != null ? it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())) : BigDecimal.ZERO);
                         im.put("productCurrentPrice", it.getProduct() != null ? it.getProduct().getPrice() : null);
            return im;
        }).collect(Collectors.toList());
        m.put("items", items);
        return m;
    }

    // ==================== Enquiries ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> enquiries() {
        List<Enquiry> all = enquiryRepository.findAll();
        return all.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("userId", e.getUser() != null ? e.getUser().getId() : null);
            m.put("userName", e.getUser() != null ? e.getUser().getName() : null);
            m.put("userMobile", e.getUser() != null ? e.getUser().getMobileNumber() : null);
            m.put("kitchenId", e.getKitchen() != null ? e.getKitchen().getId() : null);
            m.put("kitchenName", e.getKitchen() != null ? e.getKitchen().getDisplayName() : null);
            m.put("message", e.getMessage());
            m.put("status", e.getStatus() != null ? e.getStatus().name() : null);
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    // ==================== Seller approval workflow ====================

    @Transactional(readOnly = true)
    public List<User> listSellers(SellerApprovalStatus status) {
        if (status != null) {
            return userRepository.findByRoleAndSellerApprovalStatus(UserRole.SELLER, status);
        }
        return userRepository.findByRole(UserRole.SELLER);
    }

    @Transactional(readOnly = true)
    public List<User> pendingSellers() {
        return userRepository.findByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.PENDING);
    }

    @Transactional
    public User approveSeller(Long sellerId, User actingAdmin) {
        User seller = requireSeller(sellerId);
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setSellerStatusReason(null);
        seller.setApprovedAt(LocalDateTime.now());
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "approved by " + actingAdmin.getMobileNumber());
        return userRepository.save(seller);
    }

    @Transactional
    public User rejectSeller(Long sellerId, String reason, User actingAdmin) {
        User seller = requireSeller(sellerId);
        seller.setSellerApprovalStatus(SellerApprovalStatus.REJECTED);
        seller.setSellerStatusReason(reason);
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "rejected by " + actingAdmin.getMobileNumber());
        return userRepository.save(seller);
    }

    @Transactional
    public User suspendSeller(Long sellerId, String reason, User actingAdmin) {
        User seller = requireSeller(sellerId);
        seller.setSellerApprovalStatus(SellerApprovalStatus.SUSPENDED);
        seller.setSellerStatusReason(reason);
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "suspended by " + actingAdmin.getMobileNumber());
        return userRepository.save(seller);
    }

    private User requireSeller(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + sellerId));
        if (user.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("User " + sellerId + " is not a seller.");
        }
        return user;
    }

    // ---------------- Admin account management (Super Admin) ----------------

    @Transactional(readOnly = true)
    public List<User> listAdmins() {
        List<User> admins = userRepository.findByRole(UserRole.ADMIN);
        admins.addAll(userRepository.findByRole(UserRole.SUPER_ADMIN));
        return admins;
    }

    /**
     * Promotes or creates an ADMIN account.
     *
     * <p>Validation added because the Super Admin console now drives this endpoint:
     * {@code users.mobile_number} is UNIQUE but nullable, so a missing mobile
     * previously produced an unusable admin and a duplicate request produced a
     * constraint-violation 500. The mobile is now checked against the same format
     * the rest of the application uses.
     */
    @Transactional
    public User createAdmin(String name, String mobileNumber) {
        if (mobileNumber == null || !mobileNumber.trim().matches("[6-9]\\d{9}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number.");
        }
        mobileNumber = mobileNumber.trim();
        User user = userRepository.findByMobileNumber(mobileNumber).orElse(null);
        if (user == null) {
            user = new User(name == null || name.isBlank() ? "Admin" : name, mobileNumber, null, UserRole.ADMIN);
        } else if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("This account is already a Super Admin.");
        } else if (user.getRole() == UserRole.SELLER) {
            // Changing the role would orphan the seller's kitchen and every
            // offering under it. That is a product decision, not an admin action.
            throw new IllegalArgumentException(
                    "That mobile number belongs to a Seller. Promoting it would detach their kitchen "
                            + "and offerings. Convert the seller account first.");
        } else {
            user.setRole(UserRole.ADMIN);
            if (name != null && !name.isBlank()) user.setName(name);
        }
        return userRepository.save(user);
    }

    /**
     * Demotes an ADMIN back to BUYER.
     *
     * <p>Super Admin accounts can never be demoted. The last remaining ADMIN is
     * also protected so the platform can never be left without an administrative
     * account by a single mis-click; Super Admin must first create a replacement.
     */
    @Transactional
    public User demoteAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super Admin accounts cannot be demoted.");
        }
        if (user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("User " + userId + " is not an admin.");
        }
        if (userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException(
                    "This is the last Admin account. Create another Admin before demoting this one.");
        }
        user.setRole(UserRole.BUYER);
        return userRepository.save(user);
    }

    @Transactional
    public void ensureBootstrapAccounts() {
        if (userRepository.findByMobileNumber(SUPER_ADMIN_MOBILE).isEmpty()) {
            userRepository.save(new User("Super Admin", SUPER_ADMIN_MOBILE, null, UserRole.SUPER_ADMIN));
        }
        if (userRepository.findByMobileNumber(ADMIN_MOBILE).isEmpty()) {
            userRepository.save(new User("Platform Admin", ADMIN_MOBILE, null, UserRole.ADMIN));
        }
    }

    @Transactional
    public void approveLegacySellers() {
        for (User seller : userRepository.findByRole(UserRole.SELLER)) {
            if (seller.getSellerApprovalStatus() == null) {
                seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
                seller.setApprovedAt(LocalDateTime.now());
                userRepository.save(seller);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analyticsSummary() {
        return analyticsService.summary();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> traffic(String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();

        LocalDateTime start;
        LocalDateTime end;
        String granularity;
        if ("week".equalsIgnoreCase(period)) {
            start = startOfWeek;
            end = startOfWeek.plusWeeks(1);
            granularity = "day";
        } else if ("month".equalsIgnoreCase(period)) {
            start = startOfMonth;
            end = startOfMonth.plusMonths(1);
            granularity = "day";
        } else {
            start = startOfToday;
            end = startOfToday.plusDays(1);
            granularity = "hour";
        }

        long activeBuyers = orderRepository.countDistinctBuyersBetween(start, end);
        long activeSellers = orderRepository.countDistinctSellersBetween(start, end);

        List<Object[]> raw;
        if ("hour".equals(granularity)) {
            raw = orderRepository.findHourlyTrafficBetween(start, end);
        } else {
            raw = orderRepository.findDailyTrafficBetween(start, end);
        }

        List<Map<String, Object>> series = new ArrayList<>();
        if ("hour".equals(granularity)) {
            Map<Integer, long[]> hourMap = new LinkedHashMap<>();
            for (Object[] row : raw) {
                int hour = ((Number) row[0]).intValue();
                hourMap.put(hour, new long[]{ row[1] != null ? ((Number) row[1]).longValue() : 0L, row[2] != null ? ((Number) row[2]).longValue() : 0L });
            }
            for (int h = 0; h < 24; h++) {
                long[] vals = hourMap.getOrDefault(h, new long[]{0L, 0L});
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("label", String.format("%02d:00", h));
                m.put("buyers", vals[0]);
                m.put("sellers", vals[1]);
                series.add(m);
            }
        } else {
            Map<LocalDate, long[]> dateMap = new LinkedHashMap<>();
            for (Object[] row : raw) {
                Object dateObj = row[0];
                LocalDate d;
                if (dateObj instanceof java.sql.Date) {
                    d = ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof java.time.LocalDate) {
                    d = (LocalDate) dateObj;
                } else {
                    d = LocalDate.parse(String.valueOf(dateObj));
                }
                dateMap.put(d, new long[]{ row[1] != null ? ((Number) row[1]).longValue() : 0L, row[2] != null ? ((Number) row[2]).longValue() : 0L });
            }
            if ("week".equals(period)) {
                for (int i = 0; i < 7; i++) {
                    LocalDate d = start.toLocalDate().plusDays(i);
                    long[] vals = dateMap.getOrDefault(d, new long[]{0L, 0L});
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH));
                    m.put("buyers", vals[0]);
                    m.put("sellers", vals[1]);
                    series.add(m);
                }
            } else {
                LocalDate monthStart = start.toLocalDate();
                LocalDate today = LocalDate.now();
                int daysInPeriod = today.getDayOfMonth();
                for (int d = 1; d <= daysInPeriod; d++) {
                    LocalDate date = monthStart.plusDays(d - 1);
                    long[] vals = dateMap.getOrDefault(date, new long[]{0L, 0L});
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", String.valueOf(date.getDayOfMonth()));
                    m.put("buyers", vals[0]);
                    m.put("sellers", vals[1]);
                    series.add(m);
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period != null ? period : "today");
        out.put("start", start.toString());
        out.put("end", end.toString());
        out.put("granularity", granularity);
        out.put("activeBuyers", activeBuyers);
        out.put("activeSellers", activeSellers);
        out.put("series", series);
        return out;
    }

    // ==================== Manage Areas & Societies ====================

    /**
     * The full Area -&gt; Society tree for the Manage Areas &amp; Societies screen.
     *
     * <p>Inactive records are included so the Admin can see and re-enable them;
     * the buyer and seller dropdowns only ever receive the active ones.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> locations() {
        List<Map<String, Object>> areaRows = new ArrayList<>();
        long activeAreas = 0;
        long activeSocieties = 0;
        for (Area area : locationService.findAllAreas()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", area.getId());
            row.put("name", area.getName());
            row.put("active", area.isActive());
            row.put("createdAt", area.getCreatedAt());
            List<Map<String, Object>> societyRows = new ArrayList<>();
            for (Society society : locationService.findAllSocieties(area.getId())) {
                societyRows.add(societyRow(society));
                if (society.isActive()) activeSocieties++;
            }
            row.put("societyCount", societyRows.size());
            row.put("societies", societyRows);
            if (area.isActive()) activeAreas++;
            areaRows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("areas", areaRows);
        out.put("areaCount", areaRows.size());
        out.put("societyCount", areaRows.stream()
                .mapToLong(r -> ((Number) r.get("societyCount")).longValue()).sum());
        out.put("activeAreaCount", activeAreas);
        out.put("activeSocietyCount", activeSocieties);
        return out;
    }

    @Transactional
    public Map<String, Object> createArea(String name) {
        return areaRow(locationService.createArea(name));
    }

    /** Rename and/or enable-disable an Area. Only supplied fields are applied. */
    @Transactional
    public Map<String, Object> updateArea(Long areaId, String name, Boolean active) {
        if (name != null && !name.isBlank()) locationService.renameArea(areaId, name);
        if (active != null) locationService.setAreaActive(areaId, active);
        return areaRow(locationService.findArea(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area not found.")));
    }

    @Transactional
    public Map<String, Object> createSociety(Long areaId, String name) {
        return societyRow(locationService.createSociety(areaId, name));
    }

    /** Rename and/or enable-disable a Society. Only supplied fields are applied. */
    @Transactional
    public Map<String, Object> updateSociety(Long societyId, String name, Boolean active) {
        if (name != null && !name.isBlank()) locationService.renameSociety(societyId, name);
        if (active != null) locationService.setSocietyActive(societyId, active);
        return societyRow(locationService.findSociety(societyId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found.")));
    }

    // ==================== Buyer <-> Kitchen visibility diagnostic ====================

    /**
     * "Why can't this buyer see this kitchen?" - answered from the EXISTING
     * {@link KitchenVisibility} predicates, never from a second eligibility engine.
     *
     * <p>The decision is exactly the one the real buyer-facing callers make (see
     * {@code KitchenService.getKitchenByName} / {@code getKitchenDetailById}): a
     * paused kitchen is excluded, and the kitchen must be publicly visible AND
     * service-area visible. All three predicates are called directly and their raw
     * results are returned, so the Admin sees real inputs, not a re-derived opinion.
     * Normal buyer discovery is untouched by this screen.</p>
     *
     * @param buyerId   the buyer to evaluate (required)
     * @param kitchenId when present, only this kitchen is evaluated
     */
    @Transactional(readOnly = true)
    public Map<String, Object> visibilityDiagnostic(Long buyerId, Long kitchenId) {
        if (buyerId == null) throw new IllegalArgumentException("Choose a buyer to diagnose.");
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Buyer not found."));

        List<Kitchen> kitchens = kitchenId == null
                ? kitchenRepository.findAll()
                : kitchenRepository.findById(kitchenId)
                        .map(List::of)
                        .orElseThrow(() -> new KitchenNotFoundException(kitchenId));

        List<Map<String, Object>> results = new ArrayList<>();
        int visibleCount = 0;
        for (Kitchen kitchen : kitchens) {
            Map<String, Object> row = diagnoseOne(kitchen, buyer);
            if (Boolean.TRUE.equals(row.get("visible"))) visibleCount++;
            results.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalKitchens", results.size());
        summary.put("visible", visibleCount);
        summary.put("blocked", results.size() - visibleCount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("buyer", buyerDiagnosticRow(buyer));
        out.put("results", results);
        out.put("summary", summary);
        return out;
    }
    private Map<String, Object> buyerDiagnosticRow(User buyer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", buyer.getId());
        m.put("name", buyer.getName());
        m.put("mobileNumber", buyer.getMobileNumber());
        m.put("role", buyer.getRole() == null ? null : buyer.getRole().name());
        m.put("society", buyer.getSociety());
        m.put("area", buyer.getArea());
        m.put("societyRefId", buyer.getSocietyRef() == null ? null : buyer.getSocietyRef().getId());
        m.put("areaRefId", buyer.getAreaRef() == null ? null : buyer.getAreaRef().getId());

        // The same fields OrderService requires before an order can be placed. Shown as
        // information only - this screen never changes a buyer profile.
        List<String> missing = new ArrayList<>();
        if (buyer.getSociety() == null || buyer.getSociety().isBlank()) missing.add("society");
        if (buyer.getBuilding() == null || buyer.getBuilding().isBlank()) missing.add("building");
        if (buyer.getFlatHouseNumber() == null || buyer.getFlatHouseNumber().isBlank()) {
            missing.add("flat/house number");
        }
        m.put("profileComplete", missing.isEmpty());
        m.put("profileMissing", missing);
        return m;
    }

    /** Evaluates one kitchen against one buyer using only the existing predicates. */
    private Map<String, Object> diagnoseOne(Kitchen kitchen, User buyer) {
        boolean paused = KitchenVisibility.isPaused(kitchen);
        boolean publiclyVisible = KitchenVisibility.isPubliclyVisible(kitchen);
        boolean serviceAreaVisible = KitchenVisibility.isServiceAreaVisible(kitchen, buyer);
        // The same combination the buyer-facing kitchen detail endpoints use.
        boolean visible = !paused && publiclyVisible && serviceAreaVisible;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kitchenId", kitchen.getId());
        m.put("kitchenName", kitchen.getDisplayName() != null ? kitchen.getDisplayName() : kitchen.getName());
        m.put("sellerName", kitchen.getSeller() == null ? null : kitchen.getSeller().getName());
        m.put("availableToday", kitchen.getAvailableToday());
        m.put("publiclyVisible", publiclyVisible);
        m.put("paused", paused);
        m.put("serviceAreaVisible", serviceAreaVisible);
        m.put("visible", visible);
        m.put("coverageMode", coverageMode(kitchen));
        m.put("kitchenCoverage", coverageNames(kitchen));
        m.put("reasons", visibilityReasons(kitchen, buyer, paused, publiclyVisible, serviceAreaVisible, visible));
        return m;
    }
    /**
     * Which of the existing coverage representations this kitchen actually carries.
     * Purely descriptive - it never decides eligibility.
     */
    private String coverageMode(Kitchen kitchen) {
        if (kitchen.getServedSocieties() != null && !kitchen.getServedSocieties().isEmpty()) {
            return "ID-backed coverage";
        }
        if (kitchen.getServiceAreas() != null && !kitchen.getServiceAreas().isBlank()) {
            return "Legacy service-areas string";
        }
        if (kitchen.getSociety() != null && !kitchen.getSociety().isBlank()) {
            return "Legacy kitchen society";
        }
        return "No coverage configured";
    }

    private List<String> coverageNames(Kitchen kitchen) {
        if (kitchen.getServedSocieties() != null && !kitchen.getServedSocieties().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Society s : kitchen.getServedSocieties()) {
                if (s != null) names.add(s.getName());
            }
            return names;
        }
        String areas = kitchen.getServiceAreas();
        if (areas != null && !areas.isBlank()) {
            List<String> parts = new ArrayList<>();
            for (String part : areas.split(",")) {
                if (!part.trim().isEmpty()) parts.add(part.trim());
            }
            return parts;
        }
        if (kitchen.getSociety() != null && !kitchen.getSociety().isBlank()) {
            return new ArrayList<>(Collections.singletonList(kitchen.getSociety()));
        }
        return new ArrayList<>();
    }
    /**
     * Factual explanations of the SAME conditions the existing predicates evaluate.
     * Every string corresponds to a condition actually checked in
     * {@link KitchenVisibility}, {@link User#isApprovedSeller()} or the coverage
     * fields - nothing here is a new rule or a guess.
     */
    private List<String> visibilityReasons(Kitchen kitchen, User buyer, boolean paused,
                                           boolean publiclyVisible, boolean serviceAreaVisible,
                                           boolean visible) {
        List<String> reasons = new ArrayList<>();

        if (kitchen.getSeller() == null) {
            reasons.add("The kitchen has no owner, so it cannot be publicly active.");
            reasons.add("Result: this buyer cannot see or order from this kitchen.");
            return reasons;
        }
        if (kitchen.getSeller().getRole() != UserRole.SELLER) {
            reasons.add("The kitchen owner is not a seller account.");
        } else if (!kitchen.getSeller().isApprovedSeller()) {
            reasons.add("The seller is not approved, so the kitchen stays hidden.");
        }
        if (paused) {
            reasons.add("The kitchen is paused (not available today).");
        } else if (!Boolean.TRUE.equals(kitchen.getAvailableToday())) {
            reasons.add("The kitchen is not marked as available today.");
        }

        String mode = coverageMode(kitchen);
        String buyerSocietyName = buyer.getSociety();
        if ((buyerSocietyName == null || buyerSocietyName.isBlank()) && buyer.getSocietyRef() != null) {
            buyerSocietyName = buyer.getSocietyRef().getName();
        }
        boolean buyerHasNoSociety = (buyer.getSociety() == null || buyer.getSociety().isBlank())
                && buyer.getSocietyRef() == null;

        if (buyerHasNoSociety) {
            reasons.add("The buyer has not selected a Society yet, so service-area eligibility cannot be "
                    + "satisfied. Order placement also requires a complete profile.");
        } else if ("ID-backed coverage".equals(mode)) {
            if (serviceAreaVisible) {
                reasons.add("Coverage match: the buyer's Society is one of the kitchen's served Societies.");
            } else if (buyer.getSocietyRef() == null) {
                reasons.add("The kitchen's coverage is ID-backed, but the buyer's Society is only a free-text "
                        + "value with no master reference to match against.");
            } else {
                reasons.add("Coverage does not include the buyer's Society.");
            }
        } else if ("Legacy service-areas string".equals(mode)) {
            if (serviceAreaVisible) {
                reasons.add("Legacy coverage match: the buyer's Society appears in the kitchen's "
                        + "service-areas string.");
            } else {
                reasons.add("The kitchen's legacy service-areas string does not include the buyer's Society.");
            }
        } else if ("Legacy kitchen society".equals(mode)) {
            if (serviceAreaVisible) {
                reasons.add("Legacy coverage match: the buyer's Society equals the kitchen's society.");
            } else {
                reasons.add("The kitchen's legacy society (\"" + kitchen.getSociety()
                        + "\") does not match the buyer's Society (\"" + buyerSocietyName + "\").");
            }
        } else {
            reasons.add("The kitchen has no coverage configured. Anonymous visitors can still browse it, "
                    + "but no Society restricts it.");
        }

        reasons.add(visible
                ? "Result: this buyer can see and order from this kitchen."
                : "Result: this buyer cannot see or order from this kitchen.");
        return reasons;
    }
    // ==================== System health ====================

    /**
     * Lightweight, factual runtime status for the Admin System Health screen.
     *
     * <p>Only values the running application can actually report: the active Spring
     * profiles, whether demo login is on (via the very helper the security config
     * itself uses, so it cannot disagree with the real gate), and a genuine round trip
     * through JPA proving the database is reachable. Nothing is simulated.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> systemHealth() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("application", environment == null ? null : environment.getProperty("spring.application.name"));

        List<String> profiles = (environment == null || environment.getActiveProfiles() == null
                || environment.getActiveProfiles().length == 0)
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(environment.getActiveProfiles()));
        if (profiles.isEmpty()) profiles = new ArrayList<>(Collections.singletonList("default"));
        out.put("activeProfiles", profiles);

        out.put("demoLoginEnabled", environment != null
                && com.example.my_first_spring_api.SecurityConfig.isDemoEnvironment(environment));

        Map<String, Object> database = new LinkedHashMap<>();
        try {
            database.put("reachable", true);
            database.put("users", userRepository.count());
            database.put("kitchens", kitchenRepository.count());
            database.put("orders", orderRepository.count());
        } catch (RuntimeException ex) {
            database.put("reachable", false);
            database.put("error", ex.getMessage());
        }
        out.put("database", database);

        Map<String, Object> master = new LinkedHashMap<>();
        try {
            long areas = 0;
            long societies = 0;
            for (Area area : locationService.findAllAreas()) {
                areas++;
                societies += locationService.findAllSocieties(area.getId()).size();
            }
            master.put("areas", areas);
            master.put("societies", societies);
        } catch (RuntimeException ex) {
            master.put("areas", 0);
            master.put("societies", 0);
        }
        out.put("locationMaster", master);
        return out;
    }

    private Map<String, Object> areaRow(Area area) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", area.getId());
        m.put("name", area.getName());
        m.put("active", area.isActive());
        m.put("createdAt", area.getCreatedAt());
        m.put("updatedAt", area.getUpdatedAt());
        m.put("societyCount", locationService.findAllSocieties(area.getId()).size());
        return m;
    }

    private Map<String, Object> societyRow(Society society) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", society.getId());
        m.put("name", society.getName());
        m.put("active", society.isActive());
        m.put("areaId", society.getArea().getId());
        m.put("areaName", society.getArea().getName());
        m.put("createdAt", society.getCreatedAt());
        m.put("updatedAt", society.getUpdatedAt());
        return m;
    }
}

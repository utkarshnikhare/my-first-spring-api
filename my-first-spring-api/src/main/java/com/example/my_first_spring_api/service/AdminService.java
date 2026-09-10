package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
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

    @Autowired
    public AdminService(UserRepository userRepository, AnalyticsService analyticsService,
                        OrderRepository orderRepository, ProductRepository productRepository,
                        KitchenRepository kitchenRepository, EnquiryRepository enquiryRepository,
                        FavouriteRepository favouriteRepository) {
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.kitchenRepository = kitchenRepository;
        this.enquiryRepository = enquiryRepository;
        this.favouriteRepository = favouriteRepository;
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

        long totalEnquiries = allEnquiries.size();
        long openEnquiries = allEnquiries.stream().filter(e -> e.getStatus() == EnquiryStatus.WAITING_FOR_RESPONSE).count();
        long resolvedEnquiries = allEnquiries.stream().filter(e -> e.getStatus() == EnquiryStatus.SELLER_RESPONDED).count();

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
        List<Kitchen> all = kitchenRepository.findAll();
        return all.stream().map(k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.getId());
            m.put("name", k.getName());
            m.put("displayName", k.getDisplayName());
            m.put("sellerId", k.getSeller() != null ? k.getSeller().getId() : null);
            m.put("sellerName", k.getSeller() != null ? k.getSeller().getName() : null);
            m.put("society", k.getSociety());
            m.put("building", k.getBuilding());
            m.put("area", k.getSociety());
            m.put("serviceAreas", k.getServiceAreas());
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
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        kitchen.setServiceAreas(serviceAreas);
        kitchenRepository.save(kitchen);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", kitchen.getId());
        out.put("name", kitchen.getName());
        out.put("displayName", kitchen.getDisplayName());
        out.put("serviceAreas", kitchen.getServiceAreas());
        out.put("society", kitchen.getSociety());
        return out;
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
            m.put("category", p.getCategory() != null ? p.getCategory().name() : null);
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
    public Map<String, Object> orderDetail(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
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

    @Transactional
    public User createAdmin(String name, String mobileNumber) {
        User user = userRepository.findByMobileNumber(mobileNumber).orElse(null);
        if (user == null) {
            user = new User(name == null || name.isBlank() ? "Admin" : name, mobileNumber, null, UserRole.ADMIN);
        } else if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("This account is already a Super Admin.");
        } else {
            user.setRole(UserRole.ADMIN);
            if (name != null && !name.isBlank()) user.setName(name);
        }
        return userRepository.save(user);
    }

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
}

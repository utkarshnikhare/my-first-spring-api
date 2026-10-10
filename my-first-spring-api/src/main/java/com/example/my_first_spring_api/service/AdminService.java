package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
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
    private final AdminAuditService auditService;
    private final RetentionService retentionService;
    // Window-scoped traffic counters for the dashboard. Reads the SAME event
    // table the analytics screen already uses - no second analytics store.
    private final com.example.my_first_spring_api.repository.AnalyticsEventRepository analyticsEventRepository;
    private final org.springframework.core.env.Environment environment;

    @Autowired
    public AdminService(UserRepository userRepository, AnalyticsService analyticsService,
                        OrderRepository orderRepository, ProductRepository productRepository,
                        KitchenRepository kitchenRepository, EnquiryRepository enquiryRepository,
                        FavouriteRepository favouriteRepository, SocietyDirectory societyDirectory,
                        LocationService locationService,
                        AdminAuditService auditService,
                        RetentionService retentionService,
                        com.example.my_first_spring_api.repository.AnalyticsEventRepository analyticsEventRepository,
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
        this.auditService = auditService;
        this.retentionService = retentionService;
        this.analyticsEventRepository = analyticsEventRepository;
        this.environment = environment;
    }

    // ==================== Dashboard ====================

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        return dashboard(null);
    }

    /**
     * Dashboard with an optional reporting window.
     *
     * <p>Handover 4/17 ask for a date selector (Today / Last 5 Days / Custom).
     * The window is resolved HERE rather than in the browser so the traffic,
     * order-count and recorded-value cards are always computed over exactly the
     * same set of orders and can never disagree with each other.
     *
     * <p>{@code date} accepts {@code today}, {@code last5} or an explicit
     * {@code yyyy-MM-dd}. An unrecognised value falls back to today rather than
     * erroring, so a mistyped filter can never blank the operator's dashboard.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(String date) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = resolveDashboardWindowStart(date, today);
        LocalDateTime startOfToday = today.atStartOfDay();
        // The window is a HALF-OPEN [start, end) range. "Today" and an explicit
        // custom date cover exactly one day; "Last 5 Days" spans today and the
        // four days before it. Ending at today+1 keeps "today's" orders included
        // whatever time of day the dashboard is opened.
        LocalDateTime rangeStart = windowStart.atStartOfDay();
        LocalDateTime rangeEnd = isLastFiveDays(windowStart, today)
                ? startOfToday.plusDays(1)
                : windowStart.plusDays(1).atStartOfDay();
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
        // Admin V1 "Active Users Today": distinct buyers/sellers that actually
        // placed an order today. Measured from real orders, so it is never a guess.
        out.put("activeBuyersToday", allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfToday))
                .filter(o -> o.getBuyer() != null)
                .map(o -> o.getBuyer().getId()).distinct().count());
        out.put("activeSellersToday", allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(startOfToday))
                .filter(o -> o.getKitchen() != null && o.getKitchen().getSeller() != null)
                .map(o -> o.getKitchen().getSeller().getId()).distinct().count());
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
        // ---- Selected-window figures (handover 4/17 date selector) ----
        // Additive keys: the cards above keep their fixed semantics, so an
        // existing consumer of /dashboard cannot silently change meaning.
        final LocalDateTime from = rangeStart;
        final LocalDateTime to = rangeEnd;
        List<Order> inWindow = allOrders.stream()
                .filter(o -> {
                    LocalDateTime stamp = o.getOrderTime() != null ? o.getOrderTime() : o.getCreatedAt();
                    return stamp != null && !stamp.isBefore(from) && stamp.isBefore(to);
                })
                .toList();
        // Same DRAFT/CANCELLED exclusion as the headline value, so the window
        // figures are directly comparable with the cards above them.
        List<Order> counted = inWindow.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .toList();
        BigDecimal windowValue = counted.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        out.put("selectedPeriod", describeDashboardWindow(windowStart, LocalDate.now()));
        out.put("selectedStartDate", windowStart);
        out.put("ordersInPeriod", counted.size());
        out.put("recordedOrderValueInPeriod", windowValue);
        out.put("buyersInPeriod", counted.stream()
                .filter(o -> o.getBuyer() != null)
                .map(o -> o.getBuyer().getId()).distinct().count());
        out.put("sellersInPeriod", counted.stream()
                .filter(o -> o.getKitchen() != null && o.getKitchen().getSeller() != null)
                .map(o -> o.getKitchen().getSeller().getId()).distinct().count());
        // ---- Traffic in the selected window (handover 4/6) ----
        // Counted from the events the application really records. A storefront or
        // marketplace nobody has opened in the window legitimately reads zero;
        // nothing here is estimated or carried forward from an earlier day.
        out.put("marketplaceViewsInPeriod", analyticsEventRepository
                .countByEventTypeAndCreatedAtAfter(AnalyticsService.EV_MARKETPLACE_VIEW, from));
        out.put("storefrontViewsInPeriod", analyticsEventRepository.countByEventTypeAndCreatedAtAfter(
                AnalyticsService.EV_HOMEMADE_STOREFRONT_VIEW, from));
        out.put("offeringViewsInPeriod", analyticsEventRepository.countByEventTypeAndCreatedAtAfter(
                AnalyticsService.EV_PRODUCT_VIEW, from));
        out.put("enquiriesInPeriod", allEnquiries.stream()
                .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().isBefore(from))
                .count());
        // "Attention Needed": how many operational items are open right now. One
        // count for the card, computed from the SAME attention model the panel
        // below renders, so the card and the panel can never disagree.
        out.put("attentionNeeded", attentionItems().size());
        return out;
    }

    /**
     * Resolves the dashboard window to its inclusive start date.
     *
     * <p>{@code today} -&gt; today, {@code last5} -&gt; today minus 4 days (five
     * calendar days inclusive), an explicit ISO date -&gt; that date. Blank or
     * unparseable input falls back to today.</p>
     */
    static LocalDate resolveDashboardWindowStart(String date, LocalDate today) {
        if (date == null || date.isBlank()) return today;
        String v = date.trim().toLowerCase();
        if ("last5".equals(v) || "last5days".equals(v) || "last_5_days".equals(v)) {
            return today.minusDays(4);
        }
        if ("today".equals(v)) return today;
        try {
            LocalDate parsed = LocalDate.parse(date.trim());
            // A future date has no data yet; showing it as an empty window would
            // look like an outage, so clamp to today instead.
            return parsed.isAfter(today) ? today : parsed;
        } catch (RuntimeException ex) {
            return today;
        }
    }

    /** True when the resolved start is the "Last 5 Days" preset. */
    static boolean isLastFiveDays(LocalDate start, LocalDate today) {
        return start.equals(today.minusDays(4));
    }

    /** Human label for the active window, reused by the dashboard header. */
    static String describeDashboardWindow(LocalDate start, LocalDate today) {
        if (start.equals(today)) return "Today";
        if (isLastFiveDays(start, today)) return "Last 5 Days";
        return start.toString();
    }

    private boolean isLiveProduct(Product p) {
        if (p.getAvailableToday() == null || !p.getAvailableToday()) return false;
        if (p.isSoldOut()) return false;
        return true;
    }

    // ==================== Buyers ====================

    /**
     * Admin V1 buyer list: inspection and support only.
     *
     * <p>Adds the location IDs, the buyer's own delivery picture and an honest
     * account status. {@code accountStatus} is derived from real columns - it is
     * NOT a block flag, because the {@link User} model has no blocked/suspended
     * state for buyers. It reports profile completeness, which is genuinely
     * knowable today, rather than inventing a status the domain cannot store.</p>
     */
    /**
     * Admin V1 buyer search (handover 8: "Search by name, mobile, Area, Society
     * or order ID").
     *
     * <p>Every parameter is optional and they compose with AND. {@code search}
     * matches name / mobile / society / building AND - importantly - the
     * buyer's ORDER ID or order number, so support can start from an order
     * reference in a ticket and land on the right buyer. That order-number
     * match is the only new capability here; the rest reuses the same fields
     * the unfiltered list already returns.
     *
     * <p>Area/Society narrow on the buyer's STABLE references, matching how the
     * Orders screen resolves location, never on the free-text strings.</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buyers(String search, Long areaId, Long societyId, User actingAdmin) {
        List<User> all = userRepository.findByRole(UserRole.BUYER);
        String term = search == null ? null : search.trim().toLowerCase();
        // Handover 15: an Area Admin's own Area overrides anything the browser sent.
        Long scope = adminAreaScope(actingAdmin);
        final Long effectiveArea = scope != null ? scope : areaId;
        final Long effectiveSociety = societyId;
        boolean filtering = (term != null && !term.isEmpty())
                || effectiveArea != null || effectiveSociety != null;

        // Order-number matching is only needed when an order-like term was typed.
        // Building the set up front would scan every order for every keystroke
        // even when the operator is filtering purely by location.
        Set<Long> buyersMatchingOrderTerm = null;
        if (term != null && !term.isEmpty()) {
            buyersMatchingOrderTerm = new HashSet<>();
            for (Order o : orderRepository.findAll()) {
                if (o.getBuyer() == null || o.getBuyer().getId() == null) continue;
                if (orderNumberMatches(o, term) || String.valueOf(o.getId()).equals(term)) {
                    buyersMatchingOrderTerm.add(o.getBuyer().getId());
                }
            }
        }

        final Set<Long> orderTermMatches = buyersMatchingOrderTerm;
        return all.stream().filter(b -> {
            if (effectiveSociety != null) {
                if (b.getSocietyRef() == null || !effectiveSociety.equals(b.getSocietyRef().getId())) return false;
            }
            // Null-safe, and compared the good way round: a buyer with no Area
            // reference yet must be EXCLUDED by an Area filter, not blow up the
            // whole search for the operator.
            if (effectiveArea != null && !effectiveArea.equals(resolveBuyerAreaId(b))) return false;
            if (term == null || term.isEmpty()) return true;
            if (b.getId() != null && orderTermMatches != null && orderTermMatches.contains(b.getId())) return true;
            return textMatches(term, b.getName(), b.getMobileNumber(), b.getSociety(), b.getBuilding());
        }).map(this::buyerRow).collect(Collectors.toList());
    }

    /**
     * Unfiltered buyer list. Delegates to {@link #buyers(String, Long, Long, User)}
     * with no criteria so the two paths can never drift apart.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buyers() {
        return buyers(null, null, null, null);
    }

    /**
     * Unscoped buyer search.
     *
     * <p>Present for callers that legitimately have no Admin identity to scope by
     * (internal services and tests). Anything acting ON BEHALF OF AN ADMIN must use
     * the four-argument form with the acting Admin, or the handover 15 Area scope
     * would be silently bypassed.</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buyers(String search, Long areaId, Long societyId) {
        return buyers(search, areaId, societyId, null);
    }

    private static boolean orderNumberMatches(Order o, String term) {
        String number = o.getOrderNumber();
        return number != null && number.toLowerCase().contains(term);
    }

    private static boolean textMatches(String term, String... values) {
        for (String v : values) {
            if (v != null && v.toLowerCase().contains(term)) return true;
        }
        return false;
    }

    /**
     * One buyer row, shared by the unfiltered list and the searched list.
     *
     * <p>All figures are derived from the buyer's own orders, so a search
     * result carries exactly the same data as the unfiltered list - searching
     * never degrades or hides a field.</p>
     */
    private Map<String, Object> buyerRow(User b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("mobileNumber", b.getMobileNumber());
        m.put("society", b.getSociety());
        m.put("societyId", b.getSocietyRef() != null ? b.getSocietyRef().getId() : null);
        m.put("area", b.getArea());
        m.put("areaId", resolveBuyerAreaId(b));
        m.put("building", b.getBuilding());
        m.put("flatHouseNumber", b.getFlatHouseNumber());
        List<Order> orders = orderRepository.findByBuyerOrderByCreatedAtDesc(b);
        m.put("orderCount", orders.size());
        BigDecimal total = orders.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("totalOrderValue", total);
        // Support picture: payment and delivery split, read from the same
        // shared Order fields the seller and buyer screens use.
        m.put("deliveredCount", orders.stream().filter(Order::isDelivered).count());
        m.put("activeOrderCount", orders.stream().filter(Order::isActiveForDelivery).count());
        m.put("paidCount", orders.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).count());
        m.put("accountStatus", b.getSocietyRef() != null ? "PROFILE_COMPLETE" : "PROFILE_INCOMPLETE");
        // Handover 8: the Buyers list needs the account state so the console can
        // offer Block / Unblock on the right rows (blockedAt/reason feed the
        // tooltip and the Buyer detail header).
        m.put("blocked", b.isBlocked());
        m.put("blockedReason", b.getBlockedReason());
        m.put("blockedAt", b.getBlockedAt());
        m.put("favouriteKitchens", favouriteRepository.countByUser(b));
        m.put("createdAt", b.getCreatedAt());
        return m;
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
                // Handover 7.3: the Admin Pause/Resume controls need the current
                // storefront state on the row itself, or the console would offer
                // "Pause" on an already-paused storefront.
                m.put("paused", k.isStorefrontPaused());
                m.put("removed", k.getSeller() != null && k.getSeller().isStorefrontRemoved());
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

    /**
     * The single source of truth for "which orders match this filter".
     *
     * <p>Both the map-returning {@link #orders(OrderFilter)} and the
     * entity-level analytics readers run through this same predicate chain, so
     * a seller-analytics figure can never describe a different set of orders
     * than the Orders screen it sits next to.
     */
    private List<Order> matchingOrders(OrderFilter requested) {
        List<Order> all = orderRepository.findAll();
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        final OrderFilter f = requested != null ? requested : new OrderFilter();
        return all.stream()
                .filter(o -> {
                    if ("last3days".equals(f.legacyFilter)) {
                        return o.getCreatedAt() != null && o.getCreatedAt().isAfter(threeDaysAgo);
                    }
                    return true;
                })
                .filter(o -> matchesOrderDate(o, f.date))
                .filter(o -> matchesBuyerLocation(o, f.areaId, f.societyId))
                .filter(o -> f.sellerId == null || (o.getKitchen() != null && o.getKitchen().getSeller() != null
                        && f.sellerId.equals(o.getKitchen().getSeller().getId())))
                .filter(o -> f.buyerId == null || (o.getBuyer() != null && f.buyerId.equals(o.getBuyer().getId())))
                .filter(o -> matchesCategory(o, f.category))
                .filter(o -> matchesPayment(o, f.payment))
                .filter(o -> matchesDelivery(o, f.delivery))
                .filter(o -> matchesOrderStatus(o, f.status))
                .filter(o -> {
                    if (f.search == null || f.search.isBlank()) return true;
                    String s = f.search.toLowerCase();
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
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> orders(OrderFilter requested) {
        return matchingOrders(requested).stream().map(this::adminOrderRow).collect(Collectors.toList());
    }

    /**
     * Back-compatible overload for the pre-V1 caller.
     *
     * <p>The two-argument signature is kept so nothing else that reads Admin orders
     * has to change; it simply builds a filter with no axis set.</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> orders(String legacyFilter, String search) {
        OrderFilter f = new OrderFilter();
        f.legacyFilter = legacyFilter;
        f.search = search;
        return orders(f);
    }

    // ==================== Admin V1 Exports ====================

    /**
     * Builds a CSV document for the Admin V1 Exports screen.
     *
     * <p>Deliberately the smallest useful V1 export: plain RFC-4180 CSV built by
     * hand from data the Admin list endpoints already return. No new dependency,
     * no reporting framework, no scheduled job - the operator presses Download
     * and the current, filtered rows are serialised.</p>
     *
     * <p>Every document carries a generated-at timestamp and a header row, and it
     * respects the same {@link OrderFilter} the Orders screen uses, so an export
     * can never quietly contain more than the operator is looking at.</p>
     *
     * @param domain one of orders | sellers | buyers | analytics
     * @return the CSV text; never null
     */
    @Transactional(readOnly = true)
    public String exportCsv(String domain, OrderFilter filter) {
        String safeDomain = domain == null ? "orders" : domain.trim().toLowerCase();
        // An unknown domain must be an error, not a silent fallback to Orders.
        // Previously "kitchens.csv" and "bogus.csv" both returned the Orders
        // export under their own filename, so an operator could believe they had
        // downloaded kitchens and actually hold order records (incl. buyer
        // mobiles).
        if (!EXPORT_DOMAINS.contains(safeDomain)) {
            throw new IllegalArgumentException("Unknown export '" + safeDomain
                    + "'. Supported: " + String.join(", ", EXPORT_DOMAINS) + ".");
        }
        StringBuilder out = new StringBuilder();
        switch (safeDomain) {
            case "sellers" -> sellersCsv(out, filter);
            case "buyers" -> buyersCsv(out, filter);
            case "analytics" -> analyticsCsv(out, filter);
            default -> ordersCsv(out, filter);
        }
        return out.toString();
    }

    /** Domains the export screen may request. */
    public static final Set<String> EXPORT_DOMAINS =
            Set.of("orders", "sellers", "buyers", "analytics");

    /** Prepends the generated-at banner every Admin export carries. */
    private void csvBanner(StringBuilder out, String title, OrderFilter filter) {
        out.append("# ").append(title).append('\n');
        out.append("# Generated at ").append(LocalDateTime.now()).append('\n');
        if (filter != null) {
            // Echo the active filters so a downloaded file is self-describing and
            // can never be mistaken for "all rows" when a filter was applied.
            out.append("# Filters applied:");
            if (notBlank(filter.date)) out.append(" date=").append(filter.date);
            if (notBlank(filter.category)) out.append(" category=").append(filter.category);
            if (notBlank(filter.payment)) out.append(" payment=").append(filter.payment);
            if (notBlank(filter.delivery)) out.append(" delivery=").append(filter.delivery);
            if (notBlank(filter.status)) out.append(" status=").append(filter.status);
            if (filter.areaId != null) out.append(" areaId=").append(filter.areaId);
            if (filter.societyId != null) out.append(" societyId=").append(filter.societyId);
            if (filter.sellerId != null) out.append(" sellerId=").append(filter.sellerId);
            if (filter.buyerId != null) out.append(" buyerId=").append(filter.buyerId);
            if (notBlank(filter.search)) out.append(" search=").append(filter.search);
            out.append('\n');
        }
        out.append('\n');
    }

    /**
     * RFC-4180 escaping: quote when the value contains a comma, quote or newline.
     *
     * <p>Also neutralises CSV/formula injection (OWASP CSV Injection). Buyer,
     * seller and offering names are user-supplied, so a name such as
     * {@code =cmd|...} would otherwise execute when an operator opens the
     * downloaded file in Excel or Sheets. A leading {@code = + - @} (and the
     * control characters Excel strips before evaluating) are prefixed with a
     * single quote, which spreadsheets render as literal text.
     */
    private static String csv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        s = neutraliseFormula(s);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /** True when a CSV field would be interpreted as a formula by a spreadsheet. */
    static boolean isFormulaLike(String s) {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return true;
        }
        // Excel ignores leading control characters when evaluating a cell.
        return first < 0x20 && s.length() > 1 && isFormulaLike(s.substring(1));
    }

    private static String neutraliseFormula(String s) {
        return isFormulaLike(s) ? "'" + s : s;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : List.of();
    }

    private void ordersCsv(StringBuilder out, OrderFilter filter) {
        csvBanner(out, "SocioMart Admin V1 - Orders export", filter);
        out.append("Order ID,Order Number,Placed At,Buyer,Buyer Mobile,Seller,Storefront,Category,Area,Society,Building,Flat,Items,Quantity,Recorded Order Value,Payment,Delivery,Delivered At,Order Status\n");
        for (Map<String, Object> row : orders(filter)) {
            StringBuilder names = new StringBuilder();
            for (Map<String, Object> it : castList(row.get("items"))) {
                if (names.length() > 0) names.append("; ");
                names.append(it.get("productName")).append(" x").append(it.get("quantity"));
            }
            out.append(csv(row.get("id"))).append(',').append(csv(row.get("orderNumber"))).append(',')
               .append(csv(row.get("orderTime"))).append(',').append(csv(row.get("buyerName"))).append(',')
               .append(csv(row.get("buyerMobile"))).append(',').append(csv(row.get("sellerName"))).append(',')
               .append(csv(row.get("kitchenName"))).append(',').append(csv(row.get("category"))).append(',')
               .append(csv(row.get("area"))).append(',').append(csv(row.get("society"))).append(',')
               .append(csv(row.get("building"))).append(',').append(csv(row.get("flatHouseNumber"))).append(',')
               .append(csv(names.toString())).append(',').append(csv(row.get("totalQuantity"))).append(',')
               .append(csv(row.get("totalAmount"))).append(',').append(csv(row.get("paymentStatus"))).append(',')
               .append(csv(row.get("deliveryStatus"))).append(',').append(csv(row.get("deliveredAt"))).append(',')
               .append(csv(row.get("orderStatus"))).append('\n');
        }
    }

    private void sellersCsv(StringBuilder out, OrderFilter filter) {
        csvBanner(out, "SocioMart Admin V1 - Sellers export", filter);
        out.append("Seller ID,Name,Mobile,Approval Status,Status Reason,Approved At,Registered At,Storefronts,Orders,Recorded Order Value\n");
        // Handover 12.1: an export must respect the operator's active filters and
        // date range. Seller rows are kept when they match the seller/location
        // axes, but their order figures come from the FILTERED order set - so a
        // narrowed export can never carry a wider total than the screen shows.
        for (User s : userRepository.findByRole(UserRole.SELLER)) {
            if (!sellerMatchesFilter(s, filter)) continue;
            List<Order> sellerOrders = matchingOrders(filter).stream()
                    .filter(o -> o.getKitchen() != null && o.getKitchen().getSeller() != null
                            && o.getKitchen().getSeller().getId().equals(s.getId()))
                    .toList();
            out.append(csv(s.getId())).append(',').append(csv(s.getName())).append(',')
               .append(csv(s.getMobileNumber())).append(',').append(csv(s.getSellerApprovalStatus())).append(',')
               .append(csv(s.getSellerStatusReason())).append(',').append(csv(s.getApprovedAt())).append(',')
               .append(csv(s.getCreatedAt())).append(',').append(csv(kitchenRepository.findBySeller(s).size())).append(',')
               .append(csv(sellerOrders.size())).append(',')
               .append(csv(sumRecordedValue(sellerOrders))).append('\n');
        }
    }

    /**
     * True when a seller is inside the export's scope.
     *
     * <p>Only the axes that identify a SELLER narrow the row list: an explicit
     * seller id, or a seller whose storefront serves the chosen Area/Society.
     * Date, payment, delivery and order status describe ORDERS, so they shape
     * each seller's figures instead of deciding whether the seller appears.</p>
     */
    private boolean sellerMatchesFilter(User seller, OrderFilter filter) {
        if (filter == null) return true;
        if (filter.sellerId != null) return filter.sellerId.equals(seller.getId());
        if (filter.areaId == null && filter.societyId == null) return true;
        for (Kitchen k : kitchenRepository.findBySeller(seller)) {
            if (filter.societyId != null && servesSociety(k, filter.societyId)) return true;
            if (filter.areaId != null && servesAnySocietyInArea(k, filter.areaId)) return true;
        }
        return false;
    }

    /** True when the storefront serves any Society belonging to this Area. */
    private boolean servesAnySocietyInArea(Kitchen k, Long areaId) {
        if (k.getServedSocieties() == null) return false;
        for (Society s : k.getServedSocieties()) {
            if (s != null && s.getArea() != null && areaId.equals(s.getArea().getId())) return true;
        }
        return false;
    }

    /** Sum of non-draft, non-cancelled order totals for one seller's kitchens. */
    private BigDecimal sellerOrderValue(User seller) {
        BigDecimal total = BigDecimal.ZERO;
        for (Kitchen k : kitchenRepository.findBySeller(seller)) {
            for (Order o : orderRepository.findByKitchenOrderByCreatedAtDesc(k)) {
                if (o.getOrderStatus() == OrderStatus.DRAFT || o.getOrderStatus() == OrderStatus.CANCELLED) continue;
                if (o.getTotalAmount() != null) total = total.add(o.getTotalAmount());
            }
        }
        return total;
    }

    private void buyersCsv(StringBuilder out, OrderFilter filter) {
        csvBanner(out, "SocioMart Admin V1 - Buyers export", filter);
        out.append("Buyer ID,Name,Mobile,Area,Society,Building,Flat,Orders,Recorded Order Value\n");
        // Reuses the same search the Buyers screen applies, so the export honours
        // the name/mobile/society/order-id term as well as Area and Society.
        for (Map<String, Object> b : buyers(filter != null ? filter.search : null,
                filter != null ? filter.areaId : null,
                filter != null ? filter.societyId : null, null)) {
            Long buyerId = (Long) b.get("id");
            List<Order> buyerOrders = matchingOrders(filter).stream()
                    .filter(o -> o.getBuyer() != null && o.getBuyer().getId().equals(buyerId))
                    .toList();
            out.append(csv(b.get("id"))).append(',').append(csv(b.get("name"))).append(',')
               .append(csv(b.get("mobileNumber"))).append(',').append(csv(b.get("area"))).append(',')
               .append(csv(b.get("society"))).append(',').append(csv(b.get("building"))).append(',')
               .append(csv(b.get("flatHouseNumber"))).append(',').append(csv(buyerOrders.size())).append(',')
               .append(csv(sumRecordedValue(buyerOrders))).append('\n');
        }
    }

    /** Recorded Order Value of an already-filtered order set. */
    private static BigDecimal sumRecordedValue(List<Order> orders) {
        BigDecimal total = BigDecimal.ZERO;
        for (Order o : orders) {
            if (o.getOrderStatus() == OrderStatus.DRAFT || o.getOrderStatus() == OrderStatus.CANCELLED) continue;
            if (o.getTotalAmount() != null) total = total.add(o.getTotalAmount());
        }
        return total;
    }

    /** Aggregate marketplace figures - the same counters the dashboard shows. */
    private void analyticsCsv(StringBuilder out, OrderFilter filter) {
        csvBanner(out, "SocioMart Admin V1 - Analytics export", filter);
        Map<String, Object> d = dashboard(filter != null ? filter.date : null);
        out.append("Metric,Value\n");
        // The selected-window figures lead the file, so the export answers "what
        // happened in the period the operator chose" before the all-time totals.
        for (String key : List.of("selectedPeriod", "ordersInPeriod", "recordedOrderValueInPeriod",
                "totalBuyers", "totalSellers", "approvedSellers", "pendingSellers",
                "suspendedSellers", "totalOrders", "ordersToday", "ordersThisMonth",
                "totalOrderValue", "todayOrderValue", "monthOrderValue",
                "paidCount", "pendingPaymentCount", "willPayLaterCount",
                "ordersAwaitingSellerConfirmation", "ordersInFulfilment", "ordersFulfilled",
                "ordersCancelled", "ordersDraft")) {
            out.append(csv(key)).append(',').append(csv(d.get(key))).append('\n');
        }
    }

    // ==================== Retention purge (handover 11) ====================

    /**
     * Read-only preview of the destructive purge, so the Admin can see the exact
     * server-calculated count before committing to it.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> retentionPreview() {
        Map<String, Object> m = new LinkedHashMap<>(retentionService.retentionStatus());
        m.put("purgeCandidateCount", retentionService.purgeCandidates().size());
        return m;
    }

    /** Turns the destructive purge on or off. Default is off. */
    @Transactional
    public Map<String, Object> setRetentionPurgeEnabled(boolean enabled, User actingAdmin) {
        retentionService.setPurgeEnabled(enabled);
        auditService.record("RETENTION_PURGE_ENABLED", actingAdmin, "PLATFORM", null,
                "retention purge", String.valueOf(!enabled), String.valueOf(enabled),
                "Retention purge switch changed");
        return retentionPreview();
    }

    /**
     * Purges closed detailed orders older than the retention window (handover 11).
     *
     * <p>All safety guards live in {@link RetentionService}; this method adds the
     * audit record, because a purge is one of the most consequential things an
     * Admin can do and must never be an unattributable action.</p>
     */
    @Transactional
    public Map<String, Object> purgeRetention(boolean confirmed, boolean exported,
                                             String reason, User actingAdmin) {
        // Captured before the purge, so the audit entry records what the operator
        // was told would be removed, not what happens to be left afterwards.
        int before = retentionService.purgeCandidates().size();
        Map<String, Object> result =
                retentionService.purgeClosedOrdersPastRetention(confirmed, exported, reason);
        auditService.record("RETENTION_PURGE", actingAdmin, "PLATFORM", null,
                "retention purge", String.valueOf(before), "0", reason);
        return result;
    }

    // ==================== Admin role scope (handover 15) ====================

    /**
     * The Area this admin is responsible for, or {@code null} for global scope.
     *
     * <p>SUPER_ADMIN is always global: the handover gives them "all Areas". A
     * plain ADMIN is scoped only when a Super Admin has explicitly assigned one;
     * an unassigned ADMIN behaves exactly as before, so introducing the concept
     * can never silently narrow anyone's access.</p>
     *
     * <p>Returned as the AUTHORITY's scope and applied on the server, so the
     * boundary holds no matter what the browser sends.</p>
     */
    @Transactional(readOnly = true)
    public Long adminAreaScope(User actingAdmin) {
        if (actingAdmin == null) return null;
        if (actingAdmin.getRole() != UserRole.ADMIN) return null; // SUPER_ADMIN/global
        return actingAdmin.getAdminAreaId();
    }

    /**
     * Pins a read filter to the acting admin's Area.
     *
     * <p>An Area Admin's own Area always WINS over a filter the browser sent: a
     * crafted request for another Area cannot widen the scope, and an explicit
     * request for a different Area is corrected to the admin's Area rather than
     * silently returning nothing.</p>
     */
    private OrderFilter scoped(OrderFilter filter, User actingAdmin) {
        Long scope = adminAreaScope(actingAdmin);
        if (scope == null) return filter;
        OrderFilter f = filter != null ? filter : new OrderFilter();
        f.areaId = scope;
        return f;
    }

    /** Throws unless this admin may act on a record in {@code areaId}. */
    private void requireAreaAccess(Long areaId, User actingAdmin) {
        Long scope = adminAreaScope(actingAdmin);
        if (scope == null) return; // global
        if (areaId == null || !scope.equals(areaId)) {
            throw new SellerNotAuthorizedException("This record is outside your assigned Area.");
        }
    }

    /** The Area a seller operates in, or null when it cannot be determined. */
    private Long sellerAreaId(User seller) {
        for (Kitchen k : kitchenRepository.findBySeller(seller)) {
            if (k.getServedSocieties() != null) {
                for (Society soc : k.getServedSocieties()) {
                    if (soc.getArea() != null) return soc.getArea().getId();
                }
            }
        }
        return null;
    }

    /** An Area Admin may only act on sellers inside their own Area. */
    private void requireSellerAccess(User seller, User actingAdmin) {
        Long scope = adminAreaScope(actingAdmin);
        if (scope == null) return;
        Long sellerArea = sellerAreaId(seller);
        if (sellerArea == null || !scope.equals(sellerArea)) {
            throw new SellerNotAuthorizedException("This seller is outside your assigned Area.");
        }
    }

    /** An Area Admin may only act on buyers inside their own Area. */
    private void requireBuyerAccess(User buyer, User actingAdmin) {
        Long scope = adminAreaScope(actingAdmin);
        if (scope == null) return;
        if (!scope.equals(resolveBuyerAreaId(buyer))) {
            throw new SellerNotAuthorizedException("This buyer is outside your assigned Area.");
        }
    }

    /** The Area a storefront serves, or null when it serves none. */
    private Long storefrontAreaId(Kitchen k) {
        if (k == null || k.getServedSocieties() == null) return null;
        for (Society s : k.getServedSocieties()) {
            if (s != null && s.getArea() != null) return s.getArea().getId();
        }
        return null;
    }

    // ==================== Attention / Pending actions ====================

    /**
     * ONE compact operational panel for the Admin V1 dashboard.
     *
     * <p>Every item is derived from state that genuinely exists in the database and
     * links to the Admin screen that resolves it. Nothing invents a new status:
     * pending approvals, suspended sellers and unpaid orders are read from the
     * same fields the rest of the Admin already reads.</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> attentionItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        long pending = userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.PENDING);
        if (pending > 0) items.add(attention("Seller applications awaiting approval", pending, "#/approvals"));
        long changes = userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER,
                SellerApprovalStatus.CHANGES_REQUESTED);
        if (changes > 0) {
            items.add(attention("Sellers waiting on requested changes", changes, "#/sellers"));
        }
        long suspended = userRepository.countByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.SUSPENDED);
        if (suspended > 0) items.add(attention("Suspended sellers", suspended, "#/sellers"));
        // Handover 13 lists "Seller/storefront suspended OR PAUSED by Admin" and
        // "Buyer blocked" explicitly. Both are read from the same columns the
        // control screens write, so the panel can never disagree with them.
        long pausedStorefronts = kitchenRepository.findAll().stream()
                .filter(Kitchen::isStorefrontPaused).count();
        if (pausedStorefronts > 0) {
            items.add(attention("Paused storefronts", pausedStorefronts, "#/sellers"));
        }
        long blockedBuyers = userRepository.findByRole(UserRole.BUYER).stream()
                .filter(User::isBlocked).count();
        if (blockedBuyers > 0) {
            items.add(attention("Blocked buyers", blockedBuyers, "#/buyers"));
        }
        // Handover 13 also requires "unresolved support flags / complaints".
        // The internal support note IS the flag in this model, so an account that
        // still carries one is an open case someone has to close. Counted across
        // buyers and sellers, and linked to the screen where the note is read and
        // cleared - no invented alert, just the notes the Admin has actually written.
        long openSupportCases = userRepository.findByRole(UserRole.BUYER).stream()
                .filter(u -> trimToNull(u.getSupportNote()) != null).count()
                + userRepository.findByRole(UserRole.SELLER).stream()
                .filter(u -> trimToNull(u.getSupportNote()) != null).count();
        if (openSupportCases > 0) {
            items.add(attention("Accounts with an unresolved support note", openSupportCases, "#/buyers"));
        }
        long awaiting = countOrders(OrderStatus.ORDERED);
        if (awaiting > 0) items.add(attention("Orders not yet confirmed by sellers", awaiting, "#/orders"));
        long unpaid = countOrders(PaymentStatus.PENDING) + countOrders(PaymentStatus.WILL_PAY_LATER);
        if (unpaid > 0) items.add(attention("Orders with payment still pending", unpaid, "#/orders"));
        return items;
    }

    /**
     * Counts orders by one status or payment state.
     *
     * <p>Derived from the same {@code findAll()} the rest of the Admin console
     * already uses, so this adds no new repository query and no new persistence
     * surface. {@code status} and {@code payment} are alternative ways of asking
     * the same question, so exactly one is supplied.</p>
     */
    private long countOrders(OrderStatus status) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getOrderStatus() == status)
                .count();
    }

    private long countOrders(PaymentStatus payment) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getPaymentStatus() == payment)
                .count();
    }

    private static Map<String, Object> attention(String label, long count, String hash) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("count", count);
        m.put("hash", hash);
        return m;
    }

    /**
     * Admin V1 order monitoring filters.
     *
     * <p>Every axis is optional and they compose with AND. Blank / null means
     * "no constraint", so an absent filter never narrows the result set. These
     * filters are strictly read-only: none of them can mutate an order, its
     * delivery state or its payment state.</p>
     */
    public static class OrderFilter {
        public String date;         // yyyy-MM-dd, matched on the order's own day
        public Long areaId;         // stable Area id
        public Long societyId;      // stable Society id
        public Long sellerId;
        public Long buyerId;
        public String category;     // KITCHEN | HOMEMADE_PRODUCTS
        public String payment;      // PAID | PENDING | WILL_PAY_LATER
        public String delivery;     // delivered | not_delivered
        public String status;       // OrderStatus name
        public String search;
        public String legacyFilter; // "last3days", kept for the pre-V1 caller
    }

    private boolean matchesOrderDate(Order o, String date) {
        if (date == null || date.isBlank()) return true;
        LocalDate day;
        try {
            day = LocalDate.parse(date.trim());
        } catch (RuntimeException ex) {
            // An unparseable date imposes no constraint rather than hiding every row.
            return true;
        }
        LocalDateTime stamp = o.getOrderTime() != null ? o.getOrderTime() : o.getCreatedAt();
        return stamp != null && stamp.toLocalDate().equals(day);
    }

    /**
     * Area/Society match on the buyer's STABLE references, never on free text.
     *
     * <p>The denormalised {@code User.society} / {@code User.area} strings remain
     * the display value, but identity is resolved through {@code societyRef} /
     * {@code areaRef} - the same rule seller coverage and buyer eligibility
     * already use. A legacy profile that only ever had the free-text string
     * therefore never matches an id filter, which is the honest answer: there is
     * no master record to compare against.</p>
     */
    private boolean matchesBuyerLocation(Order o, Long areaId, Long societyId) {
        if (areaId == null && societyId == null) return true;
        User buyer = o.getBuyer();
        if (buyer == null) return false;
        if (societyId != null) {
            if (buyer.getSocietyRef() == null || !societyId.equals(buyer.getSocietyRef().getId())) return false;
        }
        if (areaId != null) {
            if (buyer.getAreaRef() != null) {
                if (!areaId.equals(buyer.getAreaRef().getId())) return false;
            } else if (buyer.getSocietyRef() != null && buyer.getSocietyRef().getArea() != null) {
                if (!areaId.equals(buyer.getSocietyRef().getArea().getId())) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Category is the SELLER TYPE of the storefront that owns the order's kitchen
     * - the same KITCHEN / HOMEMADE_PRODUCTS distinction the marketplace already
     * uses. No new category concept is introduced.
     */
    private boolean matchesCategory(Order o, String category) {
        if (category == null || category.isBlank()) return true;
        String wanted = category.trim().toUpperCase();
        if ("ALL".equals(wanted)) return true;
        SellerType type = o.getKitchen() != null ? o.getKitchen().getSellerType() : null;
        if (type == null) return false;
        // An unrecognised value imposes NO constraint rather than hiding every row.
        return switch (wanted) {
            case "KITCHEN", "HOMEMADE_PRODUCTS" -> type.name().equals(wanted);
            default -> true;
        };
    }

    private boolean matchesPayment(Order o, String payment) {
        if (payment == null || payment.isBlank()) return true;
        String wanted = payment.trim().toUpperCase();
        if ("ALL".equals(wanted)) return true;
        PaymentStatus ps = o.getPaymentStatus();
        if (ps == null) return false;
        return switch (wanted) {
            case "PAID", "PENDING", "WILL_PAY_LATER" -> ps.name().equals(wanted);
            default -> true; // unknown value: no constraint
        };
    }

    /**
     * Delivery filter. Reads the shared Order flag only - it never derives delivery
     * from payment or order status, and it never writes.
     *
     * <p>Cancelled orders match NEITHER bucket: the Seller drill-down and the
     * Seller progress counters both exclude them, and the Admin view must agree,
     * or the same order would appear in the seller's "remaining" count and the
     * Admin "Delivered" list at the same time.</p>
     */
    private boolean matchesDelivery(Order o, String delivery) {
        if (delivery == null || delivery.isBlank()) return true;
        String d = delivery.trim().toUpperCase();
        if ("ALL".equals(d)) return true;
        if (!o.isActiveForDelivery()) return false;
        boolean delivered = o.isDelivered();
        if ("DELIVERED".equals(d)) return delivered;
        if ("NOT_DELIVERED".equals(d) || "NOT-DELIVERED".equals(d)) return !delivered;
        return true; // unknown value: no constraint
    }

    private boolean matchesOrderStatus(Order o, String status) {
        if (status == null || status.isBlank()) return true;
        String wanted = status.trim().toUpperCase();
        if ("ALL".equals(wanted)) return true;
        OrderStatus os = o.getOrderStatus();
        if (os == null) return false;
        return switch (wanted) {
            case "DRAFT", "ORDERED", "CONFIRMED", "READY", "DELIVERED", "COMPLETED", "CANCELLED"
                    -> os.name().equals(wanted);
            default -> true; // unknown value: no constraint
        };
    }

    /**
     * One Admin Orders row.
     *
     * <p>{@code deliveryStatus} / {@code deliveredAt} / {@code deliveryEditable}
     * are read from the shared Order row through the SAME {@link DeliveryStatus}
     * the Seller tracker writes, so the Admin console can never disagree with the
     * seller's checkbox or the buyer's badge. {@code category} is the storefront's
     * existing seller type, not a new classification.</p>
     */
    private Map<String, Object> adminOrderRow(Order o) {
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
        // Admin V1 monitoring: the seller's own delivery record, read-only.
        m.put("deliveryStatus", o.getEffectiveDeliveryStatus().name());
        m.put("delivered", o.isDelivered());
        m.put("deliveredAt", o.getDeliveredAt());
        m.put("deliveryEditable", o.isActiveForDelivery());
        m.put("category", o.getKitchen() != null && o.getKitchen().getSellerType() != null
                ? o.getKitchen().getSellerType().name() : null);
        m.put("customInstructions", o.getCustomInstructions());
        m.put("createdAt", o.getCreatedAt());
        m.put("orderTime", o.getOrderTime());
        m.put("society", o.getBuyer() != null ? o.getBuyer().getSociety() : null);
        m.put("societyId", o.getBuyer() != null && o.getBuyer().getSocietyRef() != null
                ? o.getBuyer().getSocietyRef().getId() : null);
        m.put("area", o.getBuyer() != null ? o.getBuyer().getArea() : null);
        m.put("areaId", resolveBuyerAreaId(o.getBuyer()));
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
        m.put("itemCount", o.getItems().size());
        m.put("totalQuantity", o.getItems().stream()
                .map(it -> it.getQuantity() == null ? 0 : it.getQuantity())
                .mapToInt(Integer::intValue).sum());
        return m;
    }

    /**
     * The buyer's area, resolved through their own reference or - for a profile
     * that only ever set the society - through that society's parent area.
     */
    private Long resolveBuyerAreaId(User buyer) {
        if (buyer == null) return null;
        if (buyer.getAreaRef() != null) return buyer.getAreaRef().getId();
        if (buyer.getSocietyRef() != null && buyer.getSocietyRef().getArea() != null) {
            return buyer.getSocietyRef().getArea().getId();
        }
        return null;
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
        // Handover 9: the support-facing detail needs the delivery facts and a
        // status/event history, not just the current snapshot.
        m.put("deliveryStatus", order.getEffectiveDeliveryStatus().name());
        m.put("deliveredAt", order.getDeliveredAt());
        m.put("deliveryEditable", order.isActiveForDelivery());
        m.put("area", order.getBuyer() != null ? order.getBuyer().getArea() : null);
        m.put("areaId", resolveBuyerAreaId(order.getBuyer()));
        m.put("societyId", order.getBuyer() != null && order.getBuyer().getSocietyRef() != null
                ? order.getBuyer().getSocietyRef().getId() : null);
        m.put("category", order.getKitchen() != null && order.getKitchen().getSellerType() != null
                ? order.getKitchen().getSellerType().name() : null);
        m.put("acknowledgedAt", order.getAcknowledgedAt());
        m.put("updatedAt", order.getUpdatedAt());
        m.put("history", orderHistory(order));
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

    /**
 * Chronological event/status history for one order (handover 9).
 *
 * <p>Built from timestamps the order ALREADY stores - created, placed,
 * acknowledged, paid-updated, delivered, last updated - so it can never claim an
 * event that did not happen. A step with no timestamp is simply omitted rather
 * than dated "now", which would be a fabricated event.
 *
 * <p>Delivery deliberately appears only when it actually happened: a cancelled
 * order has a cleared {@code deliveredAt} and must not show a Delivered entry.</p>
 */
private List<Map<String, Object>> orderHistory(Order order) {
    List<Map<String, Object>> events = new ArrayList<>();
    addHistoryEvent(events, order.getCreatedAt(), "Order started", "Basket created");
    addHistoryEvent(events, order.getOrderTime(), "Order placed", order.getOrderNumber());
    addHistoryEvent(events, order.getAcknowledgedAt(), "Acknowledged by seller", null);
    addHistoryEvent(events, order.getPaymentStatus() == PaymentStatus.PAID ? order.getUpdatedAt() : null,
            "Payment recorded as Paid", null);
    if (order.isDelivered() && order.getDeliveredAt() != null) {
        addHistoryEvent(events, order.getDeliveredAt(), "Marked delivered by seller", null);
    }
    if (order.getOrderStatus() == OrderStatus.CANCELLED) {
        addHistoryEvent(events, order.getUpdatedAt(), "Order cancelled", null);
    }
    // Newest first: support reads "what happened last", not "what happened first".
    events.sort((a, b) -> ((LocalDateTime) b.get("at")).compareTo((LocalDateTime) a.get("at")));
    return events;
}

private static void addHistoryEvent(List<Map<String, Object>> events, LocalDateTime at,
                                    String label, String detail) {
    if (at == null) return; // no timestamp = the event did not happen
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("at", at);
    e.put("label", label);
    if (detail != null) e.put("detail", detail);
    events.add(e);
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
        requireSellerAccess(requireSeller(sellerId), actingAdmin); // handover 15
        User seller = requireSeller(sellerId);
        SellerApprovalStatus from = seller.getSellerApprovalStatus();
        seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        seller.setSellerStatusReason(null);
        seller.setApprovedAt(LocalDateTime.now());
        // Handover 7.1: store who acted, not just when.
        seller.setApprovedBy(actingAdmin);
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "approved by " + actingAdmin.getMobileNumber());
        User saved = userRepository.save(seller);
        auditService.record(AdminAuditService.SELLER_APPROVED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(), String.valueOf(from), "APPROVED", null);
        return saved;
    }

    @Transactional
    public User rejectSeller(Long sellerId, String reason, User actingAdmin) {
        User seller = requireSeller(sellerId);
        SellerApprovalStatus from = seller.getSellerApprovalStatus();
        seller.setSellerApprovalStatus(SellerApprovalStatus.REJECTED);
        seller.setSellerStatusReason(trimToNull(reason));
        seller.setApprovedBy(actingAdmin);
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "rejected by " + actingAdmin.getMobileNumber());
        User saved = userRepository.save(seller);
        auditService.record(AdminAuditService.SELLER_REJECTED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(), String.valueOf(from), "REJECTED", trimToNull(reason));
        return saved;
    }

    /**
     * Handover section 7.1 third decision: "Actions: Approve, Reject, Request
     * Changes." The seller stays in the approval queue and is NOT serving.
     *
     * <p>A reason is mandatory: without it the seller cannot know what to fix.
     */
    @Transactional
    public User requestSellerChanges(Long sellerId, String reason, User actingAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when requesting changes from a seller.");
        }
        User seller = requireSeller(sellerId);
        SellerApprovalStatus from = seller.getSellerApprovalStatus();
        seller.setSellerApprovalStatus(SellerApprovalStatus.CHANGES_REQUESTED);
        seller.setSellerStatusReason(reason.trim());
        seller.setApprovedBy(actingAdmin);
        User saved = userRepository.save(seller);
        auditService.record(AdminAuditService.SELLER_CHANGES_REQUESTED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(), String.valueOf(from), "CHANGES_REQUESTED", reason.trim());
        return saved;
    }

    /**
     * Handover section 7.3 enforcement action: "Suspend / Block seller ...
     * Seller cannot operate until Admin restores access." A reason is
     * mandatory.
     */
    @Transactional
    public User suspendSeller(Long sellerId, String reason, User actingAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when suspending a seller.");
        }
        User seller = requireSeller(sellerId);
        SellerApprovalStatus from = seller.getSellerApprovalStatus();
        seller.setSellerApprovalStatus(SellerApprovalStatus.SUSPENDED);
        seller.setSellerStatusReason(reason.trim());
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "suspended by " + actingAdmin.getMobileNumber());
        User saved = userRepository.save(seller);
        auditService.record(AdminAuditService.SELLER_SUSPENDED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(), String.valueOf(from), "SUSPENDED", reason.trim());
        return saved;
    }

    /**
     * Handover 7.3 seller control: <strong>Block seller</strong>.
     *
     * <p>The handover lists "Suspend seller" and "Block seller" as separate
     * controls, so this is not an alias for {@link #suspendSeller}: suspend is an
     * approval-state decision an Admin can lift, while block is an account-level
     * stop with a mandatory reason.</p>
     *
     * <p>Blocking sets BOTH the account flag and the approval state. That is
     * deliberate: the approval state is what the existing
     * {@link KitchenVisibility} gate already reads, so the seller's storefronts
     * leave buyer discovery immediately - "seller cannot operate until Admin
     * restores access" - without inventing a second enforcement mechanism. The
     * reason is also written to {@code sellerStatusReason}, which
     * {@code /api/auth/me} already returns, so the seller sees an understandable
     * status instead of a silent failure.</p>
     *
     * <p>Nothing historical is touched: orders, storefronts, offerings and the
     * audit trail are all left intact, so a later unblock restores the seller with
     * their history rather than rebuilding them from scratch.</p>
     */
    @Transactional
    public Map<String, Object> blockSeller(Long sellerId, String reason, User actingAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when blocking a seller.");
        }
        User seller = requireSeller(sellerId);
        requireSellerAccess(seller, actingAdmin); // handover 15 Area boundary
        boolean wasBlocked = seller.isBlocked();
        SellerApprovalStatus from = seller.getSellerApprovalStatus();

        seller.setBlocked(true);
        seller.setBlockedReason(reason.trim());
        seller.setBlockedAt(LocalDateTime.now());
        seller.setSellerApprovalStatus(SellerApprovalStatus.SUSPENDED);
        seller.setSellerStatusReason(reason.trim());
        userRepository.save(seller);
        // Recorded as an approval/status change too: handover 6 requires the
        // seller approval/status change to be captured as an event.
        analyticsService.record(AnalyticsService.EV_SELLER_APPROVED, seller.getId(),
                seller.getMobileNumber(), null, "blocked by " + actingAdmin.getMobileNumber());
        auditService.record(AdminAuditService.SELLER_BLOCKED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(),
                wasBlocked ? "BLOCKED" : String.valueOf(from), "BLOCKED", reason.trim());
        return sellerAccountState(seller);
    }

    /**
     * Handover 7.3: restores a blocked seller.
     *
     * <p>The approval state is only reset back to APPROVED when it is currently
     * SUSPENDED. A seller who is PENDING, REJECTED or CHANGES_REQUESTED is left
     * exactly as they are, so unblocking can never quietly approve an application
     * the Admin never accepted.</p>
     */
    @Transactional
    public Map<String, Object> unblockSeller(Long sellerId, String note, User actingAdmin) {
        User seller = requireSeller(sellerId);
        requireSellerAccess(seller, actingAdmin); // handover 15 Area boundary
        boolean wasBlocked = seller.isBlocked();
        SellerApprovalStatus from = seller.getSellerApprovalStatus();

        seller.setBlocked(false);
        seller.setBlockedAt(null);
        if (seller.getSellerApprovalStatus() == SellerApprovalStatus.SUSPENDED) {
            seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
            seller.setSellerStatusReason(null);
        }
        userRepository.save(seller);
        auditService.record(AdminAuditService.SELLER_UNBLOCKED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(),
                wasBlocked ? "BLOCKED" : String.valueOf(from),
                String.valueOf(seller.getSellerApprovalStatus()), trimToNull(note));
        return sellerAccountState(seller);
    }

    /**
     * Handover 7.2: an internal support note on a seller.
     *
     * <p>Internal only - it is never returned to the seller or the buyer. The
     * audit action is {@code ORDER_CORRECTED}, the same generic
     * "manual operational correction" channel already used for buyer support
     * notes, so no new audit vocabulary is invented for the same idea.</p>
     */
    @Transactional
    public Map<String, Object> saveSellerSupportNote(Long sellerId, String note, User actingAdmin) {
        User seller = requireSeller(sellerId);
        requireSellerAccess(seller, actingAdmin);
        seller.setSupportNote(trimToNull(note));
        userRepository.save(seller);
        auditService.record(AdminAuditService.ORDER_CORRECTED, actingAdmin, "SELLER",
                seller.getId(), seller.getName(), null, "SUPPORT_NOTE_SAVED", trimToNull(note));
        return sellerAccountState(seller);
    }

    /** Account-status read model for a seller, mirroring the buyer one exactly. */
    private Map<String, Object> sellerAccountState(User seller) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", seller.getId());
        m.put("name", seller.getName());
        m.put("mobileNumber", seller.getMobileNumber());
        m.put("accountStatus", seller.isBlocked() ? "BLOCKED" : "ACTIVE");
        m.put("blocked", seller.isBlocked());
        m.put("blockedReason", seller.getBlockedReason());
        m.put("blockedAt", seller.getBlockedAt());
        m.put("sellerApprovalStatus", seller.getSellerApprovalStatus());
        m.put("statusReason", seller.getSellerStatusReason());
        m.put("supportNote", seller.getSupportNote());
        return m;
    }

    private User requireSeller(Long sellerId) {
        User user = userRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + sellerId));
        if (user.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("User " + sellerId + " is not a seller.");
        }
        return user;
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ==================== Commercial / seller analytics (handover 6 & 10) ====================

    /**
     * Handover section 6 seller table, and section 10 commercial view.
     *
     * <p>One row per seller: storefront views, offering views, order count,
     * recorded order value and average order value. Traffic figures come only
     * from events the application really records
     * ({@code HOMEMADE_STOREFRONT_VIEW} / {@code PRODUCT_VIEW}); they are never
     * estimated.
     *
     * @param filter the same {@link OrderFilter} the Orders screen uses, so the
     *               figures describe exactly the rows the operator is looking at.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sellerAnalytics(OrderFilter filter) {
        Map<String, long[]> views = new HashMap<>();
        Map<String, long[]> offerings = new HashMap<>();
        for (Object[] row : analyticsService.trafficByKitchen()) {
            String key = String.valueOf(row[0]);
            views.put(key, new long[]{ num(row[1]) });
            offerings.put(key, new long[]{ num(row[2]) });
        }

        Map<Long, Map<String, Object>> bySeller = new LinkedHashMap<>();
        // The storefronts behind each seller, so view totals can be added ONCE
        // per storefront. Accumulating them inside the order loop counted a
        // storefront view once per ORDER the seller received, so a seller with
        // 5 orders and 10 views reported 50 - which made conversion nonsense.
        Map<Long, Set<Long>> kitchensBySeller = new HashMap<>();
        for (Order o : matchingOrders(filter)) {
            Kitchen k = o.getKitchen();
            if (k == null || k.getSeller() == null) continue;
            Long sellerId = k.getSeller().getId();
            Map<String, Object> row = bySeller.computeIfAbsent(sellerId, id -> {
                User s = k.getSeller();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sellerId", id);
                m.put("sellerName", s.getName());
                m.put("kitchenName", k.getDisplayName());
                m.put("sellerType", k.getSellerType() != null ? k.getSellerType().name() : null);
                m.put("storefrontViews", 0L);
                m.put("offeringViews", 0L);
                m.put("orders", 0L);
                m.put("recordedOrderValue", BigDecimal.ZERO);
                return m;
            });
            row.put("orders", num(row.get("orders")) + 1);
            BigDecimal value = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            row.put("recordedOrderValue", ((BigDecimal) row.get("recordedOrderValue")).add(value));
            kitchensBySeller.computeIfAbsent(sellerId, id -> new HashSet<>()).add(k.getId());
        }

        // Views are per STOREFRONT, not per order: each storefront's totals are
        // added exactly once, which is what makes "orders / storefront views"
        // a real conversion rate rather than an artefact of order volume.
        for (Map.Entry<Long, Set<Long>> entry : kitchensBySeller.entrySet()) {
            Map<String, Object> row = bySeller.get(entry.getKey());
            if (row == null) continue;
            long storefrontViews = 0;
            long offeringViews = 0;
            for (Long kitchenId : entry.getValue()) {
                long[] sv = views.get(String.valueOf(kitchenId));
                long[] ov = offerings.get(String.valueOf(kitchenId));
                storefrontViews += sv != null ? sv[0] : 0L;
                offeringViews += ov != null ? ov[0] : 0L;
            }
            row.put("storefrontViews", storefrontViews);
            row.put("offeringViews", offeringViews);
        }

        for (Map<String, Object> row : bySeller.values()) {
            long orders = num(row.get("orders"));
            BigDecimal value = (BigDecimal) row.get("recordedOrderValue");
            row.put("averageOrderValue", orders == 0 ? BigDecimal.ZERO
                    : value.divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP));
            // Handover 6: conversion only where events are reliably captured.
            long storefrontViews = num(row.get("storefrontViews"));
            row.put("conversionRate", storefrontViews == 0 ? null
                    : BigDecimal.valueOf(orders * 100.0 / storefrontViews).setScale(1, java.math.RoundingMode.HALF_UP));
        }
        return new ArrayList<>(bySeller.values());
    }

    /** Handover section 10: totals + average for the selected period. */
    @Transactional(readOnly = true)
    public Map<String, Object> recordedOrderValueSummary(OrderFilter filter) {
        List<Map<String, Object>> rows = orders(filter == null ? new OrderFilter() : filter);
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;
        for (Map<String, Object> row : rows) {
            Object v = row.get("totalAmount");
            if (v != null) total = total.add(new BigDecimal(String.valueOf(v)));
            count++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCount", count);
        m.put("recordedOrderValue", total);
        m.put("averageOrderValue", count == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP));
        // Handover 10: the breakdown the figure is actually made of - by seller,
        // by Area and by Society - derived from the SAME filtered rows as the
        // total, so the parts always add up to the whole. Location grouping uses
        // the stable ids; a profile with only free text is grouped under its text
        // rather than silently dropped.
        m.put("bySeller", groupRecordedValue(rows, "sellerId", "sellerName"));
        m.put("byArea", groupRecordedValue(rows, "areaId", "area"));
        m.put("bySociety", groupRecordedValue(rows, "societyId", "society"));
        return m;
    }

    /**
     * Groups already-filtered order rows into an ordered breakdown.
     *
     * <p>Only rows with a real value for the key are grouped; rows with no
     * location reference at all would otherwise create an unreadable empty
     * bucket. Each group carries its order count, recorded value and average.</p>
     */
    private List<Map<String, Object>> groupRecordedValue(List<Map<String, Object>> rows,
                                                         String idKey, String nameKey) {
        Map<Object, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(idKey);
            if (key == null) continue;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        // Largest value first: this is a commercial view, so the operator wants
        // the biggest contributors at the top without having to sort anything.
        grouped.entrySet().stream()
                .sorted((a, b) -> sumValue(b.getValue()).compareTo(sumValue(a.getValue())))
                .forEach(e -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    List<Map<String, Object>> members = e.getValue();
                    g.put(idKey, e.getKey());
                    g.put("label", members.get(0).get(nameKey));
                    g.put("orderCount", members.size());
                    BigDecimal value = sumValue(members);
                    g.put("recordedOrderValue", value);
                    g.put("averageOrderValue", members.isEmpty() ? BigDecimal.ZERO
                            : value.divide(BigDecimal.valueOf(members.size()), 2, java.math.RoundingMode.HALF_UP));
                    out.add(g);
                });
        return out;
    }

    private static BigDecimal sumValue(List<Map<String, Object>> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            Object v = r.get("totalAmount");
            if (v != null) total = total.add(new BigDecimal(String.valueOf(v)));
        }
        return total;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    // ==================== Admin storefront + buyer controls (handover 7.3 / 8) ====================

    private Kitchen requireKitchen(Long kitchenId) {
        return kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
    }

    /**
     * Handover 7.3 "Pause storefront - Temporary operational stop. Existing
     * orders remain; new orders blocked/hidden as designed."
     *
     * <p>Reversible, so unlike Block/Remove it does not require a reason; an
     * optional note is still audited when supplied.
     */
    @Transactional
    public Map<String, Object> pauseStorefront(Long kitchenId, String note, User actingAdmin) {
        Kitchen k = requireKitchen(kitchenId);
        // Handover 15: a storefront outside the admin's Area is not theirs to stop.
        requireAreaAccess(storefrontAreaId(k), actingAdmin);
        boolean was = k.isStorefrontPaused();
        k.setStorefrontPaused(true);
        kitchenRepository.save(k);
        auditService.record(AdminAuditService.STOREFRONT_PAUSED, actingAdmin, "STOREFRONT",
                k.getId(), k.getDisplayName(), was ? "PAUSED" : "ACTIVE", "PAUSED", trimToNull(note));
        return storefrontState(k);
    }

    /** Handover 7.3 "Resume - Return a paused storefront to active state." */
    @Transactional
    public Map<String, Object> resumeStorefront(Long kitchenId, User actingAdmin) {
        Kitchen k = requireKitchen(kitchenId);
        boolean was = k.isStorefrontPaused();
        k.setStorefrontPaused(false);
        kitchenRepository.save(k);
        auditService.record(AdminAuditService.STOREFRONT_RESUMED, actingAdmin, "STOREFRONT",
                k.getId(), k.getDisplayName(), was ? "PAUSED" : "ACTIVE", "ACTIVE", null);
        return storefrontState(k);
    }

    /**
     * Handover 7.3 "Remove storefront - Prefer soft removal; preserve
     * historical/audit references."
     *
     * <p>Soft: the {@link Kitchen} row and every historical order pointing at it
     * stay intact, so past orders still render. Only the storefront is flagged
     * removed. A reason is mandatory.
     */
    @Transactional
    public Map<String, Object> removeStorefront(Long kitchenId, String reason, User actingAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when removing a storefront.");
        }
        Kitchen k = requireKitchen(kitchenId);
        k.setStorefrontPaused(true);
        User seller = k.getSeller();
        if (seller != null) {
            seller.setStorefrontRemoved(true);
            seller.setStorefrontRemovedAt(LocalDateTime.now());
            userRepository.save(seller);
        }
        kitchenRepository.save(k);
        auditService.record(AdminAuditService.STOREFRONT_REMOVED, actingAdmin, "STOREFRONT",
                k.getId(), k.getDisplayName(), "ACTIVE", "REMOVED", reason.trim());
        return storefrontState(k);
    }

    private Map<String, Object> storefrontState(Kitchen k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kitchenId", k.getId());
        m.put("name", k.getDisplayName());
        m.put("sellerId", k.getSeller() != null ? k.getSeller().getId() : null);
        m.put("paused", k.isStorefrontPaused());
        m.put("availableToday", k.getAvailableToday());
        m.put("removed", k.getSeller() != null && k.getSeller().isStorefrontRemoved());
        return m;
    }

    /**
     * Handover section 8: "Block buyer in case of repeated complaints, misuse
     * or seller-reported issues; reason mandatory."
     */
    @Transactional
    public Map<String, Object> blockBuyer(Long buyerId, String reason, User actingAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when blocking a buyer.");
        }
        // Handover 15: scoped before any mutation, so an Area Admin cannot reach
        // a buyer outside their Area even with a correct buyer id.
        requireBuyerAccess(requireBuyer(buyerId), actingAdmin);
        User buyer = requireBuyer(buyerId);
        boolean was = buyer.isBlocked();
        buyer.setBlocked(true);
        buyer.setBlockedReason(reason.trim());
        buyer.setBlockedAt(LocalDateTime.now());
        userRepository.save(buyer);
        auditService.record(AdminAuditService.BUYER_BLOCKED, actingAdmin, "BUYER",
                buyer.getId(), buyer.getName(), was ? "BLOCKED" : "ACTIVE", "BLOCKED", reason.trim());
        return buyerAccountState(buyer);
    }

    /** Handover section 8: "Unblock buyer." */
    @Transactional
    public Map<String, Object> unblockBuyer(Long buyerId, String note, User actingAdmin) {
        User buyer = requireBuyer(buyerId);
        boolean was = buyer.isBlocked();
        buyer.setBlocked(false);
        buyer.setBlockedAt(null);
        userRepository.save(buyer);
        auditService.record(AdminAuditService.BUYER_UNBLOCKED, actingAdmin, "BUYER",
                buyer.getId(), buyer.getName(), was ? "BLOCKED" : "ACTIVE", "ACTIVE", trimToNull(note));
        return buyerAccountState(buyer);
    }

    /** Handover section 8: "Add internal support note if needed." Internal only. */
    @Transactional
    public Map<String, Object> saveBuyerSupportNote(Long buyerId, String note, User actingAdmin) {
        User buyer = requireBuyer(buyerId);
        buyer.setSupportNote(trimToNull(note));
        userRepository.save(buyer);
        auditService.record(AdminAuditService.ORDER_CORRECTED, actingAdmin, "BUYER",
                buyer.getId(), buyer.getName(), null, "SUPPORT_NOTE_SAVED", trimToNull(note));
        return buyerAccountState(buyer);
    }

    private User requireBuyer(Long buyerId) {
        User user = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + buyerId));
        if (user.getRole() != UserRole.BUYER) {
            throw new IllegalArgumentException("User " + buyerId + " is not a buyer.");
        }
        return user;
    }

    private Map<String, Object> buyerAccountState(User buyer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", buyer.getId());
        m.put("name", buyer.getName());
        m.put("mobileNumber", buyer.getMobileNumber());
        m.put("accountStatus", buyer.isBlocked() ? "BLOCKED" : "ACTIVE");
        m.put("blocked", buyer.isBlocked());
        m.put("blockedReason", buyer.getBlockedReason());
        m.put("blockedAt", buyer.getBlockedAt());
        m.put("supportNote", buyer.getSupportNote());
        return m;
    }

    /**
     * Handover section 8 buyer detail: "profile/location needed for order
     * support", "recent orders and their payment/delivery status", "account
     * status: Active / Blocked", plus the internal support note.
     *
     * <p>The note is deliberately only in this ADMIN payload - the buyer-facing
     * DTOs never expose it.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buyerDetail(Long buyerId) {
        return buyerDetail(buyerId, null);
    }

    /**
     * Buyer detail, optionally scoped to an Area Admin (handover 15).
     *
     * <p>{@code actingAdmin} null means global scope, which is what every caller
     * without an Admin identity gets.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buyerDetail(Long buyerId, User actingAdmin) {
        User buyer = requireBuyer(buyerId);
        requireBuyerAccess(buyer, actingAdmin); // handover 15
        List<Order> orders = orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", buyer.getId());
        m.put("name", buyer.getName());
        m.put("mobileNumber", buyer.getMobileNumber());
        m.put("flatHouseNumber", buyer.getFlatHouseNumber());
        m.put("building", buyer.getBuilding());
        m.put("society", buyer.getSociety());
        m.put("societyId", buyer.getSocietyRef() != null ? buyer.getSocietyRef().getId() : null);
        m.put("area", buyer.getArea());
        m.put("areaId", buyer.getAreaRef() != null ? buyer.getAreaRef().getId() : resolveBuyerAreaId(buyer));
        m.put("accountStatus", buyer.isBlocked() ? "BLOCKED" : "ACTIVE");
        m.put("blocked", buyer.isBlocked());
        m.put("blockedReason", buyer.getBlockedReason());
        m.put("blockedAt", buyer.getBlockedAt());
        m.put("supportNote", buyer.getSupportNote());
        m.put("joinedAt", buyer.getCreatedAt());
        m.put("orderCount", orders.size());
        m.put("deliveredCount", orders.stream().filter(Order::isDelivered).count());
        m.put("cancelledCount", orders.stream().filter(o -> o.getOrderStatus() == OrderStatus.CANCELLED).count());
        m.put("paidCount", orders.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).count());
        BigDecimal total = orders.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("recordedOrderValue", total);
        m.put("recentOrders", orders.stream().limit(20).map(this::adminOrderRow).toList());
        m.put("auditHistory", auditService.forTarget("BUYER", buyerId));
        return m;
    }
        /**
     * Handover section 7.2 seller detail: identity/contact, enabled types
     * (Kitchen / Homemade / Both), storefronts, status, service societies,
     * traffic, recent orders, recorded order value, offerings summary and the
     * Admin action history.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> sellerDetail(Long sellerId) {
        User seller = requireSeller(sellerId);
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getKitchen() != null && o.getKitchen().getSeller() != null
                        && sellerId.equals(o.getKitchen().getSeller().getId()))
                .toList();

        Map<String, long[]> traffic = new HashMap<>();
        for (Object[] row : analyticsService.trafficByKitchen()) {
            traffic.put(String.valueOf(row[0]),
                    new long[]{ row[1] != null ? ((Number) row[1]).longValue() : 0L,
                                row[2] != null ? ((Number) row[2]).longValue() : 0L });
        }

        boolean hasKitchen = false, hasHomemade = false;
        List<Map<String, Object>> storefronts = new ArrayList<>();
        for (Kitchen k : kitchens) {
            boolean homemade = k.getSellerType() == com.example.my_first_spring_api.model.SellerType.HOMEMADE_PRODUCTS
                    || k.getSellerType() == com.example.my_first_spring_api.model.SellerType.BOTH;
            boolean kitchen = k.getSellerType() == null
                    || k.getSellerType() == com.example.my_first_spring_api.model.SellerType.KITCHEN
                    || k.getSellerType() == com.example.my_first_spring_api.model.SellerType.BOTH;
            if (homemade) hasHomemade = true;
            if (kitchen) hasKitchen = true;
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", k.getId());
            s.put("name", k.getDisplayName());
            s.put("sellerType", k.getSellerType() != null ? k.getSellerType().name() : null);
            s.put("paused", k.isStorefrontPaused());
            s.put("availableToday", k.getAvailableToday());
            s.put("society", k.getSociety());
            s.put("building", k.getBuilding());
            s.put("serviceAreas", k.getServiceAreas());
            s.put("servedSocieties", k.getServedSocieties() != null
                    ? k.getServedSocieties().stream().map(Society::getName).sorted().toList() : List.of());
            s.put("offeringCount", productRepository.findByKitchen(k).size());
            long[] t = traffic.get(String.valueOf(k.getId()));
            s.put("storefrontViews", t != null ? t[0] : 0L);
            s.put("offeringViews", t != null ? t[1] : 0L);
            storefronts.add(s);
        }

        BigDecimal value = orders.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getOrderStatus() != OrderStatus.CANCELLED)
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", seller.getId());
        m.put("name", seller.getName());
        m.put("mobileNumber", seller.getMobileNumber());
        m.put("status", seller.getSellerApprovalStatus());
        m.put("statusReason", seller.getSellerStatusReason());
        m.put("approvedAt", seller.getApprovedAt());
        m.put("approvedBy", seller.getApprovedBy() != null ? seller.getApprovedBy().getName() : null);
        m.put("enabledTypes", hasKitchen && hasHomemade ? "BOTH" : hasKitchen ? "KITCHEN" : hasHomemade ? "HOMEMADE" : "NONE");
        m.put("storefronts", storefronts);
        m.put("orderCount", orders.size());
        m.put("deliveredCount", orders.stream().filter(Order::isDelivered).count());
        m.put("cancelledCount", orders.stream().filter(o -> o.getOrderStatus() == OrderStatus.CANCELLED).count());
        m.put("recordedOrderValue", value);
        m.put("averageOrderValue", orders.isEmpty() ? BigDecimal.ZERO
                : value.divide(BigDecimal.valueOf(orders.size()), 2, java.math.RoundingMode.HALF_UP));
        m.put("recentOrders", orders.stream().limit(20).map(this::adminOrderRow).toList());
        // Handover 7.2: the seller detail must carry support notes and the account
        // status, so an Admin can see why a seller is blocked without leaving the
        // screen. Both are internal-only fields.
        m.put("supportNote", seller.getSupportNote());
        m.put("accountStatus", seller.isBlocked() ? "BLOCKED" : "ACTIVE");
        m.put("blocked", seller.isBlocked());
        m.put("blockedReason", seller.getBlockedReason());
        m.put("blockedAt", seller.getBlockedAt());
        m.put("auditHistory", auditService.forTarget("SELLER", sellerId));
        return m;
    }

    /** Handover section 14 read model: the audit trail, newest first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditLog(int limit) {
        return auditService.recent(limit <= 0 ? 200 : limit);
    }

    /** Per-target history rendered inside Seller / Buyer detail (handover 7.2). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditForTarget(String targetType, Long targetId) {
        return auditService.forTarget(targetType, targetId);
    }

    /** Handover section 12: resolved retention configuration and purge preview. */
    @Transactional(readOnly = true)
    public Map<String, Object> retention() {
        return retentionService.retentionStatus();
    }

    @Transactional
    public Map<String, Object> setRetentionDays(int days, User actingAdmin) {
        int applied = retentionService.setRetentionDays(days);
        auditService.record(AdminAuditService.ORDER_CORRECTED, actingAdmin, "PLATFORM", null,
                "retention", null, String.valueOf(applied), "retention_days set to " + applied);
        Map<String, Object> m = new LinkedHashMap<>(retentionService.retentionStatus());
        m.put("retentionDays", applied);
        return m;
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
            // Handover 11 usage counts, summed from the society rows displayed
            // directly underneath, so the Area figure can never disagree with
            // the sum of its own children.
            row.put("buyerCount", societyRows.stream()
                    .mapToLong(r -> ((Number) r.get("buyerCount")).longValue()).sum());
            row.put("sellerCount", societyRows.stream()
                    .mapToLong(r -> ((Number) r.get("sellerCount")).longValue()).sum());
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

    /**
     * Rename and/or enable-disable an Area. Only supplied fields are applied.
     *
     * <p>Handover 14 requires Area/Society enable-disable to be auditable, so an
     * actual active-state CHANGE is recorded. A rename alone is not one of the
     * listed actions and is not recorded, and a PATCH that leaves {@code active}
     * untouched writes no row - so the trail shows decisions, not clicks.</p>
     */
    @Transactional
    public Map<String, Object> updateArea(Long areaId, String name, Boolean active, User actingAdmin) {
        Area existing = locationService.findArea(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area not found."));
        boolean wasActive = existing.isActive();
        if (name != null && !name.isBlank()) locationService.renameArea(areaId, name);
        if (active != null) locationService.setAreaActive(areaId, active);
        Area updated = locationService.findArea(areaId)
                .orElseThrow(() -> new IllegalArgumentException("Area not found."));
        if (active != null && wasActive != updated.isActive()) {
            auditService.record(
                    updated.isActive() ? AdminAuditService.AREA_ENABLED : AdminAuditService.AREA_DISABLED,
                    actingAdmin, "AREA", updated.getId(), updated.getName(),
                    wasActive ? "ACTIVE" : "DISABLED", updated.isActive() ? "ACTIVE" : "DISABLED",
                    "Area " + updated.getName() + (updated.isActive() ? " enabled" : " disabled"));
        }
        return areaRow(updated);
    }

    @Transactional
    public Map<String, Object> createSociety(Long areaId, String name) {
        return societyRow(locationService.createSociety(areaId, name));
    }

    /**
     * Rename and/or enable-disable a Society. Audited exactly like an Area.
     *
     * <p>Disabling never deletes and never re-points sellers: existing coverage
     * rows are preserved so re-enabling restores the seller's original opt-in.
     * Handover 11 also forbids a new Society from silently becoming served -
     * that is enforced at creation time, not here.</p>
     */
    @Transactional
    public Map<String, Object> updateSociety(Long societyId, String name, Boolean active, User actingAdmin) {
        Society existing = locationService.findSociety(societyId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found."));
        boolean wasActive = existing.isActive();
        if (name != null && !name.isBlank()) locationService.renameSociety(societyId, name);
        if (active != null) locationService.setSocietyActive(societyId, active);
        Society updated = locationService.findSociety(societyId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found."));
        if (active != null && wasActive != updated.isActive()) {
            auditService.record(
                    updated.isActive() ? AdminAuditService.SOCIETY_ENABLED : AdminAuditService.SOCIETY_DISABLED,
                    actingAdmin, "SOCIETY", updated.getId(), updated.getName(),
                    wasActive ? "ACTIVE" : "DISABLED", updated.isActive() ? "ACTIVE" : "DISABLED",
                    "Society " + updated.getName() + (updated.isActive() ? " enabled" : " disabled"));
        }
        return societyRow(updated);
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
     * profiles, whether credential-based direct demo authentication is on (via the helper the security config
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

        out.put("directAuthEnabled", environment != null
                && com.example.my_first_spring_api.SecurityConfig.isDirectAuthEnabled(environment));

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
        // Handover 11: "View number of buyers/sellers using each location".
        // Counted from the stable references, so disabling a location shows its
        // real usage instead of hiding it.
        m.put("buyerCount", countBuyersInSociety(society.getId()));
        m.put("sellerCount", countSellersServing(society.getId()));
        return m;
    }

    /** Buyers whose profile points at this Society through the stable id. */
    private long countBuyersInSociety(Long societyId) {
        return userRepository.findByRole(UserRole.BUYER).stream()
                .filter(b -> b.getSocietyRef() != null && societyId.equals(b.getSocietyRef().getId()))
                .count();
    }

    /**
     * Sellers serving this Society.
     *
     * <p>Counts storefronts that opted IN through {@code Kitchen.servedSocieties}
     * - the existing stable-ID coverage set. A newly created Society is served
     * by nobody until a seller adds it, which is exactly handover 11's "sellers
     * opt into newly added societies" rule, so this number is the honest measure
     * of real usage.</p>
     */
    private long countSellersServing(Long societyId) {
        return kitchenRepository.findAll().stream()
                .filter(k -> k.getSeller() != null)
                .filter(k -> servesSociety(k, societyId))
                .map(k -> k.getSeller().getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
    }

    /** True when the storefront's ID-based coverage explicitly includes this Society. */
    private static boolean servesSociety(Kitchen k, Long societyId) {
        if (k.getServedSocieties() == null) return false;
        for (Society s : k.getServedSocieties()) {
            if (s != null && societyId.equals(s.getId())) return true;
        }
        return false;
    }
}

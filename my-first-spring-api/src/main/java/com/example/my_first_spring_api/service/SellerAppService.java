package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.exception.TemplateNotFoundException;
import com.example.my_first_spring_api.model.Category;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerTemplate;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.AnalyticsEventRepository;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.SellerTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerAppService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SellerTemplateRepository sellerTemplateRepository;
    private final FavouriteRepository favouriteRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final FeatureService featureService;

    @Autowired
    public SellerAppService(KitchenRepository kitchenRepository,
                            ProductRepository productRepository,
                            OrderRepository orderRepository,
                            SellerTemplateRepository sellerTemplateRepository,
                            FavouriteRepository favouriteRepository,
                            AnalyticsEventRepository analyticsEventRepository,
                            FeatureService featureService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.sellerTemplateRepository = sellerTemplateRepository;
        this.favouriteRepository = favouriteRepository;
        this.analyticsEventRepository = analyticsEventRepository;
        this.featureService = featureService;
    }

    // ==================== INVENTORY CONTROL ====================

    @Transactional
    public ProductDto updateInventory(Long productId, int delta, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");
        if (product.getRemainingQuantity() == null)
            throw new IllegalArgumentException("This item has no quantity limit");
        int updated = productRepository.adjustRemainingQuantity(productId, delta);
        if (updated == 0)
            throw new IllegalArgumentException(
                    "Adjustment rejected: quantity cannot go below 0 or above the maximum available");
        Product fresh = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (fresh.getRemainingQuantity() != null && fresh.getRemainingQuantity() <= 0) {
            fresh.setAvailableToday(false);
        } else if (fresh.getRemainingQuantity() != null && fresh.getRemainingQuantity() > 0
                && !fresh.getAvailableToday()
                && !Boolean.TRUE.equals(fresh.getIsPreorder())
                && (fresh.getAvailableDate() == null || fresh.getAvailableDate().equals(java.time.LocalDate.now()))) {
            // Restocking restores same-day availability, but must not turn a
            // future pre-order into a same-day offering.
            fresh.setAvailableToday(true);
        }
        return toProductDto(productRepository.save(fresh));
    }

    @Transactional
    public ProductDto markSoldOut(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");
        product.setRemainingQuantity(0);
        product.setAvailableToday(false);
        return toProductDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto pauseOrders(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");
        product.setOrdersPaused(true);
        return toProductDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto resumeOrders(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");
        product.setOrdersPaused(false);
        return toProductDto(productRepository.save(product));
    }
    @Transactional
    public SellerTemplateDto addTemplate(User seller, SellerTemplateDto dto) {
        String orderWindowStart = OfferingTiming.normalizeHhmm(dto.getOrderWindowStart(), "Orders Open");
        String orderWindowEnd = OfferingTiming.normalizeHhmm(dto.getOrderWindowEnd(), "Orders Close");
        if (orderWindowEnd != null) {
            OfferingTiming.validateWindowPair(orderWindowStart, orderWindowEnd);
        } else if (orderWindowStart != null) {
            throw new IllegalArgumentException("Orders Close is required when Orders Open is provided.");
        }
        if (dto.getReadyByTime() != null && !dto.getReadyByTime().isBlank()) {
            OfferingTiming.parseReadyByTime(dto.getReadyByTime());
        }
        SellerTemplate template = new SellerTemplate();
        template.setSeller(seller);
        template.setName(dto.getName());
        template.setDescription(dto.getDescription());
        template.setPrice(dto.getPrice());
        template.setPriceUnit(dto.getPriceUnit());
        template.setImageUrl(dto.getImageUrl());
        template.setMaxQuantity(dto.getMaxQuantity());
        template.setCutoffTime(orderWindowEnd);
        template.setReadyByTime(dto.getReadyByTime());
        template.setOrderWindowStart(orderWindowStart);
        template.setOrderWindowEnd(orderWindowEnd);
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            try {
                template.setCategory(Category.valueOf(dto.getCategory().trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                String allowed = Arrays.stream(Category.values())
                        .map(c -> c.name()).collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Invalid category '" + dto.getCategory()
                        + "'. Allowed values: " + allowed);
            }
        }
        // Cap enforced AFTER input validation so sellers see their real error first.
        long count = sellerTemplateRepository.countBySeller(seller);
        if (count >= 3) {
            throw new IllegalStateException(
                    "Maximum 3 favourite templates allowed. Remove an existing favourite to save a new one.");
        }
        SellerTemplate saved = sellerTemplateRepository.save(template);
        return toSellerTemplateDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SellerTemplateDto> getTemplates(User seller) {
        return sellerTemplateRepository.findBySellerOrderByCreatedAtDesc(seller).stream()
                .map(this::toSellerTemplateDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTemplate(Long templateId, User seller) {
        SellerTemplate template = sellerTemplateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));
        if (!template.getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your template");
        sellerTemplateRepository.delete(template);
    }

    /** Creates a NEW independent Product from a saved template (Template Independence rule). */
    @Transactional
    public ProductDto createProductFromTemplate(Long templateId, LocalDate date, User seller) {
        SellerTemplate template = sellerTemplateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));
        if (!template.getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your template");
        Kitchen kitchen = getOwnedKitchen(seller);
        boolean preorder = date != null && date.isAfter(java.time.LocalDate.now());
        assertFeatureCompliance(seller, preorder, date);
        Product product = templateToProduct(template, kitchen, date);
        return toProductDto(productRepository.save(product));
    }
    // ==================== QUICK POST PARSING (SCREEN 4) ====================

    @Transactional(readOnly = true)
    public QuickPostParseResultDto parseQuickPost(String rawMessage) {
        QuickPostParseResultDto result = new QuickPostParseResultDto();
        result.setRawText(rawMessage);
        if (rawMessage == null || rawMessage.isBlank()) {
            result.setPublishable(false);
            return result;
        }
        String text = rawMessage.trim();

        // Extract price — supports currency symbol, Rs., or INR
        Pattern pricePattern = Pattern.compile(
                "(?:[\u20B9]|Rs[.]?|INR)[ ]*([0-9,]+(?:[.][0-9]{0,2})?)",
                Pattern.CASE_INSENSITIVE);
        Matcher priceMatcher = pricePattern.matcher(text);
        if (priceMatcher.find()) {
            String priceStr = priceMatcher.group(1).replace(",", "");
            result.setPrice(new BigDecimal(priceStr));
        }

        // Extract quantity and infer unit
        Pattern qtyPattern = Pattern.compile(
                "(?:qty[:.]?[ ]*|)([0-9]+)[ ]*" +
                        "(plates?|pieces?|boxes?|servings?|pors?|units?)",
                Pattern.CASE_INSENSITIVE);
        Matcher qtyMatcher = qtyPattern.matcher(text);
        if (qtyMatcher.find()) {
            int qty = Integer.parseInt(qtyMatcher.group(1));
            if (qty > 0) {
                result.setMaxQuantity(qty);
                String unit = qtyMatcher.group(2).toLowerCase();
                if (unit.contains("plate") || unit.contains("por"))
                    result.setPriceUnit("Per Plate");
                else if (unit.contains("piece"))
                    result.setPriceUnit("Per Piece");
                else if (unit.contains("box"))
                    result.setPriceUnit("Per Box");
            }
        }

        // Extract cutoff / last-order time
        Pattern cutoffPattern = Pattern.compile(
                "(?:order[ ]*by|last[ ]*order|before|cutoff|close[dst]?.*?)[ ]*" +
                        "([0-9]{1,2})(?:[:]([0-9]{2}))?[ ]*(am|pm)?",
                Pattern.CASE_INSENSITIVE);
        Matcher cutoffMatcher = cutoffPattern.matcher(text);
        if (cutoffMatcher.find()) {
            int hour = Integer.parseInt(cutoffMatcher.group(1));
            int min = cutoffMatcher.group(2) != null
                    ? Integer.parseInt(cutoffMatcher.group(2)) : 0;
            String ampm = cutoffMatcher.group(3) != null
                    ? cutoffMatcher.group(3).toLowerCase() : "";
            if ("pm".equals(ampm) && hour < 12) hour += 12;
            if ("am".equals(ampm) && hour == 12) hour = 0;
            result.setCutoffTime(String.format("%02d:%02d", hour, min));
        }

        // Extract name — text before the actual price match (regex position, so
        // "Rs" inside words like "thursday" can never truncate the name)
        if (result.getPrice() != null) {
            int priceIdx = priceMatcher.start();
            if (priceIdx > 0) {
                String beforePrice = text.substring(0, priceIdx).trim();
                List<String> lines = beforePrice.lines()
                        .map(l -> l.trim())
                        .filter(l -> !l.isEmpty())
                        .collect(Collectors.toList());
                for (int i = lines.size() - 1; i >= 0; i--) {
                    String candidate = lines.get(i).replaceAll("[-–—:]", "").trim();
                    if (!candidate.isEmpty() && candidate.length() < 80) {
                        result.setName(candidate);
                        break;
                    }
                }
            }
        }

        // Best-effort description from remaining text
        if (result.getName() != null && !result.getName().isBlank()) {
            String desc = text.replaceAll("(?i)" + Pattern.quote(result.getName()), "").trim();
            if (!desc.isBlank()) result.setDescription(desc);
        }

        // Publishable only when name AND price are present (Safety Gate)
        result.setPublishable(
                result.getName() != null && !result.getName().isBlank() && result.getPrice() != null);
        return result;
    }

    // ==================== HISTORY & BATCH REPUBLISH ====================

    @Transactional(readOnly = true)
    public List<ProductDto> getRecentOfferings(User seller) {
        Kitchen kitchen = getOwnedKitchen(seller);
        // History is defined by the authoritative offering date, not creation age.
        // Active today and future offerings remain on the seller dashboard.
        return productRepository
                .findByKitchenAndAvailableDateBeforeOrderByAvailableDateDescCreatedAtDesc(kitchen, LocalDate.now()).stream()
                .map(this::toProductDto)
                .collect(Collectors.toList());
    }

    /**
     * Creates brand-new independent products from existing offerings (Template Independence rule:
     * originals are never mutated — only cloned).
     */
    @Transactional
    public List<ProductDto> batchRepublish(List<Long> productIds, LocalDate date, User seller) {
        Kitchen kitchen = getOwnedKitchen(seller);
        if (productIds == null || productIds.isEmpty()) return List.of();
        if (productIds.size() > 50) {
            throw new IllegalArgumentException("Cannot republish more than 50 items at a time.");
        }
        List<Long> deduped = new ArrayList<>(new LinkedHashSet<>(productIds));
        List<ProductDto> result = new ArrayList<>();
        for (Long productId : deduped) {
            assertFeatureCompliance(seller, date != null && date.isAfter(java.time.LocalDate.now()), date);
            Product original = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            if (!original.getKitchen().getSeller().getId().equals(seller.getId()))
                throw new SellerNotAuthorizedException("Not your product: " + productId);
            Product clone = cloneProduct(original, kitchen, date);
            result.add(toProductDto(productRepository.save(clone)));
        }
        return result;
    }


    // ==================== DASHBOARD ====================

    @Transactional(readOnly = true)
    public SellerDashboardDto getDashboard(User seller) {
        Kitchen kitchen = getOwnedKitchen(seller);
        SellerDashboardDto dto = new SellerDashboardDto();
        dto.setKitchenId(kitchen.getId());
        dto.setKitchenName(kitchen.getDisplayName());

        List<Order> allOrders = orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen);
        dto.setTotalOrders(allOrders.size());
        dto.setFollowers((int) favouriteRepository.countByKitchen(kitchen));
        dto.setViewsToday((int) analyticsEventRepository
                .countByEventTypeAndKitchenIdAndCreatedAtAfter(AnalyticsService.EV_MENU_VIEW, kitchen.getId(),
                        LocalDate.now().atStartOfDay()));

        List<Product> products = productRepository.findByKitchen(kitchen);
        dto.setOfferings(products.stream().map(this::toProductDto).collect(Collectors.toList()));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal confirmedToday = BigDecimal.ZERO;
        BigDecimal pendingToday = BigDecimal.ZERO;
        BigDecimal monthRevenue = BigDecimal.ZERO;

        for (Order o : allOrders) {
            if (o.getOrderStatus() == OrderStatus.CANCELLED) continue;
            BigDecimal amt = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            if (o.getCreatedAt().isAfter(startOfMonth) && o.getPaymentStatus() == PaymentStatus.PAID)
                monthRevenue = monthRevenue.add(amt);
            if (o.getCreatedAt().isAfter(startOfDay)) {
                if (o.getPaymentStatus() == PaymentStatus.PAID)
                    confirmedToday = confirmedToday.add(amt);
                else if (o.getPaymentStatus() == PaymentStatus.PENDING)
                    pendingToday = pendingToday.add(amt);
            }
        }
        dto.setConfirmedToday(confirmedToday);
        dto.setPending(pendingToday);
        dto.setThisMonth(monthRevenue);
        return dto;
    }
    // ==================== ORDER AGGREGATION (SCREEN 7A) ====================

    @Transactional(readOnly = true)
    public SellerOrderSummaryDto getOrderSummary(User seller, LocalDate date) {
        Kitchen kitchen = getOwnedKitchen(seller);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Order> orders = orderRepository
                .findByKitchenAndCreatedAtBetweenOrderByCreatedAtDesc(kitchen, start, end);

        SellerOrderSummaryDto dto = new SellerOrderSummaryDto();
        int paidCount = 0, pendingCount = 0, cancelledCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        Map<Long, SellerOrderSummaryDto.ProductOrderAggregate> productAgg = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                cancelledCount++;
                continue; // cancelled orders never count towards revenue or item aggregates
            }
            if (order.getPaymentStatus() == PaymentStatus.PAID) paidCount++;
            else if (order.getPaymentStatus() == PaymentStatus.PENDING) pendingCount++;
            BigDecimal amt = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(amt);

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                Long pid = product.getId();
                SellerOrderSummaryDto.ProductOrderAggregate agg = productAgg.computeIfAbsent(pid, k -> {
                    SellerOrderSummaryDto.ProductOrderAggregate a =
                            new SellerOrderSummaryDto.ProductOrderAggregate();
                    a.setProductId(pid);
                    a.setProductName(product.getName());
                    a.setImageUrl(product.getImageUrl());
                    a.setRevenue(BigDecimal.ZERO);
                    return a;
                });
                agg.setTotalOrders(agg.getTotalOrders() + 1);
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                agg.setTotalPlates(agg.getTotalPlates() + qty);
                BigDecimal itemRevenue = item.getPrice() != null
                        ? item.getPrice().multiply(BigDecimal.valueOf(qty))
                        : BigDecimal.ZERO;
                agg.setRevenue(agg.getRevenue().add(itemRevenue));
                if (order.getPaymentStatus() == PaymentStatus.PAID)
                    agg.setPaidCount(agg.getPaidCount() + 1);
                else if (order.getPaymentStatus() == PaymentStatus.PENDING)
                    agg.setPendingCount(agg.getPendingCount() + 1);
            }
        }

        dto.setTotalOrderCount(orders.size());
        dto.setPaidCount(paidCount);
        dto.setPendingCount(pendingCount);
        dto.setCancelledCount(cancelledCount);
        dto.setTotalRevenue(totalRevenue);
        dto.setProducts(new ArrayList<>(productAgg.values()));
        return dto;
    }
    // ==================== ORDER DRILL-DOWN (SCREEN 7B) ====================

    @Transactional(readOnly = true)
    public OrderItemDetailDto getOrderItemDetail(User seller, Long productId, LocalDate date,
                                                    String society, String status) {
        Kitchen kitchen = getOwnedKitchen(seller);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Order> orders = orderRepository
                .findByKitchenAndCreatedAtBetweenOrderByCreatedAtDesc(kitchen, start, end);

        OrderItemDetailDto dto = new OrderItemDetailDto();
        dto.setProductId(productId);
        dto.setProductName(product.getName());
        dto.setProductImageUrl(product.getImageUrl());

        int totalPlates = 0, paidCount = 0, pendingCount = 0, cancelledCount = 0;
        int filteredTotalPlates = 0, filteredPaidCount = 0, filteredPendingCount = 0, filteredCancelledCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal filteredTotalRevenue = BigDecimal.ZERO;
        List<OrderItemDetailDto.CustomerOrderRow> rows = new ArrayList<>();

        for (Order order : orders) {
            int qtyForProduct = 0;
            BigDecimal itemRevenue = BigDecimal.ZERO;
            OrderItem matchedItem = null;
            for (OrderItem item : order.getItems()) {
                if (item.getProduct().getId().equals(productId)) {
                    qtyForProduct += (item.getQuantity() != null ? item.getQuantity() : 0);
                    itemRevenue = itemRevenue.add(item.getPrice() != null
                            ? item.getPrice().multiply(BigDecimal.valueOf(
                                    item.getQuantity() != null ? item.getQuantity() : 0))
                            : BigDecimal.ZERO);
                    if (matchedItem == null) matchedItem = item;
                }
            }
            if (qtyForProduct > 0) {
                boolean paid = order.getPaymentStatus() == PaymentStatus.PAID;
                boolean cancelled = order.getOrderStatus() == OrderStatus.CANCELLED;
                if (cancelled) {
                    cancelledCount++;
                } else {
                    if (paid) paidCount++;
                    else if (order.getPaymentStatus() == PaymentStatus.PENDING) pendingCount++;
                    totalPlates += qtyForProduct;
                    totalRevenue = totalRevenue.add(itemRevenue);
                }
                if (matchesFilters(order, society, status)) {
                    if (cancelled) {
                        filteredCancelledCount++;
                    } else {
                        if (paid) filteredPaidCount++;
                        else if (order.getPaymentStatus() == PaymentStatus.PENDING) filteredPendingCount++;
                        filteredTotalPlates += qtyForProduct;
                        filteredTotalRevenue = filteredTotalRevenue.add(itemRevenue);
                    }
                    rows.add(buildCustomerRow(order, productId, matchedItem));
                }
            }
        }

        dto.setTotalRevenue(totalRevenue);
        dto.setTotalPlates(totalPlates);
        dto.setPaidCount(paidCount);
        dto.setPendingCount(pendingCount);
        dto.setCancelledCount(cancelledCount);
        dto.setCustomers(rows);
        dto.setFilteredTotalPlates(filteredTotalPlates);
        dto.setFilteredPaidCount(filteredPaidCount);
        dto.setFilteredPendingCount(filteredPendingCount);
        dto.setFilteredCancelledCount(filteredCancelledCount);
        dto.setFilteredTotalRevenue(filteredTotalRevenue);
        return dto;
    }

    private boolean matchesFilters(Order order, String society, String status) {
        if (society != null && !society.isBlank()) {
            String buyerSociety = order.getBuyer() != null ? order.getBuyer().getSociety() : null;
            if (buyerSociety == null || !buyerSociety.toLowerCase().contains(society.toLowerCase())) {
                return false;
            }
        }
        if (status != null && !status.isBlank()) {
            String s = status.toLowerCase();
            boolean cancelled = order.getOrderStatus() == OrderStatus.CANCELLED;
            boolean paid = order.getPaymentStatus() == PaymentStatus.PAID;
            boolean pending = order.getPaymentStatus() == PaymentStatus.PENDING;
            if (s.equals("cancelled")) return cancelled;
            if (s.equals("paid")) return paid && !cancelled;
            if (s.equals("pending")) return pending && !cancelled;
        }
        return true;
    }

    private OrderItemDetailDto.CustomerOrderRow buildCustomerRow(Order order, Long productId, OrderItem matchedItem) {
        OrderItemDetailDto.CustomerOrderRow row = new OrderItemDetailDto.CustomerOrderRow();
        row.setOrderId(order.getId());
        row.setOrderNumber(order.getOrderNumber());
        int qtyForProduct = 0;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct().getId().equals(productId)) {
                qtyForProduct += (item.getQuantity() != null ? item.getQuantity() : 0);
            }
        }
        row.setQuantity(qtyForProduct);
        Product product = matchedItem != null ? matchedItem.getProduct() : null;
        row.setUnit(product != null && product.getPriceUnit() != null ? product.getPriceUnit() : "plate");
        row.setPricePerUnit(matchedItem != null ? matchedItem.getPrice() : null);
        BigDecimal lineTotal = matchedItem != null && matchedItem.getPrice() != null
                ? matchedItem.getPrice().multiply(BigDecimal.valueOf(qtyForProduct))
                : BigDecimal.ZERO;
        row.setTotalAmount(lineTotal);
        if (order.getBuyer() != null) {
            row.setBuyerName(order.getBuyer().getName());

            row.setBuyerFlat(order.getBuyer().getFlatHouseNumber());
            row.setBuilding(order.getBuyer().getBuilding());
            row.setSociety(order.getBuyer().getSociety());
        } else {
            row.setBuyerName("Unknown");
        }
        row.setPaid(order.getPaymentStatus() == PaymentStatus.PAID);
        row.setCancelled(order.getOrderStatus() == OrderStatus.CANCELLED);
        row.setOrderStatus(order.getOrderStatus() != null ? order.getOrderStatus().name() : null);
        row.setRemark(order.getCustomInstructions());
        row.setPlacedAt(order.getOrderTime() != null ? order.getOrderTime() : order.getCreatedAt());
        return row;
    }

    // ==================== EARNINGS (SCREEN 8) ====================

    @Transactional(readOnly = true)
    public SellerEarningsDto getEarnings(User seller) {
        Kitchen kitchen = getOwnedKitchen(seller);
        List<Order> allOrders = orderRepository.findByKitchenOrderByCreatedAtDesc(kitchen);

        SellerEarningsDto dto = new SellerEarningsDto();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal confirmedToday = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal thisMonth = BigDecimal.ZERO;

        Map<Long, SellerEarningsDto.ItemEarning> itemMap = new LinkedHashMap<>();

        for (Order order : allOrders) {
            if (order.getOrderStatus() == OrderStatus.CANCELLED) continue;
            BigDecimal amt = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            boolean isToday = order.getCreatedAt().isAfter(startOfDay);
            boolean isThisMonth = order.getCreatedAt().isAfter(startOfMonth);

            if (isThisMonth && order.getPaymentStatus() == PaymentStatus.PAID)
                thisMonth = thisMonth.add(amt);
            if (isToday) {
                if (order.getPaymentStatus() == PaymentStatus.PAID)
                    confirmedToday = confirmedToday.add(amt);
                else if (order.getPaymentStatus() == PaymentStatus.PENDING)
                    pending = pending.add(amt);
            }

            for (OrderItem item : order.getItems()) {
                Long pid = item.getProduct().getId();
                SellerEarningsDto.ItemEarning ie = itemMap.computeIfAbsent(pid, k -> {
                    SellerEarningsDto.ItemEarning e = new SellerEarningsDto.ItemEarning();
                    e.setProductId(pid);
                    e.setProductName(item.getProduct().getName());
                    e.setImageUrl(item.getProduct().getImageUrl());
                    e.setConfirmedRevenue(BigDecimal.ZERO);
                    e.setPendingRevenue(BigDecimal.ZERO);
                    return e;
                });
                ie.setTotalOrders(ie.getTotalOrders() + 1);
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                BigDecimal itemRevenue = item.getPrice() != null
                        ? item.getPrice().multiply(BigDecimal.valueOf(qty))
                        : BigDecimal.ZERO;
                if (order.getPaymentStatus() == PaymentStatus.PAID) {
                    ie.setConfirmedRevenue(ie.getConfirmedRevenue().add(itemRevenue));
                } else if (order.getPaymentStatus() == PaymentStatus.PENDING) {
                    ie.setPendingRevenue(ie.getPendingRevenue().add(itemRevenue));
                }
            }
        }
        dto.setConfirmedToday(confirmedToday);
        dto.setPending(pending);
        dto.setThisMonth(thisMonth);
        dto.setItems(new ArrayList<>(itemMap.values()));
        return dto;
    }
    // ==================== HELPERS ====================

    private Kitchen getOwnedKitchen(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (kitchens.isEmpty()) throw new KitchenNotFoundException((Long) null);
        return kitchens.get(0);
    }

    private void assertFeatureCompliance(User seller, boolean isPreorder, LocalDate availableDate) {
        if (isPreorder) {
            featureService.assertSellerCanUse(seller, FeatureService.KEY_PREORDERS);
        }
        if (availableDate == null) return;
        long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), availableDate);
        if (daysAhead <= 0) return;
        if (!featureService.sellerHasAccess(seller, FeatureService.KEY_MENU_ADVANCE_DAYS)) {
            throw new IllegalStateException("Publishing menus in advance is currently disabled on the platform.");
        }
        Integer limit = featureService.sellerLimit(seller, FeatureService.KEY_MENU_ADVANCE_DAYS);
        int allowed = limit != null ? limit : 0;
        if (daysAhead > allowed) {
            throw new IllegalStateException("This offering is " + daysAhead
                    + " day(s) ahead, but your kitchen can publish menus only "
                    + allowed + " day(s) in advance.");
        }
    }

    private ProductDto toProductDto(Product product) {
        Kitchen kitchen = product.getKitchen();
        ProductDto dto = new ProductDto(
                product.getId(),
                kitchen != null ? kitchen.getId() : null,
                kitchen != null ? kitchen.getDisplayName() : null,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.getAvailableToday(),
                product.getRating());
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
        dto.setCreatedAt(product.getCreatedAt());
        return dto;
    }

    private SellerTemplateDto toSellerTemplateDto(SellerTemplate template) {
        SellerTemplateDto dto = new SellerTemplateDto();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setPrice(template.getPrice());
        dto.setPriceUnit(template.getPriceUnit());
        dto.setImageUrl(template.getImageUrl());
        dto.setMaxQuantity(template.getMaxQuantity());
        dto.setCutoffTime(template.getCutoffTime());
        dto.setReadyByTime(template.getReadyByTime());
        dto.setOrderWindowStart(template.getOrderWindowStart());
        dto.setOrderWindowEnd(template.getOrderWindowEnd());
        dto.setCategory(template.getCategory() != null ? template.getCategory().name() : null);
        return dto;
    }

    /** Converts a template into a brand-new Product for the given date. */
    private Product templateToProduct(SellerTemplate template, Kitchen kitchen, LocalDate date) {
        LocalDate offeringDate = requirePublicationDate(date);
        boolean preorder = offeringDate.isAfter(LocalDate.now());
        if (preorder) {
            // Future template/clone publications use the shared Product pre-order path.
        }
        String close = requirePublicationClose(template.getOrderWindowEnd(), template.getCutoffTime());
        String readyBy = OfferingTiming.rebaseReadyByTime(template.getReadyByTime(), offeringDate);
        OfferingTiming.validateNewOffering(offeringDate, preorder,
                template.getOrderWindowStart(), close, readyBy);
        Product product = new Product(kitchen, template.getName(), template.getDescription(),
                template.getPrice(), template.getImageUrl());
        product.setPriceUnit(template.getPriceUnit());
        product.setAvailableDate(offeringDate);
        product.setAvailableToday(offeringDate.equals(LocalDate.now()) && !preorder);
        product.setOrderWindowStart(template.getOrderWindowStart());
        product.setOrderWindowEnd(close);
        product.setMaxQuantity(template.getMaxQuantity());
        product.setRemainingQuantity(template.getMaxQuantity());
        product.setIsPreorder(preorder);
        product.setCategory(template.getCategory() != null ? template.getCategory().name() : null);
        product.setCutoffTime(close);
        product.setReadyByTime(readyBy);
        product.setBookedQuantity(0);
        return product;
    }

    private LocalDate requirePublicationDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Offering date is required.");
        if (date.isBefore(LocalDate.now())) throw new IllegalArgumentException("Offering date cannot be in the past.");
        return date;
    }

    private String requirePublicationClose(String orderWindowEnd, String cutoffTime) {
        if (orderWindowEnd != null && !orderWindowEnd.isBlank()) {
            return OfferingTiming.requireOrdersClose(orderWindowEnd);
        }
        if (cutoffTime != null && !cutoffTime.isBlank()) {
            return OfferingTiming.requireOrdersClose(cutoffTime);
        }
        throw new IllegalArgumentException("Orders Close is required.");
    }

    /** Clones an existing product into a brand-new independent offering for the given date. */
    private Product cloneProduct(Product original, Kitchen kitchen, LocalDate date) {
        LocalDate offeringDate = requirePublicationDate(date);
        boolean preorder = offeringDate.isAfter(LocalDate.now());
        String close = requirePublicationClose(original.getOrderWindowEnd(), original.getCutoffTime());
        boolean flexible = original.getPreorderType() == com.example.my_first_spring_api.model.PreorderType.FLEXIBLE;
        String readyBy = flexible
                ? offeringDate + "T23:59"
                : OfferingTiming.rebaseReadyByTime(original.getReadyByTime(), offeringDate);
        OfferingTiming.validateNewOffering(offeringDate, preorder,
                original.getOrderWindowStart(), close, readyBy);
        Product clone = new Product(kitchen, original.getName(), original.getDescription(),
                original.getPrice(), original.getImageUrl());
        clone.setPriceUnit(original.getPriceUnit());
        clone.setAvailableDate(offeringDate);
        clone.setAvailableToday(offeringDate.equals(LocalDate.now()) && !preorder);
        clone.setOrderWindowStart(null);
        clone.setOrderWindowEnd(close);
        Integer independentMax = original.getMaxQuantity() != null && original.getMaxQuantity() > 0
                ? original.getMaxQuantity() : null;
        clone.setMaxQuantity(independentMax);
        clone.setRemainingQuantity(independentMax);
        clone.setIsPreorder(preorder);
        clone.setCategory(original.getCategory());
        clone.setCutoffTime(close);
        clone.setReadyByTime(readyBy);
        clone.setPreorderType(original.getPreorderType());
        clone.setAvailableUntilDate(original.getAvailableUntilDate());
        clone.setTimeSlots(original.getTimeSlots());
        clone.setBookedQuantity(0);
        clone.setOrdersPaused(false);
        return clone;
    }
}

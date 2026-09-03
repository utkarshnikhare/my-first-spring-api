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

    @Autowired
    public SellerAppService(KitchenRepository kitchenRepository,
                            ProductRepository productRepository,
                            OrderRepository orderRepository,
                            SellerTemplateRepository sellerTemplateRepository,
                            FavouriteRepository favouriteRepository,
                            AnalyticsEventRepository analyticsEventRepository) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.sellerTemplateRepository = sellerTemplateRepository;
        this.favouriteRepository = favouriteRepository;
        this.analyticsEventRepository = analyticsEventRepository;
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
        // Atomic UPDATE: guards live in the SQL, so concurrent stepper clicks can
        // never lose an increment, go below 0, or exceed the advertised maximum.
        int updated = productRepository.adjustRemainingQuantity(productId, delta);
        if (updated == 0)
            throw new IllegalArgumentException(
                    "Adjustment rejected: quantity cannot go below 0 or above the maximum available");
        Product fresh = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toProductDto(fresh);
    }

    @Transactional
    public ProductDto markSoldOut(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");
        product.setRemainingQuantity(0);
        return toProductDto(productRepository.save(product));
    }
    @Transactional
    public SellerTemplateDto addTemplate(User seller, SellerTemplateDto dto) {
        SellerTemplate template = new SellerTemplate();
        template.setSeller(seller);
        template.setName(dto.getName());
        template.setDescription(dto.getDescription());
        template.setPrice(dto.getPrice());
        template.setPriceUnit(dto.getPriceUnit());
        template.setImageUrl(dto.getImageUrl());
        template.setMaxQuantity(dto.getMaxQuantity());
        template.setCutoffTime(dto.getCutoffTime());
        template.setReadyByTime(dto.getReadyByTime());
        template.setOrderWindowStart(dto.getOrderWindowStart());
        template.setOrderWindowEnd(dto.getOrderWindowEnd());
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
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        return productRepository
                .findByKitchenAndCreatedAtAfterOrderByCreatedAtDesc(kitchen, twoDaysAgo).stream()
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

        // Demo values for client demo
        dto.setViewsToday(37);
        dto.setFollowers(18);
        dto.setTotalOrders(162);
        dto.setConfirmedToday(BigDecimal.valueOf(4000));
        dto.setPending(BigDecimal.valueOf(800));
        dto.setThisMonth(BigDecimal.valueOf(18850));

        List<Product> products = productRepository.findByKitchen(kitchen);
        dto.setOfferings(products.stream().map(this::toProductDto).collect(Collectors.toList()));

        return dto;
    }
    // ==================== ORDER AGGREGATION (SCREEN 7A) ====================

    @Transactional(readOnly = true)
    public SellerOrderSummaryDto getOrderSummary(User seller, LocalDate date) {
        Kitchen kitchen = getOwnedKitchen(seller);
        SellerOrderSummaryDto dto = new SellerOrderSummaryDto();

        // Demo values for client demo
        dto.setTotalOrderCount(30);
        dto.setPaidCount(25);
        dto.setPendingCount(3);
        dto.setCancelledCount(2);
        dto.setTotalRevenue(BigDecimal.valueOf(2880));

        // Product aggregates for demo
        List<SellerOrderSummaryDto.ProductOrderAggregate> products = new ArrayList<>();
        List<Product> kitchenProducts = productRepository.findByKitchen(kitchen);
        for (Product p : kitchenProducts) {
            SellerOrderSummaryDto.ProductOrderAggregate agg = new SellerOrderSummaryDto.ProductOrderAggregate();
            agg.setProductId(p.getId());
            agg.setProductName(p.getName());
            agg.setImageUrl(p.getImageUrl());
            int booked = p.getBookedQuantity() != null ? p.getBookedQuantity() : 0;
            agg.setTotalOrders(booked);
            agg.setTotalPlates(booked);
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            agg.setRevenue(price.multiply(BigDecimal.valueOf(booked)));
            agg.setPaidCount(booked);
            agg.setPendingCount(0);
            products.add(agg);
        }
        dto.setProducts(products);
        return dto;
    }
    // ==================== ORDER DRILL-DOWN (SCREEN 7B) ====================

    @Transactional(readOnly = true)
    public OrderItemDetailDto getOrderItemDetail(User seller, Long productId, LocalDate date) {
        Kitchen kitchen = getOwnedKitchen(seller);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.getKitchen().getSeller().getId().equals(seller.getId()))
            throw new SellerNotAuthorizedException("Not your product");

        OrderItemDetailDto dto = new OrderItemDetailDto();
        dto.setProductId(productId);
        dto.setProductName(product.getName());
        dto.setProductImageUrl(product.getImageUrl());

        // Demo values for client demo
        int booked = product.getBookedQuantity() != null ? product.getBookedQuantity() : 0;
        BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        dto.setTotalRevenue(price.multiply(BigDecimal.valueOf(booked)));
        dto.setTotalPlates(booked);
        dto.setPaidCount(booked);
        dto.setPendingCount(0);
        dto.setCancelledCount(0);

        // Demo customer rows
        List<OrderItemDetailDto.CustomerOrderRow> rows = new ArrayList<>();
        String[] flats = {"A-101", "A-102", "B-201", "B-202", "C-301"};
        String[] names = {"Priya", "Rahul", "Sneha", "Amit", "Kavita"};
        for (int i = 0; i < Math.min(booked, flats.length); i++) {
            OrderItemDetailDto.CustomerOrderRow row = new OrderItemDetailDto.CustomerOrderRow();
            row.setOrderId((long) (i + 1));
            row.setOrderNumber("SM" + (1000 + i));
            row.setQuantity(1);
            row.setBuyerName(names[i]);
            row.setBuyerFlat(flats[i]);
            row.setSociety("Sunshine Society");
            row.setPaid(true);
            row.setCancelled(false);
            rows.add(row);
        }
        dto.setCustomers(rows);
        return dto;
    }
    // ==================== EARNINGS (SCREEN 8) ====================

    @Transactional(readOnly = true)
    public SellerEarningsDto getEarnings(User seller) {
        Kitchen kitchen = getOwnedKitchen(seller);
        SellerEarningsDto dto = new SellerEarningsDto();

        // Demo values for client demo
        dto.setConfirmedToday(BigDecimal.valueOf(4000));
        dto.setPending(BigDecimal.valueOf(800));
        dto.setThisMonth(BigDecimal.valueOf(18850));

        // Item-wise breakdown for demo
        List<SellerEarningsDto.ItemEarning> items = new ArrayList<>();
        List<Product> products = productRepository.findByKitchen(kitchen);
        for (Product p : products) {
            SellerEarningsDto.ItemEarning item = new SellerEarningsDto.ItemEarning();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setImageUrl(p.getImageUrl());
            item.setTotalOrders(p.getBookedQuantity() != null ? p.getBookedQuantity() : 0);
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            item.setConfirmedRevenue(price.multiply(BigDecimal.valueOf(item.getTotalOrders())));
            item.setPendingRevenue(BigDecimal.ZERO);
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }
    // ==================== HELPERS ====================

    private Kitchen getOwnedKitchen(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (kitchens.isEmpty()) throw new KitchenNotFoundException((Long) null);
        return kitchens.get(0);
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
        dto.setCategory(product.getCategory() != null ? product.getCategory().name() : null);
        dto.setCutoffTime(product.getCutoffTime());
        dto.setReadyByTime(product.getReadyByTime());
        dto.setPreorderType(product.getPreorderType() != null ? product.getPreorderType().name() : null);
        dto.setAvailableUntilDate(product.getAvailableUntilDate());
        dto.setTimeSlots(product.getTimeSlots());
        dto.setBookedQuantity(product.getBookedQuantity());
        dto.setSoldOut(product.isSoldOut());
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
        Product product = new Product(kitchen, template.getName(), template.getDescription(),
                template.getPrice(), template.getImageUrl());
        product.setPriceUnit(template.getPriceUnit());
        product.setAvailableDate(date);
        product.setAvailableToday(date != null && date.equals(LocalDate.now()));
        product.setOrderWindowStart(template.getOrderWindowStart());
        product.setOrderWindowEnd(template.getOrderWindowEnd());
        product.setMaxQuantity(template.getMaxQuantity());
        product.setRemainingQuantity(template.getMaxQuantity());
        product.setIsPreorder(false);
        product.setCategory(template.getCategory());
        product.setCutoffTime(template.getCutoffTime());
        product.setReadyByTime(template.getReadyByTime());
        product.setBookedQuantity(0);
        return product;
    }

    /** Clones an existing product into a brand-new independent offering for the given date. */
    private Product cloneProduct(Product original, Kitchen kitchen, LocalDate date) {
        Product clone = new Product(kitchen, original.getName(), original.getDescription(),
                original.getPrice(), original.getImageUrl());
        clone.setPriceUnit(original.getPriceUnit());
        clone.setAvailableDate(date);
        clone.setAvailableToday(date != null && date.equals(LocalDate.now()));
        clone.setOrderWindowStart(original.getOrderWindowStart());
        clone.setOrderWindowEnd(original.getOrderWindowEnd());
        clone.setMaxQuantity(original.getMaxQuantity());
        clone.setRemainingQuantity(original.getMaxQuantity());
        clone.setIsPreorder(original.getIsPreorder() != null ? original.getIsPreorder() : false);
        clone.setCategory(original.getCategory());
        clone.setCutoffTime(original.getCutoffTime());
        clone.setReadyByTime(original.getReadyByTime());
        clone.setPreorderType(original.getPreorderType());
        clone.setAvailableUntilDate(original.getAvailableUntilDate());
        clone.setTimeSlots(original.getTimeSlots());
        clone.setBookedQuantity(0);
        return clone;
    }
}

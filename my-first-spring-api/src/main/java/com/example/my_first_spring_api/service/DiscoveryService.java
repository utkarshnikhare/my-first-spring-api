package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.DiscoveryDtos.CategoryTile;
import com.example.my_first_spring_api.dto.DiscoveryDtos.ComparisonOffer;
import com.example.my_first_spring_api.dto.DiscoveryDtos.ItemGroup;
import com.example.my_first_spring_api.dto.DiscoveryDtos.KitchenCard;
import com.example.my_first_spring_api.dto.DiscoveryDtos.KitchenCounts;
import com.example.my_first_spring_api.model.Category;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Buyer discovery engine (Screens 2, 2A, 3 & 7).
 *
 * Tab semantics for Screen 3:
 *  - LIVE_NOW  : kitchen has >= 1 orderable-today item right now
 *  - TOMORROW  : kitchen has items specifically available tomorrow
 *  - PRE_ORDER : kitchen has open pre-order items for future dates
 *  - ALL       : every publicly visible community kitchen (incl. ⚪ closed)
 */
@Service
@Transactional(readOnly = true)
public class DiscoveryService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Autowired
    public DiscoveryService(KitchenRepository kitchenRepository, ProductRepository productRepository,
                            OrderRepository orderRepository) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    private List<Kitchen> visibleKitchens() {
        return kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .collect(Collectors.toList());
    }

    private List<Product> visibleProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getKitchen() != null && KitchenVisibility.isPubliclyVisible(p.getKitchen()))
                .collect(Collectors.toList());
    }

    private static boolean orderableToday(Product p) {
        return Boolean.TRUE.equals(p.getAvailableToday()) && !Boolean.TRUE.equals(p.getIsPreorder()) && !p.isSoldOut();
    }

    private static boolean availableTomorrow(Product p) {
        return LocalDate.now().plusDays(1).equals(p.getAvailableDate());
    }

    private static boolean openPreorder(Product p) {
        return Boolean.TRUE.equals(p.getIsPreorder()) && !p.isSoldOut();
    }

    private boolean hasToday(List<Product> items) { return items.stream().anyMatch(DiscoveryService::orderableToday); }
    private boolean hasTomorrow(List<Product> items) { return items.stream().anyMatch(DiscoveryService::availableTomorrow); }
    private boolean hasPreorder(List<Product> items) { return items.stream().anyMatch(DiscoveryService::openPreorder); }

    private String statusOf(List<Product> items) {
        if (hasToday(items)) return "LIVE_NOW";
        if (hasPreorder(items)) return "PRE_ORDER";
        if (hasTomorrow(items)) return "TOMORROW";
        return "CLOSED";
    }

    private Set<Long> buyerKitchenIds(User buyer) {
        if (buyer == null) return Set.of();
        return orderRepository.findByBuyerOrderByCreatedAtDesc(buyer).stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT && o.getKitchen() != null)
                .map(o -> o.getKitchen().getId())
                .collect(Collectors.toSet());
    }

    // ---------- Screen 3 ----------

    public KitchenCounts getKitchenCounts() {
        List<Kitchen> kitchens = visibleKitchens();
        Map<Long, List<Product>> byKitchen = visibleProducts().stream()
                .collect(Collectors.groupingBy(p -> p.getKitchen().getId()));
        long live = kitchens.stream().filter(k -> hasToday(byKitchen.getOrDefault(k.getId(), List.of()))).count();
        long tomorrow = kitchens.stream().filter(k -> hasTomorrow(byKitchen.getOrDefault(k.getId(), List.of()))).count();
        long preorder = kitchens.stream().filter(k -> hasPreorder(byKitchen.getOrDefault(k.getId(), List.of()))).count();
        return new KitchenCounts(live, tomorrow, preorder, kitchens.size());
    }

    public List<KitchenCard> getKitchens(String tab, User buyer) {
        List<Kitchen> kitchens = visibleKitchens();
        Map<Long, List<Product>> byKitchen = visibleProducts().stream()
                .collect(Collectors.groupingBy(p -> p.getKitchen().getId()));
        Set<Long> ordered = buyerKitchenIds(buyer);

        return kitchens.stream()
                .filter(k -> matchesTab(k, byKitchen.getOrDefault(k.getId(), List.of()), tab))
                .map(k -> toCard(k, byKitchen.getOrDefault(k.getId(), List.of()), ordered))
                .sorted(Comparator
                        .comparing((KitchenCard c) -> "LIVE_NOW".equals(c.getStatus()) ? 0 : 1)
                        .thenComparing(KitchenCard::getDisplayName))
                .collect(Collectors.toList());
    }

    private boolean matchesTab(Kitchen k, List<Product> items, String tab) {
        String t = tab == null ? "LIVE_NOW" : tab.toUpperCase();
        switch (t) {
            case "LIVE_NOW": return hasToday(items);
            case "TOMORROW": return hasTomorrow(items);
            case "PREORDER": return hasPreorder(items);
            case "ALL": return true;
            default: return hasToday(items);
        }
    }

    private KitchenCard toCard(Kitchen k, List<Product> items, Set<Long> orderedKitchenIds) {
        KitchenCard card = new KitchenCard();
        card.setId(k.getId());
        card.setSlug(k.getName());
        card.setDisplayName(k.getDisplayName());
        card.setImageUrl(k.getImageUrl());
        card.setShortDescription(k.getShortDescription());
        card.setStatus(statusOf(items));
        card.setOrderableItemCount((int) items.stream().filter(DiscoveryService::orderableToday).count());
        card.setItemNames(items.stream()
                .filter(p -> orderableToday(p) || openPreorder(p))
                .map(Product::getName)
                .distinct()
                .limit(6)
                .collect(Collectors.toList()));
        card.setPreviouslyOrdered(orderedKitchenIds.contains(k.getId()));
        card.setRating(k.getRating());
        return card;
    }

    // ---------- Screen 2 / 2A ----------

    public List<CategoryTile> getCategoryTiles() {
        Map<Category, Long> counts = visibleProducts().stream()
                .filter(p -> p.getCategory() != null && !p.isSoldOut())
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));
        List<CategoryTile> tiles = new ArrayList<>();
        tiles.add(new CategoryTile("BREAKFAST", "Breakfast", "🌅", counts.getOrDefault(Category.BREAKFAST, 0L)));
        tiles.add(new CategoryTile("LUNCH", "Lunch", "🍛", counts.getOrDefault(Category.LUNCH, 0L)));
        tiles.add(new CategoryTile("DINNER", "Dinner", "🌙", counts.getOrDefault(Category.DINNER, 0L)));
        tiles.add(new CategoryTile("SNACKS", "Snacks", "🥟", counts.getOrDefault(Category.SNACKS, 0L)));
        tiles.add(new CategoryTile("SPECIAL", "Special", "✨", counts.getOrDefault(Category.SPECIAL, 0L)));
        return tiles;
    }

    /** Items grouped by dish name for the "By Items" grid, e.g. "Poha - 4 kitchens". */
    public List<ItemGroup> getItemGroups(Category category) {
        Map<String, List<Product>> byName = visibleProducts().stream()
                .filter(p -> !p.isSoldOut())
                .filter(p -> category == null || p.getCategory() == category)
                .collect(Collectors.groupingBy(Product::getName, LinkedHashMap::new, Collectors.toList()));

        return byName.entrySet().stream()
                .map(e -> {
                    ItemGroup g = new ItemGroup();
                    g.setName(e.getKey());
                    g.setKitchenCount(e.getValue().stream().map(p -> p.getKitchen().getId()).distinct().count());
                    g.setCategory(e.getValue().get(0).getCategory() != null
                            ? e.getValue().get(0).getCategory().name() : null);
                    g.setImageUrl(e.getValue().get(0).getImageUrl());
                    g.setKitchenIds(e.getValue().stream().map(p -> p.getKitchen().getId()).distinct().collect(Collectors.toList()));
                    return g;
                })
                .sorted(Comparator.comparing(ItemGroup::getName))
                .collect(Collectors.toList());
    }

    /** Kitchens having at least one non-sold-out item in the given category (Screen 2A "By Kitchens"). */
    public List<KitchenCard> getKitchensByCategory(Category category, User buyer) {
        Map<Long, List<Product>> byKitchen = visibleProducts().stream()
                .filter(p -> !p.isSoldOut())
                .filter(p -> category == null || p.getCategory() == category)
                .collect(Collectors.groupingBy(p -> p.getKitchen().getId()));
        Set<Long> ordered = buyerKitchenIds(buyer);
        return byKitchen.entrySet().stream()
                .map(e -> toCard(e.getValue().get(0).getKitchen(), e.getValue(), ordered))
                .sorted(Comparator.comparing(KitchenCard::getDisplayName))
                .collect(Collectors.toList());
    }

    public long countItemsInCategory(Category category) {
        return visibleProducts().stream()
                .filter(p -> !p.isSoldOut())
                .filter(p -> category == null || p.getCategory() == category)
                .count();
    }

    // ---------- Screen 7 (search-by-item comparison) ----------

    public List<ItemGroup> searchItemGroups(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return getItemGroups(null).stream()
                .filter(g -> g.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public List<ComparisonOffer> getComparisonOffers(String itemName, User buyer) {
        String q = itemName == null ? "" : itemName.trim().toLowerCase();
        List<Product> matches = visibleProducts().stream()
                .filter(p -> p.getName().toLowerCase().contains(q))
                .filter(p -> orderableToday(p) || openPreorder(p))
                .collect(Collectors.toList());

        Set<Long> ordered = buyerKitchenIds(buyer);
        return matches.stream()
                .map(p -> {
                    ComparisonOffer o = new ComparisonOffer();
                    Kitchen k = p.getKitchen();
                    o.setKitchenId(k.getId());
                    o.setKitchenSlug(k.getName());
                    o.setKitchenDisplayName(k.getDisplayName());
                    o.setKitchenImageUrl(k.getImageUrl());
                    o.setTagline(k.getShortDescription());
                    o.setStatus(ordered.contains(k.getId()) ? "PREVIOUSLY_ORDERED" : statusOf(List.of(p)));
                    o.setPrice(p.getPrice());
                    o.setPriceUnit(p.getPriceUnit());
                    o.setOrderBy(p.getCutoffTime());
                    o.setReadyBy(p.getReadyByTime());
                    o.setSoldOut(p.isSoldOut());
                    o.setPreorder(p.getIsPreorder());
                    o.setAvailableDate(p.getAvailableDate());
                    return o;
                })
                .sorted(Comparator.comparing(o -> o.getKitchenDisplayName() == null ? "" : o.getKitchenDisplayName()))
                .collect(Collectors.toList());
    }
}

package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenDetailDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.QuickPostDto;
import com.example.my_first_spring_api.dto.SearchResultDto;
import com.example.my_first_spring_api.exception.KitchenNotEligibleException;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.QuickPost;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.QuickPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.my_first_spring_api.AppTimeZone;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class KitchenService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final QuickPostRepository quickPostRepository;
    private final AnalyticsService analyticsService;
    /** Resolves public-URL slugs (and their aliases) to a kitchen. Field-injected so manually-constructed instances in tests keep working. */
    @Autowired
    private SlugService slugService;
    /** Recurring projection: today's resolved occurrence values on buyer DTOs (V2 §14). Field-injected so manually-constructed instances in tests keep working. */
    @Autowired
    private RecurringScheduleService recurringScheduleService;

    @Autowired
    public KitchenService(KitchenRepository kitchenRepository, ProductRepository productRepository,
                          QuickPostRepository quickPostRepository, AnalyticsService analyticsService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.quickPostRepository = quickPostRepository;
        this.analyticsService = analyticsService;
    }

    public KitchenDetailDto getKitchenByName(String name, User buyer) {
        Kitchen kitchen = kitchenRepository.findByName(name)
                .orElseThrow(() -> new KitchenNotFoundException(name));
        if (KitchenVisibility.isPaused(kitchen)) {
            return closedDetail(kitchen);
        }
        if (!KitchenVisibility.isPubliclyVisible(kitchen) || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            throw new KitchenNotFoundException(name);
        }
        analyticsService.record(AnalyticsService.EV_MENU_VIEW, null, null,
                kitchen.getId(), kitchen.getDisplayName());
        KitchenDto kitchenDto = toKitchenDto(kitchen);
        List<ProductDto> products = productRepository
                .findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
        List<ProductDto> preorderProducts = products.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsPreorder())).collect(Collectors.toList());
        List<ProductDto> regularProducts = products.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsPreorder())).collect(Collectors.toList());
        KitchenDetailDto detailDto = new KitchenDetailDto(kitchenDto, regularProducts);
        detailDto.setPreorderProducts(preorderProducts);
        detailDto.setQuickPosts(toQuickPostDtos(quickPostRepository.findByKitchenAndPostedDateOrderByCreatedAtDesc(kitchen, LocalDate.now())));
        return detailDto;
    }

    public KitchenDto getKitchenById(Long id) {
        Kitchen kitchen = kitchenRepository.findById(id)
                .orElseThrow(() -> new KitchenNotFoundException(id));
        return toKitchenDto(kitchen);
    }

    /**
     * Public kitchen storefront (Screen 4): identity + offerings split strictly
     * into "Available Today" and "Pre-order" sections.
     */
    public KitchenDetailDto getKitchenDetailById(Long id, User buyer) {
        Kitchen kitchen = kitchenRepository.findById(id)
                .orElseThrow(() -> new KitchenNotFoundException(id));
        if (KitchenVisibility.isPaused(kitchen)) {
            return closedDetail(kitchen);
        }
        if (!KitchenVisibility.isPubliclyVisible(kitchen) || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            // Deliberately a KitchenNotEligibleException (a KitchenNotFoundException):
            // the 404 concealment is preserved, but the buyer now gets the reason and
            // an "Explore kitchens" route out instead of a bare "not found".
            throw new KitchenNotEligibleException(id);
        }
        KitchenDto kitchenDto = toKitchenDto(kitchen);
        List<ProductDto> all = productRepository.findByKitchen(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
        LocalDate todayDate = LocalDate.now(AppTimeZone.ZONE);
        List<ProductDto> preorder = all.stream()
                .filter(p -> {
                    if (Boolean.TRUE.equals(p.getRecurring())) {
                        return p.getNextOccurrenceDate() != null
                                && p.getNextOccurrenceDate().isAfter(todayDate);
                    }
                    if (Boolean.TRUE.equals(p.getIsPreorder())) {
                        if (p.getAvailableDate() != null && p.getAvailableDate().isBefore(todayDate)) {
                            return false;
                        }
                        if ("HISTORY".equalsIgnoreCase(p.getLifecycleState())) {
                            return false;
                        }
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
        List<ProductDto> today = all.stream()
                .filter(p -> {
                    if (Boolean.TRUE.equals(p.getIsPreorder())) {
                        return false;
                    }
                    if (Boolean.TRUE.equals(p.getRecurring())) {
                        return p.getNextOccurrenceDate() != null
                                && !p.getNextOccurrenceDate().isAfter(todayDate);
                    }
                    if (!Boolean.TRUE.equals(p.getAvailableToday())) {
                        return false;
                    }
                    if (p.getAvailableDate() != null && p.getAvailableDate().isBefore(todayDate)) {
                        return false;
                    }
                    if ("HISTORY".equalsIgnoreCase(p.getLifecycleState())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        KitchenDetailDto dto = new KitchenDetailDto(kitchenDto, today);
        dto.setPreorderProducts(preorder);
        dto.setQuickPosts(toQuickPostDtos(quickPostRepository.findByKitchenAndPostedDateOrderByCreatedAtDesc(kitchen, todayDate)));
        return dto;
    }
    /**
     * Public storefront resolved from a public-URL slug (Screen 4), the backend
     * for {@code GET /api/kitchens/by-slug/{slug}} and the {@code /kitchen/{slug}}
     * page. Returns the SAME {@link KitchenDetailDto} shape the id-based page
     * uses, so the buyer experience and business rules are identical whichever
     * way the kitchen was opened.
     *
     * <p>Status-based visibility is enforced here, server-side:</p>
     * <ul>
     *   <li>unknown slug or a PENDING/CHANGES_REQUESTED/REJECTED/REMOVED seller →
     *       concealed 404 (never exposed, never in discovery);</li>
     *   <li>ADMIN-suspended ({@code storefrontPaused} or seller SUSPENDED) →
     *       concealed 404 for browsing too, so a suspended storefront is simply
     *       not reachable;</li>
     *   <li>SELLER-paused (the seller's own daily "available today" toggle off) →
     *       the "temporarily closed" detail with no offerings, so the page can say
     *       so and still offer Explore Other Kitchens;</li>
     *   <li>APPROVED + ACTIVE but outside the buyer's society → the same
     *       {@link KitchenNotEligibleException} 404 the id-based page throws.</li>
     * </ul>
     */
    public KitchenDetailDto getKitchenDetailBySlug(String slug, User buyer) {
        Kitchen kitchen = slugService != null
                ? slugService.resolveBySlug(slug).orElse(null)
                : kitchenRepository.findBySlug(slug).orElse(null);
        if (kitchen == null) {
            throw new KitchenNotFoundException(slug);
        }
        // A removed storefront keeps its records but is no longer available.
        if (kitchen.getSeller() != null && kitchen.getSeller().isStorefrontRemoved()) {
            throw new KitchenNotFoundException(slug);
        }
        // An Admin pause/suspension hides the storefront entirely (no orders,
        // not browsable). The seller cannot override it.
        if (KitchenVisibility.isAdminSuspended(kitchen)) {
            throw new KitchenNotFoundException(slug);
        }
        // SELLER_PAUSED: the seller's own daily toggle is off. Show the closed
        // storefront (no offerings) rather than a 404, so the buyer sees it is
        // temporarily closed and can explore other kitchens.
        if (KitchenVisibility.isPaused(kitchen)) {
            return closedDetail(kitchen);
        }
        // PENDING / CHANGES_REQUESTED / REJECTED sellers, or a buyer outside the
        // service area, are concealed behind the same 404 as a missing kitchen.
        if (!KitchenVisibility.isPubliclyVisible(kitchen) || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            throw new KitchenNotEligibleException(kitchen.getId());
        }
        analyticsService.record(AnalyticsService.EV_MENU_VIEW, null, null,
                kitchen.getId(), kitchen.getDisplayName());
        return getKitchenDetailById(kitchen.getId(), buyer);
    }



    public ProductDto getProductById(Long id, User buyer) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new com.example.my_first_spring_api.exception.ProductNotFoundException(id));
        if (product.getKitchen() != null && (!KitchenVisibility.isPubliclyVisible(product.getKitchen()) || !KitchenVisibility.isServiceAreaVisible(product.getKitchen(), buyer))) {
            throw new com.example.my_first_spring_api.exception.ProductNotFoundException(id);
        }
        return toProductDto(product);
    }

    public List<ProductDto> getProductsByKitchenName(String kitchenName, User buyer) {
        Kitchen kitchen = kitchenRepository.findByName(kitchenName)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenName));
        if (!KitchenVisibility.isPubliclyVisible(kitchen) || !KitchenVisibility.isServiceAreaVisible(kitchen, buyer)) {
            throw new KitchenNotFoundException(kitchenName);
        }
        return productRepository.findByKitchenAndAvailableTodayTrueOrderByCreatedAtDesc(kitchen).stream()
                .map(this::toProductDto).collect(Collectors.toList());
    }

    public SearchResultDto search(String query, User buyer) {
        List<ProductDto> products = productRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::toProductDto)
                .filter(p -> !p.isOrdersPaused())
                .filter(p -> p.getKitchenId() == null
                        || kitchenRepository.findById(p.getKitchenId())
                            .map(k -> KitchenVisibility.isPubliclyVisible(k) && KitchenVisibility.isServiceAreaVisible(k, buyer))
                            .orElse(false))
                .collect(Collectors.toList());

        Map<Long, KitchenDto> kitchens = new LinkedHashMap<>();
        kitchenRepository.findAll().stream()
                .filter(KitchenVisibility::isPubliclyVisible)
                .filter(k -> KitchenVisibility.isServiceAreaVisible(k, buyer))
                .filter(k -> k.getDisplayName().toLowerCase().contains(query.toLowerCase())
                        || k.getName().toLowerCase().contains(query.toLowerCase()))
                .forEach(k -> kitchens.put(k.getId(), toKitchenDto(k)));
        for (ProductDto product : products) {
            if (product.getKitchenId() != null && !kitchens.containsKey(product.getKitchenId())) {
                kitchenRepository.findById(product.getKitchenId())
                        .filter(k -> KitchenVisibility.isPubliclyVisible(k) && KitchenVisibility.isServiceAreaVisible(k, buyer))
                        .ifPresent(k -> kitchens.put(k.getId(), toKitchenDto(k)));
            }
        }
        return new SearchResultDto(products, new ArrayList<>(kitchens.values()));
    }

    private KitchenDto toKitchenDto(Kitchen kitchen) {
        KitchenDto dto = new KitchenDto(kitchen.getId(), kitchen.getName(), kitchen.getDisplayName(),
                kitchen.getDescription(), kitchen.getImageUrl(), kitchen.getRating(),
                kitchen.getAvailableToday(), kitchen.getSeller() != null ? kitchen.getSeller().getId() : null);
        dto.setShortDescription(kitchen.getShortDescription());
        dto.setSpeciality(kitchen.getSpeciality());
        dto.setSociety(kitchen.getSociety());
        dto.setServiceAreas(kitchen.getServiceAreas());
        dto.setBuilding(kitchen.getBuilding());
        dto.setWhatsappLink(kitchen.getWhatsappLink());
        dto.setInstagramLink(kitchen.getInstagramLink());
        dto.setUpiId(kitchen.getUpiId());
        dto.setOrderDeadline(kitchen.getOrderDeadline());
        dto.setSellerType(kitchen.getSellerType() != null ? kitchen.getSellerType().name() : null);
        dto.setPaused(KitchenVisibility.isPaused(kitchen));
        return dto;
    }

    private List<QuickPostDto> toQuickPostDtos(List<QuickPost> posts) {
        return posts.stream().map(post -> new QuickPostDto(post.getId(), post.getMessage(),
                post.getImageData(), post.getPostedDate(), post.getCreatedAt())).toList();
    }

    private KitchenDetailDto closedDetail(Kitchen kitchen) {
        KitchenDetailDto detail = new KitchenDetailDto(toKitchenDto(kitchen), List.of());
        detail.setPreorderProducts(List.of());
        return detail;
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
        String lifecycle = OfferingTiming.lifecycleState(product, LocalDate.now(AppTimeZone.ZONE), LocalTime.now(AppTimeZone.ZONE));
        dto.setOrdersClosed("ORDERS_CLOSED".equals(lifecycle) || "HISTORY".equals(lifecycle));
        dto.setLifecycleState(lifecycle);
        // Recurring: project today's resolved occurrence (or the next
        // occurrence's date) so the buyer sees the real per-day values and
        // the "Pre-order + delivery date" context without knowing the engine.
        // Guarded: some tests construct this service manually without a
        // recurring bean, in which case decoration is skipped (one-time only
        // paths).
        if (recurringScheduleService != null) recurringScheduleService.decorateProductDto(product, dto);
        return dto;
    }
}

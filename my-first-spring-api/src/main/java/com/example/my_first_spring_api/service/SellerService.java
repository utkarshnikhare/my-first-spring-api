package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenCreateDto;
import com.example.my_first_spring_api.dto.KitchenDto;
import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.OrderDto;
import com.example.my_first_spring_api.dto.SellerOrderSummaryRowDto;
import com.example.my_first_spring_api.dto.ProductCreateDto;
import com.example.my_first_spring_api.dto.ProductDto;
import com.example.my_first_spring_api.dto.ProductUpdateDto;
import com.example.my_first_spring_api.dto.SellerOfferingEditDto;
import com.example.my_first_spring_api.dto.AuthResponseDto;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.dto.CoverageOptionDto;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.GpayIdentifierType;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerPaymentMode;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OrderItemRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.AppTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerService {

    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final FeatureService featureService;
    private final SocietyDirectory societyDirectory;
    private final LocationService locationService;
    /** Public-URL slug authority. Field-injected so manually-constructed test instances keep working. */
    @Autowired
    private SlugService slugService;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private AdminAuditService auditService;

    @Autowired(required = false)
    private AnalyticsService analyticsService;

    @Autowired
    public SellerService(KitchenRepository kitchenRepository,
                         ProductRepository productRepository,
                         OrderItemRepository orderItemRepository,
                         OrderService orderService,
                         FeatureService featureService,
                         SocietyDirectory societyDirectory,
                         LocationService locationService) {
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.featureService = featureService;
        this.societyDirectory = societyDirectory;
        this.locationService = locationService;
    }

    public KitchenDto createKitchen(KitchenCreateDto dto, User seller) {
        String desiredSlug = dto.getName() == null ? "" : dto.getName().trim();
        if (slugService != null && SlugService.slugify(desiredSlug).isBlank()) {
            throw new IllegalArgumentException("Choose a kitchen URL containing letters or numbers.");
        }
        // The slug is the authoritative public identifier; the legacy unique
        // `name` column is kept in step (same value) so every existing
        // findByName(...) caller keeps resolving the same kitchen. When the
        // SlugService is present the slug is reserved race-safely: the DB unique
        // constraint is the final arbiter and a lost race retries with -2, -3...
        String base = slugService != null ? SlugService.slugify(desiredSlug) : desiredSlug.toLowerCase();
        Kitchen kitchen = new Kitchen(base, dto.getDisplayName(), dto.getDescription(), dto.getImageUrl(), seller);
        kitchen.setSlug(base);
        kitchen.setShortDescription(dto.getShortDescription());
        kitchen.setSpeciality(dto.getSpeciality());
        kitchen.setSociety(dto.getSociety());
        kitchen.setBuilding(dto.getBuilding());
        kitchen.setWhatsappLink(dto.getWhatsappLink());
        kitchen.setInstagramLink(dto.getInstagramLink());
        SellerPaymentMode paymentMode = SellerPaymentConfiguration.parseMode(dto.getAcceptedPaymentMode());
        GpayIdentifierType gpayIdentifierType =
                SellerPaymentConfiguration.parseGpayIdentifierType(dto.getGpayIdentifierType());
        SellerPaymentConfiguration.validate(paymentMode, dto.getUpiId(),
                gpayIdentifierType, dto.getGpayIdentifier());
        kitchen.setUpiId(cleanPaymentIdentifier(dto.getUpiId()));
        kitchen.setAcceptedPaymentMode(paymentMode);
        kitchen.setGpayIdentifierType(gpayIdentifierType);
        kitchen.setGpayIdentifier(cleanPaymentIdentifier(dto.getGpayIdentifier()));
        kitchen.setAvailableToday(dto.getAvailableToday() != null ? dto.getAvailableToday() : true);
        if (dto.getSellerType() != null && !dto.getSellerType().isBlank()) {
            try {
                com.example.my_first_spring_api.model.SellerType st = com.example.my_first_spring_api.model.SellerType.valueOf(dto.getSellerType().toUpperCase());
                kitchen.setSellerType(st);
                seller.setSellerCategory(st.name());
                if (userRepository != null) {
                    userRepository.save(seller);
                }
            }
            catch (IllegalArgumentException ignored) {}
        }
        // Reserve the slug FIRST, race-safely, so the very first INSERT is the
        // one the DB unique constraint guards; coverage is applied afterwards as
        // an update. The whole method is one transaction, so a coverage
        // validation failure rolls the reservation back with it.
        if (slugService != null) {
            slugService.reserveUniqueSlug(kitchen, base);
        } else {
            kitchen.setName(base);
            kitchenRepository.save(kitchen);
        }
        // The ID payload is authoritative when present: it replaces the kitchen's
        // COMPLETE coverage and rebuilds the display string from the saved records,
        // so the seller cannot accidentally cover a society they did not tick.
        // Legacy callers that send only the name string keep the name-based path.
        if (dto.getSocietyIds() != null) {
            locationService.saveSellerCoverage(kitchen, dto.getAreaId(), dto.getSocietyIds());
        } else {
            // Validation first (unchanged), then the ID-backed coverage is kept in step.
            locationService.applyCoverageFromNames(kitchen,
                    societyDirectory.validateAndNormalize(dto.getServiceAreas()));
        }
        return toKitchenDto(kitchen);
    }

    public KitchenDto updateKitchen(Long kitchenId, KitchenUpdateDto dto, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        if (dto.getSociety() != null) throw new IllegalArgumentException("Primary Society is a protected field and cannot be changed");
        if (dto.getName() != null) throw new IllegalArgumentException("Name is a protected field and cannot be changed");
        if (dto.getBuilding() != null) throw new IllegalArgumentException("Building is a protected field and cannot be changed");
        if (dto.getDisplayName() != null) {
            if (dto.getDisplayName().trim().isEmpty()) {
                throw new IllegalArgumentException("Kitchen Name cannot be blank.");
            }
            kitchen.setDisplayName(dto.getDisplayName().trim());
        }
        if (dto.getDescription() != null) kitchen.setDescription(dto.getDescription());
        if (dto.getShortDescription() != null) kitchen.setShortDescription(dto.getShortDescription());
        if (dto.getSpeciality() != null) kitchen.setSpeciality(dto.getSpeciality());
        if (dto.getImageUrl() != null) kitchen.setImageUrl(dto.getImageUrl());
        if (dto.getSocietyIds() != null) {
            // Coverage is authoritative by ID, so the ID set must follow this edit -
            // otherwise a change made here is silently ignored at checkout.
            locationService.saveSellerCoverage(kitchen, dto.getAreaId(), dto.getSocietyIds());
        } else if (dto.getServiceAreas() != null) {
            // Legacy name-string callers: still funnelled through the same shared
            // method so the two representations cannot drift.
            locationService.applyCoverageFromNames(kitchen,
                    societyDirectory.validateAndNormalize(dto.getServiceAreas()));
        }
        if (dto.getWhatsappLink() != null) kitchen.setWhatsappLink(dto.getWhatsappLink());
        if (dto.getWhatsappNumber() != null) {
            String cleanWa = dto.getWhatsappNumber().trim();
            if (kitchen.getSeller() != null) {
                kitchen.getSeller().setSellerWhatsappNumber(cleanWa.isEmpty() ? null : cleanWa);
                if (userRepository != null) {
                    userRepository.save(kitchen.getSeller());
                }
            }
        }
        if (dto.getInstagramLink() != null) kitchen.setInstagramLink(dto.getInstagramLink());
        SellerPaymentMode paymentMode = dto.getAcceptedPaymentMode() == null
                ? kitchen.getAcceptedPaymentMode()
                : SellerPaymentConfiguration.parseMode(dto.getAcceptedPaymentMode());
        GpayIdentifierType gpayIdentifierType = dto.getGpayIdentifierType() == null
                ? kitchen.getGpayIdentifierType()
                : SellerPaymentConfiguration.parseGpayIdentifierType(dto.getGpayIdentifierType());
        String upiId = dto.getUpiId() == null ? kitchen.getUpiId() : cleanPaymentIdentifier(dto.getUpiId());
        String gpayIdentifier = dto.getGpayIdentifier() == null
                ? kitchen.getGpayIdentifier() : cleanPaymentIdentifier(dto.getGpayIdentifier());
        if (dto.getAcceptedPaymentMode() != null || dto.getGpayIdentifierType() != null
                || dto.getGpayIdentifier() != null || dto.getUpiId() != null) {
            SellerPaymentConfiguration.validate(paymentMode, upiId, gpayIdentifierType, gpayIdentifier);
            kitchen.setAcceptedPaymentMode(paymentMode);
            kitchen.setGpayIdentifierType(gpayIdentifierType);
            kitchen.setGpayIdentifier(gpayIdentifier);
            kitchen.setUpiId(upiId);
        }
        if (dto.getGalleryImages() != null) kitchen.setGalleryImages(dto.getGalleryImages());
        if (dto.getAvailableToday() != null) kitchen.setAvailableToday(dto.getAvailableToday());
        if (dto.getSellerType() != null && !dto.getSellerType().isBlank()) {
            try {
                com.example.my_first_spring_api.model.SellerType st = com.example.my_first_spring_api.model.SellerType.valueOf(dto.getSellerType().toUpperCase());
                kitchen.setSellerType(st);
                seller.setSellerCategory(st.name());
                if (userRepository != null) {
                    userRepository.save(seller);
                }
            }
            catch (IllegalArgumentException ignored) {}
        }
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderDetailForSeller(Long orderId, User seller) {
        return orderService.getOrderDtoForSeller(orderId, seller);
    }

    public KitchenDto pauseKitchen(Long kitchenId, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        // An Admin suspension is not the seller's to change in either direction.
        if (KitchenVisibility.isAdminSuspended(kitchen)) {
            throw new SellerNotAuthorizedException(
                    "This storefront was suspended by an Admin and cannot be changed by the seller.");
        }
        kitchen.setAvailableToday(false);
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    public KitchenDto resumeKitchen(Long kitchenId, User seller) {
        Kitchen kitchen = getOwnedKitchen(kitchenId, seller);
        // Step 6: a seller can pause/resume their OWN SELLER_PAUSED state only.
        // They can NEVER override an ADMIN_SUSPENDED storefront (403) - only an
        // Admin can lift that.
        if (KitchenVisibility.isAdminSuspended(kitchen)) {
            throw new SellerNotAuthorizedException(
                    "This storefront was suspended by an Admin and cannot be resumed by the seller.");
        }
        kitchen.setAvailableToday(true);
        return toKitchenDto(kitchenRepository.save(kitchen));
    }

    @Transactional(readOnly = true)
    public KitchenDto getMyKitchen(User seller) {
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        return kitchens.isEmpty() ? null : toKitchenDto(kitchens.get(0));
    }

    /**
     * Existing societies the seller can select for "Who can order from me?"
     * in Manage Kitchen. Derived from existing platform data â€” V1 references
     * society IDs only (no GPS/maps/radius).
     */
    @Transactional(readOnly = true)
    public List<String> getKnownSocieties() {
        return societyDirectory.findAllSocieties();
    }

    /**
     * The Area/Society choices for the coverage picker: ACTIVE areas, each with its
     * ACTIVE societies, carrying the IDs the write path requires. Read from the
     * Admin-owned master via {@link LocationService}, so no location name is ever
     * hardcoded here and a newly created society is offered without a redeploy.
     */
    public java.util.List<CoverageOptionDto> getCoverageOptions() {
        return locationService.getCoverageOptions();
    }

    public ProductDto createProduct(Long kitchenId, ProductCreateDto dto, User seller) {
        Kitchen kitchen = resolveOfferingKitchen(kitchenId, seller);
        LocalDate today = AppTimeZone.today();
        boolean quickPost = Boolean.TRUE.equals(dto.getQuickPost());
        String quickPostRequestId = null;
        if (quickPost) {
            quickPostRequestId = dto.getQuickPostRequestId() == null
                    ? null : dto.getQuickPostRequestId().trim();
            if (quickPostRequestId == null || quickPostRequestId.isBlank()
                    || quickPostRequestId.length() > 80) {
                throw new IllegalArgumentException(
                        "Quick Post request reference is required and must be at most 80 characters.");
            }
            kitchen = kitchenRepository.findByIdForUpdate(kitchen.getId())
                    .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        }
        boolean recurringRequest = dto.getRecurringSchedule() != null;
        if (recurringRequest) {
            throw new IllegalArgumentException(
                    "Creating new recurring schedules is no longer supported. Existing schedules can still be managed.");
        }
        LocalDate offeringDate = quickPost ? today
                : (dto.getAvailableDate() != null ? dto.getAvailableDate() : today);
        if (quickPost && ((dto.getAvailableDate() != null && !today.equals(dto.getAvailableDate()))
                || Boolean.TRUE.equals(dto.getIsPreorder()))) {
            throw new IllegalArgumentException("Quick Posts must be one-time offerings for today.");
        }
        String description = dto.getDescription() == null ? ""
                : (quickPost ? dto.getDescription().trim() : dto.getDescription());
        if (quickPost) {
            if (description.isBlank()) {
                throw new IllegalArgumentException("Quick Post message is required.");
            }
            if (description.length() > 5000) {
                throw new IllegalArgumentException("Quick Post message is too long.");
            }
            if (dto.getPrice() == null || dto.getPrice().signum() <= 0) {
                throw new IllegalArgumentException("Price must be greater than 0.");
            }
        }
        String productName = quickPost ? quickPostTitle(description) : dto.getName();
        String quickPostImageData = quickPost
                ? validateQuickPostImageData(dto.getQuickPostImageData()) : null;
        if (quickPost) {
            Product existing = productRepository.findByKitchenAndQuickPostRequestId(
                    kitchen, quickPostRequestId).orElse(null);
            if (existing != null) {
                boolean sameRequest = existing.isQuickPost()
                        && existing.getDescription().equals(description)
                        && existing.getPrice().compareTo(dto.getPrice()) == 0
                        && Objects.equals(existing.getQuickPostImageData(), quickPostImageData);
                if (sameRequest) return toProductDto(existing);
                throw new IllegalArgumentException(
                        "Quick Post request reference is already used for a different post.");
            }
        }
        boolean preorder = dto.getIsPreorder() != null
                ? dto.getIsPreorder() : offeringDate.isAfter(today);
        if (offeringDate.isAfter(today) && !preorder) {
            preorder = true;
        }
        if (preorder && !offeringDate.isAfter(today)) {
            preorder = false;
        }
        // Validate authoritative offering input before platform feature gating so
        // malformed requests receive the actionable field error, not a feature error.
        String orderWindowStart = quickPost ? null
                : OfferingTiming.normalizeHhmm(dto.getOrderWindowStart(), "Orders Open");
        String orderWindowEnd = quickPost ? "23:59"
                : dto.getOrderWindowEnd() != null && !dto.getOrderWindowEnd().isBlank()
                    ? OfferingTiming.normalizeHhmm(dto.getOrderWindowEnd(), "Orders Close")
                    : null;
        String readyByTime = quickPost ? null
                : (dto.getReadyByTime() == null ? null : dto.getReadyByTime().trim());
        Integer maxQuantity = quickPost ? null : dto.getMaxQuantity();
        if (maxQuantity != null && maxQuantity < 0) {
            throw new IllegalArgumentException("Quantity Available cannot be negative; leave blank for unlimited.");
        }
        if (maxQuantity != null && maxQuantity == 0) {
            throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
        }
        // Server-side enforcement of the Create Offering timing rules so
        // impossible combinations are never persisted, even if the client
        // validation is bypassed.
        boolean isHomemade = isHomemadeProductDto(dto, kitchen);
        if (kitchen.getSellerType() == SellerType.KITCHEN && isHomemade) {
            throw new IllegalArgumentException("Seller is only approved for Kitchen offerings.");
        }
        if (kitchen.getSellerType() == SellerType.HOMEMADE_PRODUCTS && !isHomemade) {
            throw new IllegalArgumentException("Seller is only approved for Homemade products.");
        }

        if (quickPost) {
            // Quick Posts have no structured schedule: their server-authored
            // publication date and end-of-day cutoff enforce same-day ordering.
        } else if (isHomemade && !Boolean.TRUE.equals(preorder) && (orderWindowEnd == null || orderWindowEnd.isBlank())) {
            // Section 8.3 & 8.4: Always Available & Made on Request do not require Orders Close.
            if (readyByTime == null || readyByTime.isBlank()) {
                readyByTime = "Usually ready in 1 day";
            }
        } else {
            // Cross-day tolerance: a client-computed readyBy can land a day (or
            // more) before the offering date near a day boundary; rebase its
            // time-of-day onto the offering date instead of rejecting a valid
            // offering. Explicit "today"/"tomorrow" intent stays strict below.
            readyByTime = OfferingTiming.rebaseReadyByTimeForOffering(readyByTime, offeringDate);
            orderWindowEnd = OfferingTiming.validateNewOffering(offeringDate, preorder,
                    orderWindowStart, orderWindowEnd, readyByTime);
        }
        LocalDateTime ordersOpenFrom = !quickPost && dto.getOrderOpenDate() != null
                ? OfferingTiming.validateOrdersOpenFrom(dto.getOrderOpenDate(), dto.getOrderOpenTime(),
                        offeringDate, orderWindowEnd, readyByTime, today, AppTimeZone.time())
                : null;
        assertFeatureCompliance(seller, preorder, offeringDate, productName);
        Product product = new Product(kitchen, productName, description, dto.getPrice(),
                quickPost ? null : dto.getImageUrl());
        product.setQuickPost(quickPost);
        product.setQuickPostImageData(quickPostImageData);
        product.setQuickPostRequestId(quickPostRequestId);
        product.setDescription(description);
        product.setPriceUnit(quickPost ? "unit"
                : (dto.getPriceUnit() != null ? dto.getPriceUnit() : "portion"));
        product.setUnit(quickPost ? "unit" : (dto.getUnit() != null ? dto.getUnit() : "portion"));
        product.setAvailableToday(quickPost || dto.getAvailableToday() == null
                ? Boolean.TRUE : dto.getAvailableToday());
        product.setIsVeg(dto.getIsVeg() != null ? dto.getIsVeg() : Boolean.TRUE);
        product.setAvailableDate(offeringDate);
        product.setOrderWindowStart(orderWindowStart);
        product.setOrdersOpenFrom(ordersOpenFrom);
        product.setOrderWindowEnd(orderWindowEnd);
        // Keep the legacy column aligned for old clients/seeded records. Buyer
        // orderability uses orderWindowEnd first and this value only as fallback.
        product.setCutoffTime(orderWindowEnd);
        product.setMaxQuantity(maxQuantity);
        product.setRemainingQuantity(maxQuantity);
        product.setIsPreorder(preorder);
        product.setReadyByTime(readyByTime);

        String category = joinCategories(dto.getCategories());
        if (category == null || category.isBlank()) {
            if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
                category = dto.getCategories().get(0).trim().toUpperCase();
            }
        }
        if (category == null || category.isBlank()) {
            category = isHomemade ? "HOMEMADE" : "DINNER";
        }
        if (isHomemade && !category.toUpperCase().contains("HOMEMADE")) {
            category = "HOMEMADE," + category;
        }
        product.setCategory(category);

        return toProductDto(productRepository.save(product));
    }

    private String quickPostTitle(String message) {
        return message.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .map(line -> line.length() > 160 ? line.substring(0, 160) : line)
                .orElse("Quick Post");
    }

    public ProductDto updateProduct(Long productId, ProductUpdateDto dto, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        LocalDate today = AppTimeZone.today();
        boolean effectivePreorder = dto.getIsPreorder() != null
                ? dto.getIsPreorder()
                : (dto.getAvailableDate() != null
                    ? dto.getAvailableDate().isAfter(today)
                    : Boolean.TRUE.equals(product.getIsPreorder()));
        java.time.LocalDate effectiveDate = dto.getAvailableDate() != null
                ? dto.getAvailableDate() : (product.getAvailableDate() != null
                    ? product.getAvailableDate()
                    : (effectivePreorder ? today.plusDays(1) : today));
        if (effectiveDate != null && effectiveDate.isAfter(today) && !effectivePreorder) {
            throw new IllegalArgumentException("Future offerings must be pre-orders.");
        }
        if (effectivePreorder && (effectiveDate == null || !effectiveDate.isAfter(today))) {
            throw new IllegalArgumentException("Pre-orders must be for a future offering date.");
        }
        assertFeatureCompliance(seller, effectivePreorder, effectiveDate, product.getName());

        // Backend-authoritative "has orders" test (Requirement 5): derived from
        // persisted order items only. Draft carts are excluded so that an
        // abandoned draft never locks a seller out of editing their own offering.
        boolean hasOrders = !orderItemRepository
                .findByProductIdAndOrderOrderStatusNot(productId, OrderStatus.DRAFT)
                .isEmpty();
        String effectiveOpen = dto.getOrderWindowStart() != null
                ? OfferingTiming.normalizeHhmm(dto.getOrderWindowStart(), "Orders Open")
                : product.getOrderWindowStart();
        LocalDate effectiveOpenDate = dto.getOrderOpenDate() != null
                ? dto.getOrderOpenDate()
                : (product.getOrdersOpenFrom() != null
                    ? product.getOrdersOpenFrom().toLocalDate() : AppTimeZone.today());
        String effectiveOpenTime = dto.getOrderOpenTime() != null
                ? dto.getOrderOpenTime()
                : (product.getOrdersOpenFrom() != null
                    ? product.getOrdersOpenFrom().toLocalTime().toString().substring(0, 5) : null);
        String effectiveCloseInput = dto.getOrderWindowEnd() != null
                ? dto.getOrderWindowEnd()
                : (dto.getCutoffTime() != null ? dto.getCutoffTime() : product.getOrderWindowEnd());
        String effectiveClose = effectiveCloseInput != null
                ? OfferingTiming.requireOrdersClose(effectiveCloseInput)
                : OfferingTiming.resolveOrdersClose(product);
        String effectiveReady = dto.getReadyByTime() != null
                ? dto.getReadyByTime().trim() : product.getReadyByTime();
        if (dto.getMaxQuantity() != null && dto.getMaxQuantity() < 0) {
            throw new IllegalArgumentException("Quantity Available cannot be negative; leave blank for unlimited.");
        }
        if (dto.getMaxQuantity() != null && dto.getMaxQuantity() == 0) {
            throw new IllegalArgumentException("Quantity Available must be at least 1; leave blank for unlimited.");
        }
        if (dto.getMaxQuantity() != null && dto.getRemainingQuantity() != null
                && dto.getRemainingQuantity() > dto.getMaxQuantity()) {
            throw new IllegalArgumentException("Remaining quantity cannot exceed Quantity Available.");
        }
        if (dto.getRemainingQuantity() != null && dto.getRemainingQuantity() < 0) {
            throw new IllegalArgumentException("Remaining quantity cannot be negative.");
        }
        boolean timingChanged = dto.getAvailableDate() != null || dto.getOrderWindowStart() != null
                || dto.getOrderWindowEnd() != null || dto.getCutoffTime() != null
                || dto.getReadyByTime() != null || dto.getIsPreorder() != null
                || dto.getOrderOpenDate() != null || dto.getOrderOpenTime() != null;
        LocalDateTime effectiveOrdersOpenFrom = product.getOrdersOpenFrom();
        if (timingChanged) {
            // Same cross-day tolerance as createProduct: rebase an early
            // client-computed readyBy onto the effective offering date so a
            // day-boundary skew is corrected instead of rejected.
            effectiveReady = OfferingTiming.rebaseReadyByTimeForOffering(effectiveReady, effectiveDate);
            effectiveClose = OfferingTiming.validateNewOffering(effectiveDate, effectivePreorder,
                    effectiveOpen, effectiveClose, effectiveReady);
            boolean ordersOpenFromChanged = (dto.getOrderOpenDate() != null || dto.getOrderOpenTime() != null)
                    && !OfferingTiming.matchesOrdersOpenFrom(
                            effectiveOrdersOpenFrom, effectiveOpenDate, effectiveOpenTime);
            if (ordersOpenFromChanged) {
                effectiveOrdersOpenFrom = OfferingTiming.validateOrdersOpenFrom(
                        effectiveOpenDate, effectiveOpenTime, effectiveDate, effectiveClose,
                        effectiveReady, AppTimeZone.today(), AppTimeZone.time());
            } else if (effectiveOrdersOpenFrom != null) {
                effectiveOrdersOpenFrom = OfferingTiming.validatePersistedOrdersOpenFrom(
                        effectiveOrdersOpenFrom, effectiveDate, effectiveClose, effectiveReady,
                        AppTimeZone.today());
            }
        }

        if (hasOrders) {
            if (dto.getName() != null && !dto.getName().isBlank() && !dto.getName().equals(product.getName()))
                throw new IllegalArgumentException("Cannot rename item after orders exist.");
            if (dto.getPrice() != null && !dto.getPrice().equals(product.getPrice()))
                throw new IllegalArgumentException("Cannot change price after orders exist.");
            if (dto.getPriceUnit() != null && !dto.getPriceUnit().equals(product.getPriceUnit()))
                throw new IllegalArgumentException("Cannot change unit after orders exist.");
            if (dto.getAvailableDate() != null && !dto.getAvailableDate().equals(product.getAvailableDate()))
                throw new IllegalArgumentException("Cannot change availability date after orders exist.");
            if (dto.getOrderWindowStart() != null && !java.util.Objects.equals(effectiveOpen, product.getOrderWindowStart()))
                throw new IllegalArgumentException("Cannot change order window start after orders exist.");
            if ((dto.getOrderOpenDate() != null || dto.getOrderOpenTime() != null)
                    && !java.util.Objects.equals(effectiveOrdersOpenFrom, product.getOrdersOpenFrom())) {
                throw new IllegalArgumentException("Cannot change Orders Open From after orders exist.");
            }
            // Orders Close is the one field that stays editable after the first
            // order, but it may only be EXTENDED (Requirement 5) so that every
            // already-accepted customer order keeps its original cutoff.
            if (dto.getOrderWindowEnd() != null || dto.getCutoffTime() != null) {
                String currentClose = OfferingTiming.resolveOrdersClose(product);
                if (!java.util.Objects.equals(effectiveClose, currentClose)
                        && !OfferingTiming.isLaterHhmm(effectiveClose, currentClose)) {
                    throw new IllegalArgumentException(
                            "Orders Close can only be extended after orders exist (currently " + currentClose + ").");
                }
            }
            if (dto.getReadyByTime() != null && !java.util.Objects.equals(effectiveReady, product.getReadyByTime()))
                throw new IllegalArgumentException("Cannot change ready-by time after orders exist.");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) product.setName(dto.getName());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        if (dto.getPriceUnit() != null) product.setPriceUnit(dto.getPriceUnit());
        if (dto.getImageUrl() != null) product.setImageUrl(dto.getImageUrl());
        if (dto.getRemainingQuantity() != null) product.setRemainingQuantity(dto.getRemainingQuantity());
        if (dto.getAvailableDate() != null) {
            product.setAvailableDate(dto.getAvailableDate());
            product.setAvailableToday(dto.getAvailableDate().equals(AppTimeZone.today()) && !effectivePreorder);
        }
        if (dto.getOrderWindowStart() != null) product.setOrderWindowStart(effectiveOpen);
        if (dto.getOrderOpenDate() != null || dto.getOrderOpenTime() != null) {
            product.setOrdersOpenFrom(effectiveOrdersOpenFrom);
        }
        if (dto.getOrderWindowEnd() != null || dto.getCutoffTime() != null) {
            product.setOrderWindowEnd(effectiveClose);
            product.setCutoffTime(effectiveClose);
        }
        if (dto.getReadyByTime() != null) product.setReadyByTime(effectiveReady);
        if (dto.getMaxQuantity() != null) product.setMaxQuantity(dto.getMaxQuantity());
        if (dto.getAvailableToday() != null) product.setAvailableToday(dto.getAvailableToday());
        if (dto.getIsPreorder() != null) product.setIsPreorder(effectivePreorder);
        if (dto.getCategories() != null) product.setCategory(joinCategories(dto.getCategories()));
        return toProductDto(productRepository.save(product));
    }

    /**
     * Requirement 5 â€” authoritative read model for the Seller "Edit Offering"
     * screen. Ownership is enforced exactly like updateProduct(), and the
     * "has orders" flag is derived from the same persisted-order query (draft
     * carts excluded) so the UI and the server can never disagree about which
     * fields are frozen.
     */
    @Transactional(readOnly = true)
    public SellerOfferingEditDto getOfferingForEdit(Long productId, User seller) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        getOwnedKitchen(product.getKitchen().getId(), seller);
        List<OrderItem> liveItems = orderItemRepository
                .findByProductIdAndOrderOrderStatusNot(productId, OrderStatus.DRAFT);
        long orderCount = liveItems.stream()
                .map(item -> item.getOrder() != null ? item.getOrder().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        SellerOfferingEditDto dto = new SellerOfferingEditDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setPriceUnit(product.getPriceUnit());
        dto.setImageUrl(product.getImageUrl());
        dto.setCategory(product.getCategory());
        dto.setAvailableDate(product.getAvailableDate());
        dto.setOrderWindowStart(product.getOrderWindowStart());
        dto.setOrdersOpenFrom(product.getOrdersOpenFrom());
        // Effective cutoff: orderWindowEnd first, legacy cutoffTime as fallback.
        dto.setOrderWindowEnd(OfferingTiming.resolveOrdersClose(product));
        dto.setReadyByTime(product.getReadyByTime());
        dto.setMaxQuantity(product.getMaxQuantity());
        dto.setRemainingQuantity(product.getRemainingQuantity());
        dto.setBookedQuantity(product.getBookedQuantity());
        dto.setIsPreorder(product.getIsPreorder());
        dto.setSoldOut(product.isSoldOut());
        dto.setOrdersPaused(product.isOrdersPaused());
        dto.setLifecycleState(OfferingTiming.lifecycleState(product, AppTimeZone.today(), AppTimeZone.time()));
        dto.setHasOrders(orderCount > 0);
        dto.setOrderCount((int) orderCount);
        return dto;
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
    public List<SellerOrderSummaryRowDto> getMyOrders(User seller) {
        return orderService.getSellerOrders(seller);
    }

    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus, User seller) {
        return orderService.updateOrderStatus(orderId, newStatus, seller);
    }

    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus, String reason, User seller) {
        return orderService.updateOrderStatus(orderId, newStatus, reason, seller);
    }

    public OrderDto markOrderAsPaid(Long orderId, User seller) {
        return orderService.markOrderAsPaid(orderId, seller);
    }

    public OrderDto acknowledgeOrder(Long orderId, User seller) {
        return orderService.acknowledgeOrder(orderId, seller);
    }

    private Kitchen resolveOfferingKitchen(Long kitchenId, User seller) {
        if (kitchenId != null) {
            Kitchen kitchen = kitchenRepository.findById(kitchenId)
                    .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
            if (!kitchen.getSeller().getId().equals(seller.getId())) {
                throw new SellerNotAuthorizedException("You do not own this kitchen");
            }
            return kitchen;
        }
        List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
        if (!kitchens.isEmpty()) {
            return kitchens.get(0);
        }
        return bootstrapDefaultKitchen(seller);
    }

    private Kitchen getOwnedKitchen(Long kitchenId, User seller) {
        if (kitchenId == null) {
            List<Kitchen> kitchens = kitchenRepository.findBySeller(seller);
            if (kitchens.isEmpty()) {
                throw new KitchenNotFoundException("No kitchen found for this seller account. Please set up your kitchen first.");
            }
            return kitchens.get(0);
        }
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new KitchenNotFoundException(kitchenId));
        if (!kitchen.getSeller().getId().equals(seller.getId())) throw new SellerNotAuthorizedException("You do not own this kitchen");
        return kitchen;
    }

    private Kitchen bootstrapDefaultKitchen(User seller) {
        String sellerName = (seller.getName() != null && !seller.getName().isBlank())
                ? seller.getName().trim()
                : "Seller";
        String displayName = sellerName + "'s Kitchen";
        String base = SlugService.slugify(displayName);
        if (base.isBlank()) {
            base = "kitchen-" + (seller.getId() != null ? seller.getId() : System.currentTimeMillis());
        }
        Kitchen kitchen = new Kitchen();
        kitchen.setDisplayName(displayName);
        kitchen.setDescription("");
        kitchen.setSeller(seller);
        kitchen.setSellerType(com.example.my_first_spring_api.model.SellerType.KITCHEN);
        kitchen.setAvailableToday(true);
        if (seller.getSociety() != null && !seller.getSociety().isBlank()) {
            kitchen.setSociety(seller.getSociety());
            kitchen.setServiceAreas(seller.getSociety());
        }
        if (slugService != null) {
            slugService.reserveUniqueSlug(kitchen, base);
        } else {
            String candidate = base;
            int counter = 2;
            while (kitchenRepository.findByName(candidate).isPresent() || kitchenRepository.findBySlug(candidate).isPresent()) {
                candidate = base + "-" + counter++;
            }
            kitchen.setName(candidate);
            kitchen.setSlug(candidate);
            kitchen = kitchenRepository.saveAndFlush(kitchen);
        }
        if (seller.getSociety() != null && !seller.getSociety().isBlank() && locationService != null) {
            try {
                locationService.applyCoverageFromNames(kitchen,
                        societyDirectory != null ? societyDirectory.validateAndNormalize(seller.getSociety()) : seller.getSociety());
            } catch (Exception ignored) {}
        }
        return kitchen;
    }

    /** Joins category enum names into a comma-separated string for multi-category storage. */
    private String joinCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .flatMap(c -> java.util.Arrays.stream(c.split(",")))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .map(String::toUpperCase)
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

    private boolean isHomemadeProductDto(ProductCreateDto dto, Kitchen kitchen) {
        if (dto != null && Boolean.TRUE.equals(dto.getIsHomemade())) return true;
        if (dto != null && Boolean.FALSE.equals(dto.getIsHomemade())) return false;
        if (dto != null && dto.getCategories() != null) {
            for (String c : dto.getCategories()) {
                if (c == null) continue;
                String lower = c.toLowerCase();
                if (lower.contains("homemade") || lower.contains("ghee") || lower.contains("achar")
                        || lower.contains("papad") || lower.contains("cake") || lower.contains("sweet")
                        || lower.contains("masala")) {
                    return true;
                }
                if (lower.contains("breakfast") || lower.contains("lunch") || lower.contains("dinner")) {
                    return false;
                }
            }
        }
        return kitchen != null && kitchen.getSellerType() == SellerType.HOMEMADE_PRODUCTS;
    }

    private String validateQuickPostImageData(String imageData) {
        if (imageData == null || imageData.isBlank()) return null;
        String normalized = imageData.trim();
        if (!normalized.matches("(?i)^data:image/(png|jpe?g|gif|webp);base64,[a-z0-9+/=\\s]+$")) {
            throw new IllegalArgumentException("Quick Post image must be a valid PNG, JPEG, GIF, or WebP image.");
        }
        try {
            byte[] decoded = java.util.Base64.getMimeDecoder()
                    .decode(normalized.substring(normalized.indexOf(',') + 1));
            if (decoded.length > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("Quick Post image must be 2 MB or smaller.");
            }
            String mediaType = normalized.substring("data:image/".length(), normalized.indexOf(';'))
                    .toLowerCase(java.util.Locale.ROOT);
            if (!matchesImageSignature(mediaType, decoded)) {
                throw new IllegalArgumentException("Quick Post image content does not match its image type.");
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Quick Post image")) throw ex;
            throw new IllegalArgumentException("Quick Post image must be valid base64 image data.");
        }
        return normalized;
    }

    private boolean matchesImageSignature(String mediaType, byte[] bytes) {
        if ("png".equals(mediaType)) {
            return bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                    && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        }
        if ("jpeg".equals(mediaType) || "jpg".equals(mediaType)) {
            return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        }
        if ("gif".equals(mediaType)) {
            return bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
        }
        return "webp".equals(mediaType) && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
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
            long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(AppTimeZone.today(), availableDate);
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
        dto.setSpeciality(kitchen.getSpeciality());
        dto.setSociety(kitchen.getSociety());
        dto.setServiceAreas(kitchen.getServiceAreas());
        // Expose the authoritative ID coverage so the picker can restore the exact
        // current selection instead of re-deriving it from the display string.
        dto.setServedSocietyIds(kitchen.getServedSocieties() == null
                ? new java.util.ArrayList<>()
                : kitchen.getServedSocieties().stream()
                        .map(com.example.my_first_spring_api.model.Society::getId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList()));
        dto.setBuilding(kitchen.getBuilding());
        dto.setWhatsappLink(kitchen.getWhatsappLink());
        dto.setWhatsappNumber(kitchen.getSeller() != null ? kitchen.getSeller().getSellerWhatsappNumber() : null);
        dto.setSlug(kitchen.getSlug() != null ? kitchen.getSlug() : kitchen.getName());
        dto.setInstagramLink(kitchen.getInstagramLink());
        dto.setUpiId(kitchen.getUpiId());
        dto.setAcceptedPaymentMode(kitchen.getAcceptedPaymentMode() == null
                ? null : kitchen.getAcceptedPaymentMode().name());
        dto.setGpayIdentifierType(kitchen.getGpayIdentifierType() == null
                ? null : kitchen.getGpayIdentifierType().name());
        dto.setGpayIdentifier(kitchen.getGpayIdentifier());
        dto.setGalleryImages(kitchen.getGalleryImages());
        dto.setOrderDeadline(kitchen.getOrderDeadline());
        // The kitchen owner's name, read from the seller's own User record so the
        // UI never has to derive it from the kitchen name. Seller-facing DTO only.
        dto.setSellerName(kitchen.getSeller() == null ? null : kitchen.getSeller().getName());
        dto.setSellerType(kitchen.getSellerType() != null ? kitchen.getSellerType().name() : null);
        dto.setPaused(KitchenVisibility.isPaused(kitchen));
        return dto;
    }

    private static String cleanPaymentIdentifier(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProductDto toProductDto(Product product) {
        Kitchen kitchen = product.getKitchen();
        ProductDto dto = new ProductDto(product.getId(), kitchen != null ? kitchen.getId() : null,
                kitchen != null ? kitchen.getDisplayName() : null, product.getName(),
                product.getDescription(), product.getPrice(), product.getImageUrl(),
                product.getAvailableToday(), product.getRating());
        dto.setPriceUnit(product.getPriceUnit());
        dto.setUnit(product.getUnit());
        dto.setIsVeg(product.getIsVeg());
        dto.setAvailableDate(product.getAvailableDate());
        dto.setOrderWindowStart(product.getOrderWindowStart());
        dto.setOrdersOpenFrom(product.getOrdersOpenFrom());
        dto.setQuickPost(product.isQuickPost());
        dto.setQuickPostImageData(product.getQuickPostImageData());
        dto.setOrderWindowEnd(product.getOrderWindowEnd());
        dto.setMaxQuantity(product.getMaxQuantity());
        dto.setRemainingQuantity(product.getRemainingQuantity());
        dto.setIsPreorder(product.getIsPreorder());
        dto.setKitchenSlug(kitchen != null ? kitchen.getName() : null);
        dto.setSellerType(kitchen != null && kitchen.getSellerType() != null ? kitchen.getSellerType().name() : null);
        dto.setCategory(product.getCategory());
        dto.setCutoffTime(product.getCutoffTime());
        dto.setReadyByTime(product.getReadyByTime());
        dto.setPreorderType(product.getPreorderType() != null ? product.getPreorderType().name() : null);
        dto.setAvailableUntilDate(product.getAvailableUntilDate());
        dto.setTimeSlots(product.getTimeSlots());
        dto.setBookedQuantity(product.getBookedQuantity());
        dto.setSoldOut(product.isSoldOut());
        dto.setOrdersPaused(product.isOrdersPaused());
        String lifecycle = OfferingTiming.lifecycleState(product, AppTimeZone.today(), AppTimeZone.time());
        dto.setOrdersClosed("ORDERS_CLOSED".equals(lifecycle) || "HISTORY".equals(lifecycle)
                || "SCHEDULED_TO_OPEN".equals(lifecycle));
        dto.setLifecycleState(lifecycle);
        dto.setCreatedAt(product.getCreatedAt());
        return dto;
    }

    @Transactional(readOnly = true)
    public com.example.my_first_spring_api.dto.SellerProfileDto getSellerProfile(User seller) {
        var dto = new com.example.my_first_spring_api.dto.SellerProfileDto();
        dto.setId(seller.getId());
        dto.setName(seller.getName());
        dto.setMobileNumber(seller.getMobileNumber());
        dto.setWhatsappNumber(seller.getSellerWhatsappNumber());
        dto.setEmail(seller.getEmail());
        dto.setAlternateContact(seller.getSellerAlternateContact());
        dto.setArea(seller.getArea() != null ? seller.getArea() : (seller.getAreaRef() != null ? seller.getAreaRef().getName() : null));
        dto.setSociety(seller.getSociety() != null ? seller.getSociety() : (seller.getSocietyRef() != null ? seller.getSocietyRef().getName() : null));
        dto.setBuilding(seller.getBuilding());
        dto.setFlatHouseNumber(seller.getFlatHouseNumber());
        dto.setSellerCategory(seller.getSellerCategory());
        dto.setSellerApprovalStatus(seller.getSellerApprovalStatus() != null ? seller.getSellerApprovalStatus().name() : null);
        return dto;
    }

    @Transactional
    public com.example.my_first_spring_api.dto.SellerProfileDto updateSellerProfile(User seller, com.example.my_first_spring_api.dto.SellerProfileDto dto) {
        if (dto.getWhatsappNumber() != null) {
            String wa = dto.getWhatsappNumber().trim();
            seller.setSellerWhatsappNumber(wa.isEmpty() ? null : wa);
        }
        if (dto.getEmail() != null) {
            String em = dto.getEmail().trim();
            seller.setEmail(em.isEmpty() ? null : em);
        }
        if (dto.getAlternateContact() != null) {
            String alt = dto.getAlternateContact().trim();
            seller.setSellerAlternateContact(alt.isEmpty() ? null : alt);
        }
        if (dto.getFlatHouseNumber() != null) {
            seller.setFlatHouseNumber(dto.getFlatHouseNumber().trim());
        }
        if (dto.getBuilding() != null) {
            seller.setBuilding(dto.getBuilding().trim());
        }
        if (dto.getSellerCategory() != null && !dto.getSellerCategory().isBlank()) {
            try {
                com.example.my_first_spring_api.model.SellerType st = com.example.my_first_spring_api.model.SellerType.valueOf(dto.getSellerCategory().trim().toUpperCase(java.util.Locale.ROOT));
                seller.setSellerCategory(st.name());
                kitchenRepository.findBySeller(seller).forEach(k -> {
                    k.setSellerType(st);
                    kitchenRepository.save(k);
                });
            } catch (IllegalArgumentException ignored) {}
        }
        User saved = (userRepository != null) ? userRepository.save(seller) : seller;
        return getSellerProfile(saved);
    }

    @Transactional
    public com.example.my_first_spring_api.dto.AuthResponseDto resubmitApplication(
            User seller, java.util.Map<String, Object> body) {
        if (seller == null) throw new IllegalArgumentException("Seller is required.");
        if (body == null) body = new java.util.HashMap<>();
        // Requirement ONB-13: Seller request-changes/resubmission
        // Seller can resubmit their application after admin requested changes
        // The body may contain updated application details
        // Verify slug is NOT changed during resubmission (slug reservation guarantee)
        String reason = (String) body.getOrDefault("reason", "");
        Kitchen kitchen = kitchenRepository.findBySeller(seller).stream().findFirst().orElse(null);
        String submittedSlug = (String) body.getOrDefault("kitchenSlug", kitchen != null ? kitchen.getSlug() : null);
        if (kitchen != null && submittedSlug != null && !submittedSlug.equals(kitchen.getSlug())) {
            throw new IllegalArgumentException("Slug cannot be changed during resubmission. The kitchen slug is reserved and immutable.");
        }
        SellerApprovalStatus from = seller.getSellerApprovalStatus();
        if (from != SellerApprovalStatus.CHANGES_REQUESTED && from != SellerApprovalStatus.PENDING && from != SellerApprovalStatus.REJECTED) {
            throw new IllegalArgumentException("Application is not in a resubmittable state.");
        }
        seller.setSellerStatusReason(reason != null && !reason.isBlank() ? reason : seller.getSellerStatusReason());
        seller.setSellerApprovalStatus(SellerApprovalStatus.PENDING); // Reset to pending until admin reviews
        seller = userRepository.save(seller);
        if (body != null) {
            if (body.get("kitchenName") != null) {
                String kn = String.valueOf(body.get("kitchenName")).trim();
                if (!kn.isEmpty() && kitchen != null) {
                    kitchen.setDisplayName(kn);
                }
            }
            if (body.get("speciality") != null && kitchen != null) {
                kitchen.setSpeciality(String.valueOf(body.get("speciality")).trim());
            }
            if (body.get("shortDescription") != null && kitchen != null) {
                kitchen.setShortDescription(String.valueOf(body.get("shortDescription")).trim());
                kitchen.setDescription(String.valueOf(body.get("shortDescription")).trim());
            }
            if (body.get("sellerCategory") != null) {
                try {
                    com.example.my_first_spring_api.model.SellerType st = com.example.my_first_spring_api.model.SellerType.valueOf(String.valueOf(body.get("sellerCategory")).trim().toUpperCase(java.util.Locale.ROOT));
                    seller.setSellerCategory(st.name());
                    if (kitchen != null) kitchen.setSellerType(st);
                } catch (IllegalArgumentException ignored) {}
            }
            if (body.get("whatsappNumber") != null) {
                String wa = String.valueOf(body.get("whatsappNumber")).trim();
                seller.setSellerWhatsappNumber(wa.isEmpty() ? null : wa);
            }
            if (body.get("alternateContact") != null) {
                String alt = String.valueOf(body.get("alternateContact")).trim();
                seller.setSellerAlternateContact(alt.isEmpty() ? null : alt);
            }
        }
        seller.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        seller.setSellerStatusReason(reason != null && !reason.isBlank() ? reason : "Resubmitted with requested changes");
        if (kitchen != null) {
            kitchenRepository.save(kitchen);
        }
        User saved = (userRepository != null) ? userRepository.save(seller) : seller;
        if (auditService != null) {
            auditService.record(AdminAuditService.SELLER_RESUBMITTED, seller, "SELLER",
                    seller.getId(), seller.getName(), String.valueOf(from), "PENDING", "Resubmitted with requested changes");
        }
        if (analyticsService != null) {
            analyticsService.record(AnalyticsService.EV_USER_REGISTERED, seller.getId(), seller.getMobileNumber(), null, "Resubmitted application: " + seller.getName());
        }
        var authDto = new com.example.my_first_spring_api.dto.AuthResponseDto(
                true, "Application resubmitted for Admin approval",
                saved.getId(), saved.getName(), saved.getMobileNumber(), saved.getFlatHouseNumber(),
                saved.getRole() != null ? saved.getRole().name() : null, saved.getSellerApprovalStatus());
        authDto.setSellerApprovalStatus(saved.getSellerApprovalStatus());
        authDto.setSellerStatusReason(reason != null && !reason.isBlank() ? reason : saved.getSellerStatusReason());
        if (kitchen != null) {
            authDto.setKitchenSlug(kitchen.getSlug());
            authDto.setKitchenUrl("/kitchen/" + kitchen.getSlug());
        }
        return authDto;
    }

    @Transactional
    public com.example.my_first_spring_api.dto.AuthResponseDto cancelApplication(User seller) {
        if (seller == null) throw new IllegalArgumentException("Seller is required.");
        SellerApprovalStatus from = seller.getSellerApprovalStatus();
        if (from != SellerApprovalStatus.PENDING && from != SellerApprovalStatus.CHANGES_REQUESTED) {
            throw new IllegalArgumentException("Only pending or changes-requested applications can be cancelled.");
        }
        seller.setSellerApprovalStatus(SellerApprovalStatus.REJECTED);
        seller.setSellerStatusReason("Application cancelled by applicant");
        User saved = (userRepository != null) ? userRepository.save(seller) : seller;
        if (auditService != null) {
            auditService.record(AdminAuditService.SELLER_CANCELLED, seller, "SELLER",
                    seller.getId(), seller.getName(), String.valueOf(from), "REJECTED", "Application cancelled by applicant");
        }
        Kitchen kitchen = kitchenRepository.findBySeller(seller).stream().findFirst().orElse(null);
        var authDto = new com.example.my_first_spring_api.dto.AuthResponseDto(
                true, "Application cancelled",
                saved.getId(), saved.getName(), saved.getMobileNumber(), saved.getFlatHouseNumber(),
                saved.getRole() != null ? saved.getRole().name() : null, saved.getSellerApprovalStatus());
        authDto.setSellerApprovalStatus(saved.getSellerApprovalStatus());
        authDto.setSellerStatusReason(saved.getSellerStatusReason());
        if (kitchen != null) {
            authDto.setKitchenSlug(kitchen.getSlug());
            authDto.setKitchenUrl("/kitchen/" + kitchen.getSlug());
        }
        return authDto;
    }
}

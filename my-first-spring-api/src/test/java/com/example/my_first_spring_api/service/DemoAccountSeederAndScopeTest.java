package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.KitchenUpdateDto;
import com.example.my_first_spring_api.dto.SellerProfileDto;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DemoAccountSeederAndScopeTest {

    private UserRepository userRepository;
    private AreaRepository areaRepository;
    private SocietyRepository societyRepository;
    private KitchenRepository kitchenRepository;
    private LocationService locationService;
    private DemoAccountSeeder seeder;
    private BCryptPasswordEncoder encoder;

    private Map<String, User> userDb = new HashMap<>();
    private Map<Long, User> userById = new HashMap<>();
    private Map<String, Area> areaDb = new HashMap<>();
    private Map<String, Society> societyDb = new HashMap<>();
    private Map<Long, List<Kitchen>> kitchenDb = new HashMap<>();
    private long idSeq = 1;

    @BeforeEach
    void setUp() {
        userDb.clear();
        userById.clear();
        areaDb.clear();
        societyDb.clear();
        kitchenDb.clear();
        idSeq = 1;

        userRepository = mock(UserRepository.class);
        areaRepository = mock(AreaRepository.class);
        societyRepository = mock(SocietyRepository.class);
        kitchenRepository = mock(KitchenRepository.class);
        locationService = mock(LocationService.class);
        encoder = new BCryptPasswordEncoder();

        when(userRepository.findByMobileNumber(any())).thenAnswer(inv -> {
            String m = inv.getArgument(0);
            return Optional.ofNullable(userDb.get(m));
        });
        when(userRepository.findById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(userById.get(id));
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(idSeq++);
            userDb.put(u.getMobileNumber(), u);
            userById.put(u.getId(), u);
            return u;
        });

        when(areaRepository.findByNameIgnoreCase(any())).thenAnswer(inv -> {
            String n = inv.getArgument(0);
            return Optional.ofNullable(areaDb.get(n.toLowerCase()));
        });
        when(locationService.createArea(any())).thenAnswer(inv -> {
            String n = inv.getArgument(0);
            Area a = new Area(n);
            a.setId(idSeq++);
            areaDb.put(n.toLowerCase(), a);
            return a;
        });

        when(societyRepository.findByAreaIdAndNameKey(any(), any())).thenAnswer(inv -> {
            Long aId = inv.getArgument(0);
            String k = inv.getArgument(1);
            return Optional.ofNullable(societyDb.get(aId + ":" + k));
        });
        when(locationService.createSociety(any(), any())).thenAnswer(inv -> {
            Long aId = inv.getArgument(0);
            String n = inv.getArgument(1);
            Area a = new Area("Area");
            a.setId(aId);
            Society s = new Society(a, n);
            s.setId(idSeq++);
            societyDb.put(aId + ":" + n.toLowerCase(), s);
            return s;
        });

        when(kitchenRepository.findBySeller(any())).thenAnswer(inv -> {
            User s = inv.getArgument(0);
            return kitchenDb.getOrDefault(s.getId(), Collections.emptyList());
        });
        when(kitchenRepository.save(any(Kitchen.class))).thenAnswer(inv -> {
            Kitchen k = inv.getArgument(0);
            if (k.getId() == null) k.setId(idSeq++);
            if (k.getSeller() != null) {
                kitchenDb.computeIfAbsent(k.getSeller().getId(), x -> new ArrayList<>()).add(k);
            }
            return k;
        });

        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        seeder = new DemoAccountSeeder(userRepository, kitchenRepository, areaRepository, societyRepository, locationService, encoder, env);
    }

    @Test
    void seedsExactly10PredictableDemoAccountsIdempotently() {
        seeder.seedDemoAccounts();

        // 3 Sellers
        User seller1 = userDb.get("9000000011");
        User seller2 = userDb.get("9000000012");
        User seller3 = userDb.get("9000000013");
        assertThat(seller1).isNotNull();
        assertThat(seller1.getRole()).isEqualTo(UserRole.SELLER);
        assertThat(seller1.getSellerApprovalStatus()).isEqualTo(SellerApprovalStatus.APPROVED);
        assertThat(encoder.matches("Demo@1234", seller1.getPasswordHash())).isTrue();
        assertThat(seller1.getSellerWhatsappNumber()).isEqualTo("9000000011");

        assertThat(seller2).isNotNull();
        assertThat(seller3).isNotNull();

        // 3 Buyers
        User buyer1 = userDb.get("9000000021");
        User buyer2 = userDb.get("9000000022");
        User buyer3 = userDb.get("9000000023");
        assertThat(buyer1).isNotNull();
        assertThat(buyer1.getRole()).isEqualTo(UserRole.BUYER);
        assertThat(encoder.matches("Demo@1234", buyer1.getPasswordHash())).isTrue();
        assertThat(buyer2).isNotNull();
        assertThat(buyer3).isNotNull();

        // 2 Super Admins
        User sa1 = userDb.get("9000000001");
        User sa2 = userDb.get("9000000003");
        assertThat(sa1).isNotNull();
        assertThat(sa1.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(encoder.matches("Demo@1234", sa1.getPasswordHash())).isTrue();
        assertThat(sa2).isNotNull();
        assertThat(sa2.getRole()).isEqualTo(UserRole.SUPER_ADMIN);

        // 2 Area Admins
        User aaNorth = userDb.get("9000000002");
        User aaSouth = userDb.get("9000000004");
        assertThat(aaNorth).isNotNull();
        assertThat(aaNorth.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(aaNorth.getAdminAreaId()).isNotNull();
        assertThat(encoder.matches("Demo@1234", aaNorth.getPasswordHash())).isTrue();

        assertThat(aaSouth).isNotNull();
        assertThat(aaSouth.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(aaSouth.getAdminAreaId()).isNotNull();
        assertThat(aaSouth.getAdminAreaId()).isNotEqualTo(aaNorth.getAdminAreaId());

        assertThat(userDb).hasSize(10);

        // Idempotency: second run does not duplicate accounts
        seeder.seedDemoAccounts();
        assertThat(userDb).hasSize(10);
    }

    @Test
    void areaAdminScopingRestrictsAccessToAssignedSocieties() {
        seeder.seedDemoAccounts();

        User aaNorth = userDb.get("9000000002");
        User aaSouth = userDb.get("9000000004");
        User superAdmin = userDb.get("9000000001");

        User sellerNorth = userDb.get("9000000011");
        User sellerSouth = userDb.get("9000000012");

        OrderRepository orderRepo = mock(OrderRepository.class);
        ProductRepository prodRepo = mock(ProductRepository.class);
        EnquiryRepository enqRepo = mock(EnquiryRepository.class);
        FavouriteRepository favRepo = mock(FavouriteRepository.class);
        SocietyDirectory socDir = mock(SocietyDirectory.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        RetentionService retService = mock(RetentionService.class);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsEventRepository eventRepo = mock(AnalyticsEventRepository.class);

        AdminService adminService = new AdminService(userRepository, analyticsService, orderRepo, prodRepo,
                kitchenRepository, enqRepo, favRepo, socDir, locationService, auditService, retService, eventRepo, null);

        // Set pending status for testing pending list
        sellerNorth.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        sellerSouth.setSellerApprovalStatus(SellerApprovalStatus.PENDING);
        when(userRepository.findByRoleAndSellerApprovalStatus(UserRole.SELLER, SellerApprovalStatus.PENDING))
                .thenReturn(List.of(sellerNorth, sellerSouth));

        // Area Admin North only sees North seller
        List<User> northPending = adminService.pendingSellers(aaNorth);
        assertThat(northPending).containsExactly(sellerNorth);

        // Area Admin South only sees South seller
        List<User> southPending = adminService.pendingSellers(aaSouth);
        assertThat(southPending).containsExactly(sellerSouth);

        // Super Admin sees all pending
        List<User> allPending = adminService.pendingSellers(superAdmin);
        assertThat(allPending).containsExactlyInAnyOrder(sellerNorth, sellerSouth);

        // North Area Admin cannot view South seller details (FORBIDDEN)
        assertThatThrownBy(() -> adminService.sellerDetail(sellerSouth.getId(), aaNorth))
                .isInstanceOf(com.example.my_first_spring_api.exception.SellerNotAuthorizedException.class);

        // North Area Admin can view North seller details
        Map<String, Object> detail = adminService.sellerDetail(sellerNorth.getId(), aaNorth);
        assertThat(detail).isNotNull();
        assertThat(detail.get("mobileNumber")).isEqualTo("9000000011");
        assertThat(detail.get("whatsappNumber")).isEqualTo("9000000011");
    }

    @Test
    void sellerKitchenPreservesImmutableSlugAndRejectsBlankName() {
        User seller = new User("Test Seller", "9000000099", null, UserRole.SELLER);
        seller.setId(99L);
        Kitchen kitchen = new Kitchen("test-kitchen-slug", "My Kitchen", "Desc", null, seller);
        kitchen.setId(88L);
        kitchen.setSlug("test-kitchen-slug");

        KitchenRepository kRepo = mock(KitchenRepository.class);
        when(kRepo.findById(88L)).thenReturn(Optional.of(kitchen));
        when(kRepo.save(any(Kitchen.class))).thenAnswer(inv -> inv.getArgument(0));

        SellerService sellerService = new SellerService(kRepo, mock(ProductRepository.class),
                mock(OrderItemRepository.class), mock(OrderService.class), mock(FeatureService.class),
                mock(SocietyDirectory.class), mock(LocationService.class));

        // Reject whitespace-only name
        KitchenUpdateDto blankDto = new KitchenUpdateDto();
        blankDto.setDisplayName("   ");
        assertThatThrownBy(() -> sellerService.updateKitchen(88L, blankDto, seller))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kitchen Name cannot be blank");

        // Update with valid name and WhatsApp number
        KitchenUpdateDto validDto = new KitchenUpdateDto();
        validDto.setDisplayName("Updated Kitchen Name");
        validDto.setWhatsappNumber("9998887776");

        var updated = sellerService.updateKitchen(88L, validDto, seller);
        assertThat(updated.getDisplayName()).isEqualTo("Updated Kitchen Name");
        assertThat(updated.getSlug()).isEqualTo("test-kitchen-slug"); // permanent immutable slug!
        assertThat(seller.getSellerWhatsappNumber()).isEqualTo("9998887776");
    }
}

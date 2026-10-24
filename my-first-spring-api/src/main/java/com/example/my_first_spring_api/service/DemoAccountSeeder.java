package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Area;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.SellerType;
import com.example.my_first_spring_api.model.Society;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.AreaRepository;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.SocietyRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Idempotently seeds the 10 predictable demo test accounts specified in Developer Brief V1:
 * <ul>
 *   <li>3 Demo Sellers: 9000000011, 9000000012, 9000000013</li>
 *   <li>3 Demo Buyers: 9000000021, 9000000022, 9000000023</li>
 *   <li>2 Super Admins: 9000000001, 9000000003</li>
 *   <li>2 Area Admins: 9000000002 (North/East), 9000000004 (South/West)</li>
 * </ul>
 * Standard demo password for seeded accounts: Demo@1234
 */
@Service
public class DemoAccountSeeder {

    public static final String DEMO_PASSWORD = "Demo@1234";

    public static final String SELLER_1_MOBILE = "9000000011";
    public static final String SELLER_2_MOBILE = "9000000012";
    public static final String SELLER_3_MOBILE = "9000000013";

    public static final String BUYER_1_MOBILE = "9000000021";
    public static final String BUYER_2_MOBILE = "9000000022";
    public static final String BUYER_3_MOBILE = "9000000023";

    public static final String SUPER_ADMIN_1_MOBILE = "9000000001";
    public static final String SUPER_ADMIN_2_MOBILE = "9000000003";

    public static final String AREA_ADMIN_NORTH_MOBILE = "9000000002";
    public static final String AREA_ADMIN_SOUTH_MOBILE = "9000000004";

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;
    private final AreaRepository areaRepository;
    private final SocietyRepository societyRepository;
    private final LocationService locationService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DemoAccountSeeder(UserRepository userRepository,
                             KitchenRepository kitchenRepository,
                             AreaRepository areaRepository,
                             SocietyRepository societyRepository,
                             LocationService locationService,
                             PasswordEncoder passwordEncoder,
                             Environment environment) {
        this.userRepository = userRepository;
        this.kitchenRepository = kitchenRepository;
        this.areaRepository = areaRepository;
        this.societyRepository = societyRepository;
        this.locationService = locationService;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    public boolean isEnabled() {
        if (environment == null) return true;
        String prop = environment.getProperty("sociomart.seed.demo-accounts");
        if (prop != null) {
            return "true".equalsIgnoreCase(prop.trim());
        }
        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) return true;
        List<String> list = Arrays.asList(profiles);
        return !list.contains("prod") && !list.contains("production");
    }

    @Transactional
    public void seedDemoAccounts() {
        if (!isEnabled()) {
            return;
        }

        log.info("Checking and seeding predictable demo accounts (Brief V1)...");

        // 1. Ensure zones/societies exist
        Area northArea = getOrCreateArea("North Zone");
        Society northSociety = getOrCreateSociety(northArea, "North Pride Society");

        Area southArea = getOrCreateArea("South Zone");
        Society southSociety = getOrCreateSociety(southArea, "South Palms Society");

        Area centralArea = getOrCreateArea("Central Zone");
        Society centralSociety = getOrCreateSociety(centralArea, "Central Heights");

        String encodedDemoPassword = passwordEncoder.encode(DEMO_PASSWORD);

        // 2. Super Admins
        ensureSuperAdmin(SUPER_ADMIN_1_MOBILE, "Super Admin Primary", encodedDemoPassword);
        ensureSuperAdmin(SUPER_ADMIN_2_MOBILE, "Super Admin Secondary", encodedDemoPassword);

        // 3. Area Admins (scoped to their respective areas)
        ensureAreaAdmin(AREA_ADMIN_NORTH_MOBILE, "Area Admin North", northArea.getId(), encodedDemoPassword);
        ensureAreaAdmin(AREA_ADMIN_SOUTH_MOBILE, "Area Admin South", southArea.getId(), encodedDemoPassword);

        // 4. Demo Sellers with approved status, kitchen and WhatsApp number
        ensureSeller(SELLER_1_MOBILE, "Seller One", "North Zone Kitchen", "north-zone-kitchen",
                "North Indian & Punjabi Kitchen", northArea, northSociety, encodedDemoPassword);

        ensureSeller(SELLER_2_MOBILE, "Seller Two", "South Zone Kitchen", "south-zone-kitchen",
                "South Indian & Coastal Kitchen", southArea, southSociety, encodedDemoPassword);

        ensureSeller(SELLER_3_MOBILE, "Seller Three", "Central Kitchen", "central-kitchen",
                "Homemade Maharashtrian Thali", centralArea, centralSociety, encodedDemoPassword);

        // 5. Demo Buyers
        ensureBuyer(BUYER_1_MOBILE, "Buyer One", northArea, northSociety, "A-101", encodedDemoPassword);
        ensureBuyer(BUYER_2_MOBILE, "Buyer Two", southArea, southSociety, "B-202", encodedDemoPassword);
        ensureBuyer(BUYER_3_MOBILE, "Buyer Three", centralArea, centralSociety, "C-303", encodedDemoPassword);

        log.info("Demo accounts seeded successfully.");
    }

    private Area getOrCreateArea(String name) {
        return areaRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> locationService.createArea(name));
    }

    private Society getOrCreateSociety(Area area, String name) {
        String key = name.trim().toLowerCase();
        return societyRepository.findByAreaIdAndNameKey(area.getId(), key)
                .orElseGet(() -> locationService.createSociety(area.getId(), name));
    }

    private void ensureSuperAdmin(String mobile, String name, String encodedPassword) {
        if (userRepository.findByMobileNumber(mobile).isEmpty()) {
            User u = new User(name, mobile, null, UserRole.SUPER_ADMIN);
            u.setPasswordHash(encodedPassword);
            userRepository.save(u);
            log.info("Created demo Super Admin: {} ({})", name, mobile);
        }
    }

    private void ensureAreaAdmin(String mobile, String name, Long areaId, String encodedPassword) {
        if (userRepository.findByMobileNumber(mobile).isEmpty()) {
            User u = new User(name, mobile, null, UserRole.ADMIN);
            u.setAdminAreaId(areaId);
            u.setPasswordHash(encodedPassword);
            userRepository.save(u);
            log.info("Created demo Area Admin: {} ({}) scoped to area {}", name, mobile, areaId);
        }
    }

    private void ensureSeller(String mobile, String sellerName, String kitchenName, String slug,
                              String speciality, Area area, Society society, String encodedPassword) {
        User seller = userRepository.findByMobileNumber(mobile).orElse(null);
        if (seller == null) {
            seller = new User(sellerName, mobile, null, UserRole.SELLER);
            seller.setPasswordHash(encodedPassword);
            seller.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
            seller.setApprovedAt(LocalDateTime.now());
            seller.setSellerWhatsappNumber(mobile);
            seller.setAreaRef(area);
            seller.setSocietyRef(society);
            seller.setArea(area.getName());
            seller.setSociety(society.getName());
            seller = userRepository.save(seller);
            log.info("Created demo Seller: {} ({})", sellerName, mobile);
        }

        // Ensure kitchen exists for this seller
        var existingKitchens = kitchenRepository.findBySeller(seller);
        if (existingKitchens.isEmpty()) {
            Kitchen k = new Kitchen();
            k.setName(slug);
            k.setDisplayName(kitchenName);
            k.setSlug(slug);
            k.setSpeciality(speciality);
            k.setDescription(speciality + " - fresh daily homemade food.");
            k.setShortDescription(speciality);
            k.setSeller(seller);
            k.setSociety(society.getName());
            k.setServiceAreas(society.getName());
            Set<Society> served = new LinkedHashSet<>();
            served.add(society);
            k.setServedSocieties(served);
            k.setWhatsappLink(mobile);
            k.setSellerType(SellerType.KITCHEN);
            k.setAvailableToday(true);
            kitchenRepository.save(k);
            log.info("Created demo Kitchen: {} for seller {}", kitchenName, sellerName);
        }
    }

    private void ensureBuyer(String mobile, String name, Area area, Society society,
                             String flat, String encodedPassword) {
        if (userRepository.findByMobileNumber(mobile).isEmpty()) {
            User b = new User(name, mobile, null, UserRole.BUYER);
            b.setPasswordHash(encodedPassword);
            b.setAreaRef(area);
            b.setSocietyRef(society);
            b.setArea(area.getName());
            b.setSociety(society.getName());
            b.setFlatHouseNumber(flat);
            userRepository.save(b);
            log.info("Created demo Buyer: {} ({})", name, mobile);
        }
    }
}

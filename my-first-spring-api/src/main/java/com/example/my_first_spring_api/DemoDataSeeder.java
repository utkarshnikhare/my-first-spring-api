package com.example.my_first_spring_api;

import com.example.my_first_spring_api.model.Kitchen;

import com.example.my_first_spring_api.model.PlatformSetting;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PlatformSettingRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seeds the database with realistic demo kitchens and menus — once only, guarded
 * by a platform_settings flag — so the search & discovery features can be tested
 * immediately. Includes one PENDING-approval kitchen to prove unapproved
 * sellers' kitchens are never exposed in public results.
 * Only active when 'demo' or 'dev' profile is enabled.
 */
@Component
@Profile({"demo", "dev"})
public class DemoDataSeeder {

    private static final String DEMO_SEED_FLAG = "demo_data_seeded";

    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final PlatformSettingRepository platformSettingRepository;

    @Autowired
    public DemoDataSeeder(UserRepository userRepository, KitchenRepository kitchenRepository,
                           ProductRepository productRepository, PlatformSettingRepository platformSettingRepository) {
        this.userRepository = userRepository;
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.platformSettingRepository = platformSettingRepository;
    }

    @Transactional
    public void seedIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_SEED_FLAG).isPresent()) {
            return;
        }
        // ---- 8 approved demo sellers (+ 1 pending seller for visibility tests) ----
        User aarti = seller("Aarti", "9100000001", "A-101", SellerApprovalStatus.APPROVED);
        User meena = seller("Meena", "9100000002", "A-102", SellerApprovalStatus.APPROVED);
        User ravi = seller("Ravi", "9100000003", "B-201", SellerApprovalStatus.APPROVED);
        User lakshmi = seller("Lakshmi", "9100000004", "C-301", SellerApprovalStatus.APPROVED);
        User suresh = seller("Suresh", "9100000005", "B-210", SellerApprovalStatus.APPROVED);
        User farah = seller("Farah", "9100000006", "D-401", SellerApprovalStatus.APPROVED); 
        User geeta = seller("Geeta", "9100000007", "D-402", SellerApprovalStatus.APPROVED);
 
        User arjun = seller("Arjun", "9100000008", "E-501", SellerApprovalStatus.APPROVED);
 
        User priya = seller("Priya", "9100000009", "E-502", SellerApprovalStatus.PENDING);

        // ---- Active demo kitchens ----
        Kitchen kAarti = kitchen("aarti-kitchen", "Aarti Kitchen", "North Indian home-style meals, cooked fresh every day",
                "Aarti Kitchen serves comforting North Indian home food cooked to order.", "aarti@okhdfc", 4.7, "9:30 PM", aarti); 
        Kitchen kMoms = kitchen("moms-special-kitchen", "Mom's Special Kitchen", "Wholesome recipes from mom's kitchen, made with love",
                "Comfort food that tastes exactly like home: parathas, rajma-chawal and kheer.", "moms@okhdfc", 4.9, "8:30 PM", meena); 
        Kitchen kDesi = kitchen("desi-rasoi", "Desi Rasoi", "Traditional thalis and comfort food from across India",
                "A wholesome desi kitchen serving thalis, dal, rajma and lassi every day.", "desirasoi@okhdfc", 4.6, "9:00 PM", ravi); 
        Kitchen kRoyal = kitchen("royal-south-indian", "Royal South Indian", "Authentic South Indian tiffin, dosas and chai",
                "Idli, dosa, upma and vada — served with fresh sambhar and coconut chutney.", "royalsouth@okhdfc", 4.8, "11:30 AM", lakshmi); 
        Kitchen kPunjabi = kitchen("punjabi-tadka-house", "Punjabi Tadka House", "Rich Punjabi curries, tandoori breads and lassi",
                "Butter-laden Punjabi classics: paneer, dal makhani, naan and lassi.", "punjabitadka@okhdfc", 4.5, "10:00 PM", suresh); 
        Kitchen kBiryani = kitchen("biryani-bistro", "Biryani Bistro", "Slow-cooked dum biryanis and Indo-Chinese street favourites",
                "Dum biryanis, paneer tikka and hakka noodles — all under one roof.", "biryanibistro@okhdfc", 4.7, "11:00 PM", farah); 
        Kitchen kHealthy = kitchen("healthy-bites", "Healthy Bites", "Light, wholesome bowls and breakfasts — guilt-free",
                "Healthy poha, upma, salads and whole-grain burgers for every mood.", "healthybites@okhdfc", 4.4, "8:00 PM", geeta); 
        Kitchen kSnacks = kitchen("sweet-snack-corner", "Sweet & Snack Corner", "Evening chai-time snacks, sweets and savouries",
                "Samosa, pav bhaji, noodles, gulab jamun and kheer — the classic chai break.", "sweetsnacks@okhdfc", 4.6, "10:30 PM", arjun); 
        Kitchen kPending = kitchen("pending-kitchen-demo", "Pending Kitchen (Demo)", "Hidden kitchen used to verify approval filtering",
                "This kitchen belongs to a PENDING-approval seller and must never appear publicly.", "pending@okhdfc", 3.0, "9:00 PM", priya);
        // ---- Aarti Kitchen ----
        product(kAarti, "Poha", "Fluffy flattened-rice breakfast tempered with peanuts, curry leaves and turmeric", 40, "plate",20,14,4.6);
        product(kAarti, "Paneer Butter Masala", "Creamy tomato gravy with soft paneer cubes, served with rice or roti", 180,"plate",10,6,4.8);
        product(kAarti, "Dal Tadka", "Yellow dal finished with a sizzling garlic-cumin tadka",  120,"plate",15,9,4.5);
        product(kAarti, "Chapati", "Soft whole-wheat phulkas, hot off the tawa",  15,"plate",30,22,4.4);
        product(kAarti, "Gulab Jamun", "Warm milk-solid dumplings soaked in rose-cardamom syrup",  50,"plate",10,4,4.7);

        // ---- Mom's Special Kitchen ----
        product(kMoms, "Aloo Paratha", "Stuffed potato paratha served with white butter and curd",  60,"plate",15,9,4.8);
        product(kMoms, "Puri Bhaji", "Crisp golden puris with spiced potato bhaji",  70,"plate",12,7,4.5);
        product(kMoms, "Rajma Chawal", "Creamy rajma over steamy basmati rice, pure comfort",  140,"plate",12,6,4.7);
        product(kMoms, "Masala Chai", "Ginger-cardamom chai, brewed the way mom makes it",  20,"cup",50,40,4.9);
        product(kMoms, "Kheer", "Slow-cooked rice kheer with saffron, almonds and pistachios",  80,"bowl",10,5,4.6);

        // ---- Desi Rasoi ----
        product(kDesi, "Veg Thali", "A wholesome platter: dal, sabzi, roti, rice, salad and pickle",  180,"platter",10,4,4.7);
        product(kDesi, "Dal Tadka", "Signature dal fry with garlic-kasuri methi tadka",  120,"plate",15,10,4.5);
        product(kDesi, "Rajma Chawal", "Punjabi-style rajma simmered overnight, served with rice",  140,"plate",12,8,4.6);
        product(kDesi, "Lassi", "Thick, sweet Punjabi lassi topped with malai",  50,"glass",20,12,4.8);
        product(kDesi, "Masala Chai", "Masala chai with ginger, clove and cinnamon",  20,"cup",50,35,4.6);

        // ---- Royal South Indian ----
        product(kRoyal, "Idli Sambhar", "Soft steamed idlis served with piping-hot sambhar and coconut chutney",  70,"plate",20,12,4.9);
        product(kRoyal, "Upma", "Rava upma tempered with curry leaves, mustard seeds and cashews",  50,"plate",15,9,4.5);
        product(kRoyal, "Masala Dosa", "Crisp golden dosa with spiced potato filling, sambhar and chutneys",  90,"plate",12,7,4.8);
        product(kRoyal, "Medu Vada", "Crisp lentil doughnuts with coconut chutney",  60,"plate",12,8,4.4);
        product(kRoyal, "Masala Chai", "Strong filter-style chai with a hint of ginger",  20,"cup",50,30,4.5);

        // ---- Punjabi Tadka House ----
        product(kPunjabi, "Paneer Butter Masala", "Silky butter-tomato gravy with tandoori paneer cubes",  180,"plate",10,5,4.7);
        product(kPunjabi, "Dal Makhani", "Black lentils slow-simmered with butter and cream",  150,"bowl",12,6,4.8);
        product(kPunjabi, "Aloo Paratha", "Crisp potato-stuffed paratha with butter, pickle and curd",  60,"plate",15,10,4.6);
        product(kPunjabi, "Butter Naan", "Tandoor-baked naan brushed with garlic butter",  25,"piece",30,20,4.5);
        product(kPunjabi, "Chicken Curry", "Homestyle chicken curry with a tomato-onion masala",  190,"plate",10,5,4.2);
        product(kPunjabi, "Lassi", "Sweet lassi with a saffron-snow cap",  50,"glass",20,14,4.6);

        // ---- Biryani Bistro ----
        product(kBiryani, "Chicken Biryani", "Fragrant basmati layers with spiced chicken, raita and salan",  220,"plate",10,4,4.9);
        product(kBiryani, "Veg Biryani", "Garden vegetables and basmati with saffron, mint and fried onions",  180,"plate",10,5,4.6);
        product(kBiryani, "Paneer Tikka", "Char-grilled paneer cubes marinated in yogurt tandoori masala",  150,"plate",12,7,4.7);
        product(kBiryani, "Hakka Noodles", "Street-style hakka noodles tossed with veggies and dark soy",  130,"plate",15,8,4.4);
        product(kBiryani, "Masala Chai", "Kulhad chai with a smoky ginger kick",  20,"cup",50,40,4.5);

        // ---- Healthy Bites ----
        product(kHealthy, "Veg Burger", "Whole-grain bun with grilled veggie patty, lettuce and hummus",  90,"piece",10,5,4.3);
        product(kHealthy, "Upma", "Light rava upma with vegetables and roasted cashews",  50,"plate",15,10,4.4);
        product(kHealthy, "Poha", "Light poha with peanuts, peas and fresh coriander",  40,"plate",20,15,4.5);
        product(kHealthy, "Fresh Veg Salad", "Garden bowl with sprouts, seeds and a lemon-tahini dressing",  120,"bowl",10,6,4.4);
        product(kHealthy, "Masala Chai", "Ginger-tulsi chai, lightly sweetened",  20,"cup",50,40,4.5);
        product(kHealthy, "Lassi", "Wholesome lassi with a hint of cardamom",  50,"glass",20,12,4.4);

        // ---- Sweet & Snack Corner ----
        product(kSnacks, "Samosa", "Crisp flaky samosa with spiced potato-pea filling, served with chutney",  20,"piece",30,18,4.6);
        product(kSnacks, "Pav Bhaji", "Buttery pav with smoky mashed bhaji topped with onions and coriander",  90,"plate",15,8,4.7);
        product(kSnacks, "Hakka Noodles", "Classic hakka noodles with crunchy vegetables",  130,"plate",15,9,4.4);
        product(kSnacks, "Gulab Jamun", "Hot gulab jamuns drenched in rose syrup",  50,"plate",12,6,4.8);
        product(kSnacks, "Kheer", "Rich slow-cooked kheer with cardamom and nuts",  80,"bowl",10,5,4.6);
        product(kSnacks, "Masala Chai", "Adrak-wali chai for your chai break",  20,"cup",50,35,4.5);

        // ---- Pending kitchen (must never appear publicly) ----
        product(kPending, "Hidden Chicken Curry", "Should never appear publicly",  200,"plate",10,5,3.0);
        product(kPending, "Hidden Biryani", "Should never appear publicly",  190,"plate",10,4,3.0);

        platformSettingRepository.save(new PlatformSetting(DEMO_SEED_FLAG, "true"));
    }

    private User seller(String name, String mobile, String flat, SellerApprovalStatus status) {
        User u = new User(name, mobile, flat, UserRole.SELLER);
        u.setSociety("Pride World City");
        u.setBuilding("Tower A");
        u.setSellerApprovalStatus(status);
        if (status == SellerApprovalStatus.APPROVED) u.setApprovedAt(LocalDateTime.now());
        return userRepository.save(u);
    }

    private Kitchen kitchen(String slug, String displayName, String shortDescription, String description,
                             String upi, double rating, String deadline, User seller) {
        Kitchen k = new Kitchen(slug, displayName, description, null, seller);
        k.setShortDescription(shortDescription);
        k.setSociety("Pride World City");
        k.setBuilding("Tower A");
        k.setWhatsappLink(null);
        k.setInstagramLink(null);
        k.setUpiId(upi);
        k.setAvailableToday(true);
        k.setOrderDeadline(deadline);
        k.setRating(rating);
        return kitchenRepository.save(k);
    }

    private Product product(Kitchen k, String name, String description, int price, String unit,
                               Integer maxQ, Integer remQ, double rating) {
        Product p = new Product(k, name, description, BigDecimal.valueOf(price), null);
        p.setPriceUnit(unit);
        p.setAvailableToday(true);
        p.setMaxQuantity(maxQ);
        p.setRemainingQuantity(remQ);
        p.setRating(rating);
        p.setIsPreorder(false);
        return productRepository.save(p);
    }
}
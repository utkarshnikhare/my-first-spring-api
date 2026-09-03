package com.example.my_first_spring_api;

import com.example.my_first_spring_api.model.Category;
import com.example.my_first_spring_api.model.Kitchen;

import com.example.my_first_spring_api.model.PlatformSetting;
import com.example.my_first_spring_api.model.PreorderType;
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
import java.util.Map;

/**
 * Seeds the database with realistic demo kitchens and menus — once only, guarded
 * by a platform_settings flag — so the search & discovery features can be tested
 * immediately. Includes one PENDING-approval kitchen to prove unapproved
 * sellers' kitchens are never exposed in public results.
 * Only active when 'demo', 'dev' or the implicit 'default' profile is enabled.
 */
@Component
@Profile({"demo", "dev", "default"})
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
        // ---- 15 approved demo sellers ----
        User aarti = seller("Aarti", "9100000001", "A-101", SellerApprovalStatus.APPROVED, "Sunshine Society", "Building B");
        User meena = seller("Meena", "9100000002", "A-102", SellerApprovalStatus.APPROVED, "Sunshine Society", "Building A");
        User ravi = seller("Ravi", "9100000003", "B-201", SellerApprovalStatus.APPROVED, "Sunshine Society", "Building C");
        User lakshmi = seller("Lakshmi", "9100000004", "C-301", SellerApprovalStatus.APPROVED, "Green Valley", "Tower 1");
        User suresh = seller("Suresh", "9100000005", "B-210", SellerApprovalStatus.APPROVED, "Green Valley", "Tower 2");
        User farah = seller("Farah", "9100000006", "D-401", SellerApprovalStatus.APPROVED, "Green Valley", "Tower 3"); 
        User geeta = seller("Geeta", "9100000007", "D-402", SellerApprovalStatus.APPROVED, "Lake View", "Block A");
 
        User arjun = seller("Arjun", "9100000008", "E-501", SellerApprovalStatus.APPROVED, "Lake View", "Block B");
 
        User priya = seller("Priya", "9100000009", "E-502", SellerApprovalStatus.APPROVED, "Lake View", "Block C");
        User vikram = seller("Vikram", "9100000010", "F-601", SellerApprovalStatus.APPROVED, "Hill Side", "Wing 1");
        User anita = seller("Anita", "9100000011", "F-602", SellerApprovalStatus.APPROVED, "Hill Side", "Wing 2");
        User rajesh = seller("Rajesh", "9100000012", "G-701", SellerApprovalStatus.APPROVED, "Hill Side", "Wing 3");
        User sunita = seller("Sunita", "9100000013", "G-702", SellerApprovalStatus.APPROVED, "Riverside", "Tower X");
        User deepak = seller("Deepak", "9100000014", "H-801", SellerApprovalStatus.APPROVED, "Riverside", "Tower Y");
        User kavita = seller("Kavita", "9100000015", "H-802", SellerApprovalStatus.APPROVED, "Riverside", "Tower Z");

        // ---- 15 Active demo kitchens ----
        Kitchen kAarti = kitchen("aarti-kitchen", "Aarti Kitchen", "Homemade Maharashtrian Food",
                "Authentic Maharashtrian dishes made with love — poha, misal, puran poli and more.", "aarti@okhdfc", 4.7, "9:00 AM", aarti);
        Kitchen kPunjabi = kitchen("punjabi-rasoi", "Punjabi Rasoi", "Punjabi Specialities",
                "Rich and creamy Punjabi curries, tandoori breads and refreshing lassi.", "punjabi@okhdfc", 4.6, "10:00 PM", meena);
        Kitchen kDakshin = kitchen("dakshin-kitchen", "Dakshin Kitchen", "South Indian Food",
                "Authentic South Indian tiffin — idli, dosa, upma, pongal and filter coffee.", "dakshin@okhdfc", 4.8, "11:00 AM", ravi);
        Kitchen kGujarati = kitchen("gujarati-ghar", "Gujarati Ghar", "Gujarati Cuisine",
                "Traditional Gujarati thali with thepla, dhokla, khandvi and undhiyu.", "gujarati@okhdfc", 4.5, "9:30 PM", lakshmi);
        Kitchen kMarwar = kitchen("marwar-rasoi", "Marwar Rasoi", "Rajasthani Food",
                "Royal Rajasthani cuisine — dal baati churma, gatte ki ker sangri and more.", "marwar@okhdfc", 4.7, "10:00 PM", suresh);
        Kitchen kBangla = kitchen("bangla-bhojan", "Bangla Bhojan", "Bengali Specialities",
                "Authentic Bengali cuisine — fish curry, mishti doi and rasgulla.", "bangla@okhdfc", 4.6, "9:00 PM", farah);
        Kitchen kDeccan = kitchen("deccan-kitchen", "Deccan Kitchen", "Hyderabadi Food",
                "Famous Hyderabadi biryani, haleem and kebabs slow-cooked to perfection.", "deccan@okhdfc", 4.8, "11:00 PM", geeta);
        Kitchen kKonkan = kitchen("konkan-swad", "Konkan Swad", "Goan/Konkan Food",
                "Coastal Goan and Konkan delicacies — fish curry, sol kadi and poee bread.", "konkan@okhdfc", 4.5, "10:30 PM", arjun);
        Kitchen kKerala = kitchen("kerala-taste", "Kerala Taste House", "Kerala Cuisine",
                "Traditional Kerala sadya, appam, stew and spicy fish preparations.", "kerala@okhdfc", 4.7, "9:00 PM", priya);
        Kitchen kMadras = kitchen("madras-kitchen", "Madras Kitchen", "Tamil Food",
                "Classic Tamil meals — sambar, rasam, curd rice and filter coffee.", "madras@okhdfc", 4.6, "11:30 AM", vikram);
        Kitchen kStreet = kitchen("desi-street-kitchen", "Desi Street Kitchen", "Indian Street Food",
                "Samosa, vada pav, pav bhaji, bhel and all your favourite street foods.", "street@okhdfc", 4.4, "10:00 PM", anita);
        Kitchen kMithas = kitchen("mithas-kitchen", "Mithas Kitchen", "Traditional Indian Sweets & Desserts",
                "Gulab jamun, rasgulla, jalebi, kheer and festive mithai.", "mithas@okhdfc", 4.8, "8:00 PM", rajesh);
        Kitchen kGhar = kitchen("ghar-ka-swad", "Ghar Ka Swad", "Homemade Vegetarian Food",
                "Simple, wholesome vegetarian meals just like home-cooked food.", "ghar@okhdfc", 4.5, "9:00 PM", sunita);
        Kitchen kTiffin = kitchen("morning-tiffin", "Morning Tiffin House", "Breakfast & Snacks",
                "Fresh breakfast tiffin — poha, upma, idli, dosa and chai.", "tiffin@okhdfc", 4.6, "10:30 AM", deepak);
        Kitchen kMulti = kitchen("bharat-multi-cuisine", "Bharat Multi-Cuisine Kitchen", "Multi-Cuisine Indian Food",
                "A diverse menu spanning North Indian, South Indian, Chinese and Continental.", "multi@okhdfc", 4.5, "10:00 PM", kavita);
        // ---- Aarti Kitchen (Maharashtrian) ----
        product(kAarti, "Poha", "Fluffy flattened-rice breakfast tempered with peanuts, curry leaves and turmeric", 40, "plate", 50, 30, 4.6);
        // Unlimited-quantity offering: 50 booked, no cap — shows "50 booked · No limit" (Spec 4.4).
        Product modak = product(kAarti, "Modak", "Traditional steamed modak filled with coconut and jaggery — Ganesh Chaturthi special", 60, "piece", null, null, 4.9);
        modak.setBookedQuantity(50);
        productRepository.save(modak);
        product(kAarti, "Idli", "Soft steamed rice and lentil cakes served with sambhar and coconut chutney", 50, "plate", 50, 30, 4.7);
        product(kAarti, "Misal Pav", "Spicy sprouted moth beans curry served with bread, farsan and lemon", 80, "plate", 30, 18, 4.8);
        product(kAarti, "Puran Poli", "Sweet flatbread stuffed with chana dal and jaggery, served with ghee", 50, "piece", 20, 12, 4.6);
        product(kAarti, "Sabudana Khichdi", "Tapioca pearls cooked with peanuts, potatoes and cumin — fasting special", 70, "plate", 25, 15, 4.5);
        product(kAarti, "Thalipeeth", "Multi-grain flatbread served with white butter and curd", 60, "plate", 20, 10, 4.4);

        // ---- Punjabi Rasoi ----
        product(kPunjabi, "Punjabi Chole", "Spicy chickpea curry with onions, tomatoes and fresh coriander", 120, "plate", 40, 25, 4.7);
        product(kPunjabi, "Rajma Chawal", "Creamy rajma over steamy basmati rice, pure comfort", 140, "plate", 35, 20, 4.8);
        product(kPunjabi, "Dal Makhani", "Slow-cooked black lentils in a rich buttery gravy", 160, "plate", 30, 18, 4.9);
        product(kPunjabi, "Paneer Butter Masala", "Creamy tomato gravy with soft paneer cubes", 180, "plate", 25, 15, 4.8);
        product(kPunjabi, "Butter Naan", "Tandoor-baked naan brushed with butter", 40, "piece", 50, 35, 4.5);
        product(kPunjabi, "Lassi", "Thick sweet lassi topped with malai", 50, "glass", 40, 28, 4.6);

        // ---- Dakshin Kitchen (South Indian) ----
        product(kDakshin, "Masala Dosa", "Crispy rice crepe filled with spiced potato, served with sambhar and chutney", 80, "plate", 40, 22, 4.8);
        product(kDakshin, "Medu Vada", "Crispy lentil fritters served with sambhar and coconut chutney", 50, "plate", 35, 20, 4.6);
        product(kDakshin, "Upma", "Semolina porridge with vegetables, nuts and curry leaves", 50, "plate", 30, 18, 4.5);
        product(kDakshin, "Pongal", "Savory rice and lentil dish with pepper, cumin and ghee", 70, "plate", 25, 15, 4.7);
        product(kDakshin, "Filter Coffee", "Traditional South Indian filter coffee", 30, "cup", 50, 40, 4.9);

        // ---- Gujarati Ghar ----
        product(kGujarati, "Gujarati Thali", "Complete thali with dal, rice, roti, sabzi, salad and sweet", 150, "plate", 30, 18, 4.7);
        product(kGujarati, "Dhokla", "Steamed savory cake made from chickpea flour, tempered with mustard", 60, "plate", 40, 28, 4.6);
        product(kGujarati, "Khandvi", "Soft gram flour rolls tempered with mustard and coconut", 70, "plate", 25, 15, 4.5);
        product(kGujarati, "Thepla", "Fenugreek flatbread, perfect for travel", 30, "piece", 50, 38, 4.4);
        product(kGujarati, "Undhiyu", "Mixed vegetable dish cooked in an earthen pot", 140, "plate", 20, 12, 4.7);

        // ---- Marwar Rasoi (Rajasthani) ----
        product(kMarwar, "Dal Baati Churma", "Baked baati with dal and sweet churma — Rajasthani classic", 180, "plate", 25, 15, 4.8);
        product(kMarwar, "Gatte Ki Sabzi", "Gram flour dumplings in spicy yogurt gravy", 140, "plate", 20, 12, 4.6);
        product(kMarwar, "Ker Sangri", "Desert beans and berries cooked with spices", 120, "plate", 15, 8, 4.5);
        product(kMarwar, "Bajra Roti", "Pearl millet flatbread served with white butter", 40, "piece", 30, 20, 4.4);

        // ---- Bangla Bhojan (Bengali) ----
        product(kBangla, "Bengali Fish Curry", "Rohu fish in mustard gravy with green chillies", 200, "plate", 20, 12, 4.8);
        product(kBangla, "Mishti Doi", "Sweetened yogurt set in earthen pots", 60, "cup", 30, 20, 4.7);
        product(kBangla, "Rasgulla", "Soft cottage cheese balls in light sugar syrup", 40, "piece", 40, 28, 4.6);
        product(kBangla, "Sandesh", "Traditional Bengali sweet made from fresh paneer", 80, "piece", 25, 18, 4.7);

        // ---- Deccan Kitchen (Hyderabadi) ----
        product(kDeccan, "Hyderabadi Biryani", "Aromatic basmati rice layered with spiced chicken and saffron", 250, "plate", 30, 18, 4.9);
        product(kDeccan, "Veg Biryani", "Fragrant rice with mixed vegetables and biryani spices", 180, "plate", 25, 15, 4.7);
        product(kDeccan, "Haleem", "Slow-cooked wheat and meat porridge with spices", 200, "plate", 20, 12, 4.8);
        product(kDeccan, "Kebabs", "Tandoori chicken kebabs marinated in yogurt and spices", 180, "plate", 25, 15, 4.6);

        // ---- Konkan Swad (Goan/Konkan) ----
        product(kKonkan, "Goan Fish Curry", "Fish cooked in coconut, kokum and red chilli gravy", 220, "plate", 20, 12, 4.8);
        product(kKonkan, "Sol Kadi", "Refreshing drink made from kokum and coconut milk", 50, "glass", 30, 22, 4.6);
        product(kKonkan, "Poee Bread", "Goan wood-fired flatbread", 40, "piece", 25, 18, 4.5);
        product(kKonkan, "Prawn Balchão", "Spicy prawn pickle-style curry with Goan vinegar", 280, "plate", 15, 8, 4.7);

        // ---- Kerala Taste House ----
        product(kKerala, "Kerala Sadya", "Traditional vegetarian feast served on banana leaf", 200, "plate", 20, 12, 4.8);
        product(kKerala, "Appam with Stew", "Lacy rice pancakes with coconut vegetable stew", 120, "plate", 25, 15, 4.7);
        product(kKerala, "Kerala Fish Fry", "Spicy marinated fish fried with curry leaves", 180, "plate", 20, 12, 4.6);
        product(kKerala, "Payasam", "Sweet milk pudding with cardamom and nuts", 80, "bowl", 30, 20, 4.7);

        // ---- Madras Kitchen (Tamil) ----
        product(kMadras, "Sambar", "Lentil stew with vegetables, tamarind and sambar powder", 80, "plate", 40, 28, 4.6);
        product(kMadras, "Rasam", "Spicy tamarind soup with pepper and cumin", 50, "cup", 35, 25, 4.5);
        product(kMadras, "Curd Rice", "Thick yogurt rice tempered with mustard and ginger", 60, "plate", 30, 20, 4.4);
        product(kMadras, "Tamil Meals", "Full meals with rice, sambar, rasam, poriyal and appalam", 150, "plate", 25, 15, 4.7);

        // ---- Desi Street Kitchen ----
        product(kStreet, "Samosa", "Crispy pastry filled with spiced potatoes and peas", 20, "piece", 50, 35, 4.5);
        product(kStreet, "Vada Pav", "Spicy potato fritter in a pav with garlic and dry chutney", 40, "plate", 40, 28, 4.6);
        product(kStreet, "Pav Bhaji", "Mashed vegetable curry served with buttered pav", 100, "plate", 30, 18, 4.7);
        product(kStreet, "Bhel Puri", "Puffed rice snack with chutneys, onions and sev", 50, "plate", 35, 25, 4.4);
        product(kStreet, "Jalebi", "Crispy spirals soaked in saffron sugar syrup", 40, "plate", 40, 30, 4.5);

        // ---- Mithas Kitchen (Sweets) ----
        product(kMithas, "Gulab Jamun", "Warm milk-solid dumplings soaked in rose-cardamom syrup", 50, "plate", 30, 20, 4.8);
        product(kMithas, "Rasgulla", "Soft cottage cheese balls in light sugar syrup", 40, "piece", 40, 28, 4.7);
        product(kMithas, "Jalebi", "Crispy spirals soaked in saffron sugar syrup", 40, "plate", 35, 25, 4.6);
        product(kMithas, "Kheer", "Slow-cooked rice kheer with saffron, almonds and pistachios", 80, "bowl", 25, 15, 4.7);
        product(kMithas, "Shrikhand", "Creamy strained yogurt with saffron and cardamom", 90, "bowl", 20, 12, 4.6);
        product(kMithas, "Chai", "Ginger-cardamom chai, brewed fresh", 20, "cup", 50, 40, 4.5);

        // ---- Ghar Ka Swad (Homemade Vegetarian) ----
        product(kGhar, "Dal Tadka", "Yellow dal finished with a sizzling garlic-cumin tadka", 120, "plate", 30, 18, 4.5);
        product(kGhar, "Aloo Gobi", "Potato and cauliflower dry curry with cumin and turmeric", 100, "plate", 25, 15, 4.4);
        product(kGhar, "Chapati", "Soft whole-wheat phulkas, hot off the tawa", 15, "piece", 50, 38, 4.3);
        product(kGhar, "Mixed Veg Curry", "Seasonal vegetables in a light onion-tomato gravy", 130, "plate", 20, 12, 4.5);
        product(kGhar, "Raita", "Whisked yogurt with cucumber and roasted cumin", 40, "bowl", 30, 22, 4.4);

        // ---- Morning Tiffin House ----
        product(kTiffin, "Poha", "Fluffy flattened-rice breakfast tempered with peanuts and curry leaves", 40, "plate", 40, 28, 4.6);
        product(kTiffin, "Upma", "Semolina porridge with vegetables, nuts and curry leaves", 50, "plate", 35, 22, 4.5);
        product(kTiffin, "Idli", "Soft steamed rice cakes with sambhar and chutney", 50, "plate", 40, 25, 4.7);
        product(kTiffin, "Dosa", "Crispy rice crepe with potato filling", 70, "plate", 30, 18, 4.6);
        product(kTiffin, "Masala Chai", "Ginger-cardamom chai, brewed fresh", 20, "cup", 50, 40, 4.8);

        // ---- Bharat Multi-Cuisine Kitchen ----
        product(kMulti, "Butter Chicken", "Tender chicken in creamy tomato-butter gravy", 220, "plate", 25, 15, 4.8);
        product(kMulti, "Paneer Tikka", "Marinated cottage cheese cubes grilled in tandoor", 180, "plate", 20, 12, 4.7);
        product(kMulti, "Hakka Noodles", "Stir-fried noodles with vegetables and soy sauce", 120, "plate", 30, 18, 4.5);
        product(kMulti, "Veg Fried Rice", "Rice stir-fried with mixed vegetables and sauces", 110, "plate", 25, 15, 4.4);
        product(kMulti, "Gulab Jamun", "Warm milk-solid dumplings in rose-cardamom syrup", 50, "plate", 30, 20, 4.6);

        platformSettingRepository.save(new PlatformSetting(DEMO_SEED_FLAG, "true"));
    }

    private User seller(String name, String mobile, String flat, SellerApprovalStatus status, String society, String building) {
        User u = new User(name, mobile, flat, UserRole.SELLER);
        u.setSociety(society);
        u.setBuilding(building);
        u.setSellerApprovalStatus(status);
        if (status == SellerApprovalStatus.APPROVED) u.setApprovedAt(LocalDateTime.now());
        return userRepository.save(u);
    }

    private Kitchen kitchen(String slug, String displayName, String shortDescription, String description,
                             String upi, double rating, String deadline, User seller) {
        Kitchen k = new Kitchen(slug, displayName, description, null, seller);
        k.setShortDescription(shortDescription);
        k.setSociety(seller.getSociety());
        k.setBuilding(seller.getBuilding());
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
        // Offering-level discovery metadata: category + the offering's own cutoff
        // (cutoffs NEVER belong to the kitchen as a whole — Spec 1.4).
        p.setCategory(categoryFor(name));
        p.setCutoffTime(cutoffFor(name));
        p.setReadyByTime(readyByFor(name));
        if (maxQ != null && remQ != null) p.setBookedQuantity(Math.max(0, maxQ - remQ));
        return productRepository.save(p);
    }

    private static final Map<String, Category> CATEGORIES = Map.ofEntries(
            Map.entry("poha", Category.BREAKFAST), Map.entry("upma", Category.BREAKFAST),
            Map.entry("idli", Category.BREAKFAST), Map.entry("masala dosa", Category.BREAKFAST),
            Map.entry("medu vada", Category.BREAKFAST), Map.entry("aloo paratha", Category.BREAKFAST),
            Map.entry("puri bhaji", Category.BREAKFAST), Map.entry("tea", Category.BREAKFAST),
            Map.entry("chai", Category.BREAKFAST), Map.entry("burger", Category.SNACKS),
            Map.entry("veg thali", Category.LUNCH), Map.entry("rajma chawal", Category.LUNCH),
            Map.entry("dal tadka", Category.LUNCH), Map.entry("paneer butter masala", Category.LUNCH),
            Map.entry("dal makhani", Category.LUNCH), Map.entry("chicken curry", Category.LUNCH),
            Map.entry("chicken biryani", Category.LUNCH), Map.entry("veg biryani", Category.LUNCH),
            Map.entry("chapati", Category.LUNCH), Map.entry("butter naan", Category.LUNCH),
            Map.entry("hakka noodles", Category.DINNER), Map.entry("paneer tikka", Category.DINNER),
            Map.entry("pav bhaji", Category.DINNER), Map.entry("fresh veg salad", Category.SPECIAL),
            Map.entry("lassi", Category.SNACKS), Map.entry("samosa", Category.SNACKS),
            Map.entry("gulab jamun", Category.SNACKS), Map.entry("kheer", Category.SNACKS));

    private static Category categoryFor(String name) {
        String n = name.toLowerCase();
        for (Map.Entry<String, Category> e : CATEGORIES.entrySet()) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return Category.SPECIAL;
    }

    /** Demo per-offering cutoffs (HH:mm) — e.g. breakfasts close at 11 AM. */
    private static String cutoffFor(String name) {
        String n = name.toLowerCase();
        if (n.contains("chai") || n.contains("vada")) return "10:30";
        if (n.contains("dosa") || n.contains("idli") || n.contains("upma") || n.contains("poha")
                || n.contains("paratha") || n.contains("puri")) return "11:00";
        if (n.contains("biryani") || n.contains("thali")) return "12:30";
        if (n.contains("noodles") || n.contains("bhaji")) return "18:30";
        return "20:00";
    }

    private static String readyByFor(String name) {
        String n = name.toLowerCase();
        if (n.contains("chai") || n.contains("vada")) return "12:00 PM today";
        if (n.contains("dosa") || n.contains("idli") || n.contains("upma") || n.contains("poha")
                || n.contains("paratha") || n.contains("puri")) return "1:00 PM today";
        if (n.contains("biryani") || n.contains("thali")) return "4:00 PM today";
        if (n.contains("noodles") || n.contains("bhaji")) return "8:00 PM this evening";
        return "9:00 PM today";
    }

    /** One deliberately sold-out offering (🔴 Sold out with disabled button). */
    private Product soldOutProduct(Kitchen k, String name, String description, int price, String unit,
                                   Integer maxQ, double rating) {
        Product p = product(k, name, description, price, unit, maxQ, 0, rating);
        return p;
    }

    /** FIXED pre-order: available tomorrow only, cutoff 12 PM today. */
    private Product fixedPreorder(Kitchen k, String name, String description, int price, String unit,
                                  Integer maxQ, double rating, Category category) {
        Product p = new Product(k, name, description, BigDecimal.valueOf(price), null);
        p.setPriceUnit(unit);
        p.setAvailableToday(false);
        p.setAvailableDate(java.time.LocalDate.now().plusDays(1));
        p.setMaxQuantity(maxQ);
        p.setRemainingQuantity(maxQ);
        p.setRating(rating);
        p.setIsPreorder(true);
        p.setPreorderType(PreorderType.FIXED);
        p.setCategory(category);
        p.setCutoffTime("12:00");
        p.setReadyByTime("1:30 PM tomorrow");
        p.setBookedQuantity(0);
        return productRepository.save(p);
    }

    /** FLEXIBLE pre-order: buyer picks date (tomorrow..+6d) + slot; day-before cutoff. */
    private Product flexiblePreorder(Kitchen k, String name, String description, int price, String unit,
                                     Integer maxQ, double rating, Category category) {
        Product p = new Product(k, name, description, BigDecimal.valueOf(price), null);
        p.setPriceUnit(unit);
        p.setAvailableToday(false);
        p.setAvailableDate(java.time.LocalDate.now().plusDays(1));
        p.setAvailableUntilDate(java.time.LocalDate.now().plusDays(6));
        p.setMaxQuantity(maxQ);
        p.setRemainingQuantity(maxQ);
        p.setRating(rating);
        p.setIsPreorder(true);
        p.setPreorderType(PreorderType.FLEXIBLE);
        p.setCategory(category);
        p.setCutoffTime("21:00");
        p.setReadyByTime("your chosen slot");
        p.setTimeSlots("1:00 PM,4:00 PM,8:00 PM");
        p.setBookedQuantity(0);
        return productRepository.save(p);
    }
}
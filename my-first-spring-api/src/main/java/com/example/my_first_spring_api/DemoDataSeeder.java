package com.example.my_first_spring_api;

import com.example.my_first_spring_api.model.Enquiry;
import com.example.my_first_spring_api.model.EnquiryStatus;
import com.example.my_first_spring_api.model.SellerTemplate;
import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Order;
import com.example.my_first_spring_api.model.OrderItem;
import com.example.my_first_spring_api.model.OrderStatus;
import com.example.my_first_spring_api.model.PaymentStatus;
import com.example.my_first_spring_api.model.PlatformSetting;
import com.example.my_first_spring_api.model.PreorderType;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.Category;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.PlatformSettingRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.SellerTemplateRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import com.example.my_first_spring_api.repository.EnquiryRepository;
import com.example.my_first_spring_api.repository.FavouriteRepository;
import com.example.my_first_spring_api.model.Favourite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private static final String DEMO_BUYERS_FLAG = "demo_buyers_seeded";
    private static final String DEMO_ORDERS_FLAG = "demo_orders_seeded";
    private static final String DEMO_ENQUIRIES_FLAG = "demo_enquiries_seeded";
    private static final String DEMO_FAVOURITES_FLAG = "demo_favourites_seeded";
    private static final String DEMO_VIEW_ORDERS_FLAG = "demo_view_orders_seeded";
    private static final String DEMO_SELLER_ARCHIVE_FLAG = "demo_seller_archive_seeded";
    /** The seller the Seller App demo-login signs in as — its archive is what the demo shows. */
    private static final String DEMO_SELLER_MOBILE = "9100000001";
    private int orderCounter = 0;

    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;
    private final ProductRepository productRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final com.example.my_first_spring_api.repository.OrderRepository orderRepository;
    private final EnquiryRepository enquiryRepository;
    private final FavouriteRepository favouriteRepository;
    private final SellerTemplateRepository sellerTemplateRepository;

    @Autowired
    public DemoDataSeeder(UserRepository userRepository, KitchenRepository kitchenRepository,
                           ProductRepository productRepository, PlatformSettingRepository platformSettingRepository,
                           com.example.my_first_spring_api.repository.OrderRepository orderRepository,
                           EnquiryRepository enquiryRepository, FavouriteRepository favouriteRepository,
                           SellerTemplateRepository sellerTemplateRepository) {
        this.userRepository = userRepository;
        this.kitchenRepository = kitchenRepository;
        this.productRepository = productRepository;
        this.platformSettingRepository = platformSettingRepository;
        this.orderRepository = orderRepository;
        this.enquiryRepository = enquiryRepository;
        this.favouriteRepository = favouriteRepository;
        this.sellerTemplateRepository = sellerTemplateRepository;
    }

    /** Idempotent entry point called from DataInitializer on every startup. */
    public void seedAll() {
        seedIfEmpty();
        seedBuyersIfEmpty();
        seedOrdersIfEmpty();
        seedViewOrdersScenarioIfEmpty();
        seedEnquiriesIfEmpty();
        seedFavouritesIfEmpty();
        seedSellerArchiveIfEmpty();
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
        User meenaCakes = seller("Meena", "9100000016", "I-101", SellerApprovalStatus.APPROVED, "Lohegaon", "Sai Arcade");

        // ---- 15 Active demo kitchens ----
        Kitchen kAarti = kitchen("aarti-kitchen", "Aarti Kitchen", "Homemade Maharashtrian Food",
                "Authentic Maharashtrian dishes made with love — poha, misal, puran poli and more.", "aarti@okhdfc", 4.7, "9:00 AM", aarti, "https://example.com/aarti-kitchen.jpg", "https://instagram.com/aartikitchen");
        Kitchen kPunjabi = kitchen("punjabi-rasoi", "Punjabi Rasoi", "Punjabi Specialities",
                "Rich and creamy Punjabi curries, tandoori breads and refreshing lassi.", "punjabi@okhdfc", 4.6, "10:00 PM", meena, null, null);
        Kitchen kDakshin = kitchen("dakshin-kitchen", "Dakshin Kitchen", "South Indian Food",
                "Authentic South Indian tiffin — idli, dosa, upma, pongal and filter coffee.", "dakshin@okhdfc", 4.8, "11:00 AM", ravi, null, null);
        Kitchen kGujarati = kitchen("gujarati-ghar", "Gujarati Ghar", "Gujarati Cuisine",
                "Traditional Gujarati thali with thepla, dhokla, khandvi and undhiyu.", "gujarati@okhdfc", 4.5, "9:30 PM", lakshmi, null, null);
        Kitchen kMarwar = kitchen("marwar-rasoi", "Marwar Rasoi", "Rajasthani Food",
                "Royal Rajasthani cuisine — dal baati churma, gatte ki ker sangri and more.", "marwar@okhdfc", 4.7, "10:00 PM", suresh, null, null);
        Kitchen kBangla = kitchen("bangla-bhojan", "Bangla Bhojan", "Bengali Specialities",
                "Authentic Bengali cuisine — fish curry, mishti doi and rasgulla.", "bangla@okhdfc", 4.6, "9:00 PM", farah, null, null);
        Kitchen kDeccan = kitchen("deccan-kitchen", "Deccan Kitchen", "Hyderabadi Food",
                "Famous Hyderabadi biryani, haleem and kebabs slow-cooked to perfection.", "deccan@okhdfc", 4.8, "11:00 PM", geeta, null, null);
        Kitchen kKonkan = kitchen("konkan-swad", "Konkan Swad", "Goan/Konkan Food",
                "Coastal Goan and Konkan delicacies — fish curry, sol kadi and poee bread.", "konkan@okhdfc", 4.5, "10:30 PM", arjun, null, null);
        Kitchen kKerala = kitchen("kerala-taste", "Kerala Taste House", "Kerala Cuisine",
                "Traditional Kerala sadya, appam, stew and spicy fish preparations.", "kerala@okhdfc", 4.7, "9:00 PM", priya, null, null);
        Kitchen kMadras = kitchen("madras-kitchen", "Madras Kitchen", "Tamil Food",
                "Classic Tamil meals — sambar, rasam, curd rice and filter coffee.", "madras@okhdfc", 4.6, "11:30 AM", vikram, null, null);
        Kitchen kStreet = kitchen("desi-street-kitchen", "Desi Street Kitchen", "Indian Street Food",
                "Samosa, vada pav, pav bhaji, bhel and all your favourite street foods.", "street@okhdfc", 4.4, "10:00 PM", anita, null, null);
        Kitchen kMithas = kitchen("mithas-kitchen", "Mithas Kitchen", "Traditional Indian Sweets & Desserts",
                "Gulab jamun, rasgulla, jalebi, kheer and festive mithai.", "mithas@okhdfc", 4.8, "8:00 PM", rajesh, null, null);
        Kitchen kGhar = kitchen("ghar-ka-swad", "Ghar Ka Swad", "Homemade Vegetarian Food",
                "Simple, wholesome vegetarian meals just like home-cooked food.", "ghar@okhdfc", 4.5, "9:00 PM", sunita, null, null);
        Kitchen kTiffin = kitchen("morning-tiffin", "Morning Tiffin House", "Breakfast & Snacks",
                "Fresh breakfast tiffin — poha, upma, idli, dosa and chai.", "tiffin@okhdfc", 4.6, "10:30 AM", deepak, null, null);
        Kitchen kMulti = kitchen("bharat-multi-cuisine", "Bharat Multi-Cuisine Kitchen", "Multi-Cuisine Indian Food",
                "A diverse menu spanning North Indian, South Indian, Chinese and Continental.", "multi@okhdfc", 4.5, "10:00 PM", kavita, null, null);
        Kitchen kMeenaCakes = kitchen("meena-cakes", "Meena's Cakes", "Homemade Cakes & Snacks",
                "Custom cakes, cookies, and snacks made to order. Message for custom designs.", "meena@okhdfc", 4.8, "No deadline", meenaCakes, "https://example.com/meena-cakes.jpg", "@meenacakes");
        kMeenaCakes.setSellerType(com.example.my_first_spring_api.model.SellerType.HOMEMADE_PRODUCTS);
        kitchenRepository.save(kMeenaCakes);
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

        // Pre-order demo: Puran Poli for next Monday
        // NOTE: Product's 5th constructor arg is imageUrl (not unit) — pass null
        // and set the unit via setPriceUnit, otherwise imageUrl="piece" renders
        // <img src="piece"> and the browser requests GET /piece (404 + console error).
        Product prePuran = new Product(kAarti, "Puran Poli (Pre-order)", "Sweet flatbread stuffed with chana dal and jaggery, served with ghee — pre-order for Monday", BigDecimal.valueOf(70), null);
        prePuran.setPriceUnit("piece");
        prePuran.setAvailableToday(false);
        prePuran.setAvailableDate(java.time.LocalDate.now().plusDays(7 - java.time.LocalDate.now().getDayOfWeek().getValue() + 1));
        prePuran.setMaxQuantity(30);
        prePuran.setRemainingQuantity(30);
        prePuran.setRating(4.7);
        prePuran.setIsPreorder(true);
        prePuran.setPreorderType(PreorderType.FIXED);
        prePuran.setCategory("SPECIAL");
        prePuran.setCutoffTime("12:00");
        prePuran.setReadyByTime("1:30 PM Monday");
        prePuran.setBookedQuantity(0);
        productRepository.save(prePuran);

        // ---- Meena's Cakes (Homemade) ----
        product(kMeenaCakes, "Chocolate Cake", "Rich chocolate cake with creamy frosting. Available in 250g/500g/1kg.", 250, "250g", 20, 10, 4.9);
        product(kMeenaCakes, "Mixed Fruit Cake", "Fresh fruit cake with seasonal fruits and whipped cream.", 280, "250g", 15, 8, 4.7);
        product(kMeenaCakes, "Special Mango Cake", "Mango-flavoured cake with mango pieces and mango pulp frosting.", 550, "500g", 10, 5, 4.8);
        product(kMeenaCakes, "Gulab Jamun", "Soft milk-solid balls soaked in rose-flavoured sugar syrup.", 120, "piece", 30, 15, 4.6);
        product(kMeenaCakes, "Laddoo", "Traditional besan laddoo made with ghee and dry fruits.", 100, "piece", 40, 20, 4.5);

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

        platformSettingRepository.save(new PlatformSetting("enquiry_lead_fee", "0"));
        platformSettingRepository.save(new PlatformSetting(DEMO_SEED_FLAG, "true"));
    }

    // ==================== DEMO BUYERS ====================

    public void seedBuyersIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_BUYERS_FLAG).isPresent()) return;
        // Buyer societies MUST match seller/kitchen societies (seeMarketplaceService.isServiceAreaVisible)
        // so that authenticated buyers can discover and order from kitchens in their area.
        // Kitchen societies: Sunshine Society, Green Valley, Lake View, Hill Side, Riverside, Lohegaon
        seedBuyer("Aarav Mehta", "9876500001", "A-402", "Sunshine Society", "A Wing");
        seedBuyer("Priya Sharma", "9876500002", "B-105", "Sunshine Society", "B Wing");
        seedBuyer("Rahul Joshi", "9876500003", "C-303", "Sunshine Society", "Tower 2");
        seedBuyer("Neha Patil", "9876500004", "D-201", "Green Valley", "Building C");
        seedBuyer("Rohan Desai", "9876500005", "A-101", "Green Valley", "Tower 1");
        seedBuyer("Sneha Kulkarni", "9876500006", "B-505", "Green Valley", "A Wing");
        seedBuyer("Ananya Shah", "9876500007", "C-404", "Lake View", "Tower 3");
        seedBuyer("Karan Verma", "9876500008", "A-301", "Lake View", "Block A");
        seedBuyer("Meera Iyer", "9876500009", "D-102", "Lake View", "Building B");
        seedBuyer("Vikram Rao", "9876500010", "B-202", "Hill Side", "Tower 2");
        seedBuyer("Ishaan Gupta", "9876500011", "A-501", "Hill Side", "C Wing");
        seedBuyer("Diya Nair", "9876500012", "C-101", "Hill Side", "Tower 1");
        seedBuyer("Amit Tiwari", "9876500013", "D-303", "Riverside", "Building A");
        seedBuyer("Pooja Reddy", "9876500014", "B-401", "Riverside", "Tower 3");
        seedBuyer("Nikhil Jain", "9876500015", "A-202", "Lohegaon", "Tower 1");
        platformSettingRepository.save(new PlatformSetting(DEMO_BUYERS_FLAG, "true"));
    }

    private User seedBuyer(String name, String mobile, String flat, String society, String building) {
        User b = userRepository.findByMobileNumber(mobile).orElse(null);
        if (b == null) {
            b = new User(name, mobile, flat, UserRole.BUYER);
        }
        b.setSociety(society);
        b.setBuilding(building);
        return userRepository.save(b);
    }

    // ==================== DEMO ORDERS ====================

    public void seedOrdersIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_ORDERS_FLAG).isPresent()) return;
        java.util.List<User> sellers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.SELLER && u.getSellerApprovalStatus() == SellerApprovalStatus.APPROVED)
                .collect(java.util.stream.Collectors.toList());
        for (User s : sellers) {
            seedOrdersForSeller(s, true);
            seedOrdersForSeller(s, false);
        }
        platformSettingRepository.save(new PlatformSetting(DEMO_ORDERS_FLAG, "true"));
    }

    private void seedOrdersForSeller(User seller, boolean today) {
        java.util.List<Kitchen> kits = kitchenRepository.findBySeller(seller);
        if (kits.isEmpty()) return;
        Kitchen k = kits.get(0);
        java.util.List<Product> products = productRepository.findByKitchen(k);
        if (products.isEmpty()) return;
        String[] mobiles = {"9876500001","9876500002","9876500003","9876500004","9876500005"};
        String[] remarks = {"Less spicy please","Extra chutney","","Ring bell twice","No onions"};
        for (int i = 0; i < Math.min(products.size(), 4); i++) {
            User buyer = userRepository.findByMobileNumber(mobiles[i % mobiles.length]).orElse(null);
            if (buyer == null) continue;
            int qty = (i % 3) + 1;
            String pay = (i % 3 == 0) ? "PAID" : (i % 3 == 1) ? "WILL_PAY_LATER" : "PAID";
            String ord = (i == 3 && !today) ? "CANCELLED" : (pay.equals("PAID") ? "CONFIRMED" : "ORDERED");
            createOrder(buyer, products.get(i), qty, pay, ord, remarks[i], today);
        }
    }

    private Order createOrder(User buyer, Product p, int qty, String payStatus, String ordStatus, String remark, boolean today) {
        Order o = new Order(buyer, p.getKitchen());
        o.setOrderStatus(OrderStatus.valueOf(ordStatus));
        o.setPaymentStatus(PaymentStatus.valueOf(payStatus));
        o.setCustomInstructions(remark.isBlank() ? null : remark);
        o.setOrderNumber("SM-" + (5000 + orderCounter++));
        o = orderRepository.save(o);
        OrderItem item = new OrderItem(p, qty, p.getPrice());
        o.addItem(item);
        o.recalculateTotal();
        LocalDateTime ts = today
                ? LocalDateTime.now().minusMinutes(5L + (long)(Math.random() * 230))
                : LocalDateTime.now().minusDays(1).minusMinutes((long)(Math.random() * 360));
        o.setCreatedAt(ts);
        o.setOrderTime(ts);
        o.setUpdatedAt(ts);
        return orderRepository.save(o);
    }

    // ==================== VIEW-ORDERS DEMO SCENARIO (AFFECTED OFFERING) ====================

    /**
     * Deterministic scenario for the offering reported on the Seller "View Orders"
     * screen — Aarti Kitchen's Poha.
     *
     * The generic seeder above drops at most one small order per product per day
     * and never applies the inventory rules that real checkout uses
     * (ProductRepository.consumeStock / restoreStock), so the offering card could
     * advertise a booked quantity that no persisted order ever accounted for.
     *
     * This step creates six fixed orders — all stamped today — for six seeded
     * buyers across two societies: three PAID, one PENDING, one WILL_PAY_LATER and
     * one CANCELLED, with different quantities (6+3+2+4+3 booked plates plus a
     * cancelled 2-plate order). It then re-derives the offering's inventory from
     * ALL persisted orders using the existing business rules:
     *   booked    = sum of non-cancelled order plates (a cancelled order's stock is
     *               counted as restored, exactly like OrderService.restoreStock);
     *   remaining = maxQuantity - booked.
     *
     * Guarded by a platform_settings flag so re-seeding never duplicates orders,
     * customers or inventory movements, and the reconciliation is an absolute
     * computation from persisted orders, so it is idempotent by construction.
     * The dashboard's booked/available figures are therefore backed 1:1 by the
     * orders the seller can open under View Orders.
     */
    public void seedViewOrdersScenarioIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_VIEW_ORDERS_FLAG).isPresent()) return;

        User aarti = userRepository.findByMobileNumber("9100000001").orElse(null);
        if (aarti != null) {
            java.util.List<Kitchen> kits = kitchenRepository.findBySeller(aarti);
            if (!kits.isEmpty()) {
                Product poha = productRepository.findByKitchen(kits.get(0)).stream()
                        .filter(p -> "Poha".equalsIgnoreCase(p.getName()))
                        .findFirst().orElse(null);
                if (poha != null) {
                    // {buyer mobile, quantity, payment status, order status, remark, minutes ago}
                    String[][] rows = {
                            {"9876500001", "6", "PAID", "CONFIRMED", "Extra chutney", "18"},
                            {"9876500002", "3", "PENDING", "ORDERED", "", "46"},
                            {"9876500003", "2", "WILL_PAY_LATER", "ORDERED", "Less spicy please", "74"},
                            {"9876500004", "4", "PAID", "CONFIRMED", "No onions", "102"},
                            {"9876500005", "3", "PAID", "CONFIRMED", "", "130"},
                            {"9876500006", "2", "PAID", "CANCELLED", "Ordered by mistake", "158"}
                    };
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime dayStart = LocalDate.now().atStartOfDay();
                    for (int i = 0; i < rows.length; i++) {
                        User buyer = userRepository.findByMobileNumber(rows[i][0]).orElse(null);
                        if (buyer == null) continue;
                        LocalDateTime ts = now.minusMinutes(Long.parseLong(rows[i][5]));
                        if (ts.isBefore(dayStart.plusMinutes(1))) ts = dayStart.plusMinutes(1 + i);
                        createScenarioOrder(buyer, poha, Integer.parseInt(rows[i][1]),
                                rows[i][2], rows[i][3], rows[i][4], "SM-71" + i, ts);
                    }
                    reconcileOfferingInventory(poha);
                }
            }
        }
        platformSettingRepository.save(new PlatformSetting(DEMO_VIEW_ORDERS_FLAG, "true"));
    }

    /** Like {@link #createOrder} but with a fixed order number and timestamp (deterministic). */
    private Order createScenarioOrder(User buyer, Product p, int qty, String payStatus, String ordStatus,
                                      String remark, String orderNumber, LocalDateTime ts) {
        Order o = new Order(buyer, p.getKitchen());
        o.setOrderStatus(OrderStatus.valueOf(ordStatus));
        o.setPaymentStatus(PaymentStatus.valueOf(payStatus));
        o.setCustomInstructions(remark == null || remark.isBlank() ? null : remark);
        o.setOrderNumber(orderNumber);
        o = orderRepository.save(o);
        o.addItem(new OrderItem(p, qty, p.getPrice()));
        o.recalculateTotal();
        o.setCreatedAt(ts);
        o.setOrderTime(ts);
        o.setUpdatedAt(ts);
        return orderRepository.save(o);
    }

    /**
     * Derives the offering's booked/available quantities from the authoritative
     * persisted orders under the existing inventory rules (non-cancelled plates
     * are booked; remaining = max - booked). Being an absolute computation, it
     * can never double-decrement no matter how often the seeder runs.
     */
    private void reconcileOfferingInventory(Product p) {
        if (p.getRemainingQuantity() == null || p.getMaxQuantity() == null) return;
        int booked = 0;
        LocalDateTime allTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        for (Order o : orderRepository.findByKitchenAndCreatedAtAfterWithItems(p.getKitchen(), allTime)) {
            if (o.getOrderStatus() == OrderStatus.CANCELLED || o.getOrderStatus() == OrderStatus.DRAFT) continue;
            for (OrderItem item : o.getItems()) {
                if (item == null || item.getProduct() == null || item.getProduct().getId() == null) continue;
                if (!item.getProduct().getId().equals(p.getId())) continue;
                booked += item.getQuantity() != null ? item.getQuantity() : 0;
            }
        }
        p.setBookedQuantity(booked);
        p.setRemainingQuantity(Math.max(0, p.getMaxQuantity() - booked));
        productRepository.save(p);
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
                             String upi, double rating, String deadline, User seller, String imageUrl, String instagramLink) {
        Kitchen k = new Kitchen(slug, displayName, description, imageUrl, seller);
        k.setShortDescription(shortDescription);
        k.setSociety(seller.getSociety());
        k.setBuilding(seller.getBuilding());
        k.setWhatsappLink(null);
        k.setInstagramLink(instagramLink);
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

    private static final Map<String, String> CATEGORIES = Map.ofEntries(
            Map.entry("poha", "BREAKFAST"), Map.entry("upma", "BREAKFAST"),
            Map.entry("idli", "BREAKFAST"), Map.entry("masala dosa", "BREAKFAST"),
            Map.entry("medu vada", "BREAKFAST"), Map.entry("aloo paratha", "BREAKFAST"),
            Map.entry("puri bhaji", "BREAKFAST"), Map.entry("tea", "BREAKFAST"),
            Map.entry("chai", "BREAKFAST"), Map.entry("burger", "SNACKS"),
            Map.entry("veg thali", "LUNCH"), Map.entry("rajma chawal", "LUNCH"),
            Map.entry("dal tadka", "LUNCH"), Map.entry("paneer butter masala", "LUNCH"),
            Map.entry("dal makhani", "LUNCH"), Map.entry("chicken curry", "LUNCH"),
            Map.entry("chicken biryani", "LUNCH"), Map.entry("veg biryani", "LUNCH"),
            Map.entry("chapati", "LUNCH"), Map.entry("butter naan", "LUNCH"),
            Map.entry("hakka noodles", "DINNER"), Map.entry("paneer tikka", "DINNER"),
            Map.entry("pav bhaji", "DINNER"), Map.entry("fresh veg salad", "SPECIAL"),
            Map.entry("lassi", "SNACKS"), Map.entry("samosa", "SNACKS"),
            Map.entry("gulab jamun", "SNACKS"), Map.entry("kheer", "SNACKS"));

    private static String categoryFor(String name) {
        String n = name.toLowerCase();
        for (Map.Entry<String, String> e : CATEGORIES.entrySet()) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return "SPECIAL";
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
                                  Integer maxQ, double rating, String category) {
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
                                     Integer maxQ, double rating, String category) {
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

    // ==================== DEMO ENQUIRIES ====================

    public void seedEnquiriesIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_ENQUIRIES_FLAG).isPresent()) return;
        java.util.List<User> buyers = userRepository.findByRole(UserRole.BUYER);
        java.util.List<Kitchen> kitchens = kitchenRepository.findAll();
        if (buyers.isEmpty() || kitchens.isEmpty()) return;
        String[] messages = {
            "Do you make gluten-free options?",
            "Can I customise the spice level?",
            "What time do you start accepting orders?",
            "Do you deliver to Palm Residency?",
            "Can I order for a party of 20 people?"
        };
        for (int i = 0; i < Math.min(5, kitchens.size()); i++) {
            User buyer = buyers.get(i % buyers.size());
            Kitchen kitchen = kitchens.get(i);
            Enquiry enquiry = new Enquiry();
            enquiry.setUser(buyer);
            enquiry.setKitchen(kitchen);
            enquiry.setMessage(messages[i % messages.length]);
            enquiry.setStatus(i % 3 == 0 ? EnquiryStatus.CONTACTED : EnquiryStatus.NEW);
            enquiry.setCreatedAt(LocalDateTime.now().minusDays(1).minusHours(i * 2));
            enquiryRepository.save(enquiry);
        }
        platformSettingRepository.save(new PlatformSetting(DEMO_ENQUIRIES_FLAG, "true"));
    }

    // ==================== DEMO FAVOURITES ====================

    public void seedFavouritesIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_FAVOURITES_FLAG).isPresent()) return;
        java.util.List<User> buyers = userRepository.findByRole(UserRole.BUYER);
        if (buyers.isEmpty()) return;
        User buyer = buyers.get(0);
        java.util.List<Kitchen> kitchens = kitchenRepository.findAll();
        for (int i = 0; i < Math.min(3, kitchens.size()); i++) {
            Favourite f = new Favourite();
            f.setUser(buyer);
            f.setKitchen(kitchens.get(i));
            favouriteRepository.save(f);
        }
        platformSettingRepository.save(new PlatformSetting(DEMO_FAVOURITES_FLAG, "true"));
    }

    // ==================== DEMO SELLER FAVOURITES & HISTORY ====================

    /**
     * Seeds the demo seller's own archive so the Favourites and History screens
     * display real persisted records instead of an empty list:
     *
     * <ul>
     *   <li>Favourites are {@link SellerTemplate} rows — the very same entity the
     *       create-form "Save as template" toggle writes, so the pills, the 3-cap
     *       and template publishing all work on them unchanged.</li>
     *   <li>History is not a separate entity: it is offerings whose authoritative
     *       offering date has passed ({@code availableDate < today}), which is the
     *       exact complement of the dashboard filter. Seeding those rows is the
     *       only honest way to make History non-empty — dated relative to today so
     *       the rule holds whatever day the demo runs.</li>
     * </ul>
     *
     * Additive and idempotent: its own flag plus an per-entity emptiness check
     * mean a boot never duplicates a saved template or an offering, and no
     * existing row is ever edited. Past-dated offerings stay hidden from every
     * live buyer surface because they are not available today.
     */
    @Transactional
    public void seedSellerArchiveIfEmpty() {
        if (platformSettingRepository.findBySettingKey(DEMO_SELLER_ARCHIVE_FLAG).isPresent()) return;
        User demoSeller = userRepository.findByMobileNumber(DEMO_SELLER_MOBILE).orElse(null);
        if (demoSeller == null) return;
        java.util.List<Kitchen> kitchens = kitchenRepository.findBySeller(demoSeller);
        if (kitchens.isEmpty()) return;
        Kitchen kitchen = kitchens.get(0);

        if (sellerTemplateRepository.countBySeller(demoSeller) == 0) {
            savedTemplate(demoSeller, "Poha + Jalebi", "Breakfast poha with homemade jalebi.",
                    45, "plate", 30, "BREAKFAST", "07:00", "11:00", "1:00 PM today");
            savedTemplate(demoSeller, "Misal Pav", "Spicy misal topped with kanda, sev and pav.",
                    70, "plate", 25, "LUNCH", "09:00", "12:30", "4:00 PM today");
            savedTemplate(demoSeller, "Puran Poli", "Gud and chana dal stuffed poli, ghee on the side.",
                    90, "poli", 20, "SPECIAL", "09:00", "12:30", "4:00 PM today");
        }

        // Names deliberately differ from today's live catalog (Poha, Modak, Idli,
        // Misal Pav, Puran Poli, Sabudana Khichdi, Thalipeeth): History must never
        // show the same dish as a currently-on-sale offering, or the two screens
        // contradict each other on screen.
        LocalDate today = LocalDate.now();
        if (productRepository.findByKitchenAndAvailableDateBeforeOrderByAvailableDateDescCreatedAtDesc(kitchen, today).isEmpty()) {
            pastOffering(kitchen, "Vada Pav", "Classic batata vada in a soft pav, served with butter and chaat masala.",
                    40, "pav", "SNACKS", today.minusDays(1));
            pastOffering(kitchen, "Methi Muthiya", "Steamed fenugreek dumplings tempered with mustard seeds and curry leaf.",
                    55, "plate", "BREAKFAST", today.minusDays(2));
            pastOffering(kitchen, "Sheera", "Semolina sweet upma with ghee, cashews and raisins.",
                    65, "bowl", "SPECIAL", today.minusDays(3));
        }

        platformSettingRepository.save(new PlatformSetting(DEMO_SELLER_ARCHIVE_FLAG, "true"));
    }

    /** A saved favourite, built exactly as the create-form template toggle persists it. */
    private SellerTemplate savedTemplate(User seller, String name, String description, int price,
                                         String unit, int maxQ, String category,
                                         String open, String close, String readyBy) {
        SellerTemplate t = new SellerTemplate();
        t.setSeller(seller);
        t.setName(name);
        t.setDescription(description);
        t.setPrice(BigDecimal.valueOf(price));
        t.setPriceUnit(unit);
        t.setMaxQuantity(maxQ);
        t.setCategory(Category.valueOf(category));
        t.setOrderWindowStart(open);
        t.setOrderWindowEnd(close);
        t.setCutoffTime(close);
        t.setReadyByTime(readyBy);
        return sellerTemplateRepository.save(t);
    }

    /**
     * An offering from a previous day. Stock is left full so the row is reported
     * with the HISTORY lifecycle rather than SOLD_OUT, and availableToday is left
     * false so the row is never a live, orderable offering.
     */
    private Product pastOffering(Kitchen k, String name, String description, int price,
                                 String unit, String category, LocalDate offeringDate) {
        Product p = new Product(k, name, description, BigDecimal.valueOf(price), null);
        p.setPriceUnit(unit);
        p.setAvailableToday(false);
        p.setAvailableDate(offeringDate);
        p.setIsPreorder(false);
        p.setMaxQuantity(20);
        p.setRemainingQuantity(20);
        p.setBookedQuantity(0);
        p.setRating(4.6);
        p.setCategory(category);
        p.setCutoffTime("12:30");
        p.setReadyByTime("4:00 PM");
        return productRepository.save(p);
    }
}

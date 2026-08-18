package com.example.aistore.config;

import com.example.aistore.entity.Address;
import com.example.aistore.entity.Brand;
import com.example.aistore.entity.Cart;
import com.example.aistore.entity.CartItem;
import com.example.aistore.entity.Category;
import com.example.aistore.entity.CustomerFeedback;
import com.example.aistore.entity.Inventory;
import com.example.aistore.entity.InteractionType;
import com.example.aistore.entity.MarketProduct;
import com.example.aistore.entity.Order;
import com.example.aistore.entity.OrderItem;
import com.example.aistore.entity.OrderStatus;
import com.example.aistore.entity.Payment;
import com.example.aistore.entity.PaymentStatus;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.ProductImage;
import com.example.aistore.entity.ProductSpecification;
import com.example.aistore.entity.Review;
import com.example.aistore.entity.SearchHistory;
import com.example.aistore.entity.User;
import com.example.aistore.entity.UserInteraction;
import com.example.aistore.entity.UserPreference;
import com.example.aistore.entity.UserRole;
import com.example.aistore.entity.Wishlist;
import com.example.aistore.entity.WishlistItem;
import com.example.aistore.repository.AddressRepository;
import com.example.aistore.repository.BrandRepository;
import com.example.aistore.repository.CartItemRepository;
import com.example.aistore.repository.CartRepository;
import com.example.aistore.repository.CategoryRepository;
import com.example.aistore.repository.CustomerFeedbackRepository;
import com.example.aistore.repository.InventoryRepository;
import com.example.aistore.repository.MarketProductRepository;
import com.example.aistore.repository.OrderItemRepository;
import com.example.aistore.repository.OrderRepository;
import com.example.aistore.repository.PaymentRepository;
import com.example.aistore.repository.ProductImageRepository;
import com.example.aistore.repository.ProductRepository;
import com.example.aistore.repository.ProductSpecificationRepository;
import com.example.aistore.repository.ReviewRepository;
import com.example.aistore.repository.SearchHistoryRepository;
import com.example.aistore.repository.UserInteractionRepository;
import com.example.aistore.repository.UserPreferenceRepository;
import com.example.aistore.repository.UserRepository;
import com.example.aistore.repository.WishlistItemRepository;
import com.example.aistore.repository.WishlistRepository;
import com.example.aistore.service.CustomerFeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Automated database seeder - populates the full demo storefront:
 * 8 categories, 12 brands, 2 demo accounts (+8 seed customers), 100 products,
 * specs, images, reviews with sentiment analysis, 55+ orders, 500+ interactions.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final Random RANDOM = new Random(42);

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductSpecificationRepository specRepository;
    private final InventoryRepository inventoryRepository;
    private final ReviewRepository reviewRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final UserInteractionRepository interactionRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final MarketProductRepository marketProductRepository;
    private final CustomerFeedbackService feedbackService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(CategoryRepository categoryRepository, BrandRepository brandRepository,
                      UserRepository userRepository, AddressRepository addressRepository,
                      UserPreferenceRepository preferenceRepository, ProductRepository productRepository,
                      ProductImageRepository imageRepository, ProductSpecificationRepository specRepository,
                      InventoryRepository inventoryRepository, ReviewRepository reviewRepository,
                      CustomerFeedbackRepository feedbackRepository, CartRepository cartRepository,
                      CartItemRepository cartItemRepository, OrderRepository orderRepository,
                      OrderItemRepository orderItemRepository, PaymentRepository paymentRepository,
                      WishlistRepository wishlistRepository, WishlistItemRepository wishlistItemRepository,
                      UserInteractionRepository interactionRepository,
                      SearchHistoryRepository searchHistoryRepository,
                      MarketProductRepository marketProductRepository,
                      CustomerFeedbackService feedbackService, PasswordEncoder passwordEncoder) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.preferenceRepository = preferenceRepository;
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.specRepository = specRepository;
        this.inventoryRepository = inventoryRepository;
        this.reviewRepository = reviewRepository;
        this.feedbackRepository = feedbackRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.wishlistRepository = wishlistRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.interactionRepository = interactionRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.marketProductRepository = marketProductRepository;
        this.feedbackService = feedbackService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("[DataSeeder] Database already seeded ({} categories) - skipping", categoryRepository.count());
            return;
        }
        long start = System.currentTimeMillis();
        log.info("[DataSeeder] Seeding OmniMart AI demo data...");

        Map<String, Category> categories = seedCategories();
        Map<String, Brand> brands = seedBrands();
        User admin = seedAdmin();
        User demoUser = seedDemoUser(admin);
        List<User> seedCustomers = seedCustomers();

        Map<String, Product> products = seedProducts(categories, brands);
        seedProductSpecs(products);
        seedReviews(products, List.of(admin, demoUser), seedCustomers);
        seedInventory(products);
        seedCartAndWishlist(demoUser, products);
        seedOrders(demoUser, admin, products, seedCustomers);
        seedInteractions(demoUser, admin, products, categories, brands);
        seedMarketPrices(products);

        log.info("[DataSeeder] Seeded {} products, {} users, {} orders in {}ms",
                products.size(), userRepository.count(), orderRepository.count(),
                System.currentTimeMillis() - start);
    }

    // ========================================================================
    // CATEGORIES & BRANDS
    // ========================================================================

    private Map<String, Category> seedCategories() {
        Map<String, Category> result = new LinkedHashMap<>();
        String[][] data = {
                {"Smartphones", "smartphones", "Flagship and budget mobile phones", "📱", "Smartphone category icon"},
                {"Laptops", "laptops", "Ultrabooks, gaming and business laptops", "💻", "Laptop category icon"},
                {"Headphones", "headphones", "Wireless headphones, earbuds and noise cancelling audio", "🎧", "Headphones category icon"},
                {"Gaming", "gaming", "Consoles, handhelds and gaming peripherals", "🎮", "Gaming category icon"},
                {"Smart Home", "smart-home", "Smart speakers, hubs, vacuums and IoT devices", "🏠", "Smart Home category icon"},
                {"Cameras", "cameras", "Mirrorless, DSLR and compact cameras", "📷", "Camera category icon"},
                {"Accessories", "accessories", "Chargers, mice, keyboards and cables", "🔌", "Accessories category icon"},
                {"Monitors", "monitors", "4K, gaming and professional monitors", "🖥️", "Monitor category icon"},
        };
        for (int i = 0; i < data.length; i++) {
            Category c = new Category();
            c.setName(data[i][0]);
            c.setSlug(data[i][1]);
            c.setDescription(data[i][2]);
            c.setIcon(data[i][3]);
            c.setImageUrl(data[i][4]);
            c.setDisplayOrder(i);
            c.setActive(true);
            result.put(data[i][0], categoryRepository.save(c));
        }
        return result;
    }

    private Map<String, Brand> seedBrands() {
        Map<String, Brand> result = new LinkedHashMap<>();
        String[][] data = {
                {"Samsung", "samsung", "South Korean electronics giant", "https://www.samsung.com"},
                {"Apple", "apple", "Premium consumer electronics", "https://www.apple.com"},
                {"Sony", "sony", "Consumer electronics & entertainment", "https://www.sony.com"},
                {"Dell", "dell", "Enterprise and consumer computing", "https://www.dell.com"},
                {"Lenovo", "lenovo", "Computers and smart devices", "https://www.lenovo.com"},
                {"ASUS", "asus", "Motherboards, laptops and ROG gaming", "https://www.asus.com"},
                {"HP", "hp", "Personal computing and printing", "https://www.hp.com"},
                {"Bose", "bose", "Premium audio engineering", "https://www.bose.com"},
                {"OnePlus", "oneplus", "Flagship killer smartphones & audio", "https://www.oneplus.com"},
                {"Logitech", "logitech", "Peripherals and video collaboration", "https://www.logitech.com"},
                {"Canon", "canon", "Imaging and optical products", "https://www.canon.com"},
                {"LG", "lg", "Consumer electronics & home appliances", "https://www.lg.com"},
        };
        for (String[] d : data) {
            Brand b = new Brand();
            b.setName(d[0]);
            b.setSlug(d[1]);
            b.setDescription(d[2]);
            b.setWebsite(d[3]);
            b.setActive(true);
            result.put(d[0], brandRepository.save(b));
        }
        return result;
    }

    // ========================================================================
    // USERS
    // ========================================================================

    private User seedAdmin() {
        User admin = new User();
        admin.setEmail("admin@omnimart.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setFullName("OmniMart System Admin");
        admin.setPhone("+91-9000000001");
        admin.setActive(true);
        admin.addRole(UserRole.ROLE_ADMIN);
        admin.addRole(UserRole.ROLE_USER);
        admin = userRepository.save(admin);

        UserPreference pref = new UserPreference();
        pref.setUser(admin);
        pref.setPreferredCategoriesJson("{\"Laptops\":70,\"Monitors\":60}");
        pref.setPreferredBrandsJson("{\"Dell\":80,\"ASUS\":70}");
        pref.setRecommendationsEnabled(true);
        pref.setBehaviorTrackingEnabled(true);
        preferenceRepository.save(pref);
        return admin;
    }

    private User seedDemoUser(User admin) {
        User user = new User();
        user.setEmail("user@omnimart.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFullName("Rahul Sharma");
        user.setPhone("+91-9876543210");
        user.setActive(true);
        user.addRole(UserRole.ROLE_USER);
        user = userRepository.save(user);

        UserPreference pref = new UserPreference();
        pref.setUser(user);
        pref.setPreferredCategoriesJson("{\"Smartphones\":80,\"Laptops\":60,\"Headphones\":70}");
        pref.setPreferredBrandsJson("{\"Samsung\":90,\"Apple\":85,\"Sony\":75}");
        pref.setMinBudget(new BigDecimal("15000"));
        pref.setMaxBudget(new BigDecimal("150000"));
        pref.setRecommendationsEnabled(true);
        pref.setBehaviorTrackingEnabled(true);
        preferenceRepository.save(pref);

        Address address = new Address();
        address.setUser(user);
        address.setFullName("Rahul Sharma");
        address.setStreetAddress("42, Cyber City, Sector 29");
        address.setApartment("Tower B, Flat 1204");
        address.setCity("Gurugram");
        address.setState("Haryana");
        address.setPostalCode("122001");
        address.setCountry("India");
        address.setPhone("+91-9876543210");
        address.setAddressType("HOME");
        address.setDefault(true);
        addressRepository.save(address);

        Address office = new Address();
        office.setUser(user);
        office.setFullName("Rahul Sharma");
        office.setStreetAddress("DLF Cyber Park, Udyog Vihar");
        office.setApartment("Floor 8, Wing C");
        office.setCity("Gurugram");
        office.setState("Haryana");
        office.setPostalCode("122002");
        office.setCountry("India");
        office.setPhone("+91-9876543210");
        office.setAddressType("WORK");
        office.setDefault(false);
        addressRepository.save(office);
        return user;
    }

    private List<User> seedCustomers() {
        List<User> customers = new ArrayList<>();
        String[][] data = {
                {"priya@example.com", "Priya Verma", "+91-9000000002"},
                {"arjun@example.com", "Arjun Mehta", "+91-9000000003"},
                {"sneha@example.com", "Sneha Iyer", "+91-9000000004"},
                {"vikram@example.com", "Vikram Singh", "+91-9000000005"},
                {"ananya@example.com", "Ananya Gupta", "+91-9000000006"},
                {"karan@example.com", "Karan Malhotra", "+91-9000000007"},
                {"meera@example.com", "Meera Nair", "+91-9000000008"},
                {"rohan@example.com", "Rohan Desai", "+91-9000000009"},
        };
        for (String[] d : data) {
            User u = new User();
            u.setEmail(d[0]);
            u.setPassword(passwordEncoder.encode("password123"));
            u.setFullName(d[1]);
            u.setPhone(d[2]);
            u.setActive(true);
            u.addRole(UserRole.ROLE_USER);
            customers.add(userRepository.save(u));
        }
        return customers;
    }

    // ========================================================================
    // PRODUCTS
    // ========================================================================

    private record ProductTemplate(String name, String brand, int price, int ratingX10, int reviews, String tags, String shortDesc) {
    }

    private Map<String, Product> seedProducts(Map<String, Category> categories, Map<String, Brand> brands) {
        List<ProductTemplate> templates = buildProductTemplates();
        Map<String, Product> result = new LinkedHashMap<>();
        int index = 0;
        for (ProductTemplate t : templates) {
            Product p = new Product();
            p.setName(t.name());
            String slug = slugify(t.name()) + "-" + (100 + index);
            p.setSlug(slug);
            p.setSku("SKU-" + String.format("%04d", 1000 + index));
            p.setPrice(new BigDecimal(t.price()));
            p.setOriginalPrice(new BigDecimal(t.price()).multiply(new BigDecimal("1.18")).setScale(0, RoundingMode.HALF_UP));
            p.setDiscountPercentage(new BigDecimal("15.00"));
            p.setStock(15 + RANDOM.nextInt(85));
            p.setInStock(p.getStock() > 0);
            p.setFeatured(index < 10 || index % 17 == 0);
            p.setActive(true);
            p.setRating(t.ratingX10() / 10.0);
            p.setReviewCount(t.reviews());
            p.setTags(t.tags());
            p.setShortDescription(t.shortDesc());
            p.setFullDescription("The " + t.name() + " delivers " + t.shortDesc().toLowerCase() + ". "
                    + "Backed by OmniMart AI's verified specifications and customer sentiment analytics, "
                    + "this product is one of the most balanced picks in its segment for " + t.tags() + " use cases.");
            p.setPrimaryImageUrl("https://placehold.co/600x600/e2e8f0/1e293b?text=" + t.name().replace(" ", "+"));
            p.setCategory(categories.get(categoryOf(t.name())));
            p.setBrand(brands.get(t.brand()));
            productRepository.save(p);

            ProductImage image = new ProductImage();
            image.setProduct(p);
            image.setImageUrl(p.getPrimaryImageUrl());
            image.setAltText(t.name() + " product image");
            image.setDisplayOrder(0);
            image.setPrimary(true);
            imageRepository.save(image);
            p.getImages().add(image);

            result.put(t.name(), p);
            index++;
        }
        return result;
    }

    private String slugify(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private List<ProductTemplate> buildProductTemplates() {
        List<ProductTemplate> list = new ArrayList<>();
        // ----- Smartphones (12) -----
        list.add(new ProductTemplate("Samsung Galaxy S24 Ultra 5G", "Samsung", 129999, 49, 2400, "flagship,5g,oled,amoled,camera,fast-charging", "8K camera flagship with titanium frame and Galaxy AI"));
        list.add(new ProductTemplate("Samsung Galaxy S23 FE 5G", "Samsung", 49999, 46, 850, "5g,amoled,flagship,camera", "Fan Edition with flagship-grade cameras and 120Hz AMOLED"));
        list.add(new ProductTemplate("Samsung Galaxy A55 5G", "Samsung", 36999, 45, 620, "5g,amoled,battery,budget", "Mid-range all-rounder with 5000mAh battery and OLED display"));
        list.add(new ProductTemplate("Apple iPhone 15 Pro Max", "Apple", 159900, 49, 3100, "flagship,oled,5g,camera,battery", "Titanium flagship with A17 Pro and 5x telephoto camera"));
        list.add(new ProductTemplate("Apple iPhone 15", "Apple", 79900, 48, 2200, "5g,oled,camera,battery", "A16 Bionic with 48MP main camera and Dynamic Island"));
        list.add(new ProductTemplate("Apple iPhone 14", "Apple", 64900, 46, 1700, "5g,oled,camera", "Proven all-rounder with A15 Bionic and dual cameras"));
        list.add(new ProductTemplate("OnePlus 12 5G", "OnePlus", 69999, 47, 980, "flagship,5g,amoled,fast-charging,camera", "Snapdragon 8 Gen 3 flagship with 2K AMOLED and 100W charging"));
        list.add(new ProductTemplate("OnePlus 12R 5G", "OnePlus", 42999, 45, 540, "5g,amoled,fast-charging,gaming", "Flagship-killer with 1.5K LTPO display and 5500mAh battery"));
        list.add(new ProductTemplate("OnePlus Nord CE 4 5G", "OnePlus", 24999, 44, 470, "5g,amoled,budget,battery", "Budget 5G with 100W fast charging and clean OxygenOS"));
        list.add(new ProductTemplate("Sony Xperia 1 VI", "Sony", 99999, 45, 210, "flagship,5g,oled,camera", "Creator-focused flagship with true optical zoom and 4K display"));
        list.add(new ProductTemplate("Samsung Galaxy Z Flip 6", "Samsung", 99999, 46, 480, "foldable,5g,amoled,camera,flagship", "Compact foldable with FlexCam and 4000mAh battery"));
        list.add(new ProductTemplate("Apple iPhone 15 Plus", "Apple", 89900, 47, 890, "5g,oled,battery,camera", "Big-screen iPhone with all-day battery and 48MP camera"));

        // ----- Laptops (16) -----
        list.add(new ProductTemplate("Apple MacBook Pro 14 M3 Pro", "Apple", 199900, 49, 1600, "macbook,flagship,ssd,battery,lightweight", "Pro performance with M3 Pro chip and 18-hour battery"));
        list.add(new ProductTemplate("Apple MacBook Air 13 M3", "Apple", 114900, 48, 1400, "macbook,lightweight,battery,ssd", "Fanless ultraportable with M3 and 18-hour battery"));
        list.add(new ProductTemplate("Dell XPS 13 Plus", "Dell", 137990, 47, 720, "ultrabook,lightweight,oled,ssd", "Zero-lattice keyboard ultrabook with 13.4-inch OLED"));
        list.add(new ProductTemplate("Dell Inspiron 15 3530", "Dell", 58990, 44, 610, "budget,ssd,battery", "Everyday i5 laptop with FHD display and 16GB RAM"));
        list.add(new ProductTemplate("Dell Latitude 7440", "Dell", 118990, 46, 380, "business,lightweight,ssd", "Business ultrabook with vPro and AI-ready Intel Core"));
        list.add(new ProductTemplate("Dell G15 Gaming", "Dell", 92990, 45, 940, "gaming,rgb,ssd", "RTX 4060 gaming laptop with 165Hz display"));
        list.add(new ProductTemplate("Lenovo ThinkPad X1 Carbon Gen 12", "Lenovo", 164990, 48, 550, "business,lightweight,ssd,battery", "Carbon-fiber business flagship with 3K OLED option"));
        list.add(new ProductTemplate("Lenovo IdeaPad Slim 5", "Lenovo", 67990, 44, 700, "budget,lightweight,ssd", "Slim Ryzen 7 laptop with OLED panel and long battery"));
        list.add(new ProductTemplate("Lenovo Legion 5 Pro", "Lenovo", 134990, 47, 1200, "gaming,ssd,rgb", "RTX 4070 gaming beast with WQXGA 165Hz screen"));
        list.add(new ProductTemplate("ASUS ROG Strix G16", "ASUS", 145990, 47, 1100, "gaming,rgb,ssd", "i9 + RTX 4070 gaming laptop with 16-inch 165Hz"));
        list.add(new ProductTemplate("ASUS VivoBook 15", "ASUS", 47990, 43, 820, "budget,ssd", "Value i5 laptop with 512GB SSD and backlit keyboard"));
        list.add(new ProductTemplate("ASUS Zenbook 14 OLED", "ASUS", 87990, 46, 660, "lightweight,oled,ssd,battery", "2.8K OLED ultrabook under 1.2kg with 75Wh battery"));
        list.add(new ProductTemplate("HP Spectre x360 16", "HP", 154990, 47, 430, "flagship,oled,ssd", "Convertible flagship with 2-in-1 OLED and AI features"));
        list.add(new ProductTemplate("HP Pavilion 15", "HP", 56990, 44, 890, "budget,ssd", "Versatile 15.6-inch with i5 and Iris Xe graphics"));
        list.add(new ProductTemplate("HP Envy 16", "HP", 129990, 46, 310, "flagship,ssd,gaming", "RTX 4060 creator laptop with 120Hz 2.5K display"));
        list.add(new ProductTemplate("Apple MacBook Air 15 M3", "Apple", 134900, 48, 980, "macbook,lightweight,battery,ssd", "15.3-inch Air with M3 and 18-hour battery life"));

        // ----- Headphones (14) -----
        list.add(new ProductTemplate("Sony WH-1000XM5", "Sony", 29990, 49, 2800, "anc,bluetooth,wireless,audio", "Industry-leading noise cancellation with 30-hour battery"));
        list.add(new ProductTemplate("Sony WH-CH720N", "Sony", 9990, 44, 760, "anc,bluetooth,wireless,budget", "Affordable wireless ANC headphones with 35-hour battery"));
        list.add(new ProductTemplate("Sony WF-1000XM5", "Sony", 24990, 48, 1500, "anc,bluetooth,wireless,audio", "Flagship earbuds with 8.4mm drivers and adaptive ANC"));
        list.add(new ProductTemplate("Bose QuietComfort Ultra", "Bose", 34999, 49, 950, "anc,bluetooth,wireless,audio", "Immersive audio with Bose CustomTune ANC and spatial audio"));
        list.add(new ProductTemplate("Bose QuietComfort 45", "Bose", 27990, 47, 1300, "anc,bluetooth,wireless,audio", "Acclaimed ANC headphones with 24-hour playtime"));
        list.add(new ProductTemplate("Bose SoundLink Flex", "Bose", 13999, 46, 620, "bluetooth,wireless,audio,battery", "Rugged portable speaker with 12-hour playtime"));
        list.add(new ProductTemplate("Apple AirPods Pro 2", "Apple", 24900, 49, 3500, "anc,bluetooth,wireless,audio", "Adaptive ANC earbuds with H2 chip and MagSafe case"));
        list.add(new ProductTemplate("Apple AirPods Max", "Apple", 59900, 48, 830, "anc,bluetooth,wireless,audio", "Over-ear audiophile-grade headphones with aluminum build"));
        list.add(new ProductTemplate("Apple AirPods 4", "Apple", 12900, 46, 1100, "bluetooth,wireless,audio", "Open-fit earbuds with USB-C and spatial audio"));
        list.add(new ProductTemplate("Samsung Galaxy Buds3 Pro", "Samsung", 19999, 47, 640, "anc,bluetooth,wireless,audio", "Blade-style earbuds with adaptive ANC and 26-hour battery"));
        list.add(new ProductTemplate("Samsung Galaxy Buds FE", "Samsung", 8999, 44, 520, "bluetooth,wireless,budget,audio", "Budget Galaxy earbuds with ANC and comfortable fit"));
        list.add(new ProductTemplate("OnePlus Buds Pro 3", "OnePlus", 11999, 45, 710, "anc,bluetooth,wireless,audio", "Dynaudio-tuned earbuds with 43dB ANC"));
        list.add(new ProductTemplate("Logitech G435", "Logitech", 6999, 43, 480, "bluetooth,wireless,gaming,budget", "Ultra-light wireless gaming headset under 166g"));
        list.add(new ProductTemplate("Logitech Zone Vibe 100", "Logitech", 9999, 44, 350, "bluetooth,wireless,audio", "Hybrid work headset with plush ear cushions"));

        // ----- Gaming (12) -----
        list.add(new ProductTemplate("ASUS ROG Ally", "ASUS", 69990, 47, 800, "gaming,handheld,flagship", "AMD Z1 Extreme handheld with 120Hz 7-inch display"));
        list.add(new ProductTemplate("Lenovo Legion Go", "Lenovo", 84990, 45, 430, "gaming,handheld", "8.8-inch QHD handheld with detachable controllers"));
        list.add(new ProductTemplate("Sony PlayStation 5 Slim", "Sony", 54990, 49, 2600, "gaming,flagship,4k", "Next-gen console with 1TB SSD and 4K gaming"));
        list.add(new ProductTemplate("Sony DualSense Wireless Controller", "Sony", 6490, 48, 1900, "gaming", "Haptic-feedback controller with adaptive triggers"));
        list.add(new ProductTemplate("Sony PlayStation Pulse 3D Headset", "Sony", 12990, 45, 520, "gaming,audio,wireless", "3D audio headset tuned for PS5 tempest engine"));
        list.add(new ProductTemplate("Logitech G Pro X Superlight", "Logitech", 13999, 49, 1500, "gaming,wireless", "63g esports mouse with HERO 25K sensor"));
        list.add(new ProductTemplate("Logitech G915 X Lightspeed", "Logitech", 23999, 48, 640, "gaming,wireless,rgb", "Low-profile wireless mechanical keyboard"));
        list.add(new ProductTemplate("Logitech G733 Lightspeed", "Logitech", 11999, 45, 890, "gaming,wireless,rgb,audio", "RGB wireless headset with Blue VO!CE mic"));
        list.add(new ProductTemplate("Logitech G502 X Plus", "Logitech", 10999, 46, 1100, "gaming,wireless,rgb", "Lightspeed wireless mouse with LIGHTFORCE switches"));
        list.add(new ProductTemplate("ASUS ROG Strix RTX 4070 Super", "ASUS", 76999, 47, 380, "gaming,4k", "12GB graphics card with tri-fan cooling"));
        list.add(new ProductTemplate("HP OMEN 27k Monitor", "HP", 42999, 45, 290, "gaming,4k", "144Hz 4K gaming monitor with 1ms response"));
        list.add(new ProductTemplate("Sony PlayStation Portal", "Sony", 19990, 42, 460, "gaming,handheld", "Remote-play handheld for PS5 console"));

        // ----- Smart Home (10) -----
        list.add(new ProductTemplate("Samsung SmartThings Station", "Samsung", 8990, 44, 320, "smart-home,iot", "Smart home hub with wireless charging pad"));
        list.add(new ProductTemplate("Samsung Galaxy SmartTag2", "Samsung", 2999, 43, 540, "smart-home,iot,budget", "UWB smart tracker with 500-day battery"));
        list.add(new ProductTemplate("Samsung Bespoke Jet Bot AI", "Samsung", 64990, 46, 210, "smart-home,iot", "AI robot vacuum with LiDAR and object avoidance"));
        list.add(new ProductTemplate("LG ThinQ AI Speaker", "LG", 7490, 42, 260, "smart-home,iot,audio", "Voice assistant speaker with 360-degree sound"));
        list.add(new ProductTemplate("LG CordZero Robotic Vacuum", "LG", 39990, 45, 330, "smart-home,iot", "All-in-one robot vacuum with self-emptying tower"));
        list.add(new ProductTemplate("LG PuriCare Air Purifier", "LG", 24990, 46, 410, "smart-home,iot", "360-degree air purifier with ThinQ app control"));
        list.add(new ProductTemplate("Samsung Galaxy Smart Bulb Kit", "Samsung", 3499, 42, 190, "smart-home,iot,budget", "Smart LED bulbs with app scheduling and dimming"));
        list.add(new ProductTemplate("LG Smart Doorbell Pro", "LG", 12990, 43, 150, "smart-home,iot", "2K video doorbell with two-way talk"));
        list.add(new ProductTemplate("Samsung SmartThings Button", "Samsung", 1999, 41, 130, "smart-home,iot,budget", "Programmable scenes button for SmartThings scenes"));
        list.add(new ProductTemplate("LG Styler Steam Closet", "LG", 129990, 47, 90, "smart-home,flagship", "Steam clothing care with gentle drying"));

        // ----- Cameras (12) -----
        list.add(new ProductTemplate("Canon EOS R50", "Canon", 69990, 47, 480, "camera,mirrorless", "Entry mirrorless with 24MP APS-C and 4K video"));
        list.add(new ProductTemplate("Canon EOS R6 Mark II", "Canon", 229990, 49, 620, "camera,mirrorless,flagship", "24MP full-frame with 40fps burst and 4K60"));
        list.add(new ProductTemplate("Canon EOS 90D", "Canon", 94990, 46, 350, "camera,dslr", "32.5MP DSLR with 45-point AF and 4K"));
        list.add(new ProductTemplate("Canon PowerShot G7X Mark III", "Canon", 69990, 45, 540, "camera,compact", "Vlogger favorite with 1-inch sensor and 4K"));
        list.add(new ProductTemplate("Canon EOS M50 Mark II", "Canon", 59990, 44, 420, "camera,mirrorless", "Compact mirrorless with vertical video mode"));
        list.add(new ProductTemplate("Sony Alpha A7 IV", "Sony", 234990, 49, 900, "camera,mirrorless,flagship", "33MP full-frame with 4K60 and AI autofocus"));
        list.add(new ProductTemplate("Sony Alpha A6400", "Sony", 84990, 47, 750, "camera,mirrorless", "Real-time tracking APS-C with 11fps"));
        list.add(new ProductTemplate("Sony ZV-E10", "Sony", 62990, 46, 680, "camera,mirrorless", "Vlogging APS-C with flip screen and product mode"));
        list.add(new ProductTemplate("Sony Alpha A6700", "Sony", 134990, 48, 310, "camera,mirrorless", "26MP APS-C flagship with AI subject recognition"));
        list.add(new ProductTemplate("Sony FX30", "Sony", 164990, 48, 240, "camera,cinema,4k", "Cinema line 4K120 with S-Cinetone"));
        list.add(new ProductTemplate("Canon RF 24-105mm F4 L", "Canon", 114990, 47, 180, "camera,lens", "L-series standard zoom with IS"));
        list.add(new ProductTemplate("Sony FE 24-70mm F2.8 GM II", "Sony", 199990, 49, 150, "camera,lens,flagship", "G Master zoom with outstanding sharpness"));

        // ----- Accessories (12) -----
        list.add(new ProductTemplate("Logitech MX Master 3S", "Logitech", 10999, 48, 2100, "wireless,bluetooth", "Flagship productivity mouse with 8K DPI sensor"));
        list.add(new ProductTemplate("Logitech MX Keys S", "Logitech", 10999, 47, 1300, "wireless,bluetooth", "Backlit smart keyboard with multi-device pairing"));
        list.add(new ProductTemplate("Logitech C920 HD Pro", "Logitech", 6999, 46, 1600, "webcam,4k", "1080p streaming webcam with dual mics"));
        list.add(new ProductTemplate("Logitech Brio 4K", "Logitech", 17999, 45, 480, "webcam,4k", "4K Ultra HD webcam with HDR and IR face auth"));
        list.add(new ProductTemplate("Logitech Pebble 2", "Logitech", 1999, 42, 950, "wireless,budget", "Ultra-quiet wireless mouse with silent clicks"));
        list.add(new ProductTemplate("Logitech StreamCam", "Logitech", 15999, 44, 380, "webcam,4k", "1080p60 content-creation webcam"));
        list.add(new ProductTemplate("Apple AirTag 4 Pack", "Apple", 12400, 47, 1400, "iot,bluetooth", "Precision finding item trackers with U1 chip"));
        list.add(new ProductTemplate("Apple MagSafe Charger", "Apple", 4490, 45, 1800, "fast-charging,wireless", "15W magnetic wireless charging puck"));
        list.add(new ProductTemplate("Apple 20W USB-C Adapter", "Apple", 1999, 44, 1300, "fast-charging,budget", "Compact fast-charging power adapter"));
        list.add(new ProductTemplate("Samsung 25W Travel Adapter", "Samsung", 1499, 43, 860, "fast-charging,budget", "PD fast charger for Galaxy devices"));
        list.add(new ProductTemplate("OnePlus 80W SuperVOOC Charger", "OnePlus", 2999, 45, 720, "fast-charging", "80W fast charger with USB-C cable"));
        list.add(new ProductTemplate("Apple USB-C to Lightning Cable", "Apple", 1999, 43, 1100, "budget", "1m braided USB-C to Lightning cable"));

        // ----- Monitors (12) -----
        list.add(new ProductTemplate("Samsung Odyssey OLED G9", "Samsung", 194990, 48, 380, "gaming,oled,4k,hdr", "49-inch 240Hz QD-OLED ultra-wide with 0.03ms"));
        list.add(new ProductTemplate("Samsung ViewFinity S8", "Samsung", 45990, 45, 310, "4k,hdr", "28-inch UHD monitor with USB-C 90W"));
        list.add(new ProductTemplate("Samsung Smart Monitor M8", "Samsung", 69990, 46, 280, "4k,smart-home", "32-inch 4K smart monitor with Tizen and IoT hub"));
        list.add(new ProductTemplate("LG UltraGear 27GN950", "LG", 59990, 46, 460, "gaming,4k,hdr", "27-inch 4K 144Hz Nano IPS gaming monitor"));
        list.add(new ProductTemplate("LG 32UN880", "LG", 49990, 45, 190, "4k,hdr", "32-inch UHD with ergonomic stand and USB-C"));
        list.add(new ProductTemplate("LG DualUp 28MQ780", "LG", 64990, 44, 120, "4k,productivity", "16:18 square-ratio monitor for productivity"));
        list.add(new ProductTemplate("Dell UltraSharp U2723QE", "Dell", 61990, 47, 420, "4k,hdr,productivity", "27-inch 4K with IPS Black and 90W USB-C"));
        list.add(new ProductTemplate("Dell S2721QS", "Dell", 34990, 44, 640, "4k,budget", "27-inch 4K with slim bezels and AMD FreeSync"));
        list.add(new ProductTemplate("ASUS ROG Swift PG27AQDM", "ASUS", 94990, 47, 250, "gaming,oled,hdr", "27-inch 240Hz OLED with 0.03ms response"));
        list.add(new ProductTemplate("ASUS ProArt PA279CV", "ASUS", 45990, 46, 220, "4k,hdr,productivity", "27-inch 4K creator monitor with 100% sRGB"));
        list.add(new ProductTemplate("Lenovo Legion R27f-30", "Lenovo", 19990, 43, 350, "gaming,budget", "27-inch 180Hz FHD gaming monitor"));
        list.add(new ProductTemplate("HP OMEN 32q", "HP", 32990, 44, 230, "gaming,4k", "32-inch QHD 165Hz gaming monitor"));

        return list;
    }

    private String categoryOf(String name) {
        if (name.contains("iPhone") || name.contains("Galaxy S2") || name.contains("Galaxy S23")
                || name.contains("Galaxy A55") || name.contains("Xperia") || name.contains("OnePlus 1")
                || name.contains("Z Flip")) {
            return "Smartphones";
        }
        if (name.contains("ROG Ally") || name.contains("Legion Go") || name.contains("PlayStation")
                || name.contains("DualSense") || name.contains("Pulse 3D") || name.contains("Superlight")
                || name.contains("G915") || name.contains("G733") || name.contains("G502")
                || name.contains("RTX 4070") || name.contains("OMEN 27k") || name.contains("Portal")) {
            return "Gaming";
        }
        if (name.contains("MacBook") || name.contains("XPS") || name.contains("Inspiron")
                || name.contains("Latitude") || name.contains("ThinkPad") || name.contains("IdeaPad")
                || name.contains("Legion") || name.contains("VivoBook")
                || name.contains("Zenbook") || name.contains("Spectre") || name.contains("Pavilion")
                || name.contains("Envy") || name.contains("G15") || name.contains("G16")) {
            return "Laptops";
        }
        if (name.contains("WH-") || name.contains("WF-") || name.contains("QuietComfort")
                || name.contains("SoundLink") || name.contains("AirPods") || name.contains("Buds")
                || name.contains("G435") || name.contains("Zone Vibe")) {
            return "Headphones";
        }
        if (name.contains("SmartThings") || name.contains("SmartTag") || name.contains("Jet Bot")
                || name.contains("ThinQ") || name.contains("CordZero") || name.contains("PuriCare")
                || name.contains("Smart Bulb") || name.contains("Smart Doorbell") || name.contains("Styler")) {
            return "Smart Home";
        }
        if (name.contains("Canon") || name.contains("Alpha") || name.contains("ZV-") || name.contains("FX30")
                || name.contains("RF 24-105") || name.contains("FE 24-70")) {
            return "Cameras";
        }
        if (name.contains("MX Master") || name.contains("MX Keys") || name.contains("C920")
                || name.contains("Brio") || name.contains("Pebble") || name.contains("StreamCam")
                || name.contains("AirTag") || name.contains("MagSafe") || name.contains("Adapter")
                || name.contains("Charger") || name.contains("Lightning Cable")) {
            return "Accessories";
        }
        return "Monitors";
    }

    // ========================================================================
    // SPECS
    // ========================================================================

    private void seedProductSpecs(Map<String, Product> products) {
        int i = 0;
        for (Map.Entry<String, Product> e : products.entrySet()) {
            Product p = e.getValue();
            String cat = p.getCategory().getName();
            int price = p.getPrice().intValue();
            boolean highEnd = price > 80000;
            boolean mid = price > 30000;
            int tier = highEnd ? 2 : (mid ? 1 : 0);
            List<String[]> specs = specsFor(cat, tier, i);
            int order = 0;
            for (String[] s : specs) {
                ProductSpecification spec = new ProductSpecification();
                spec.setProduct(p);
                spec.setSpecGroup(s[0]);
                spec.setSpecKey(s[1]);
                spec.setSpecValue(s[2]);
                spec.setDisplayOrder(order++);
                specRepository.save(spec);
                p.getSpecifications().add(spec);
            }
            i++;
        }
    }

    private List<String[]> specsFor(String category, int tier, int seed) {
        List<String[]> specs = new ArrayList<>();
        Random r = new Random(seed * 31L);
        switch (category) {
            case "Smartphones" -> {
                specs.add(new String[]{"Performance", "Processor", tier == 2 ? pick(r, "Snapdragon 8 Gen 3", "A17 Pro", "Exynos 2400") : tier == 1 ? pick(r, "Snapdragon 7 Gen 3", "A16 Bionic", "Dimensity 7200") : pick(r, "Snapdragon 6 Gen 1", "Dimensity 6100", "Helio G99")});
                specs.add(new String[]{"Performance", "RAM", tier == 2 ? "12GB LPDDR5X" : tier == 1 ? "8GB LPDDR5" : "6GB LPDDR4X"});
                specs.add(new String[]{"Storage", "Internal Storage", tier == 2 ? pick(r, "256GB", "512GB") : "128GB"});
                specs.add(new String[]{"Display", "Screen Size", tier == 2 ? pick(r, "6.8-inch", "6.7-inch") : "6.5-inch"});
                specs.add(new String[]{"Display", "Refresh Rate", tier == 2 ? "120Hz LTPO" : "120Hz"});
                specs.add(new String[]{"Display", "Panel", tier == 2 ? "AMOLED 2X" : "AMOLED"});
                specs.add(new String[]{"Battery", "Capacity", tier == 2 ? pick(r, "5000mAh", "4800mAh") : pick(r, "5000mAh", "4500mAh")});
                specs.add(new String[]{"Battery", "Charging", tier == 2 ? pick(r, "45W Fast", "100W Fast") : pick(r, "25W Fast", "33W Fast")});
                specs.add(new String[]{"Camera", "Main Camera", tier == 2 ? "200MP + 50MP + 12MP" : tier == 1 ? "50MP + 8MP" : "50MP + 2MP"});
                specs.add(new String[]{"Camera", "Front Camera", "32MP"});
                specs.add(new String[]{"OS", "Operating System", "Android 14 / iOS 17"});
                specs.add(new String[]{"Connectivity", "Network", "5G + Wi-Fi 6E"});
            }
            case "Laptops" -> {
                specs.add(new String[]{"Performance", "Processor", tier == 2 ? pick(r, "Intel Core i9-14900HX", "Apple M3 Pro", "AMD Ryzen 9 7945HX") : tier == 1 ? pick(r, "Intel Core i7-13620H", "Apple M3", "AMD Ryzen 7 7840HS") : pick(r, "Intel Core i5-1335U", "AMD Ryzen 5 7530U")});
                specs.add(new String[]{"Performance", "RAM", tier == 2 ? "32GB LPDDR5" : tier == 1 ? "16GB LPDDR5" : "16GB DDR4"});
                specs.add(new String[]{"Storage", "SSD", tier == 2 ? "1TB NVMe" : tier == 1 ? "512GB NVMe" : "512GB NVMe"});
                specs.add(new String[]{"Graphics", "GPU", tier == 2 ? pick(r, "RTX 4070 8GB", "Integrated 10-core", "RTX 4060") : tier == 1 ? pick(r, "RTX 4050 6GB", "Iris Xe", "Radeon 780M") : "Integrated"});
                specs.add(new String[]{"Display", "Screen Size", "14-16 inch"});
                specs.add(new String[]{"Display", "Resolution", tier == 2 ? pick(r, "3.2K OLED", "QHD+ 165Hz") : tier == 1 ? "WQXGA" : "FHD"});
                specs.add(new String[]{"Battery", "Battery Life", tier == 2 ? "Up to 18 hours" : "Up to 12 hours"});
                specs.add(new String[]{"OS", "Operating System", pick(r, "Windows 11 Home", "macOS Sonoma")});
                specs.add(new String[]{"Weight", "Weight", tier == 2 ? "1.7-2.2 kg" : "1.3-1.8 kg"});
            }
            case "Headphones" -> {
                specs.add(new String[]{"Audio", "Driver Size", tier == 2 ? pick(r, "40mm Dynamic", "11mm Dynamic") : pick(r, "30mm Dynamic", "8.4mm Dynamic")});
                specs.add(new String[]{"Audio", "Active Noise Cancelling", tier == 2 ? "Adaptive ANC" : pick(r, "ANC", "None")});
                specs.add(new String[]{"Battery", "Battery Life", tier == 2 ? pick(r, "30 hours", "36 hours", "24 hours") : pick(r, "35 hours", "26 hours", "12 hours")});
                specs.add(new String[]{"Connectivity", "Bluetooth", "Bluetooth 5.3"});
                specs.add(new String[]{"Audio", "Codec Support", pick(r, "LDAC + AAC", "AAC + SBC", "AAC")});
                specs.add(new String[]{"Design", "Weight", tier == 2 ? pick(r, "250g", "200g") : pick(r, "192g", "5g per bud")});
            }
            case "Gaming" -> {
                specs.add(new String[]{"Performance", "Chipset", pick(r, "AMD Z1 Extreme", "Custom AMD 8-core", "Intel Core i7")});
                specs.add(new String[]{"Performance", "RAM", pick(r, "16GB LPDDR5", "16GB DDR5")});
                specs.add(new String[]{"Storage", "SSD", pick(r, "512GB NVMe", "1TB NVMe")});
                specs.add(new String[]{"Display", "Screen", pick(r, "7-inch 120Hz", "8.8-inch QHD 144Hz")});
                specs.add(new String[]{"Graphics", "GPU", pick(r, "RDNA 3 12 CUs", "RTX 4070", "RDNA 3")});
                specs.add(new String[]{"Connectivity", "Wireless", "Wi-Fi 6E + Bluetooth 5.3"});
            }
            case "Smart Home" -> {
                specs.add(new String[]{"Connectivity", "Protocol", pick(r, "Wi-Fi + Zigbee", "Wi-Fi + Matter", "Thread")});
                specs.add(new String[]{"Power", "Power Source", pick(r, "USB-C", "Battery (replaceable)", "Mains")});
                specs.add(new String[]{"Compatibility", "App Support", "SmartThings / ThinQ"});
                specs.add(new String[]{"Sensor", "Sensors", pick(r, "Motion + Light", "LiDAR + Camera", "Temperature + Humidity")});
            }
            case "Cameras" -> {
                specs.add(new String[]{"Sensor", "Sensor", tier == 2 ? "Full-frame 33MP" : tier == 1 ? "APS-C 26MP" : "APS-C 24MP"});
                specs.add(new String[]{"Video", "Video Recording", tier == 2 ? "4K60 / 4K120" : pick(r, "4K60", "4K30")});
                specs.add(new String[]{"Autofocus", "AF System", tier == 2 ? "AI Real-time Tracking" : "Phase Detect 425pt"});
                specs.add(new String[]{"Display", "Viewfinder", tier == 2 ? "OLED EVF" : "Optional EVF"});
                specs.add(new String[]{"Connectivity", "Wireless", "Wi-Fi + Bluetooth"});
            }
            case "Accessories" -> {
                specs.add(new String[]{"Connectivity", "Interface", pick(r, "USB-C", "Bluetooth + 2.4GHz", "USB-A")});
                specs.add(new String[]{"Battery", "Battery Life", tier == 2 ? pick(r, "70 days", "10 hours") : pick(r, "18 months", "6 months")});
                specs.add(new String[]{"Compatibility", "Compatibility", "Windows / macOS / Android / iOS"});
            }
            default -> {
                specs.add(new String[]{"Display", "Panel", tier == 2 ? pick(r, "OLED", "IPS Black") : pick(r, "IPS", "VA")});
                specs.add(new String[]{"Display", "Resolution", tier == 2 ? "4K UHD" : pick(r, "QHD", "FHD")});
                specs.add(new String[]{"Display", "Refresh Rate", tier == 2 ? pick(r, "144Hz", "240Hz") : pick(r, "60Hz", "180Hz")});
                specs.add(new String[]{"Display", "HDR", tier == 2 ? "HDR10+ / DisplayHDR 600" : "HDR10"});
                specs.add(new String[]{"Connectivity", "Ports", "HDMI 2.1 + DisplayPort + USB-C 90W"});
                specs.add(new String[]{"Ergonomics", "Stand", "Height/Tilt/Swivel Adjustable"});
            }
        }
        return specs;
    }

    private String pick(Random r, String... options) {
        return options[r.nextInt(options.length)];
    }

    // ========================================================================
    // REVIEWS + FEEDBACK
    // ========================================================================

    private void seedReviews(Map<String, Product> products, List<User> mainUsers, List<User> customers) {
        String[][] templates = {
                {"5", "Absolutely love it!", "This product is amazing, exceeded all my expectations. Build quality is top notch and it performs beautifully every single day.", "Delighted"},
                {"5", "Best purchase this year", "Excellent value for money. The " + "display" + " is crisp and vibrant, performance is buttery smooth with zero lag. I recommend this to everyone.", "Satisfied"},
                {"4", "Great product, minor niggles", "Very good overall. Fast delivery, great build. Battery could be slightly better under heavy use but totally acceptable.", "Satisfied"},
                {"3", "Decent but not perfect", "It works well for the price but the average camera in low light and occasional lag during heavy gaming are noticeable. Value for money though.", "Neutral"},
                {"2", "Disappointed with battery", "Battery drain under gaming load is real and the device heats up quite a bit. Delivery was delayed too. Not the experience I expected.", "Frustrated"},
                {"1", "Not worth the money", "Worst experience - overheating during normal use, build quality is average and the customer support experience was poor. Requesting a refund.", "Disappointed"},
                {"4", "Solid performer", "Smooth performance, no lag at all. The " + "screen" + " is bright and colors are punchy. Wish the speaker was slightly louder.", "Satisfied"},
                {"5", "Premium feel", "Premium build, amazing camera, excellent anc implementation. The " + "battery" + " lasts a full day easily. Super fast 1-day delivery.", "Delighted"},
        };
        List<User> pool = new ArrayList<>(mainUsers);
        pool.addAll(customers);
        int idx = 0;
        for (Product p : products.values()) {
            int reviewCount = 4 + RANDOM.nextInt(5);
            for (int r = 0; r < reviewCount; r++) {
                String[] t = templates[(idx + r * 3) % templates.length];
                User u = pool.get((idx + r) % pool.size());
                Review review = new Review();
                review.setProduct(p);
                review.setUser(u);
                review.setRating(Integer.parseInt(t[0]));
                review.setTitle(t[1]);
                review.setComment(t[2]);
                review.setVerifiedPurchase(r % 3 != 0);
                review.setHelpfulCount(RANDOM.nextInt(60));
                reviewRepository.save(review);
                feedbackService.analyzeAndPersist(review);
            }
            idx++;
        }
    }

    // ========================================================================
    // INVENTORY, CART, WISHLIST
    // ========================================================================

    private void seedInventory(Map<String, Product> products) {
        int i = 0;
        for (Product p : products.values()) {
            Inventory inv = new Inventory();
            inv.setProduct(p);
            inv.setStockQuantity(p.getStock());
            inv.setLowStockThreshold(5 + (i % 4));
            inv.setReservedQuantity(0);
            inv.setWarehouseLocation(pick(new Random(i * 7), "Delhi FC-1", "Mumbai FC-2", "Bengaluru FC-3", "Gurugram FC-4"));
            inv.setLastRestockedAt(LocalDateTime.now().minusDays(RANDOM.nextInt(30)));
            inventoryRepository.save(inv);
            i++;
        }
    }

    private void seedCartAndWishlist(User demoUser, Map<String, Product> products) {
        List<Product> values = new ArrayList<>(products.values());
        Wishlist wishlist = new Wishlist();
        wishlist.setUser(demoUser);
        wishlistRepository.save(wishlist);
        for (int i = 0; i < 8; i++) {
            WishlistItem item = new WishlistItem();
            item.setWishlist(wishlist);
            item.setProduct(values.get(RANDOM.nextInt(values.size())));
            wishlistItemRepository.save(item);
            wishlist.getItems().add(item);
        }

        Cart cart = new Cart();
        cart.setUser(demoUser);
        cart.setSessionId("seed-session-demo");
        cartRepository.save(cart);
        for (int i = 0; i < 3; i++) {
            Product p = values.get(RANDOM.nextInt(values.size()));
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(p);
            item.setQuantity(1 + RANDOM.nextInt(2));
            item.setUnitPrice(p.getPrice());
            cartItemRepository.save(item);
            cart.getItems().add(item);
        }
    }

    // ========================================================================
    // ORDERS
    // ========================================================================

    private void seedOrders(User demoUser, User admin, Map<String, Product> products, List<User> customers) {
        List<Product> values = new ArrayList<>(products.values());
        List<User> buyers = new ArrayList<>();
        buyers.add(demoUser);
        buyers.add(admin);
        buyers.addAll(customers);

        OrderStatus[] statuses = {OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.DELIVERED,
                OrderStatus.DELIVERED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.PROCESSING, OrderStatus.CANCELLED};
        String[] carriers = {"BlueDart", "Delhivery", "Ekart", "DTDC"};
        String[] methods = {"UPI", "CREDIT_CARD", "NET_BANKING", "COD", "UPI"};

        for (int o = 0; o < 55; o++) {
            User buyer = buyers.get(o % buyers.size());
            int itemCount = 1 + RANDOM.nextInt(3);
            BigDecimal subtotal = BigDecimal.ZERO;
            List<OrderItem> items = new ArrayList<>();
            List<Product> used = new ArrayList<>();
            for (int it = 0; it < itemCount; it++) {
                Product p = values.get(RANDOM.nextInt(values.size()));
                if (used.contains(p)) {
                    continue;
                }
                used.add(p);
                int qty = 1 + RANDOM.nextInt(2);
                OrderItem oi = new OrderItem();
                oi.setProduct(p);
                oi.setProductName(p.getName());
                oi.setProductImageUrl(p.getPrimaryImageUrl());
                oi.setQuantity(qty);
                oi.setUnitPrice(p.getPrice());
                oi.setTotalPrice(p.getPrice().multiply(BigDecimal.valueOf(qty)));
                items.add(oi);
                subtotal = subtotal.add(oi.getTotalPrice());
            }
            if (items.isEmpty()) {
                continue;
            }
            BigDecimal tax = subtotal.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal shipping = subtotal.compareTo(new BigDecimal("4999")) >= 0 ? BigDecimal.ZERO : new BigDecimal("49");
            BigDecimal finalAmount = subtotal.add(tax).add(shipping);

            Order order = new Order();
            order.setOrderNumber("OM" + String.format("%08d", 10000000 + o));
            order.setUser(buyer);
            order.setTotalAmount(subtotal);
            order.setDiscountAmount(new BigDecimal("0"));
            order.setTaxAmount(tax);
            order.setShippingFee(shipping);
            order.setFinalAmount(finalAmount);
            OrderStatus status = statuses[o % statuses.length];
            order.setStatus(status);
            order.setCarrier(carriers[o % carriers.length]);
            if (status == OrderStatus.SHIPPED || status == OrderStatus.OUT_FOR_DELIVERY || status == OrderStatus.DELIVERED) {
                order.setTrackingNumber("TRK-" + (5000000 + o * 17));
            }
            LocalDateTime created = LocalDateTime.now().minusDays(RANDOM.nextInt(90)).minusHours(RANDOM.nextInt(20));
            order.setCreatedAt(created);
            order.setUpdatedAt(created);
            if (status == OrderStatus.DELIVERED) {
                order.setDeliveredAt(created.plusDays(3 + RANDOM.nextInt(3)));
            }
            order.setEstimatedDeliveryDate(created.plusDays(5));
            if (buyer == demoUser) {
                var demoAddresses = addressRepository.findByUserIdOrderByCreatedAtDesc(demoUser.getId());
                if (!demoAddresses.isEmpty()) {
                    order.setShippingAddress(demoAddresses.get(0));
                }
            }
            orderRepository.save(order);

            for (OrderItem oi : items) {
                oi.setOrder(order);
                orderItemRepository.save(oi);
                order.getItems().add(oi);
            }

            String method = methods[o % methods.length];
            boolean isCod = "COD".equals(method);
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setPaymentMethod(method);
            payment.setAmount(finalAmount);
            payment.setStatus(isCod ? PaymentStatus.PENDING
                    : (status == OrderStatus.CANCELLED ? PaymentStatus.REFUNDED : PaymentStatus.COMPLETED));
            payment.setTransactionId(isCod ? null : "TXN-SEED-" + (o * 911));
            payment.setPaidAt(isCod ? null : created.plusMinutes(4));
            paymentRepository.save(payment);
            order.setPayment(payment);
        }
    }

    // ========================================================================
    // INTERACTIONS & SEARCH HISTORY
    // ========================================================================

    private void seedInteractions(User demoUser, User admin, Map<String, Product> products,
                                  Map<String, Category> categories, Map<String, Brand> brands) {
        List<Product> values = new ArrayList<>(products.values());
        InteractionType[] types = {InteractionType.PRODUCT_VIEW, InteractionType.PRODUCT_VIEW,
                InteractionType.SEARCH, InteractionType.ADD_TO_CART, InteractionType.ADD_TO_WISHLIST,
                InteractionType.PRODUCT_COMPARE, InteractionType.FILTER_APPLY, InteractionType.PRODUCT_PURCHASE};
        String[] queries = {"gaming laptop under 80000", "camera phone under 40000", "sony headphones with anc",
                "best 4k monitor", "apple macbook", "samsung galaxy", "wireless earbuds", "oled tv",
                "fast charging", "noise cancelling headphones"};

        for (int i = 0; i < 550; i++) {
            UserInteraction ui = new UserInteraction();
            if (i % 5 == 0) {
                ui.setUser(admin);
            } else {
                ui.setUser(demoUser);
            }
            ui.setSessionId("seed-session-" + (i % 7));
            InteractionType type = types[i % types.length];
            ui.setInteractionType(type);
            if (type == InteractionType.SEARCH || type == InteractionType.FILTER_APPLY) {
                ui.setSearchQuery(queries[i % queries.length]);
            } else {
                Product p = values.get(RANDOM.nextInt(values.size()));
                ui.setProductId(p.getId());
                ui.setCategoryName(p.getCategory() != null ? p.getCategory().getName() : null);
                ui.setBrandName(p.getBrand() != null ? p.getBrand().getName() : null);
            }
            ui.setDurationSeconds(5 + RANDOM.nextInt(120));
            interactionRepository.save(ui);
        }

        String[] history = {"iPhone 15 pro max price", "best gaming laptops", "samsung s24 ultra review",
                "wireless headphones under 10000", "4k monitor for design", "canon camera for vlogging",
                "oneplus 12 vs samsung s24", "bose vs sony anc"};
        for (int i = 0; i < history.length; i++) {
            SearchHistory sh = new SearchHistory();
            sh.setUser(demoUser);
            sh.setSessionId("seed-session-0");
            sh.setQuery(history[i]);
            sh.setResultCount(4 + RANDOM.nextInt(40));
            searchHistoryRepository.save(sh);
        }
    }

    private void seedMarketPrices(Map<String, Product> products) {
        String[] competitors = {"Amazon.in", "Flipkart", "Croma"};
        List<Product> values = new ArrayList<>(products.values());
        for (int i = 0; i < Math.min(45, values.size()); i++) {
            Product p = values.get(i * 2 % values.size());
            for (String competitor : competitors) {
                MarketProduct mp = new MarketProduct();
                mp.setProduct(p);
                mp.setCompetitorName(competitor);
                double variance = 0.92 + RANDOM.nextDouble() * 0.18;
                mp.setCompetitorPrice(p.getPrice().multiply(BigDecimal.valueOf(variance)).setScale(0, RoundingMode.HALF_UP));
                mp.setCompetitorUrl("https://" + competitor.replace(".in", "").toLowerCase() + ".com/search?q="
                        + p.getName().replace(" ", "+"));
                mp.setInStock(RANDOM.nextDouble() > 0.15);
                mp.setCheckedAt(LocalDateTime.now().minusHours(RANDOM.nextInt(72)));
                marketProductRepository.save(mp);
            }
        }
    }
}
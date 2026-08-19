"""Automated data seeder: categories, brands, demo accounts, 100+ products,
reviews + sentiment telemetry, orders, interactions, chat history.

Equivalent of the Spring Boot `DataSeeder` CommandLineRunner.
"""
from __future__ import annotations

import json
import logging
import random
import uuid
from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from .database import SessionLocal
from .enums import UserRole
from .models import (
    Address,
    AIRecommendationLog,
    Brand,
    Cart,
    CartItem,
    Category,
    ChatConversation,
    ChatMessage,
    Inventory,
    MarketProduct,
    Order,
    OrderItem,
    Payment,
    Product,
    ProductImage,
    ProductSpecification,
    Review,
    SearchHistory,
    User,
    UserInteraction,
    UserPreference,
    Wishlist,
    WishlistItem,
)
from .security import hash_password
from .seed_data import BRANDS, CATEGORIES, PRODUCTS, SPEC_DEFAULTS, SPEC_TEMPLATES
from .services.feedback_service import CustomerFeedbackService

logger = logging.getLogger(__name__)

POSITIVE_REVIEWS = [
    "Absolutely love this {product}. The build quality is premium and it exceeded every expectation. Worth every rupee.",
    "Amazing purchase! The performance is blazing fast and smooth. I use it daily and it never disappoints.",
    "This is easily one of the best purchases I have ever made. Superb quality, fast delivery, flawless experience.",
    "Outstanding product. The display is vibrant and razor sharp, and battery life is excellent for my usage.",
    "Great value for money. Premium build quality, works perfectly out of the box. Highly recommend this to everyone.",
    "Fantastic buy! Crystal clear audio, stunning visuals, and the packaging was premium too. 5 stars well deserved.",
    "Best purchase this year. It feels solid and premium in hand, performance is buttery smooth. Love it!",
    "Excellent quality and super fast 1-day delivery. The product is exactly as described, maybe even better.",
]

NEGATIVE_REVIEWS = [
    "Disappointed with the battery. Battery drains fast under normal usage and charging takes too long.",
    "The device heats up during gaming, thermal throttling kicks in and performance drops noticeably.",
    "Poor build quality. The finish feels cheap and flimsy, not worth the premium price tag.",
    "Camera quality in low light is terrible, photos come out grainy and blurry. Big letdown.",
    "Delivery was delayed by a week and packaging arrived damaged. Not happy with the experience.",
    "Overpriced for what it offers. The screen colors are washed out and the speakers sound muffled.",
    "Performance lags after a few weeks of use, apps crash and the device freezes randomly.",
    "Not worth the money. Defective unit on arrival, had to request a refund through customer care.",
]

MIXED_REVIEWS = [
    "Good product overall but the battery backup could be better. Performance is solid though.",
    "The features are great and the display is nice, but I wish the delivery was faster. Mixed experience.",
    "Nice build and design, however the audio quality is average compared to the competition.",
    "Decent buy. Camera is good in daylight but struggles in low light. Value for money is acceptable.",
]

CATEGORY_KEYWORDS: dict[str, list[str]] = {
    "Smartphones": ["Galaxy", "iPhone", "OnePlus", "Xperia", "Velvet", "Nord"],
    "Laptops": ["MacBook", "XPS", "Inspiron", "ThinkPad", "Yoga", "IdeaPad", "Zephyrus", "VivoBook", "Spectre", "Pavilion", "Envy", "Gram", "Legion 5 Pro", "OMEN Transcend", "Strix G16", "Alienware m16", "TUF", "LOQ"],
    "Headphones": ["WH-", "WF-", "CH720", "QuietComfort", "SoundLink", "AirPods", "Buds", "Tone Free", "LinkBuds"],
    "Gaming": ["PlayStation", "DualSense", "ROG Ally", "Legion Go", "G Pro", "G502", "G915", "G733", "Strix G16", "Alienware m16", "OMEN Transcend"],
    "Smart Home": ["TV", "Soundbar", "Bespoke", "AeroTower", "Family Hub", "Styler"],
    "Cameras": ["EOS", "Alpha", "PowerShot", "ZV-E10", "RX100"],
    "Accessories": ["MagSafe", "Adapter", "AirTag", "Charger", "MX Master", "MX Keys", "Pebble", "Brio", "HD22", "SUPERVOOC"],
    "Monitors": ["Odyssey", "ViewFinity", "UltraGear", "UltraFine", "gram+view", "UltraSharp", "S2722QC", "AW3423", "ProArt", "VG27", "OMEN 27k", "G6 27"],
}


def _slugify(name: str) -> str:
    return "-".join(name.lower().split())[:120]


def _find_category(name: str, cat_by_name: dict[str, Category]) -> Category:
    lower = name.lower()
    for cat_name, keywords in CATEGORY_KEYWORDS.items():
        if any(kw.lower() in lower for kw in keywords):
            return cat_by_name[cat_name]
    return cat_by_name["Accessories"]


def _build_specs(db: Session, product: Product, overrides: dict) -> None:
    cat_name = product.category.name if product.category else "Accessories"
    template = SPEC_TEMPLATES.get(cat_name, SPEC_TEMPLATES["Accessories"])
    defaults = SPEC_DEFAULTS.get(cat_name, {})
    for order, (group, key, placeholder) in enumerate(template):
        value = overrides.get(placeholder) or defaults.get(placeholder)
        if value is None:
            continue
        db.add(
            ProductSpecification(
                product_id=product.id, spec_group=group, spec_key=key,
                spec_value=str(value), display_order=order,
            )
        )


def seed_database() -> None:
    db = SessionLocal()
    try:
        if db.query(Category).count() > 0:
            logger.info("Database already seeded - skipping seeder")
            return

        rng = random.Random(42)
        logger.info("Seeding OmniMart AI demo database...")

        # ---------------------------------------------------------- categories
        cat_map: dict[str, Category] = {}
        for name, slug, desc, icon, order in CATEGORIES:
            cat = Category(name=name, slug=slug, description=desc, icon=icon, display_order=order, active=True)
            db.add(cat)
            cat_map[name] = cat

        # ---------------------------------------------------------- brands
        brand_map: dict[str, Brand] = {}
        for name, slug, desc, website in BRANDS:
            brand = Brand(name=name, slug=slug, description=desc, website=website, active=True)
            db.add(brand)
            brand_map[name] = brand
        db.flush()

        # ---------------------------------------------------------- accounts
        admin = User(
            email="admin@omnimart.com",
            password=hash_password("admin123"),
            full_name="System Administrator",
            phone="+91-9876543210",
            active=True,
            roles=[UserRole.ROLE_ADMIN.value, UserRole.ROLE_USER.value],
        )
        customer = User(
            email="user@omnimart.com",
            password=hash_password("password123"),
            full_name="Rahul Sharma",
            phone="+91-9812345678",
            active=True,
            roles=[UserRole.ROLE_USER.value],
        )
        db.add_all([admin, customer])
        db.flush()

        db.add(
            Address(
                user_id=customer.id, full_name="Rahul Sharma",
                street_address="24, Cyber City, Sector 24", city="Gurugram", state="Haryana",
                postal_code="122002", country="India", phone="+91-9812345678",
                address_type="HOME", is_default=True,
            )
        )
        db.add(
            UserPreference(
                user_id=customer.id,
                preferred_categories_json=json.dumps({"Smartphones": 80, "Laptops": 60, "Gaming": 40}),
                preferred_brands_json=json.dumps({"Samsung": 90, "Apple": 85, "OnePlus": 70}),
                min_budget=15000, max_budget=160000,
                recommendations_enabled=True, behavior_tracking_enabled=True,
            )
        )
        db.add(
            UserPreference(
                user_id=admin.id,
                preferred_categories_json=json.dumps({}),
                preferred_brands_json=json.dumps({}),
                recommendations_enabled=True, behavior_tracking_enabled=True,
            )
        )
        db.flush()

        # ---------------------------------------------------------- products
        products: list[Product] = []
        for idx, (brand_name, name, price, orig, rating, reviews, stock, tags, featured, overrides) in enumerate(PRODUCTS, start=1):
            category = _find_category(name, cat_map)
            product = Product(
                name=name,
                slug=_slugify(name),
                sku=f"OM-{idx:04d}-{brand_name[:2].upper()}",
                price=price,
                original_price=orig,
                discount_percentage=round((1 - price / orig) * 100, 1) if orig else 0.0,
                stock=stock,
                in_stock=stock > 0,
                featured=featured,
                active=True,
                rating=rating,
                review_count=reviews,
                tags=tags,
                short_description=f"{brand_name} {name} - premium {category.name.lower()} with top-tier specs and build.",
                full_description=(
                    f"The {name} by {brand_name} delivers flagship-level performance with an immersive experience. "
                    f"Rated {rating} stars by {reviews} customers, it combines cutting-edge technology, "
                    f"premium materials and intelligent features for everyday excellence."
                ),
                primary_image_url=f"https://picsum.photos/seed/{_slugify(name)}/600/600",
                category=category,
                brand=brand_map.get(brand_name),
            )
            db.add(product)
            db.flush()
            products.append(product)

            db.add(
                Inventory(
                    product_id=product.id, stock_quantity=stock, low_stock_threshold=5,
                    reserved_quantity=0, warehouse_location=f"WH-{idx % 4 + 1}",
                )
            )
            for img_idx in range(3):
                db.add(
                    ProductImage(
                        product_id=product.id,
                        image_url=f"https://picsum.photos/seed/{_slugify(name)}-{img_idx}/800/800",
                        alt_text=f"{name} image {img_idx + 1}",
                        display_order=img_idx,
                        is_primary=img_idx == 0,
                    )
                )
            _build_specs(db, product, overrides)
            db.flush()

        # ---------------------------------------------------------- reviews
        for prod in products:
            count = 6 + rng.randint(0, 6)
            for _ in range(count):
                if rng.random() < 0.18:
                    rating = max(1, round(prod.rating) - rng.randint(1, 2))
                else:
                    rating = min(5, max(1, round(prod.rating) + rng.choice([-1, 0, 0, 1])))
                if rating >= 4:
                    text = rng.choice(POSITIVE_REVIEWS).format(product=prod.name)
                elif rating == 3:
                    text = rng.choice(MIXED_REVIEWS).format(product=prod.name)
                else:
                    text = rng.choice(NEGATIVE_REVIEWS).format(product=prod.name)
                db.add(
                    Review(
                        product_id=prod.id,
                        user_id=rng.choice([admin.id, customer.id]) if rng.random() < 0.4 else None,
                        rating=rating,
                        title=text.split(".")[0][:120],
                        comment=text[:1800],
                        verified_purchase=rng.random() < 0.8,
                        helpful_count=rng.randint(0, 120),
                        created_at=datetime.utcnow() - timedelta(days=rng.randint(1, 120)),
                    )
                )
        db.flush()

        # ------------------------------------------ sentiment NLP telemetry
        total_reviews = db.query(Review).count()
        logger.info("Running NLP sentiment analysis on %d reviews...", total_reviews)
        CustomerFeedbackService(db).analyze_all_reviews()
        db.flush()

        # ---------------------------------------------------------- extra users + orders
        extra_users = []
        names = ["Priya Verma", "Arjun Mehta", "Sneha Kulkarni", "Vikram Singh", "Ananya Iyer", "Rohan Gupta"]
        cities = ["Mumbai", "Delhi", "Bengaluru", "Hyderabad", "Chennai", "Pune"]
        for i, full_name in enumerate(names):
            u = User(
                email=f"customer{i + 1}@omnimart.com",
                password=hash_password("password123"),
                full_name=full_name,
                active=True,
                roles=[UserRole.ROLE_USER.value],
            )
            db.add(u)
            db.flush()
            db.add(
                Address(
                    user_id=u.id, full_name=full_name, street_address=f"{100 + i * 17}, MG Road",
                    city=cities[i], state="India", postal_code=f"{400000 + i * 137}",
                    country="India", phone="+91-9000000000",
                    address_type="HOME", is_default=True,
                )
            )
            extra_users.append(u)

        all_buyers = [customer, admin] + extra_users
        for i in range(64):
            buyer = rng.choice(all_buyers)
            items = [(rng.choice(products), rng.randint(1, 2)) for _ in range(rng.randint(1, 3))]
            total = sum(float(p.price) * q for p, q in items)
            discount = sum((float(p.original_price) - float(p.price)) * q for p, q in items if p.original_price)
            discount = max(0.0, discount)
            shipping = 0.0 if total >= 999 else 49.0
            tax = total * 0.18
            final = total - discount + tax + shipping
            created = datetime.utcnow() - timedelta(days=rng.randint(0, 75), hours=rng.randint(0, 23))
            status = rng.choices(
                ["DELIVERED", "DELIVERED", "DELIVERED", "SHIPPED", "OUT_FOR_DELIVERY", "PROCESSING", "CONFIRMED", "CANCELLED", "PENDING"],
                weights=[40, 20, 10, 8, 6, 6, 4, 3, 3],
            )[0]
            order = Order(
                order_number=f"OM{created.strftime('%Y%m%d')}{uuid.uuid4().hex[:8].upper()}",
                user_id=buyer.id,
                total_amount=round(total, 2),
                discount_amount=round(discount, 2),
                tax_amount=round(tax, 2),
                shipping_fee=round(shipping, 2),
                final_amount=round(final, 2),
                status=status,
                carrier=rng.choice(["Delhivery", "Blue Dart", "Ekart", "XpressBees"]),
                tracking_number=f"TRK{uuid.uuid4().hex[:10].upper()}",
                shipping_address_id=buyer.addresses[0].id if buyer.addresses else None,
                estimated_delivery_date=created + timedelta(days=5),
                delivered_at=(created + timedelta(days=4, hours=6)) if status == "DELIVERED" else None,
                created_at=created,
            )
            db.add(order)
            db.flush()
            for prod, qty in items:
                db.add(
                    OrderItem(
                        order_id=order.id, product_id=prod.id, product_name=prod.name,
                        product_image_url=prod.primary_image_url, quantity=qty,
                        unit_price=prod.price, total_price=round(float(prod.price) * qty, 2),
                    )
                )
            db.add(
                Payment(
                    order_id=order.id,
                    payment_method=rng.choice(["UPI", "CREDIT_CARD", "NET_BANKING", "COD"]),
                    transaction_id="TXN" + uuid.uuid4().hex[:12].upper(),
                    amount=round(final, 2),
                    status="COMPLETED" if status != "CANCELLED" else "FAILED",
                    paid_at=created if status != "CANCELLED" else None,
                )
            )

        # ---------------------------------------------------------- telemetry
        session_pool = [uuid.uuid4().hex for _ in range(30)]
        interaction_types = ["PRODUCT_VIEW", "SEARCH", "ADD_TO_CART", "ADD_TO_WISHLIST", "PRODUCT_COMPARE", "FILTER_APPLY", "PRODUCT_VIEW", "PRODUCT_VIEW"]
        search_terms = ["iphone 16", "gaming laptop under 80000", "anc headphones", "oled monitor", "camera phone", "foldable", "4k tv", "macbook", "budget smartphone", "ps5", "smart watch", "wireless earbuds"]
        for _ in range(560):
            buyer = rng.choice(all_buyers) if rng.random() < 0.6 else None
            prod = rng.choice(products)
            itype = rng.choice(interaction_types)
            db.add(
                UserInteraction(
                    user_id=buyer.id if buyer else None,
                    session_id=rng.choice(session_pool),
                    interaction_type=itype,
                    product_id=prod.id if itype != "SEARCH" else None,
                    category_name=prod.category.name if prod.category and itype == "PRODUCT_VIEW" else None,
                    brand_name=prod.brand.name if prod.brand and itype == "PRODUCT_VIEW" else None,
                    search_query=rng.choice(search_terms) if itype == "SEARCH" else None,
                    duration_seconds=rng.randint(3, 240) if itype == "PRODUCT_VIEW" else 0,
                    created_at=datetime.utcnow() - timedelta(days=rng.randint(0, 45), minutes=rng.randint(0, 1440)),
                )
            )
        for _ in range(24):
            db.add(
                SearchHistory(
                    user_id=customer.id if rng.random() < 0.5 else None,
                    session_id=rng.choice(session_pool),
                    query=rng.choice(search_terms),
                    result_count=rng.randint(3, 40),
                    created_at=datetime.utcnow() - timedelta(days=rng.randint(0, 20)),
                )
            )

        # ---------------------------------------------------------- cart, wishlist, market prices
        wl = Wishlist(user_id=customer.id)
        db.add(wl)
        db.flush()
        for prod in rng.sample(products, 6):
            db.add(WishlistItem(wishlist_id=wl.id, product_id=prod.id))

        cart = Cart(user_id=customer.id, session_id=uuid.uuid4().hex)
        db.add(cart)
        db.flush()
        for prod in rng.sample(products, 3):
            db.add(CartItem(cart_id=cart.id, product_id=prod.id, quantity=rng.randint(1, 2), unit_price=prod.price))

        for prod in rng.sample(products, 40):
            for competitor in ["Amazon.in", "Flipkart", "Croma"]:
                db.add(
                    MarketProduct(
                        product_id=prod.id,
                        competitor_name=competitor,
                        competitor_price=round(float(prod.price) * rng.uniform(0.94, 1.08), 0),
                        competitor_url=f"https://www.{competitor.lower().replace('.in', '')}.com/s/{_slugify(prod.name)}",
                        in_stock=rng.random() < 0.9,
                        checked_at=datetime.utcnow() - timedelta(hours=rng.randint(0, 48)),
                    )
                )

        # ---------------------------------------------------------- chat history
        conv = ChatConversation(
            conversation_id=uuid.uuid4().hex, user_id=customer.id,
            session_id=session_pool[0], title="Recommend a flagship phone under 60000",
        )
        db.add(conv)
        db.flush()
        db.add(
            ChatMessage(
                conversation_id=conv.id, sender="USER",
                content="Recommend a flagship phone under ₹60,000",
                created_at=datetime.utcnow() - timedelta(hours=2),
            )
        )
        db.add(
            ChatMessage(
                conversation_id=conv.id, sender="ASSISTANT",
                content="Based on your request, here are the best matches: 1. OnePlus 13R 5G - ₹42,999, rated 4.5★...",
                tool_calls_json=json.dumps({"intent": "recommend", "tool": "searchProducts"}),
                recommended_product_ids_json=json.dumps([p.id for p in products[:5]]),
                reasoning_summary="Parsed budget <= ₹60000; ranked by hybrid score.",
                created_at=datetime.utcnow() - timedelta(hours=2, minutes=1),
            )
        )
        db.add(
            AIRecommendationLog(
                user_id=customer.id, query_text="Recommend a flagship phone under ₹60,000",
                tool_used="searchProducts",
                product_ids_json=json.dumps([p.id for p in products[:5]]),
                provider_used="mock", execution_time_ms=42,
                generated_reasoning="budget<=60000", created_at=datetime.utcnow() - timedelta(hours=2),
            )
        )

        db.commit()
        logger.info(
            "Seeding complete: %d products, %d brands, %d categories, 64 orders, 560 interactions, %d reviews",
            len(products), len(BRANDS), len(CATEGORIES), total_reviews,
        )
    finally:
        db.close()
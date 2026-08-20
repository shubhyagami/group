"""JPA-entity equivalents for the OmniMart AI domain (SQLAlchemy 2.0)."""
from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    JSON,
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Text,
)
from sqlalchemy.orm import relationship

from .database import Base
from .enums import UserRole


def utcnow() -> datetime:
    return datetime.utcnow()


class TimestampMixin:
    created_at = Column(DateTime, default=utcnow, nullable=False)
    updated_at = Column(DateTime, default=utcnow, onupdate=utcnow, nullable=False)


class User(Base, TimestampMixin):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password = Column(String(255), nullable=False)
    full_name = Column(String(255), nullable=False)
    phone = Column(String(40))
    avatar_url = Column(String(500))
    active = Column(Boolean, default=True, nullable=False)
    roles = Column(JSON, default=list)  # e.g. ["ROLE_USER", "ROLE_ADMIN"]

    addresses = relationship("Address", back_populates="user", cascade="all, delete-orphan")
    user_preference = relationship(
        "UserPreference", back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    cart = relationship("Cart", back_populates="user", uselist=False, cascade="all, delete-orphan")
    wishlist = relationship("Wishlist", back_populates="user", uselist=False, cascade="all, delete-orphan")
    orders = relationship("Order", back_populates="user")
    reviews = relationship("Review", back_populates="user")

    def is_admin(self) -> bool:
        return UserRole.ROLE_ADMIN.value in (self.roles or [])

    def has_role(self, role) -> bool:
        return role.value in (self.roles or [])

    @property
    def role_enum_list(self):
        return list(self.roles or [])


class Address(Base, TimestampMixin):
    __tablename__ = "addresses"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    full_name = Column(String(255))
    street_address = Column(String(500), nullable=False)
    apartment = Column(String(255))
    city = Column(String(120), nullable=False)
    state = Column(String(120))
    postal_code = Column(String(20))
    country = Column(String(80), default="India")
    phone = Column(String(40))
    address_type = Column(String(20), default="HOME")  # HOME / WORK
    is_default = Column(Boolean, default=False)

    user = relationship("User", back_populates="addresses")


class Category(Base):
    __tablename__ = "categories"
    __table_args__ = (
        Index("ix_categories_name", "name", unique=True),
        Index("ix_categories_slug", "slug", unique=True),
    )

    id = Column(Integer, primary_key=True)
    name = Column(String(120), unique=True, nullable=False)
    slug = Column(String(140), unique=True, nullable=False)
    description = Column(Text)
    icon = Column(String(255))
    image_url = Column(String(500))
    display_order = Column(Integer, default=0)
    active = Column(Boolean, default=True)
    parent_id = Column(Integer, ForeignKey("categories.id"))

    parent_category = relationship("Category", remote_side=[id], backref="sub_categories")
    products = relationship("Product", back_populates="category")


class Brand(Base):
    __tablename__ = "brands"
    __table_args__ = (
        Index("ix_brands_name", "name", unique=True),
        Index("ix_brands_slug", "slug", unique=True),
    )

    id = Column(Integer, primary_key=True)
    name = Column(String(120), unique=True, nullable=False)
    slug = Column(String(140), unique=True, nullable=False)
    logo_url = Column(String(500))
    description = Column(Text)
    website = Column(String(255))
    active = Column(Boolean, default=True)

    products = relationship("Product", back_populates="brand")


class Product(Base, TimestampMixin):
    __tablename__ = "products"
    __table_args__ = (
        Index("ix_products_slug", "slug", unique=True),
        Index("ix_products_sku", "sku", unique=True),
        Index("ix_products_category_price", "category_id", "price"),
    )

    id = Column(Integer, primary_key=True)
    name = Column(String(255), nullable=False, index=True)
    slug = Column(String(300), unique=True, nullable=False)
    sku = Column(String(80), unique=True, nullable=False)
    short_description = Column(Text)
    full_description = Column(Text)  # LOB
    price = Column(Numeric(12, 2), nullable=False)
    original_price = Column(Numeric(12, 2))
    discount_percentage = Column(Float, default=0.0)
    stock = Column(Integer, default=0)
    in_stock = Column(Boolean, default=True)
    featured = Column(Boolean, default=False)
    active = Column(Boolean, default=True)
    rating = Column(Float, default=4.5)
    review_count = Column(Integer, default=0)
    tags = Column(String(500))  # "flagship,oled,5g,gaming"
    primary_image_url = Column(String(500))

    category_id = Column(Integer, ForeignKey("categories.id"), index=True)
    brand_id = Column(Integer, ForeignKey("brands.id"), index=True)

    category = relationship("Category", back_populates="products")
    brand = relationship("Brand", back_populates="products")
    images = relationship("ProductImage", back_populates="product", cascade="all, delete-orphan")
    specifications = relationship(
        "ProductSpecification", back_populates="product", cascade="all, delete-orphan"
    )
    reviews = relationship("Review", back_populates="product", cascade="all, delete-orphan")
    inventory = relationship("Inventory", back_populates="product", uselist=False, cascade="all, delete-orphan")
    market_prices = relationship("MarketProduct", back_populates="product", cascade="all, delete-orphan")

    @property
    def tag_list(self) -> list[str]:
        return [t.strip() for t in (self.tags or "").split(",") if t.strip()]

    @property
    def discount_pct(self) -> float:
        if self.original_price and self.original_price > 0:
            return round((1 - float(self.price) / float(self.original_price)) * 100, 1)
        return float(self.discount_percentage or 0.0)


class ProductImage(Base):
    __tablename__ = "product_images"

    id = Column(Integer, primary_key=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    image_url = Column(String(500), nullable=False)
    alt_text = Column(String(255))
    display_order = Column(Integer, default=0)
    is_primary = Column(Boolean, default=False)

    product = relationship("Product", back_populates="images")


class ProductSpecification(Base):
    __tablename__ = "product_specifications"
    __table_args__ = (Index("ix_specs_product", "product_id"),)

    id = Column(Integer, primary_key=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    spec_group = Column(String(120))  # "Performance", "Display", "Battery"
    spec_key = Column(String(120))  # "Processor", "RAM", "Screen Size"
    spec_value = Column(String(255))
    display_order = Column(Integer, default=0)

    product = relationship("Product", back_populates="specifications")


class Inventory(Base):
    __tablename__ = "inventory"

    id = Column(Integer, primary_key=True)
    product_id = Column(Integer, ForeignKey("products.id"), unique=True, nullable=False, index=True)
    stock_quantity = Column(Integer, default=0)
    low_stock_threshold = Column(Integer, default=5)
    reserved_quantity = Column(Integer, default=0)
    warehouse_location = Column(String(120))
    last_restocked_at = Column(DateTime, default=utcnow)

    product = relationship("Product", back_populates="inventory")


class Review(Base):
    __tablename__ = "reviews"
    __table_args__ = (Index("ix_reviews_product_rating", "product_id", "rating"),)

    id = Column(Integer, primary_key=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    rating = Column(Integer, nullable=False)  # 1-5
    title = Column(String(255))
    comment = Column(Text)  # up to 2000 chars
    verified_purchase = Column(Boolean, default=False)
    helpful_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=utcnow, nullable=False)

    product = relationship("Product", back_populates="reviews")
    user = relationship("User", back_populates="reviews")
    feedback = relationship("CustomerFeedback", back_populates="review", uselist=False, cascade="all, delete-orphan")


class CustomerFeedback(Base):
    __tablename__ = "customer_feedback"
    __table_args__ = (Index("ix_feedback_product_sentiment", "product_id", "sentiment"),)

    id = Column(Integer, primary_key=True)
    review_id = Column(Integer, ForeignKey("reviews.id"), unique=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    sentiment = Column(String(20))  # Positive / Negative / Mixed / Neutral
    emotion = Column(String(20))  # Delighted / Satisfied / Frustrated / Disappointed / Neutral
    primary_topic = Column(String(40))  # Battery / Display / Camera / Performance / Delivery / Build Quality / Audio / General
    specific_issues_json = Column(Text)  # JSON list of issues
    positive_aspects_json = Column(Text)  # JSON list of positives
    confidence_score = Column(Float, default=0.0)
    source = Column(String(20), default="REVIEW")
    created_at = Column(DateTime, default=utcnow, nullable=False)

    review = relationship("Review", back_populates="feedback")
    product = relationship("Product")


class Cart(Base):
    __tablename__ = "carts"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), unique=True)
    session_id = Column(String(120), index=True)
    updated_at = Column(DateTime, default=utcnow, onupdate=utcnow)

    user = relationship("User", back_populates="cart")
    items = relationship("CartItem", back_populates="cart", cascade="all, delete-orphan")


class CartItem(Base):
    __tablename__ = "cart_items"

    id = Column(Integer, primary_key=True)
    cart_id = Column(Integer, ForeignKey("carts.id"), nullable=False, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    quantity = Column(Integer, default=1)
    unit_price = Column(Numeric(12, 2))
    added_at = Column(DateTime, default=utcnow)

    cart = relationship("Cart", back_populates="items")
    product = relationship("Product")


class Order(Base, TimestampMixin):
    __tablename__ = "orders"
    __table_args__ = (Index("ix_orders_user_created", "user_id", "created_at"),)

    id = Column(Integer, primary_key=True)
    order_number = Column(String(40), unique=True, nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    total_amount = Column(Numeric(12, 2), default=0)
    discount_amount = Column(Numeric(12, 2), default=0)
    tax_amount = Column(Numeric(12, 2), default=0)
    shipping_fee = Column(Numeric(12, 2), default=0)
    final_amount = Column(Numeric(12, 2), default=0)
    status = Column(String(30), default="PENDING", index=True)
    shipping_address_id = Column(Integer, ForeignKey("addresses.id"))
    carrier = Column(String(120))
    tracking_number = Column(String(120))
    cancellation_reason = Column(Text)
    return_reason = Column(Text)
    return_status = Column(String(40))
    estimated_delivery_date = Column(DateTime)
    delivered_at = Column(DateTime)

    user = relationship("User", back_populates="orders")
    shipping_address = relationship("Address")
    items = relationship("OrderItem", back_populates="order", cascade="all, delete-orphan")
    payment = relationship("Payment", back_populates="order", uselist=False, cascade="all, delete-orphan")


class OrderItem(Base):
    __tablename__ = "order_items"

    id = Column(Integer, primary_key=True)
    order_id = Column(Integer, ForeignKey("orders.id"), nullable=False, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    product_name = Column(String(255))
    product_image_url = Column(String(500))
    quantity = Column(Integer, default=1)
    unit_price = Column(Numeric(12, 2))
    total_price = Column(Numeric(12, 2))

    order = relationship("Order", back_populates="items")
    product = relationship("Product")


class Payment(Base):
    __tablename__ = "payments"

    id = Column(Integer, primary_key=True)
    order_id = Column(Integer, ForeignKey("orders.id"), unique=True, nullable=False)
    payment_method = Column(String(30))  # CREDIT_CARD / UPI / NET_BANKING / COD
    transaction_id = Column(String(120), index=True)
    amount = Column(Numeric(12, 2))
    status = Column(String(20), default="PENDING")
    paid_at = Column(DateTime)

    order = relationship("Order", back_populates="payment")


class Wishlist(Base):
    __tablename__ = "wishlists"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), unique=True, nullable=False)

    user = relationship("User", back_populates="wishlist")
    items = relationship("WishlistItem", back_populates="wishlist", cascade="all, delete-orphan")


class WishlistItem(Base):
    __tablename__ = "wishlist_items"

    id = Column(Integer, primary_key=True)
    wishlist_id = Column(Integer, ForeignKey("wishlists.id"), nullable=False, index=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    added_at = Column(DateTime, default=utcnow)

    wishlist = relationship("Wishlist", back_populates="items")
    product = relationship("Product")


class UserPreference(Base):
    __tablename__ = "user_preferences"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), unique=True, nullable=False)
    preferred_categories_json = Column(Text)  # {"Smartphones": 80, "Laptops": 60}
    preferred_brands_json = Column(Text)  # {"Samsung": 90, "Apple": 85}
    min_budget = Column(Numeric(12, 2))
    max_budget = Column(Numeric(12, 2))
    recommendations_enabled = Column(Boolean, default=True)
    behavior_tracking_enabled = Column(Boolean, default=True)
    last_updated = Column(DateTime, default=utcnow, onupdate=utcnow)

    user = relationship("User", back_populates="user_preference")

    def preferred_categories(self) -> dict[str, float]:
        import json

        try:
            return json.loads(self.preferred_categories_json or "{}")
        except Exception:
            return {}

    def preferred_brands(self) -> dict[str, float]:
        import json

        try:
            return json.loads(self.preferred_brands_json or "{}")
        except Exception:
            return {}


class UserInteraction(Base):
    __tablename__ = "user_interactions"
    __table_args__ = (Index("ix_interactions_user_type", "user_id", "interaction_type"),)

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), index=True)
    session_id = Column(String(120), index=True)
    interaction_type = Column(String(40), nullable=False)
    product_id = Column(Integer, ForeignKey("products.id"))
    category_name = Column(String(120))
    brand_name = Column(String(120))
    search_query = Column(String(500))
    duration_seconds = Column(Integer, default=0)
    created_at = Column(DateTime, default=utcnow, nullable=False)

    user = relationship("User")


class MarketProduct(Base):
    __tablename__ = "market_products"
    __table_args__ = (Index("ix_market_product_competitor", "product_id", "competitor_name"),)

    id = Column(Integer, primary_key=True)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False, index=True)
    competitor_name = Column(String(120))  # Flipkart / Amazon.in / Croma
    competitor_price = Column(Numeric(12, 2))
    competitor_url = Column(String(500))
    in_stock = Column(Boolean, default=True)
    checked_at = Column(DateTime, default=utcnow)

    product = relationship("Product", back_populates="market_prices")


class SearchHistory(Base):
    __tablename__ = "search_history"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), index=True)
    session_id = Column(String(120), index=True)
    query = Column(String(500))
    result_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=utcnow, nullable=False)
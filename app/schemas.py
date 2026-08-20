"""Pydantic DTOs / request & response records for all APIs."""
from __future__ import annotations

from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field


# --------------------------------------------------------------------------
# Generic
# --------------------------------------------------------------------------
class ApiMessage(BaseModel):
    message: str


class ErrorResponse(BaseModel):
    detail: str


# --------------------------------------------------------------------------
# Auth / User
# --------------------------------------------------------------------------
class RegisterRequest(BaseModel):
    email: str = Field(min_length=5, max_length=255)
    password: str = Field(min_length=6, max_length=128)
    full_name: str = Field(min_length=2, max_length=255)
    phone: Optional[str] = None


class LoginRequest(BaseModel):
    email: str
    password: str


class UserDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    full_name: str
    phone: Optional[str] = None
    avatar_url: Optional[str] = None
    active: bool
    roles: list[str]


class AddressDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    full_name: Optional[str] = None
    street_address: str
    apartment: Optional[str] = None
    city: str
    state: Optional[str] = None
    postal_code: Optional[str] = None
    country: Optional[str] = None
    phone: Optional[str] = None
    address_type: Optional[str] = None
    is_default: bool


# --------------------------------------------------------------------------
# Products
# --------------------------------------------------------------------------
class CategoryDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    slug: str
    description: Optional[str] = None
    icon: Optional[str] = None
    image_url: Optional[str] = None


class BrandDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    slug: str
    logo_url: Optional[str] = None


class ProductCardDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    slug: str
    price: float
    original_price: Optional[float] = None
    discount_percentage: float = 0.0
    rating: float
    review_count: int
    stock: int
    in_stock: bool
    featured: bool
    tags: Optional[str] = None
    primary_image_url: Optional[str] = None
    category_name: Optional[str] = None
    brand_name: Optional[str] = None
    why_recommended: Optional[str] = None  # explainable AI badge


class SpecDto(BaseModel):
    spec_group: str
    spec_key: str
    spec_value: str


class ReviewDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    product_id: int
    user_id: Optional[int] = None
    rating: int
    title: Optional[str] = None
    comment: Optional[str] = None
    verified_purchase: bool
    helpful_count: int
    created_at: datetime
    user_name: Optional[str] = None


class ProductDetailDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    slug: str
    sku: str
    short_description: Optional[str] = None
    full_description: Optional[str] = None
    price: float
    original_price: Optional[float] = None
    discount_percentage: float = 0.0
    stock: int
    in_stock: bool
    featured: bool
    rating: float
    review_count: int
    tags: Optional[str] = None
    primary_image_url: Optional[str] = None
    category: Optional[CategoryDto] = None
    brand: Optional[BrandDto] = None
    images: list[dict[str, Any]] = []
    specifications: list[SpecDto] = []
    latest_reviews: list[ReviewDto] = []


class ReviewRequest(BaseModel):
    rating: int = Field(ge=1, le=5)
    title: Optional[str] = None
    comment: Optional[str] = None
    verified_purchase: bool = False


class ProductListResponse(BaseModel):
    products: list[ProductCardDto]
    total: int
    page: int
    page_size: int
    filters: dict[str, Any] = {}


# --------------------------------------------------------------------------
# Cart / Orders
# --------------------------------------------------------------------------
class CartItemDto(BaseModel):
    id: int
    product_id: int
    name: str
    image_url: Optional[str] = None
    unit_price: float
    quantity: int
    total_price: float
    in_stock: bool


class CartDto(BaseModel):
    id: int
    items: list[CartItemDto] = []
    subtotal: float = 0.0
    item_count: int = 0
    session_id: Optional[str] = None


class CartAddRequest(BaseModel):
    product_id: int
    quantity: int = Field(default=1, ge=1, le=99)


class CartUpdateRequest(BaseModel):
    product_id: int
    quantity: int = Field(ge=0, le=99)


class OrderItemDto(BaseModel):
    id: int
    product_id: int
    product_name: str
    product_image_url: Optional[str] = None
    quantity: int
    unit_price: float
    total_price: float


class PaymentDto(BaseModel):
    id: int
    payment_method: str
    transaction_id: Optional[str] = None
    amount: float
    status: str
    paid_at: Optional[datetime] = None


class OrderDto(BaseModel):
    id: int
    order_number: str
    total_amount: float
    discount_amount: float
    tax_amount: float
    shipping_fee: float
    final_amount: float
    status: str
    carrier: Optional[str] = None
    tracking_number: Optional[str] = None
    cancellation_reason: Optional[str] = None
    return_reason: Optional[str] = None
    return_status: Optional[str] = None
    estimated_delivery_date: Optional[datetime] = None
    delivered_at: Optional[datetime] = None
    created_at: datetime
    items: list[OrderItemDto] = []
    payment: Optional[PaymentDto] = None
    shipping_address: Optional[AddressDto] = None


class CheckoutRequest(BaseModel):
    address_id: int
    payment_method: str = Field(default="UPI", pattern="^(CREDIT_CARD|UPI|NET_BANKING|COD)$")
    email_receipt: bool = True


# --------------------------------------------------------------------------
# Recommendations
# --------------------------------------------------------------------------
class RecommendationResponse(BaseModel):
    products: list[ProductCardDto]
    strategy: str
    explained: bool = True


# --------------------------------------------------------------------------
# Search
# --------------------------------------------------------------------------
class AutocompleteResponse(BaseModel):
    queries: list[str] = []
    products: list[dict[str, Any]] = []
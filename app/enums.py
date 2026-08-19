"""Domain enums mirroring the Java `UserRole`, `OrderStatus`, `PaymentStatus`,
`InteractionType` enums."""
from __future__ import annotations

import enum


class UserRole(str, enum.Enum):
    ROLE_USER = "ROLE_USER"
    ROLE_ADMIN = "ROLE_ADMIN"


class OrderStatus(str, enum.Enum):
    PENDING = "PENDING"
    CONFIRMED = "CONFIRMED"
    PROCESSING = "PROCESSING"
    SHIPPED = "SHIPPED"
    OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY"
    DELIVERED = "DELIVERED"
    CANCELLED = "CANCELLED"
    RETURN_REQUESTED = "RETURN_REQUESTED"
    RETURNED = "RETURNED"


class PaymentStatus(str, enum.Enum):
    PENDING = "PENDING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    REFUNDED = "REFUNDED"


class InteractionType(str, enum.Enum):
    PRODUCT_VIEW = "PRODUCT_VIEW"
    SEARCH = "SEARCH"
    ADD_TO_CART = "ADD_TO_CART"
    REMOVE_FROM_CART = "REMOVE_FROM_CART"
    ADD_TO_WISHLIST = "ADD_TO_WISHLIST"
    PRODUCT_PURCHASE = "PRODUCT_PURCHASE"
    PRODUCT_COMPARE = "PRODUCT_COMPARE"
    FILTER_APPLY = "FILTER_APPLY"


class Sentiment(str, enum.Enum):
    POSITIVE = "Positive"
    NEGATIVE = "Negative"
    MIXED = "Mixed"
    NEUTRAL = "Neutral"


class Emotion(str, enum.Enum):
    DELIGHTED = "Delighted"
    SATISFIED = "Satisfied"
    FRUSTRATED = "Frustrated"
    DISAPPOINTED = "Disappointed"
    NEUTRAL = "Neutral"
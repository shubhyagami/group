"""Shared Product -> ProductCardDto mapping."""
from __future__ import annotations

from ..models import Product


def product_to_card(product: Product, why_recommended: str | None = None) -> dict:
    return {
        "id": product.id,
        "name": product.name,
        "slug": product.slug,
        "price": float(product.price),
        "original_price": float(product.original_price) if product.original_price else None,
        "discount_percentage": product.discount_pct,
        "rating": product.rating,
        "review_count": product.review_count,
        "stock": product.stock,
        "in_stock": product.in_stock,
        "featured": product.featured,
        "tags": product.tags,
        "primary_image_url": product.primary_image_url,
        "category_name": product.category.name if product.category else None,
        "brand_name": product.brand.name if product.brand else None,
        "why_recommended": why_recommended,
    }
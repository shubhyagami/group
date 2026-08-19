"""Safe database tool routing for the AI orchestrator.

Every tool returns VERIFIED database facts which are injected into the
model prompt to guarantee zero hallucinations. Tools are read-only.
"""
from __future__ import annotations

from sqlalchemy.orm import Session

from ..models import Product, User
from ..services.comparison_service import ComparisonService
from ..services.feedback_service import CustomerFeedbackService
from ..services.product_cards import product_to_card
from ..services.recommendation_service import HybridRecommendationService
from ..services.search_service import SearchService


class ToolRouter:
    def __init__(self, db: Session):
        self.db = db
        self.search_svc = SearchService(db)
        self.compare_svc = ComparisonService(db)
        self.feedback_svc = CustomerFeedbackService(db)
        self.reco_svc = HybridRecommendationService(db)

    # ------------------------------------------------------------- tools
    def search_products(self, parsed: dict, limit: int = 8) -> dict:
        # Category/brand filters are more precise than raw text; when they are
        # present the text query is only used as a soft signal.
        text_query = parsed.get("query")
        if parsed.get("category") or parsed.get("brand"):
            text_query = None
        products, total = self.search_svc.apply_filters(
            query=text_query,
            min_price=parsed.get("min_price"),
            max_price=parsed.get("max_price"),
            min_rating=parsed.get("min_rating"),
            tags=parsed.get("tags"),
            page_size=200,
            in_stock_only=True,
        )
        if parsed.get("category"):
            products = [p for p in products if p.category and p.category.name == parsed["category"]]
        if parsed.get("brand"):
            products = [p for p in products if p.brand and p.brand.name.lower() == parsed["brand"].lower()]
        total = len(products)
        return {"products": [product_to_card(p) for p in products[:limit]], "total": total}

    def compare_products(self, product_ids: list[int]) -> dict:
        if not product_ids:
            return {"products": [], "spec_matrix": [], "identical_cells": [], "ai_verdict": "", "provider": "mock"}
        return self.compare_svc.build_comparison(product_ids)

    def get_product_feedback_summary(self, product_id: int) -> dict:
        return self.feedback_svc.product_feedback_summary(product_id)

    def get_recommended_products(self, user: User | None, limit: int = 8) -> dict:
        if user:
            results = self.reco_svc.recommend_for_user(user, limit)
            strategy = "hybrid"
        else:
            results = self.reco_svc.popularity_based(limit)
            strategy = "popularity"
        return {
            "products": [product_to_card(p, why) for p, why in results],
            "strategy": strategy,
        }

    def get_product_info(self, product_id: int) -> dict:
        product = self.db.get(Product, product_id)
        if product is None:
            return {}
        specs = product.specifications[:6]
        return {
            "id": product.id,
            "name": product.name,
            "price": float(product.price),
            "original_price": float(product.original_price) if product.original_price else None,
            "discount_percentage": product.discount_pct,
            "rating": product.rating,
            "review_count": product.review_count,
            "stock": product.stock,
            "in_stock": product.in_stock,
            "tags": product.tag_list,
            "top_specs": [(s.spec_key, s.spec_value) for s in specs],
            "category": product.category.name if product.category else None,
            "brand": product.brand.name if product.brand else None,
        }
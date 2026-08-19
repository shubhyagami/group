"""Multi-product hardware comparison engine & spec matrix builder."""
from __future__ import annotations

from collections import defaultdict

from sqlalchemy.orm import Session

from ..ai.mock import MockAIProvider
from ..models import Product, ProductSpecification


class ComparisonService:
    def __init__(self, db: Session):
        self.db = db
        self.nlp = MockAIProvider()

    def build_comparison(self, product_ids: list[int], max_products: int = 5) -> dict:
        ids = product_ids[:max_products]
        products = (
            self.db.query(Product)
            .filter(Product.id.in_(ids), Product.active.is_(True))
            .all()
        )
        products.sort(key=lambda p: ids.index(p.id))

        # spec matrix: group -> key -> list of values per product
        matrix: dict[tuple[str, str], dict[int, str]] = defaultdict(dict)
        specs = (
            self.db.query(ProductSpecification)
            .filter(ProductSpecification.product_id.in_([p.id for p in products]))
            .order_by(ProductSpecification.display_order, ProductSpecification.id)
            .all()
        )
        for s in specs:
            matrix[(s.spec_group or "General", s.spec_key)][s.product_id] = s.spec_value

        rows = []
        identical_cells = []
        row_idx = 0
        for (group, key), values in matrix.items():
            ordered_values = [values.get(p.id) for p in products]
            is_identical = len({v for v in ordered_values if v}) <= 1
            rows.append(
                {
                    "spec_group": group,
                    "spec_key": key,
                    "values": ordered_values,
                }
            )
            if is_identical:
                identical_cells.append(row_idx)
            row_idx += 1

        product_cards = [
            {
                "id": p.id,
                "name": p.name,
                "slug": p.slug,
                "price": float(p.price),
                "original_price": float(p.original_price) if p.original_price else None,
                "discount_percentage": p.discount_pct,
                "rating": p.rating,
                "review_count": p.review_count,
                "stock": p.stock,
                "in_stock": p.in_stock,
                "featured": p.featured,
                "tags": p.tags,
                "primary_image_url": p.primary_image_url,
                "category_name": p.category.name if p.category else None,
                "brand_name": p.brand.name if p.brand else None,
                "why_recommended": None,
            }
            for p in products
        ]

        verdict = self.nlp.comparison_verdict(product_cards, rows)
        return {
            "products": product_cards,
            "spec_matrix": rows,
            "identical_cells": identical_cells,
            "ai_verdict": verdict,
            "provider": self.nlp.name,
        }
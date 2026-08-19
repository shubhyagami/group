"""Product search: filters, autocomplete and natural-language search."""
from __future__ import annotations

import re

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from ..ai.mock import MockAIProvider
from ..models import Brand, Category, Product

TEXT_STOPWORDS = {"the", "with", "and", "best", "good", "for", "me", "any", "some", "a", "an", "please"}


class SearchService:
    def __init__(self, db: Session):
        self.db = db
        self.nlp = MockAIProvider()

    def parse_nl(self, query: str) -> dict:
        return self.nlp.parse_natural_language_search(query)

    def _apply_query_filter(self, q, query: str):
        """Tokenized OR matching against name/tags/short description so NL
        queries like 'flagship phone' or 'anc headphones' hit reliably."""
        words = [w for w in re.split(r"\W+", query.lower()) if len(w) >= 3 and w not in TEXT_STOPWORDS]
        if not words:
            return q
        conditions = [
            Product.name.ilike(f"%{w}%")
            | Product.tags.ilike(f"%{w}%")
            | Product.short_description.ilike(f"%{w}%")
            | Product.full_description.ilike(f"%{w}%")
            for w in words
        ]
        return q.filter(or_(*conditions))

    def apply_filters(
        self,
        query: str | None = None,
        category_slug: str | None = None,
        brand_slug: str | None = None,
        min_price: float | None = None,
        max_price: float | None = None,
        min_rating: float | None = None,
        tags: list[str] | None = None,
        featured: bool | None = None,
        sort: str = "relevance",
        page: int = 1,
        page_size: int = 12,
        in_stock_only: bool = False,
    ):
        def _build(with_tags: bool) -> tuple:
            q = self.db.query(Product).filter(Product.active.is_(True))
            if query:
                q = self._apply_query_filter(q, query)
            if category_slug:
                cat = self.db.query(Category).filter(Category.slug == category_slug).first()
                if cat:
                    q = q.filter(Product.category_id == cat.id)
            if brand_slug:
                brand = self.db.query(Brand).filter(Brand.slug == brand_slug).first()
                if brand:
                    q = q.filter(Product.brand_id == brand.id)
            if min_price is not None:
                q = q.filter(Product.price >= min_price)
            if max_price is not None:
                q = q.filter(Product.price <= max_price)
            if min_rating is not None:
                q = q.filter(Product.rating >= min_rating)
            if with_tags and tags:
                for tag in tags:
                    q = q.filter(Product.tags.ilike(f"%{tag}%"))
            if featured:
                q = q.filter(Product.featured.is_(True))
            if in_stock_only:
                q = q.filter(Product.in_stock.is_(True))
            return q, q.count()

        q, total = _build(with_tags=True)
        if total == 0 and tags:  # soft-tag fallback: feature tags are hints, not hard filters
            q, total = _build(with_tags=False)

        if sort == "price_asc":
            q = q.order_by(Product.price.asc())
        elif sort == "price_desc":
            q = q.order_by(Product.price.desc())
        elif sort == "rating":
            q = q.order_by(Product.rating.desc(), Product.review_count.desc())
        elif sort == "newest":
            q = q.order_by(Product.created_at.desc())
        elif sort == "popular":
            q = q.order_by(Product.review_count.desc())
        else:
            q = q.order_by(Product.featured.desc(), Product.review_count.desc())

        products = q.offset((page - 1) * page_size).limit(page_size).all()
        return products, total

    def autocomplete(self, q: str, limit: int = 6) -> dict:
        if not q:
            return {"queries": [], "products": []}
        like = f"%{q}%"
        products = (
            self.db.query(Product)
            .filter(Product.active.is_(True), or_(Product.name.ilike(like), Product.tags.ilike(like)))
            .order_by(Product.review_count.desc())
            .limit(limit)
            .all()
        )
        categories = (
            self.db.query(Category)
            .filter(Category.name.ilike(like))
            .limit(3)
            .all()
        )
        queries = [p.name for p in products[:3]] + [c.name for c in categories]
        return {
            "queries": queries,
            "products": [
                {
                    "id": p.id,
                    "name": p.name,
                    "price": float(p.price),
                    "rating": p.rating,
                    "review_count": p.review_count,
                    "image": p.primary_image_url,
                    "slug": p.slug,
                }
                for p in products
            ],
        }

    def popular_searches(self, limit: int = 10) -> list[str]:
        from ..models import SearchHistory

        rows = (
            self.db.query(SearchHistory.query, func.count(SearchHistory.id))
            .group_by(SearchHistory.query)
            .order_by(func.count(SearchHistory.id).desc())
            .limit(limit)
            .all()
        )
        return [r[0] for r in rows if r[0]]
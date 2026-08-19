"""Hybrid recommendation engine.

FinalScore(p) = (w_pref * S_pref) + (w_beh * S_beh) + (w_cont * S_cont)
                + (w_rat * S_rat) + (w_pop * S_pop)

Weights: w_pref=0.35, w_beh=0.25, w_cont=0.20, w_rat=0.10, w_pop=0.10
"""
from __future__ import annotations

from sqlalchemy import desc, func
from sqlalchemy.orm import Session

from ..models import Category, Product, User, UserInteraction, UserPreference

W_PREF = 0.35
W_BEH = 0.25
W_CONT = 0.20
W_RAT = 0.10
W_POP = 0.10

POPULAR_TAGS = ["flagship", "gaming", "bestseller", "oled", "5g", "foldable"]


class HybridRecommendationService:
    def __init__(self, db: Session):
        self.db = db

    # ------------------------------------------------------------ helpers
    def _load_preference(self, user: User) -> UserPreference | None:
        return (
            self.db.query(UserPreference).filter(UserPreference.user_id == user.id).first()
            if user
            else None
        )

    def _load_behavior(self, user: User, top_n: int = 20) -> tuple[dict, dict, set]:
        if not user:
            return {}, {}, set()
        rows = (
            self.db.query(UserInteraction)
            .filter(UserInteraction.user_id == user.id)
            .order_by(desc(UserInteraction.created_at))
            .limit(top_n)
            .all()
        )
        cat_counts: dict[str, int] = {}
        brand_counts: dict[str, int] = {}
        product_ids: set[int] = set()
        for r in rows:
            if r.category_name:
                cat_counts[r.category_name] = cat_counts.get(r.category_name, 0) + 1
            if r.brand_name:
                brand_counts[r.brand_name] = brand_counts.get(r.brand_name, 0) + 1
            if r.product_id:
                product_ids.add(r.product_id)
        return cat_counts, brand_counts, product_ids

    # ------------------------------------------------------------ scoring
    def _score(self, product: Product, user: User, pref: UserPreference | None,
               cat_counts: dict, brand_counts: dict, product_ids: set, max_reviews: int) -> tuple[float, str]:
        s_pref = 0.0
        why: list[str] = []
        if pref:
            cats = pref.preferred_categories()
            brands = pref.preferred_brands()
            cat_w = cats.get(product.category.name if product.category else "", 0) / 100.0 if product.category else 0.0
            brand_w = brands.get(product.brand.name if product.brand else "", 0) / 100.0 if product.brand else 0.0
            if cat_w > 0:
                why.append(f"Matches preferred category ({product.category.name})")
            if brand_w > 0:
                why.append(f"Preferred brand ({product.brand.name})")
            price = float(product.price)
            budget_w = 1.0
            if pref.min_budget is not None and price < float(pref.min_budget) * 0.85:
                budget_w = 0.3
            if pref.max_budget is not None and price > float(pref.max_budget):
                budget_w = 0.0
            s_pref = min(1.0, 0.5 * cat_w + 0.4 * brand_w + 0.1 * budget_w)

        s_beh = 0.0
        if user and (cat_counts or brand_counts or product_ids):
            total = max(1, sum(cat_counts.values()) + sum(brand_counts.values()) + len(product_ids))
            cat_match = cat_counts.get(product.category.name if product.category else "", 0)
            brand_match = brand_counts.get(product.brand.name if product.brand else "", 0)
            prod_match = 1 if product.id in product_ids else 0
            s_beh = min(1.0, (0.5 * cat_match + 0.3 * brand_match + 0.2 * prod_match) / total)

        tags = set(product.tag_list)
        matched = len(tags & set(POPULAR_TAGS))
        s_cont = min(1.0, matched / max(1, len(POPULAR_TAGS)) * 2 + (0.2 if product.featured else 0.0))

        s_rat = min(1.0, product.rating / 5.0)
        s_pop = min(1.0, product.review_count / max(1, max_reviews))

        score = W_PREF * s_pref + W_BEH * s_beh + W_CONT * s_cont + W_RAT * s_rat + W_POP * s_pop

        if product.rating >= 4.6:
            why.append(f"Top rated ({product.rating:.1f}★)")
        if product.review_count >= 500:
            why.append(f"Popular ({product.review_count}+ reviews)")
        if product.featured:
            why.append("Editor's pick")
        if not why:
            why.append(f"Trending in {product.category.name if product.category else 'store'}")

        return score, " • ".join(why[:3])

    # ------------------------------------------------------------ queries
    def recommend_for_user(self, user: User | None, limit: int = 8) -> list[tuple[Product, str]]:
        products = self.db.query(Product).filter(Product.active.is_(True), Product.in_stock.is_(True)).all()
        if not products:
            return []
        max_reviews = max(p.review_count for p in products) or 1

        pref = self._load_preference(user)
        cat_counts, brand_counts, product_ids = self._load_behavior(user)

        scored = []
        for p in products:
            score, why = self._score(p, user, pref, cat_counts, brand_counts, product_ids, max_reviews)
            scored.append((score, why, p))
        scored.sort(key=lambda t: t[0], reverse=True)
        return [(p, why) for _, why, p in scored[:limit]]

    def popularity_based(self, limit: int = 8) -> list[tuple[Product, str]]:
        rows = (
            self.db.query(Product)
            .filter(Product.active.is_(True), Product.in_stock.is_(True))
            .order_by(desc(Product.review_count), desc(Product.rating))
            .limit(limit)
            .all()
        )
        return [(p, f"Trending in {p.category.name if p.category else 'store'} • Top rated ({p.rating:.1f}★)") for p in rows]

    def related_products(self, product: Product, limit: int = 4) -> list[Product]:
        return (
            self.db.query(Product)
            .filter(
                Product.active.is_(True),
                Product.in_stock.is_(True),
                Product.id != product.id,
                Product.category_id == product.category_id,
            )
            .order_by(desc(Product.rating))
            .limit(limit)
            .all()
        )

    def top_categories_for_user(self, user: User | None, limit: int = 6) -> list[dict]:
        cat_counts, _, _ = self._load_behavior(user, 50)
        if not cat_counts:
            rows = (
                self.db.query(Product.category_id, func.count(Product.id))
                .filter(Product.active.is_(True))
                .group_by(Product.category_id)
                .order_by(desc(func.count(Product.id)))
                .limit(limit)
                .all()
            )
            cats = self.db.query(Category).filter(Category.id.in_([r[0] for r in rows])).all()
            return [{"name": c.name, "slug": c.slug} for c in cats]
        ordered = sorted(cat_counts.items(), key=lambda kv: kv[1], reverse=True)[:limit]
        return [{"name": name, "slug": name.lower().replace(" ", "-")} for name, _ in ordered]
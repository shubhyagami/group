"""Product search: filters, autocomplete and natural-language search."""
from __future__ import annotations

import re

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from ..models import Brand, Category, Product

TEXT_STOPWORDS = {"the", "with", "and", "best", "good", "for", "me", "any", "some", "a", "an", "please"}

# --------------------------------------------------------------------------
# Natural-language search -> structured filters (deterministic NLP)
# --------------------------------------------------------------------------
CATEGORY_KEYWORDS: list[tuple[str, list[str]]] = [
    ("Smartphones", ["smartphone", "iphone", "mobile", "android phone", "galaxy", "handset", "xperia", "oneplus", " phone", " phones"]),
    ("Laptops", ["laptop", "notebook", "macbook", "ultrabook", "thinkpad", "chromebook"]),
    ("Headphones", ["headphone", "headset", "earbud", "earphone", "airpods", "buds", "anc headphone"]),
    ("Gaming", ["gaming", "console", "playstation", "ps5", "controller", "gamepad", "joystick"]),
    ("Smart Home", ["smart home", "smart tv", "television", "tv ", "soundbar", "robot vacuum", "refrigerator", "air purifier", "fridge", "washer", "dryer"]),
    ("Cameras", ["camera", "mirrorless", "dslr", "vlogging", "photography", "vlog"]),
    ("Accessories", ["accessory", "charger", "adapter", "keyboard", "mouse", "webcam", "case", "cable", "trackpad", "airtag"]),
    ("Monitors", ["monitor", "display", "ultrawide", "oled monitor", "screen"]),
]

BRAND_NAMES = [
    "Samsung", "Apple", "Sony", "Dell", "Lenovo", "ASUS", "HP", "Bose",
    "OnePlus", "Logitech", "Canon", "LG",
]

FEATURE_TAGS = {
    "anc": ["anc", "noise cancelling", "noise-cancelling", "noise canceling"],
    "oled": ["oled", "amoled"],
    "5g": ["5g"],
    "gaming": ["gaming"],
    "foldable": ["fold", "flip"],
    "120hz": ["120hz", "120 hz", "high refresh"],
    "hdr": ["hdr"],
    "fast-charging": ["fast charging", "fast charge", "super fast"],
    "water-resistant": ["water resistant", "ip68", "ip67"],
}

FILLER_WORDS = {
    "recommend", "recommendations", "suggest", "please", "some", "good", "best", "great",
    "want", "wanted", "looking", "look", "find", "find me", "show", "show me", "get",
    "give", "need", "a", "an", "the", "me", "for", "i", "my", "with", "and", "or",
    "of", "in", "under", "below", "within", "around", "above", "over", "budget",
    "under", "price", "priced", "buy", "purchase", "which", "what", "help",
}

NUMBER_WORDS = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5}


def _normalize_amount(raw: str) -> float | None:
    """'80k' -> 80000, '1.5l'/'1.5 lakh' -> 150000, '40000' -> 40000, '8,000' -> 8000."""
    s = raw.strip().lower().replace(",", "").replace("₹", "").replace("rs", "").replace("inr", "").strip()
    m = re.match(r"^(\d+(?:\.\d+)?)\s*(k|lakh|l|cr|crore)?$", s)
    if not m:
        return None
    value = float(m.group(1))
    suffix = m.group(2) or ""
    if suffix == "k":
        return value * 1000
    if suffix in ("l", "lakh"):
        return value * 100000
    if suffix in ("cr", "crore"):
        return value * 10000000
    return value


NUMBER_RE = r"\d[\d,]*(?:\.\d+)?\s*(?:k|lakh|l|cr|crore|rupees?|rs|inr)?"


def _clean_search_query(raw: str) -> str:
    cleaned = raw
    patterns = [
        rf"under\s+{NUMBER_RE}",
        rf"below\s+{NUMBER_RE}",
        rf"(?:up\s*to|upto)\s+{NUMBER_RE}",
        rf"less\s+than\s+{NUMBER_RE}",
        rf"(?:within|around|max(?:imum)?)\s+{NUMBER_RE}",
        rf"(?:budget of\s+)?{NUMBER_RE}",
        rf"above\s+{NUMBER_RE}",
        rf"over\s+{NUMBER_RE}",
        rf"more\s+than\s+{NUMBER_RE}",
        rf"(?:minimum|min)\s+{NUMBER_RE}",
        r"\d+\s*star",
        r"\d+\s*\+?\s*rating",
        r"(?:rating|rated)\s*(?:of|above|below|at)?\s*\d+(?:\.\d+)?",
    ]
    for p in patterns:
        cleaned = re.sub(p, " ", cleaned, flags=re.IGNORECASE)

    lower = cleaned.lower()
    for filler in sorted(FILLER_WORDS, key=len, reverse=True):
        lower = re.sub(rf"\b{re.escape(filler)}\b", " ", lower)
    return re.sub(r"\s+", " ", lower).strip()


def parse_nl(query: str) -> dict:
    """Extract category/brand/budget/rating/tags from a natural-language query."""
    raw = query or ""
    lower = raw.lower()
    parsed: dict = {
        "raw_query": raw,
        "query": None,
        "category": None,
        "brand": None,
        "min_price": None,
        "max_price": None,
        "min_rating": None,
        "tags": [],
    }

    for phrase in ("under", "below", "upto", "up to", "less than", "within", "max"):
        m = re.search(rf"{phrase}\s+([\d.,]+\s*(?:k|lakh|l|cr|crore)?)", lower)
        if m:
            val = _normalize_amount(m.group(1))
            if val is not None and (parsed["max_price"] is None or val < parsed["max_price"]):
                parsed["max_price"] = val
    for phrase in ("above", "over", "more than", "minimum", "min"):
        m = re.search(rf"{phrase}\s+([\d.,]+\s*(?:k|lakh|l|cr|crore)?)", lower)
        if m:
            val = _normalize_amount(m.group(1))
            if val is not None and (parsed["min_price"] is None or val > parsed["min_price"]):
                parsed["min_price"] = val
    m = re.search(r"(?:rs|rupees|inr|budget of)\s*([\d.,]+\s*(?:k|lakh|l|cr|crore)?)", lower)
    if m:
        val = _normalize_amount(m.group(1))
        if val is not None:
            parsed["max_price"] = min(parsed["max_price"] or val, val)

    m = re.search(r"(\d+(?:\.\d+)?)\s*\+?\s*star", lower)
    if not m:
        m = re.search(r"(?:rating|rated)\s*(?:of|above)?\s*(\d+(?:\.\d+)?)", lower)
    if m:
        try:
            parsed["min_rating"] = float(m.group(1))
        except ValueError:
            pass
    for word, num in NUMBER_WORDS.items():
        if re.search(rf"\b{word}\b.*\bstar", lower) or re.search(rf"\bstar\b.*\b{word}\b", lower):
            parsed["min_rating"] = num
            break

    for cat, keywords in CATEGORY_KEYWORDS:
        if any(kw in lower for kw in keywords):
            parsed["category"] = cat
            break
    if not parsed["category"]:
        for brand in ("iphone", "galaxy", "oneplus", "macbook", "xperia", "thinkpad"):
            if brand in lower:
                parsed["category"] = "Smartphones" if brand in ("iphone", "galaxy", "oneplus", "xperia") else "Laptops"
                break

    for brand in BRAND_NAMES:
        if brand.lower() in lower:
            parsed["brand"] = brand
            break

    for tag, keywords in FEATURE_TAGS.items():
        if any(kw in lower for kw in keywords):
            parsed["tags"].append(tag)

    parsed["query"] = _clean_search_query(raw) or None
    return parsed


class SearchService:
    def __init__(self, db: Session):
        self.db = db

    def parse_nl(self, query: str) -> dict:
        return parse_nl(query)

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
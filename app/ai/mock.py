"""Deterministic offline AI: natural-language spec extraction, customer
feedback sentiment/emotion NLP, admin BI answers and grounded chat replies.

Zero external dependencies - every answer is derived from verified facts.
"""
from __future__ import annotations

import json
import re
from typing import Any

from .base import AIProvider, AIResult

# --------------------------------------------------------------------------
# Natural language search -> structured filters
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

# conversational filler words stripped from the cleaned text query
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

    # drop conversational filler words (multi-word first)
    lower = cleaned.lower()
    for filler in sorted(FILLER_WORDS, key=len, reverse=True):
        lower = re.sub(rf"\b{re.escape(filler)}\b", " ", lower)
    return re.sub(r"\s+", " ", lower).strip()


class MockAIProvider(AIProvider):
    """Zero-dependency deterministic engine used as final fallback and for
    offline NLP tasks (sentiment, NL search, admin BI)."""

    name = "mock"

    # ------------------------------------------------------------------ NLP
    def parse_natural_language_search(self, query: str) -> dict[str, Any]:
        raw = query or ""
        lower = raw.lower()
        parsed: dict[str, Any] = {
            "raw_query": raw,
            "query": None,
            "category": None,
            "brand": None,
            "min_price": None,
            "max_price": None,
            "min_rating": None,
            "tags": [],
        }

        # Budget bounds
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
        # Plain number alone or with "rs/rupees/inr"
        m = re.search(r"(?:rs|rupees|inr|budget of)\s*([\d.,]+\s*(?:k|lakh|l|cr|crore)?)", lower)
        if m:
            val = _normalize_amount(m.group(1))
            if val is not None:
                parsed["max_price"] = min(parsed["max_price"] or val, val)

        # Min rating
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

        # Category
        for cat, keywords in CATEGORY_KEYWORDS:
            if any(kw in lower for kw in keywords):
                parsed["category"] = cat
                break
        if not parsed["category"]:
            for brand in ("iphone", "galaxy", "oneplus", "macbook", "xperia", "thinkpad"):
                if brand in lower:
                    parsed["category"] = (
                        "Smartphones" if brand in ("iphone", "galaxy", "oneplus", "xperia") else "Laptops"
                    )
                    break

        # Brand
        for brand in BRAND_NAMES:
            if brand.lower() in lower:
                parsed["brand"] = brand
                break

        # Feature tags
        for tag, keywords in FEATURE_TAGS.items():
            if any(kw in lower for kw in keywords):
                parsed["tags"].append(tag)

        parsed["query"] = _clean_search_query(raw) or None
        return parsed

    # ------------------------------------------- Customer feedback NLP
    TOPICS: list[tuple[str, list[str]]] = [
        ("Battery", ["battery", "charging", "charge", "drain", "backup", "fast charging"]),
        ("Display", ["display", "screen", "oled", "amoled", "panel", "refresh rate", "brightness", "bezels"]),
        ("Camera", ["camera", "photo", "picture", "zoom", "video", "night mode", "portrait", "lens", "sensor"]),
        ("Performance", ["performance", "speed", "processor", "ram", "lag", "smooth", "gaming", "heat", "thermal", "freeze", "crash"]),
        ("Delivery", ["delivery", "shipping", "packaging", "courier", "arrived", "delivered", "dispatch"]),
        ("Audio", ["audio", "sound", "bass", "speaker", "anc", "noise cancelling", "microphone", "mic", "treble"]),
        ("Build Quality", ["build", "premium", "plastic", "finish", "durable", "solid", "fragile", "flimsy", "hinge"]),
    ]

    NEGATIVE_SIGNALS: list[tuple[re.Pattern, str]] = [
        (re.compile(r"battery.{0,30}(drain|short|poor|weak|dies|fast|low)", re.I), "Battery drains fast / short battery life"),
        (re.compile(r"(overheat|thermal|throttl|heats? up)", re.I), "Thermal throttling / heating issues"),
        (re.compile(r"(lag|freeze|stutter|crash|hang)", re.I), "Performance lag / app crashes"),
        (re.compile(r"(blur|grainy|noisy|dark photo|low light)", re.I), "Camera quality in low light"),
        (re.compile(r"(not worth|overpriced|pricey)", re.I), "Overpriced for the value"),
        (re.compile(r"(broken|defect|faulty|dead on arrival|refund)", re.I), "Defective unit / DOA"),
        (re.compile(r"(delay|late|never arrived|slow delivery)", re.I), "Slow / late delivery"),
        (re.compile(r"(flimsy|cheap plastic|fragile|hinge)", re.I), "Build quality concerns"),
        (re.compile(r"(noisy|hiss|no bass|muffled)", re.I), "Audio quality issues"),
        (re.compile(r"(faded|washed|colors)", re.I), "Display color accuracy issues"),
        (re.compile(r"(speaker|bluetooth).{0,25}(disconnect|drop|issue)", re.I), "Connectivity drops"),
        (re.compile(r"(disappoint|worst|terrible|awful|poor|bad|issue|problem|unhappy|regret)", re.I), "Overall dissatisfaction"),
    ]

    POSITIVE_SIGNALS: list[tuple[re.Pattern, str]] = [
        (re.compile(r"(vibrant|stunning|gorgeous|razor sharp|bright) (display|screen|oled|amoled)", re.I), "Vibrant high-refresh display"),
        (re.compile(r"(amazing|excellent|great|fantastic|superb|stellar) (camera|photo|picture|video)", re.I), "Excellent camera quality"),
        (re.compile(r"(blazing|super fast|fast) (charging|charge)", re.I), "Fast charging support"),
        (re.compile(r"(all.day|long battery|great battery|excellent battery|lasts)", re.I), "Long battery life"),
        (re.compile(r"(smooth|buttery|fluid|snappy) (performance|experience|gaming)", re.I), "Smooth performance"),
        (re.compile(r"(crystal clear|immersive|premium|fantastic) (audio|sound|bass)", re.I), "Premium audio quality"),
        (re.compile(r"(1.day|next.day|super fast|lightning|quick) (delivery|shipping)", re.I), "Super-fast delivery"),
        (re.compile(r"(premium|solid|great) (build|quality|finish)", re.I), "Premium build quality"),
        (re.compile(r"(worth every|value for money|highly recommend|love it|best purchase)", re.I), "Great value for money"),
        (re.compile(r"(noise cancellation|anc).{0,30}(impressive|excellent|great|works)", re.I), "Effective noise cancellation"),
    ]

    def analyze_customer_feedback(self, review_text: str, rating: int) -> dict[str, Any]:
        text = review_text or ""
        lower = text.lower()

        topic_hits: dict[str, int] = {}
        for topic, keywords in self.TOPICS:
            topic_hits[topic] = sum(1 for kw in keywords if kw in lower)
        primary_topic = max(topic_hits, key=topic_hits.get) if any(topic_hits.values()) else "General"

        issues = [label for pattern, label in self.NEGATIVE_SIGNALS if pattern.search(lower)]
        positives = [label for pattern, label in self.POSITIVE_SIGNALS if pattern.search(lower)]

        has_neg = bool(issues)
        has_pos = bool(positives)

        if rating >= 4:
            sentiment = "Positive"
        elif rating <= 2:
            sentiment = "Negative"
        else:
            sentiment = "Mixed" if has_neg and has_pos else "Neutral"

        if sentiment == "Positive":
            emotion = "Delighted" if rating >= 5 else "Satisfied"
        elif sentiment == "Negative":
            emotion = "Frustrated" if rating <= 1 else "Disappointed"
        else:
            emotion = "Neutral"

        signal_count = len(issues) + len(positives)
        confidence = min(0.99, 0.5 + 0.08 * signal_count) if signal_count else 0.42
        if rating in (1, 5):
            confidence = min(0.99, confidence + 0.05)

        return {
            "sentiment": sentiment,
            "emotion": emotion,
            "primary_topic": primary_topic,
            "specific_issues": issues,
            "positive_aspects": positives,
            "confidence_score": round(confidence, 2),
            "source": "REVIEW",
        }

    # ------------------------------------------- Admin BI answers
    def answer_admin_query(self, question: str, context: dict[str, Any]) -> str:
        q = (question or "").lower()
        revenue = context.get("total_revenue", 0)
        orders = context.get("order_count", 0)
        users = context.get("user_count", 0)
        sentiment = context.get("sentiment_distribution", {})
        neg_topics = context.get("top_negative_topics", [])
        churn = context.get("churn_risk_signals", [])
        top_products = context.get("top_products_by_revenue", [])
        low_stock = context.get("low_stock_products", [])

        lines: list[str] = []

        if any(k in q for k in ("sentiment", "feedback", "review", "customer say", "complain", "complaint", "negative", "issues", "problem")):
            lines.append(
                f"Sentiment analysis across {sum(sentiment.values() or [0])} reviews: "
                f"Positive {sentiment.get('Positive', 0)}, Negative {sentiment.get('Negative', 0)}, "
                f"Mixed {sentiment.get('Mixed', 0)}, Neutral {sentiment.get('Neutral', 0)}."
            )
            if neg_topics:
                worst = neg_topics[0]
                lines.append(
                    f"Customers complain most about {worst.get('topic', 'General')} "
                    f"({worst.get('count', 0)} negative reviews) - priority fix area."
                )
            else:
                lines.append("No significant negative feedback clusters detected.")
        elif any(k in q for k in ("revenue", "sales", "order", "income", "profit", "earning")):
            lines.append(
                f"Total revenue is ₹{revenue:,.2f} from {orders} orders "
                f"with {users} registered users."
            )
            if top_products:
                lines.append(f"Top revenue driver: {top_products[0].get('name')} (₹{top_products[0].get('revenue', 0):,.2f}).")
        elif any(k in q for k in ("stock", "inventory", "low stock", "restock")):
            if low_stock:
                names = ", ".join(p.get("name", "") for p in low_stock[:5])
                lines.append(f"{len(low_stock)} products are at or below low-stock threshold: {names}.")
            else:
                lines.append("All products are above low-stock thresholds.")
        elif any(k in q for k in ("churn", "at risk", "losing customer")):
            if churn:
                lines.append(
                    f"{len(churn)} users show churn risk signals from negative delivery/quality feedback. "
                    f"Recommended: priority outreach and issue remediation."
                )
            else:
                lines.append("No churn risk signals currently detected.")
        else:
            lines.append(
                f"OmniMart AI store health: ₹{revenue:,.2f} revenue from {orders} orders, "
                f"{users} users, {context.get('product_count', 0)} live products."
            )
            lines.append(
                f"Customer satisfaction is {sentiment.get('Positive', 0) / max(1, sum(sentiment.values() or [1])) * 100:.1f}% positive."
            )
            if neg_topics:
                lines.append(f"Watch out for {neg_topics[0].get('topic', 'General')} complaints.")

        return " ".join(lines)

    # ------------------------------------------- Grounded chat fallback
    def chat_reply(self, intent: str, tool_facts: dict[str, Any], history: list[dict]) -> AIResult:
        products = tool_facts.get("products", [])
        comparison = tool_facts if "spec_matrix" in tool_facts else tool_facts.get("comparison")
        feedback = tool_facts if "sentiment_distribution" in tool_facts else tool_facts.get("feedback")
        product = tool_facts.get("product")
        greeting_user = tool_facts.get("user_name")

        if intent == "greeting":
            text = f"Hello{', ' + greeting_user if greeting_user else ''}! I'm OmniMart AI - your shopping assistant. Ask me for product recommendations, price comparisons, or customer feedback insights."
            return AIResult(text=text, provider=self.name, model="deterministic", follow_up_suggestions=[
                "Recommend a flagship phone under ₹60,000",
                "Compare iPhone 16 Pro Max vs Galaxy S25 Ultra",
                "What do customers say about battery life?",
            ])

        if intent == "compare" and comparison:
            names = [p["name"] for p in comparison.get("products", [])]
            text = f"Here's a head-to-head comparison of {', '.join(names)}. "
            text += comparison.get("ai_verdict") or comparison.get("verdict") or ""
            return AIResult(text=text, provider=self.name, model="deterministic", follow_up_suggestions=[
                "Which one has better value for money?",
                "Show me customer reviews for the winner",
            ])

        if intent == "feedback" and feedback:
            text = (
                f"Customer feedback for {feedback.get('product_name', 'this product')}: "
                f"average rating {feedback.get('avg_rating', 0):.1f}★ across {feedback.get('total', 0)} reviews. "
                f"Sentiment split: {feedback.get('sentiment_distribution', {})}. "
            )
            topics = feedback.get("topic_breakdown", [])
            if topics:
                text += "Main topics: " + "; ".join(f"{t['topic']} ({t['count']})" for t in topics[:4]) + ". "
            issues = feedback.get("top_issues", [])
            if issues:
                text += "Common complaints: " + "; ".join(str(i.get("issue", i)) for i in issues[:3]) + ". "
            pos = feedback.get("top_positives", [])
            if pos:
                text += "Highlights: " + "; ".join(str(p.get("aspect", p)) for p in pos[:3]) + "."
            return AIResult(text=text, provider=self.name, model="deterministic", follow_up_suggestions=[
                "Recommend a product with better reviews",
                "Compare this with an alternative",
            ])

        if intent == "product_info" and product:
            p = product
            text = (
                f"{p['name']} - ₹{p['price']:,.0f} (MRP ₹{p['original_price']:,.0f}, "
                f"{p['discount_percentage']:.0f}% off), rated {p['rating']}★ by {p['review_count']} customers. "
                f"Currently {p['stock']} units in stock. "
            )
            if p.get("top_specs"):
                text += "Key specs: " + "; ".join(f"{k}: {v}" for k, v in p["top_specs"]) + "."
            return AIResult(text=text, provider=self.name, model="deterministic", follow_up_suggestions=[
                "What do customers say about it?",
                "Compare it with a similar product",
                "Add it to my cart",
            ])

        # Default: recommend
        if products:
            text = "Based on your request, here are the best matches:\n"
            for i, p in enumerate(products[:5], start=1):
                badge = f" ({p.get('why_recommended')})" if p.get("why_recommended") else ""
                text += f"{i}. {p['name']} - ₹{p['price']:,.0f}, {p['rating']}★ ({p['review_count']} reviews){badge}\n"
            text += "Want me to compare any two of these, or check customer feedback for a specific one?"
            return AIResult(text=text, provider=self.name, model="deterministic", follow_up_suggestions=[
                "Compare the top two",
                "What do customers say about the first one?",
                "Show me cheaper alternatives",
            ])

        return AIResult(
            text="I couldn't find matching products in our catalog. Try rephrasing with a category, brand, or budget.",
            provider=self.name,
            model="deterministic",
        )

    async def chat(self, system: str, messages: list[dict], temperature: float = 0.4) -> AIResult:
        raise RuntimeError("MockAIProvider.chat is not used directly; use chat_reply with grounded facts.")

    # ------------------------------------------- Comparison verdict
    def comparison_verdict(self, products: list[dict], matrix_rows: list[dict]) -> str:
        if len(products) < 2:
            return "Add at least two products to compare."
        best_rating = max(products, key=lambda p: p["rating"])
        best_value = min(products, key=lambda p: (p["price"] / max(p["rating"], 0.1)))
        parts = [
            f"{best_rating['name']} leads on customer rating ({best_rating['rating']}★ vs {max(p['rating'] for p in products):.1f}★ max).",
        ]
        if best_value["id"] != best_rating["id"]:
            parts.append(
                f"{best_value['name']} offers the best rating-to-price value at ₹{best_value['price']:,.0f}."
            )
        parts.append(
            f"Price spread is {min(p['price'] for p in products):,.0f} to {max(p['price'] for p in products):,.0f}."
        )
        return " ".join(parts)

    def follow_ups_for_intent(self, intent: str) -> list[str]:
        return {
            "greeting": ["Recommend a flagship phone under ₹60,000", "Best laptops for gaming"],
            "recommend": ["Compare the top two", "What do customers say about battery?"],
            "compare": ["Which is better for gaming?", "Show customer reviews for the winner"],
            "feedback": ["Recommend an alternative", "Show me the specs"],
            "product_info": ["Add it to my cart", "Check stock"],
        }.get(intent, ["Recommend more products", "Compare options"])


# --------------------------------------------------------------------------
# Admin analytics context builder (kept here so mock BI answers are self-contained)
# --------------------------------------------------------------------------
def build_admin_context(db, schemas: dict[str, Any]) -> dict[str, Any]:
    return schemas
"""Customer feedback sentiment & emotion NLP + aggregated analytics."""
from __future__ import annotations

import json
import re
from collections import defaultdict

from sqlalchemy import desc, func
from sqlalchemy.orm import Session

from ..models import CustomerFeedback, Product, Review, User

SENTIMENT_GROUPS = ["Positive", "Negative", "Mixed", "Neutral"]
TOPIC_GROUPS = ["Battery", "Display", "Camera", "Performance", "Delivery", "Audio", "Build Quality", "General"]

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


def analyze_customer_feedback(review_text: str, rating: int) -> dict:
    """Deterministic sentiment/emotion/topic classification from review text."""
    text = review_text or ""
    lower = text.lower()

    topic_hits: dict[str, int] = {}
    for topic, keywords in TOPICS:
        topic_hits[topic] = sum(1 for kw in keywords if kw in lower)
    primary_topic = max(topic_hits, key=topic_hits.get) if any(topic_hits.values()) else "General"

    issues = [label for pattern, label in NEGATIVE_SIGNALS if pattern.search(lower)]
    positives = [label for pattern, label in POSITIVE_SIGNALS if pattern.search(lower)]

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


class CustomerFeedbackService:
    def __init__(self, db: Session):
        self.db = db

    # ------------------------------------------------------------ analysis
    def analyze_review(self, review: Review) -> CustomerFeedback:
        analysis = analyze_customer_feedback(review.comment or "", review.rating)
        fb = (
            self.db.query(CustomerFeedback).filter(CustomerFeedback.review_id == review.id).first()
        )
        if fb is None:
            fb = CustomerFeedback(review_id=review.id, product_id=review.product_id, user_id=review.user_id)
            self.db.add(fb)
        fb.sentiment = analysis["sentiment"]
        fb.emotion = analysis["emotion"]
        fb.primary_topic = analysis["primary_topic"]
        fb.specific_issues_json = json.dumps(analysis["specific_issues"])
        fb.positive_aspects_json = json.dumps(analysis["positive_aspects"])
        fb.confidence_score = analysis["confidence_score"]
        fb.source = analysis["source"]
        self.db.flush()
        return fb

    def analyze_all_reviews(self) -> int:
        reviews = self.db.query(Review).all()
        count = 0
        for r in reviews:
            self.analyze_review(r)
            count += 1
        return count

    def analyze_review_text(self, text: str, rating: int) -> dict:
        return analyze_customer_feedback(text, rating)

    # ------------------------------------------------------------ summaries
    def product_feedback_summary(self, product_id: int) -> dict:
        feedbacks = (
            self.db.query(CustomerFeedback)
            .filter(CustomerFeedback.product_id == product_id)
            .all()
        )
        product = self.db.get(Product, product_id)
        sentiment_dist: dict[str, int] = defaultdict(int)
        topic_breakdown: dict[str, int] = defaultdict(int)
        issues: dict[str, int] = defaultdict(int)
        positives: dict[str, int] = defaultdict(int)

        for fb in feedbacks:
            sentiment_dist[fb.sentiment or "Neutral"] += 1
            topic_breakdown[fb.primary_topic or "General"] += 1
            for i in json.loads(fb.specific_issues_json or "[]"):
                issues[i] += 1
            for p in json.loads(fb.positive_aspects_json or "[]"):
                positives[p] += 1

        avg_rating = (
            self.db.query(func.avg(Review.rating))
            .filter(Review.product_id == product_id)
            .scalar()
            or 0
        )

        return {
            "product_id": product_id,
            "product_name": product.name if product else "",
            "total": len(feedbacks),
            "avg_rating": round(float(avg_rating), 1),
            "sentiment_distribution": dict(sentiment_dist),
            "topic_breakdown": [{"topic": t, "count": c} for t, c in sorted(topic_breakdown.items(), key=lambda kv: kv[1], reverse=True)],
            "top_issues": [{"issue": k, "count": v} for k, v in sorted(issues.items(), key=lambda kv: kv[1], reverse=True)[:6]],
            "top_positives": [{"aspect": k, "count": v} for k, v in sorted(positives.items(), key=lambda kv: kv[1], reverse=True)[:6]],
        }

    # ------------------------------------------------------------ admin analytics
    def sentiment_distribution(self) -> dict[str, int]:
        rows = (
            self.db.query(CustomerFeedback.sentiment, func.count(CustomerFeedback.id))
            .group_by(CustomerFeedback.sentiment)
            .all()
        )
        dist = {g: 0 for g in SENTIMENT_GROUPS}
        for sent, count in rows:
            dist[sent or "Neutral"] = count
        return dist

    def count_negative_issues_by_topic(self) -> list[dict]:
        rows = (
            self.db.query(CustomerFeedback.primary_topic, func.count(CustomerFeedback.id))
            .filter(CustomerFeedback.sentiment == "Negative")
            .group_by(CustomerFeedback.primary_topic)
            .order_by(desc(func.count(CustomerFeedback.id)))
            .all()
        )
        return [{"topic": t or "General", "count": c} for t, c in rows]

    def products_with_most_negative_feedback(self, limit: int = 6) -> list[dict]:
        rows = (
            self.db.query(
                Product.id,
                Product.name,
                func.count(CustomerFeedback.id).label("neg_count"),
            )
            .join(CustomerFeedback, CustomerFeedback.product_id == Product.id)
            .filter(CustomerFeedback.sentiment == "Negative")
            .group_by(Product.id, Product.name)
            .order_by(desc("neg_count"))
            .limit(limit)
            .all()
        )
        return [{"product_id": r.id, "name": r.name, "negative_reviews": r.neg_count} for r in rows]

    def churn_risk_signals(self, limit: int = 8) -> list[dict]:
        """Users with 2+ negative reviews (delivery/quality) -> churn risk."""
        rows = (
            self.db.query(
                CustomerFeedback.user_id,
                func.count(CustomerFeedback.id).label("neg_count"),
            )
            .filter(
                CustomerFeedback.sentiment == "Negative",
                CustomerFeedback.user_id.isnot(None),
            )
            .group_by(CustomerFeedback.user_id)
            .having(func.count(CustomerFeedback.id) >= 2)
            .order_by(desc("neg_count"))
            .limit(limit)
            .all()
        )
        signals = []
        for r in rows:
            u = self.db.get(User, r.user_id)
            signals.append(
                {
                    "user_id": r.user_id,
                    "email": u.email if u else None,
                    "name": u.full_name if u else None,
                    "negative_reviews": r.neg_count,
                    "signal": "Repeat negative feedback - outreach recommended",
                }
            )
        return signals
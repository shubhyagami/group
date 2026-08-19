"""Customer feedback sentiment & emotion NLP + aggregated analytics."""
from __future__ import annotations

import json
from collections import defaultdict

from sqlalchemy import desc, func
from sqlalchemy.orm import Session

from ..ai.mock import MockAIProvider
from ..models import CustomerFeedback, Product, Review, User

SENTIMENT_GROUPS = ["Positive", "Negative", "Mixed", "Neutral"]
TOPIC_GROUPS = ["Battery", "Display", "Camera", "Performance", "Delivery", "Audio", "Build Quality", "General"]


class CustomerFeedbackService:
    def __init__(self, db: Session):
        self.db = db
        self.nlp = MockAIProvider()

    # ------------------------------------------------------------ analysis
    def analyze_review(self, review: Review) -> CustomerFeedback:
        analysis = self.nlp.analyze_customer_feedback(review.comment or "", review.rating)
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
        return self.nlp.analyze_customer_feedback(text, rating)

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
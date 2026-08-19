"""Admin analytics & executive AI Q&A (ADMIN role only)."""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import func
from sqlalchemy.orm import Session

from ..ai.mock import MockAIProvider
from ..database import get_db
from ..models import Order, OrderItem, Product, User
from ..schemas import (
    AdminAnalyticsDto,
    AdminAskRequest,
    AdminAskResponse,
    OrderDto,
    ProductCardDto,
)
from ..security import get_current_admin
from ..services.feedback_service import CustomerFeedbackService
from ..services.order_service import OrderService
from ..services.product_cards import product_to_card

router = APIRouter(prefix="/api/admin", tags=["admin"])


def _build_analytics(db: Session) -> dict:
    # Revenue
    revenue_row = (
        db.query(func.coalesce(func.sum(Order.final_amount), 0))
        .filter(Order.status.notin_(["CANCELLED", "RETURNED"]))
        .scalar()
    )
    order_count = db.query(func.count(Order.id)).scalar() or 0
    user_count = db.query(func.count(User.id)).scalar() or 0
    product_count = db.query(func.count(Product.id)).filter(Product.active.is_(True)).scalar() or 0

    status_rows = db.query(Order.status, func.count(Order.id)).group_by(Order.status).all()
    status_dist = {s or "PENDING": c for s, c in status_rows}

    feedback = CustomerFeedbackService(db)
    sentiment_dist = feedback.sentiment_distribution()
    top_negative_topics = feedback.count_negative_issues_by_topic()
    most_negative = feedback.products_with_most_negative_feedback()
    churn = feedback.churn_risk_signals()

    top_products_rows = (
        db.query(Product.name, func.sum(OrderItem.total_price).label("revenue"))
        .join(OrderItem, OrderItem.product_id == Product.id)
        .join(Order, Order.id == OrderItem.order_id)
        .filter(Order.status.notin_(["CANCELLED", "RETURNED"]))
        .group_by(Product.id, Product.name)
        .order_by(func.sum(OrderItem.total_price).desc())
        .limit(6)
        .all()
    )
    top_products = [{"name": r.name, "revenue": round(float(r.revenue or 0), 2)} for r in top_products_rows]

    recent = (
        db.query(Order)
        .order_by(Order.created_at.desc())
        .limit(10)
        .all()
    )
    order_svc = OrderService(db)
    recent_orders = [OrderDto(**order_svc.order_to_dict(o)) for o in recent]

    low_stock = (
        db.query(Product)
        .filter(Product.active.is_(True), Product.stock <= 5)
        .order_by(Product.stock.asc())
        .limit(10)
        .all()
    )

    return {
        "total_revenue": round(float(revenue_row or 0), 2),
        "order_count": order_count,
        "user_count": user_count,
        "product_count": product_count,
        "avg_order_value": round(float(revenue_row or 0) / max(1, order_count), 2),
        "order_status_distribution": status_dist,
        "sentiment_distribution": sentiment_dist,
        "top_negative_topics": top_negative_topics,
        "products_with_most_negative_feedback": most_negative,
        "top_products_by_revenue": top_products,
        "churn_risk_signals": churn,
        "recent_orders": recent_orders,
        "low_stock_products": [ProductCardDto(**product_to_card(p)) for p in low_stock],
    }


@router.get("/analytics-data", response_model=AdminAnalyticsDto)
def analytics_data(db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    return AdminAnalyticsDto(**_build_analytics(db))


@router.post("/ask-ai", response_model=AdminAskResponse)
def ask_ai(payload: AdminAskRequest, db: Session = Depends(get_db), admin: User = Depends(get_current_admin)):
    context = _build_analytics(db)
    context_slim = {
        "total_revenue": context["total_revenue"],
        "order_count": context["order_count"],
        "user_count": context["user_count"],
        "product_count": context["product_count"],
        "sentiment_distribution": context["sentiment_distribution"],
        "top_negative_topics": context["top_negative_topics"],
        "churn_risk_signals": context["churn_risk_signals"],
        "top_products_by_revenue": context["top_products_by_revenue"],
        "low_stock_products": [{"name": p.name, "stock": p.stock} for p in context["low_stock_products"]],
    }
    mock = MockAIProvider()
    answer = mock.answer_admin_query(payload.question, context_slim)
    return AdminAskResponse(answer=answer, provider="mock (deterministic BI)", context_tokens=len(str(context_slim)))
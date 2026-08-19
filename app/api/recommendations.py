"""Personalized recommendations endpoint."""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import ProductCardDto, RecommendationResponse
from ..security import get_optional_user
from ..services.product_cards import product_to_card
from ..services.recommendation_service import HybridRecommendationService

router = APIRouter(prefix="/api/recommendations", tags=["recommendations"])


@router.get("", response_model=RecommendationResponse)
def recommendations(
    limit: int = Query(8, ge=1, le=24),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    svc = HybridRecommendationService(db)
    if user and user.user_preference is not None and not user.user_preference.recommendations_enabled:
        results = svc.popularity_based(limit)
        strategy = "popularity (user opted out)"
    elif user:
        results = svc.recommend_for_user(user, limit)
        strategy = "hybrid (preferences+behavior+content+rating+popularity)"
    else:
        results = svc.popularity_based(limit)
        strategy = "popularity (anonymous)"
    return RecommendationResponse(
        products=[ProductCardDto(**product_to_card(p, why)) for p, why in results],
        strategy=strategy,
    )
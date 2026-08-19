"""Real-time behavioral telemetry ingestion."""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import InteractionRequest
from ..security import get_optional_user
from ..services.telemetry_service import TelemetryService

router = APIRouter(prefix="/api/telemetry", tags=["telemetry"])


@router.post("/interaction")
def record_interaction(
    payload: InteractionRequest,
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    TelemetryService(db).record_interaction(
        interaction_type=payload.interaction_type,
        user=user,
        session_id=payload.session_id,
        product_id=payload.product_id,
        category_name=payload.category_name,
        brand_name=payload.brand_name,
        search_query=payload.search_query,
        duration_seconds=payload.duration_seconds,
    )
    db.commit()
    return {"message": "Interaction recorded"}
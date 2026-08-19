"""Multi-product hardware comparison API."""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Cookie, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import ProductComparisonDto
from ..security import get_optional_user
from ..services.comparison_service import ComparisonService
from ..services.telemetry_service import TelemetryService

router = APIRouter(prefix="/api/compare", tags=["compare"])


@router.get("/data", response_model=ProductComparisonDto)
def compare_data(
    ids: str = Query(..., description="comma-separated product ids, e.g. 1,2,3"),
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    try:
        product_ids = [int(x) for x in ids.split(",") if x.strip().isdigit()]
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid ids format")
    if len(product_ids) < 2:
        raise HTTPException(status_code=400, detail="Provide at least two product ids to compare")
    if len(product_ids) > 5:
        raise HTTPException(status_code=400, detail="Compare at most 5 products")

    TelemetryService(db).record_interaction("PRODUCT_COMPARE", user=user, session_id=cart_session or uuid.uuid4().hex)
    db.commit()
    return ProductComparisonDto(**ComparisonService(db).build_comparison(product_ids))
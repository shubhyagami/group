"""Search: autocomplete + natural-language search with spec extraction."""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Cookie, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Brand, Category, User
from ..schemas import (
    AutocompleteResponse,
    NlSearchResponse,
    ParsedSearchDto,
    ProductCardDto,
)
from ..security import get_optional_user
from ..services.product_cards import product_to_card
from ..services.search_service import SearchService
from ..services.telemetry_service import TelemetryService

router = APIRouter(prefix="/api/search", tags=["search"])


@router.get("/autocomplete", response_model=AutocompleteResponse)
def autocomplete(q: str = Query("", max_length=120), db: Session = Depends(get_db)):
    return AutocompleteResponse(**SearchService(db).autocomplete(q))


@router.post("/nl", response_model=NlSearchResponse)
def natural_language_search(
    body: dict,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    query = (body.get("query") or "").strip()
    if not query:
        raise HTTPException(status_code=400, detail="query is required")

    svc = SearchService(db)
    parsed = svc.parse_nl(query)

    # category/brand are precise filters; raw text is a soft signal when present
    text_query = parsed.get("query")
    if parsed.get("category") or parsed.get("brand"):
        text_query = None

    products, total = svc.apply_filters(
        query=text_query,
        min_price=parsed.get("min_price"),
        max_price=parsed.get("max_price"),
        min_rating=parsed.get("min_rating"),
        tags=parsed.get("tags"),
        page_size=200,
        in_stock_only=True,
    )
    if parsed.get("category"):
        products = [p for p in products if p.category and p.category.name == parsed["category"]]
    if parsed.get("brand"):
        products = [p for p in products if p.brand and p.brand.name.lower() == parsed["brand"].lower()]

    TelemetryService(db).record_search(query, len(products), user, cart_session or uuid.uuid4().hex)
    db.commit()

    hint_parts = []
    if parsed.get("category"):
        hint_parts.append(f"category: {parsed['category']}")
    if parsed.get("brand"):
        hint_parts.append(f"brand: {parsed['brand']}")
    if parsed.get("max_price"):
        hint_parts.append(f"budget: ≤ ₹{parsed['max_price']:,.0f}")
    if parsed.get("min_rating"):
        hint_parts.append(f"rating: ≥ {parsed['min_rating']}★")
    if parsed.get("tags"):
        hint_parts.append("tags: " + ", ".join(parsed["tags"]))

    return NlSearchResponse(
        parsed=ParsedSearchDto(**parsed),
        products=[ProductCardDto(**product_to_card(p)) for p in products],
        total=len(products),
        filters_hint=" • ".join(hint_parts) if hint_parts else "No filters extracted",
    )
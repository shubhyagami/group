"""Search: autocomplete + natural-language search with spec extraction."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..schemas import AutocompleteResponse, ProductCardDto
from ..services.product_cards import product_to_card
from ..services.search_service import SearchService

router = APIRouter(prefix="/api/search", tags=["search"])


@router.get("/autocomplete", response_model=AutocompleteResponse)
def autocomplete(q: str = Query("", max_length=120), db: Session = Depends(get_db)):
    return AutocompleteResponse(**SearchService(db).autocomplete(q))
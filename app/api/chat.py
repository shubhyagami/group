"""Conversational AI assistant endpoint (public, session-scoped)."""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Cookie, Depends, Request
from sqlalchemy.orm import Session

from ..ai.orchestrator import AIOrchestrator
from ..database import get_db
from ..models import User
from ..schemas import ChatRequest, ChatResponse
from ..security import get_optional_user

router = APIRouter(prefix="/api/chat", tags=["chat"])


@router.post("", response_model=ChatResponse)
async def chat(
    payload: ChatRequest,
    request: Request,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    session_id = cart_session or uuid.uuid4().hex
    db.expunge_all()
    orchestrator = AIOrchestrator(db)
    result = await orchestrator.handle_message(
        message=payload.message,
        conversation_id=payload.conversation_id,
        current_product_id=payload.current_product_id,
        user=user,
        session_id=session_id,
    )
    return ChatResponse(**result)
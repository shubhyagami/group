"""Behavioral telemetry collector: clickstream events -> user interactions."""
from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from ..models import SearchHistory, User, UserInteraction


def generate_session_id() -> str:
    return uuid.uuid4().hex


class TelemetryService:
    def __init__(self, db: Session):
        self.db = db

    def record_interaction(
        self,
        interaction_type: str,
        user: User | None = None,
        session_id: str | None = None,
        product_id: int | None = None,
        category_name: str | None = None,
        brand_name: str | None = None,
        search_query: str | None = None,
        duration_seconds: int = 0,
    ) -> UserInteraction:
        interaction = UserInteraction(
            user_id=user.id if user else None,
            session_id=session_id or generate_session_id(),
            interaction_type=interaction_type,
            product_id=product_id,
            category_name=category_name,
            brand_name=brand_name,
            search_query=search_query,
            duration_seconds=duration_seconds,
        )
        self.db.add(interaction)
        if interaction_type == "SEARCH" and search_query:
            self.db.add(
                SearchHistory(
                    user_id=user.id if user else None,
                    session_id=session_id,
                    query=search_query,
                    result_count=0,
                )
            )
        self.db.flush()
        return interaction

    def record_search(self, query: str, result_count: int, user: User | None = None, session_id: str | None = None) -> None:
        self.db.add(
            SearchHistory(
                user_id=user.id if user else None,
                session_id=session_id,
                query=query,
                result_count=result_count,
            )
        )
        self.db.flush()
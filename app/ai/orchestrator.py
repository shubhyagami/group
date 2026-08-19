"""Autonomous multi-turn conversational AI orchestrator.

Pipeline: intent detection -> safe DB tool routing (grounded facts) ->
provider chain (NVIDIA multi-key -> Local -> Mock) -> persistence of
conversation + audit logs.
"""
from __future__ import annotations

import json
import logging
import re
import time
import uuid

from sqlalchemy.orm import Session

from ..config import settings
from ..models import AIRecommendationLog, ChatConversation, ChatMessage, User
from .local import LocalAIProvider
from .mock import MockAIProvider
from .nvidia import NvidiaAIProvider
from .tool_router import ToolRouter

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are OmniMart AI, the autonomous shopping assistant of an AI-powered e-commerce platform.
You answer ONLY from the verified store facts provided in the context below. Never invent prices, ratings, stock,
or products that are not listed. Be concise, friendly and specific. When products are listed, refer to them by name
and price. If the user asks something outside the store, politely redirect to products."""
 

def detect_intent(message: str) -> str:
    lower = (message or "").lower()
    if any(k in lower for k in ["compare", "difference between", " vs ", " versus ", "better between"]):
        return "compare"
    if any(k in lower for k in ["recommend", "suggest", "best ", "find me", "show me", "buy ", "looking for", "under ", "within ", "budget", "want"]):
        return "recommend"
    if any(k in lower for k in ["review", "customer", "feedback", "complaint", "say about", "issues with", "battery of", "camera of", "worth it"]):
        return "feedback"
    if any(k in lower for k in ["price", "cost", "stock", "available", "details", "specs", "specification", "what is", "tell me about"]):
        return "product_info"
    if any(k in lower for k in ["hello", " hi", "hey", "thanks", "thank you", "who are you"]):
        return "greeting"
    return "recommend"


def _extract_reference_ids(text: str) -> list[int]:
    return [int(m) for m in re.findall(r"#(\d+)", text or "")]


class AIOrchestrator:
    def __init__(self, db: Session):
        self.db = db
        self.mock = MockAIProvider()
        self.tools = ToolRouter(db)

    # ------------------------------------------------------- conversation
    def _get_conversation(self, conversation_id: str | None, user: User | None, session_id: str) -> ChatConversation:
        if conversation_id:
            conv = (
                self.db.query(ChatConversation)
                .filter(ChatConversation.conversation_id == conversation_id)
                .first()
            )
            if conv:
                return conv
        conv = ChatConversation(
            conversation_id=conversation_id or uuid.uuid4().hex,
            user_id=user.id if user else None,
            session_id=session_id,
        )
        self.db.add(conv)
        self.db.flush()
        return conv

    def _history(self, conv: ChatConversation, limit: int = 6) -> list[dict]:
        messages = conv.messages[-limit:]
        return [{"role": "assistant" if m.sender == "ASSISTANT" else "user", "content": m.content} for m in messages]

    # ------------------------------------------------------- provider chain
    def _provider_chain(self) -> list:
        chain = []
        preferred = (settings.AI_PROVIDER or "nvidia").lower()
        if preferred == "nvidia":
            chain.append(NvidiaAIProvider())
            chain.append(LocalAIProvider())
        elif preferred == "local":
            chain.append(LocalAIProvider())
            chain.append(NvidiaAIProvider())
        else:
            chain.append(NvidiaAIProvider())
            chain.append(LocalAIProvider())
        return chain

    # ------------------------------------------------------- main handler
    async def handle_message(
        self,
        message: str,
        conversation_id: str | None,
        current_product_id: int | None,
        user: User | None,
        session_id: str,
    ) -> dict:
        start = time.monotonic()
        conv = self._get_conversation(conversation_id, user, session_id)
        if not conv.title:
            conv.title = message[:60]

        self.db.add(ChatMessage(conversation_id=conv.id, sender="USER", content=message))
        self.db.flush()

        intent = detect_intent(message)
        tool_facts: dict = {}
        tool_used: str | None = None
        candidate_ids: list[int] = []

        # ---- multi-turn context: product refs from previous assistant turns
        prev_ids: list[int] = []
        prev_assistant = (
            self.db.query(ChatMessage)
            .filter(ChatMessage.conversation_id == conv.id, ChatMessage.sender == "ASSISTANT")
            .order_by(ChatMessage.id.desc())
            .first()
        )
        if prev_assistant and prev_assistant.recommended_product_ids_json:
            try:
                prev_ids = json.loads(prev_assistant.recommended_product_ids_json) or []
            except json.JSONDecodeError:
                prev_ids = []

        # ---- tool routing
        if intent == "compare":
            tool_used = "compareProducts"
            ref_ids = _extract_reference_ids(message)
            if not ref_ids and prev_ids and re.search(r"(top|first|second|two|those|them|above|previous|compare)", message.lower()):
                ref_ids = prev_ids[:2]
            if not ref_ids and current_product_id and prev_ids:
                ref_ids = [prev_ids[0], current_product_id]
            if not ref_ids and current_product_id:
                ref_ids = [current_product_id]
            if not ref_ids and prev_ids:
                ref_ids = prev_ids[:2]
            tool_facts = self.tools.compare_products(ref_ids)
            candidate_ids = [p["id"] for p in tool_facts.get("products", [])]

        elif intent == "feedback":
            tool_used = "getProductFeedbackSummary"
            target_id = current_product_id
            if not target_id:
                refs = _extract_reference_ids(message) or prev_ids
                target_id = refs[0] if refs else None
            if target_id:
                tool_facts = self.tools.get_product_feedback_summary(target_id)
                candidate_ids = [target_id]

        elif intent == "product_info":
            tool_used = "getProductInfo"
            target_id = current_product_id
            if not target_id:
                refs = _extract_reference_ids(message) or prev_ids
                target_id = refs[0] if refs else None
            if target_id:
                tool_facts = {"product": self.tools.get_product_info(target_id)}
                candidate_ids = [target_id]

        else:  # recommend / greeting
            tool_used = "searchProducts" if intent == "recommend" else "getRecommendedProducts"
            if intent == "recommend":
                parsed = self.mock.parse_natural_language_search(message)
                tool_facts = self.tools.search_products(parsed, limit=8)
            else:
                tool_facts = self.tools.get_recommended_products(user, limit=8)
            candidate_ids = [p["id"] for p in tool_facts.get("products", [])]

        # ---- build grounded prompt facts
        facts = self._format_facts(intent, tool_facts)
        history = self._history(conv, limit=6)
        user_msg = {
            "role": "user",
            "content": f"User message: {message}\n\nVerified store facts:\n{facts}\n\n"
            f"Current page product: {current_product_id or 'none'}",
        }

        # ---- provider chain with graceful failover
        used_provider = "mock"
        reply_text = ""
        follow_ups = self.mock.follow_ups_for_intent(intent)
        errors: list[str] = []

        grounded_product_count = len(tool_facts.get("products", []))

        for provider in self._provider_chain():
            try:
                result = await provider.chat(SYSTEM_PROMPT, history[-4:] + [user_msg])
                reply_text = result.text
                used_provider = result.provider
                break
            except Exception as exc:  # noqa: BLE001 - provider failover is expected
                errors.append(f"{provider.name}: {exc}")
                logger.warning("Provider %s failed for chat: %s", provider.name, exc)

        # Zero-hallucination guard: if verified facts contain products but the
        # model claims there are none, replace with the grounded deterministic reply.
        if reply_text and grounded_product_count > 0 and re.search(
            r"(couldn'?t find|no (matching|matching products|products|such)|don'?t (have|currently have)|not available)",
            reply_text,
            re.IGNORECASE,
        ):
            logger.info("Model reply contradicted grounded facts - using deterministic reply")
            used_provider = "mock"

        if not reply_text or used_provider == "mock":
            mock_result = self.mock.chat_reply(intent, tool_facts, history)
            reply_text = mock_result.text
            if mock_result.follow_up_suggestions:
                follow_ups = mock_result.follow_up_suggestions
            if errors:
                logger.info("Fell back to MockAIProvider after errors: %s", "; ".join(errors))

        # ---- persist assistant turn
        self.db.add(
            ChatMessage(
                conversation_id=conv.id,
                sender="ASSISTANT",
                content=reply_text,
                tool_calls_json=json.dumps({"intent": intent, "tool": tool_used, "errors": errors}),
                recommended_product_ids_json=json.dumps(candidate_ids),
                reasoning_summary=f"Intent '{intent}' routed to tool '{tool_used}' using provider '{used_provider}'.",
            )
        )
        self.db.flush()

        # ---- audit log
        self.db.add(
            AIRecommendationLog(
                user_id=user.id if user else None,
                query_text=message,
                tool_used=tool_used,
                product_ids_json=json.dumps(candidate_ids),
                generated_reasoning=facts[:2000],
                provider_used=used_provider,
                execution_time_ms=int((time.monotonic() - start) * 1000),
            )
        )
        self.db.commit()

        return {
            "message": reply_text,
            "conversation_id": conv.conversation_id,
            "candidate_products": tool_facts.get("products", []),
            "reasoning_summary": f"Intent '{intent}' -> tool '{tool_used}' -> provider '{used_provider}'.",
            "follow_up_suggestions": follow_ups[:4],
            "tool_used": tool_used,
            "active_provider": used_provider,
        }

    # ------------------------------------------------------- fact formatting
    @staticmethod
    def _format_facts(intent: str, tool_facts: dict) -> str:
        if intent == "compare" and tool_facts.get("products"):
            lines = []
            for p in tool_facts["products"]:
                lines.append(
                    f"- {p['name']} | ₹{p['price']:,.0f} (MRP {p.get('original_price') or 'n/a'}) | "
                    f"rating {p['rating']}★ ({p['review_count']} reviews) | stock {p['stock']} | {p.get('brand_name')}"
                )
            matrix = tool_facts.get("spec_matrix", [])
            for row in matrix[:12]:
                vals = " | ".join(str(v or "—") for v in row["values"])
                lines.append(f"  Spec [{row['spec_group']}] {row['spec_key']}: {vals}")
            return "\n".join(lines)
        if intent == "feedback" and tool_facts.get("total") is not None:
            return json.dumps(tool_facts, indent=2, default=str)
        if intent == "product_info" and tool_facts.get("product"):
            return json.dumps(tool_facts["product"], indent=2, default=str)
        products = tool_facts.get("products", [])
        return "\n".join(
            f"- {p['name']} | ₹{p['price']:,.0f} | {p['rating']}★ ({p['review_count']} reviews) | "
            f"stock {p['stock']} | {p.get('brand_name')} | {p.get('why_recommended') or ''}"
            for p in products[:8]
        ) or "No products matched."
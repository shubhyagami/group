"""Local AI provider for OpenAI-compatible endpoints (Ollama / vLLM / LocalAI)."""
from __future__ import annotations

import json
import logging

import httpx

from ..config import settings
from .base import AIProvider, AIResult

logger = logging.getLogger(__name__)


class LocalAIProvider(AIProvider):
    name = "local"

    def __init__(self, base_url: str | None = None, model: str | None = None):
        self.base_url = (base_url or settings.LOCAL_AI_BASE_URL).rstrip("/")
        self.model = model or settings.LOCAL_AI_MODEL
        self.timeout = settings.AI_TIMEOUT_MS / 1000.0

    async def chat(self, system: str, messages: list[dict], temperature: float = 0.4) -> AIResult:
        body = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}] + messages,
            "temperature": temperature,
            "max_tokens": 900,
        }
        url = f"{self.base_url}/chat/completions"
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                resp = await client.post(url, json=body)
                resp.raise_for_status()
                data = resp.json()
                text = data["choices"][0]["message"]["content"].strip()
                return AIResult(text=text, provider=self.name, model=data.get("model", self.model))
            except (httpx.TimeoutException, httpx.HTTPError, KeyError, json.JSONDecodeError) as exc:
                logger.warning("Local AI unavailable: %s", exc)
                raise RuntimeError(f"Local AI failed: {exc}") from exc
"""NVIDIA Nemotron provider with sequential multi-key failover.

Tries every configured key in order; on HTTP 401/403/429/5xx or timeout
(2.5s) immediately moves to the next key. Raises only when ALL keys fail
so the orchestrator can fall back to local/mock seamlessly.
"""
from __future__ import annotations

import json
import logging
import time

import httpx

from ..config import settings
from .base import AIProvider, AIResult

logger = logging.getLogger(__name__)


class NvidiaAIProvider(AIProvider):
    name = "nvidia"

    def __init__(self, keys: list[str] | None = None, base_url: str | None = None, model: str | None = None):
        self.keys = keys if keys is not None else settings.nvidia_keys
        self.base_url = (base_url or settings.NVIDIA_BASE_URL).rstrip("/")
        self.model = model or settings.NVIDIA_MODEL
        self.timeout = settings.AI_TIMEOUT_MS / 1000.0

    async def chat(self, system: str, messages: list[dict], temperature: float = 0.4) -> AIResult:
        if not self.keys:
            raise RuntimeError("No NVIDIA API keys configured")

        body = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}] + messages,
            "temperature": temperature,
            "max_tokens": 900,
            "top_p": 0.9,
        }
        url = f"{self.base_url}/chat/completions"
        last_error: Exception | None = None

        async with httpx.AsyncClient(timeout=self.timeout) as client:
            for idx, key in enumerate(self.keys):
                try:
                    start = time.monotonic()
                    resp = await client.post(
                        url,
                        headers={
                            "Authorization": f"Bearer {key}",
                            "Content-Type": "application/json",
                            "Accept": "application/json",
                        },
                        json=body,
                    )
                    if resp.status_code in (401, 403):
                        logger.warning("NVIDIA key %d rejected (HTTP %d)", idx + 1, resp.status_code)
                        continue
                    if resp.status_code in (429, 500, 502, 503, 504):
                        logger.warning("NVIDIA key %d rate-limited/error (HTTP %d)", idx + 1, resp.status_code)
                        continue
                    resp.raise_for_status()
                    data = resp.json()
                    text = data["choices"][0]["message"]["content"].strip()
                    elapsed = int((time.monotonic() - start) * 1000)
                    return AIResult(
                        text=text,
                        provider=self.name,
                        model=data.get("model", self.model),
                        raw={"status": resp.status_code, "ms": elapsed},
                    )
                except (httpx.TimeoutException, httpx.HTTPError, KeyError, json.JSONDecodeError) as exc:
                    last_error = exc
                    logger.warning("NVIDIA key %d failed: %s", idx + 1, exc)
                    continue

        raise RuntimeError(f"All {len(self.keys)} NVIDIA keys failed; last error: {last_error}")
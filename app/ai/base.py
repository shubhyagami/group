"""AI provider abstraction: NVIDIA (multi-key failover) -> Local -> Mock."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class AIResult:
    text: str
    provider: str
    model: str = ""
    raw: dict | None = None
    follow_up_suggestions: list[str] = field(default_factory=list)


class AIProvider(ABC):
    name: str = "unknown"

    @abstractmethod
    async def chat(self, system: str, messages: list[dict], temperature: float = 0.4) -> AIResult:
        """Single chat completion call; MUST raise on failure so the orchestrator
        can fall back to the next provider in the chain."""
        raise NotImplementedError
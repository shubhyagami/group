"""In-memory concurrent OTP store with TTL expiry and attempt rate-limiting."""
from __future__ import annotations

import random
import threading
import time

from ..config import settings


class OtpEntry:
    __slots__ = ("otp", "expires_at", "attempts", "locked", "purpose")

    def __init__(self, otp: str, ttl_seconds: int, purpose: str):
        self.otp = otp
        self.expires_at = time.time() + ttl_seconds
        self.attempts = 0
        self.locked = False
        self.purpose = purpose


class OtpStore:
    """Thread-safe in-memory store: 5-minute TTL, max 5 failed attempts -> lockout."""

    def __init__(self, ttl_seconds: int | None = None, max_attempts: int | None = None):
        self.ttl = ttl_seconds or settings.OTP_TTL_SECONDS
        self.max_attempts = max_attempts or settings.OTP_MAX_ATTEMPTS
        self._entries: dict[str, OtpEntry] = {}
        self._lock = threading.Lock()

    def generate(self, email: str, purpose: str = "VERIFICATION") -> str:
        otp = f"{random.randint(0, 999999):06d}"
        with self._lock:
            self._entries[email.lower()] = OtpEntry(otp, self.ttl, purpose)
        return otp

    def verify(self, email: str, otp: str) -> tuple[bool, str]:
        key = email.lower()
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                return False, "No OTP requested for this email"
            if entry.locked:
                return False, "Too many failed attempts. Request a new OTP."
            if time.time() > entry.expires_at:
                self._entries.pop(key, None)
                return False, "OTP expired. Request a new one."
            if entry.otp != otp.strip():
                entry.attempts += 1
                if entry.attempts >= self.max_attempts:
                    entry.locked = True
                    self._entries.pop(key, None)
                    return False, "Too many failed attempts. Request a new OTP."
                remaining = self.max_attempts - entry.attempts
                return False, f"Invalid OTP. {remaining} attempt(s) remaining."
            self._entries.pop(key, None)
            return True, "OTP verified"

    def clear(self) -> None:
        with self._lock:
            self._entries.clear()


otp_store = OtpStore()
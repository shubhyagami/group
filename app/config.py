"""Application configuration loaded from environment / .env file."""
from __future__ import annotations

import base64
import json
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


def extract_raw_brevo_key(value: str) -> str:
    """Sanitize a Brevo API key.

    Accepts either the raw `xkeysib-...` token or a base64-encoded payload such as
    `{"api_key":"xkeysib-..."}` and returns the raw token.
    """
    if not value:
        return ""
    v = value.strip()
    if v.startswith("xkeysib-"):
        return v
    try:
        decoded = base64.b64decode(v, validate=True).decode("utf-8")
        if "api_key" in decoded:
            try:
                return str(json.loads(decoded)["api_key"]).strip()
            except (json.JSONDecodeError, KeyError, TypeError):
                pass
    except Exception:
        pass
    return v


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    # Server
    APP_NAME: str = "OmniMart AI E-Commerce Backend"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8080

    # Database
    DATABASE_URL: str = "sqlite:///./omnimart.db"

    # Security / sessions
    SECRET_KEY: str = "omnimart-secure-enterprise-jwt-key-2026-super-secret"
    SESSION_COOKIE_NAME: str = "omnimart_session"
    SESSION_MAX_AGE: int = 7 * 24 * 3600

    # Brevo transactional email
    BREVO_API_KEY: str = ""
    BREVO_SENDER_EMAIL: str = "shubhkumarsinha192@gmail.com"
    BREVO_SENDER_NAME: str = "OmniMart AI"

    # Seeder
    SEED_DEMO_DATA: bool = True

    @property
    def raw_brevo_api_key(self) -> str:
        return extract_raw_brevo_key(self.BREVO_API_KEY)


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
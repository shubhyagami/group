"""OmniMart AI - FastAPI application factory & middleware wiring."""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.sessions import SessionMiddleware

from .api import (
    admin,
    auth,
    cart,
    chat,
    compare,
    orders,
    otp,
    products,
    profile,
    recommendations,
    search,
    telemetry,
)
from .config import settings
from .database import Base, engine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # create tables + seed demo data (idempotent)
    Base.metadata.create_all(bind=engine)
    if settings.SEED_DEMO_DATA:
        from .seed import seed_database

        seed_database()
    yield


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description=(
        "Autonomous AI-Powered E-Commerce & Recommendation Platform backend. "
        "Hybrid recommendation engine, NLP sentiment analytics, natural-language search, "
        "multi-product comparison, conversational AI with safe tool routing, "
        "session-cookie auth, Brevo transactional email and in-memory OTP."
    ),
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    SessionMiddleware,
    secret_key=settings.SECRET_KEY,
    session_cookie=settings.SESSION_COOKIE_NAME,
    max_age=settings.SESSION_MAX_AGE,
    same_site="lax",
    https_only=False,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

for router in (
    auth.router,
    products.router,
    cart.router,
    orders.router,
    profile.router,
    chat.router,
    recommendations.router,
    search.router,
    compare.router,
    admin.router,
    telemetry.router,
    otp.router,
):
    app.include_router(router)


@app.get("/")
def root():
    return {
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "docs": "/docs",
        "health": "/health",
        "demo_accounts": {
            "admin": {"email": "admin@omnimart.com", "password": "admin123"},
            "customer": {"email": "user@omnimart.com", "password": "password123"},
        },
        "endpoints": [
            "POST /login | /register | /logout | /me",
            "GET  /products?category=&brand=&min_price=&max_price=&min_rating=&sort=",
            "GET  /products/{slug}",
            "GET  /cart | POST /cart/add | /cart/update | /cart/remove",
            "POST /checkout | GET /orders | GET /orders/{id}",
            "GET  /profile | POST /profile/address | PUT /profile/preferences",
            "GET  /wishlist | POST /wishlist/add | /wishlist/remove",
            "POST /api/chat",
            "GET  /api/recommendations?limit=8",
            "GET  /api/search/autocomplete?q= | POST /api/search/nl",
            "GET  /api/compare/data?ids=1,2,3",
            "POST /api/telemetry/interaction",
            "POST /api/otp/send | /api/otp/verify",
            "GET  /api/admin/analytics-data | POST /api/admin/ask-ai  (ADMIN)",
        ],
    }


@app.get("/health")
def health():
    return {"status": "ok", "service": settings.APP_NAME, "version": settings.APP_VERSION}
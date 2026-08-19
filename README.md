# OmniMart AI — Python Backend

Autonomous AI-Powered E-Commerce & Recommendation Platform backend (FastAPI + SQLAlchemy + SQLite).

## Features

- **Hybrid Recommendation Engine** — `FinalScore = 0.35·S_pref + 0.25·S_beh + 0.20·S_cont + 0.10·S_rat + 0.10·S_pop` with explainable `whyRecommended` badges
- **Customer Feedback NLP** — deterministic sentiment/emotion/topic extraction (Positive/Negative/Mixed/Neutral; Delighted/Satisfied/Frustrated/Disappointed), issue & positive-aspect extraction, admin sentiment analytics
- **Natural-Language Search** — "gaming laptops under 80000" → structured filters (budget, category, brand, rating, feature tags)
- **Multi-Product Comparison** — spec matrix builder with identical-cell highlighting + AI verdict
- **Multi-turn Conversational AI** — safe DB tool routing (searchProducts, compareProducts, getProductFeedbackSummary, getRecommendedProducts), zero-hallucination grounded facts, audit logs
- **Multi-Provider AI failover** — NVIDIA Nemotron (sequential multi-key, 2.5s timeout) → Local (Ollama/vLLM) → Deterministic Mock
- **Session-cookie auth** — BCrypt hashing, ROLE_USER/ROLE_ADMIN guards
- **Brevo transactional email** — dark HTML OTP templates + order invoices, base64-key sanitization
- **In-memory OTP** — 5-minute TTL, 5-attempt lockout
- **Cart/Order lifecycle** — guest carts, stock reservation, payments
- **Telemetry** — clickstream → user interactions powering recommendations
- **Seeder** — 8 categories, 12 brands, 102 products, ~800 reviews + sentiment, 64 orders, 560 interactions, demo accounts

## Quick start

```bash
python -m venv .venv
.venv\Scripts\activate          # Windows
pip install -r requirements.txt
python run.py                   # http://localhost:8080  (docs at /docs)
```

Config lives in `.env` (copy from `.env.example`). No DB setup needed — SQLite file `omnimart.db` is created and auto-seeded on first start. Set `DATABASE_URL` to a MySQL/Postgres URL to switch databases.

## Demo accounts

| Role  | Email               | Password     |
|-------|---------------------|--------------|
| Admin | admin@omnimart.com  | admin123     |
| User  | user@omnimart.com   | password123  |

## Key endpoints

| Method | Path                          | Auth   | Description |
|--------|-------------------------------|--------|-------------|
| POST   | `/login`                      | —      | Session login (cookie) |
| POST   | `/register`                   | —      | Create account |
| GET    | `/products`                   | —      | Filtered catalog |
| GET    | `/products/{slug}`            | —      | Product detail + specs + reviews |
| POST   | `/products/{id}/reviews`      | User   | Submit review (auto sentiment analysis) |
| GET    | `/cart` · `/orders`           | —/User | Cart & order history |
| POST   | `/checkout`                   | User   | Place order (stock reservation) |
| GET    | `/profile` · `/wishlist`      | User   | Profile, preferences, wishlist |
| POST   | `/api/chat`                   | —      | Conversational AI assistant |
| GET    | `/api/recommendations`        | —      | Hybrid recommendations |
| POST   | `/api/search/nl`              | —      | NL search with parsed filters |
| GET    | `/api/compare/data?ids=1,2,3` | —      | Spec matrix + AI verdict |
| POST   | `/api/telemetry/interaction`  | —      | Clickstream events |
| POST   | `/api/otp/send` · `/verify`   | —      | Email OTP (Brevo) |
| GET    | `/api/admin/analytics-data`   | Admin  | Revenue, sentiment, churn analytics |
| POST   | `/api/admin/ask-ai`           | Admin  | Executive BI Q&A |

## Deployment

- **Docker**: `docker build -t omnimart . && docker run -p 8080:8080 omnimart`
- **Render**: commit with `render.yaml`; set `NVIDIA_API_KEYS` / `BREVO_API_KEY` in the dashboard.

## Architecture

```
app/
├── main.py            app factory, middleware, router wiring
├── config.py          env settings (pydantic-settings)
├── database.py        engine + session
├── models.py          25 JPA-equivalent entities
├── schemas.py         Pydantic DTOs
├── security.py        bcrypt + session auth + role guards
├── seed.py            DataSeeder (idempotent CommandLineRunner equivalent)
├── ai/
│   ├── nvidia.py      multi-key sequential failover
│   ├── local.py       Ollama / vLLM OpenAI-compatible
│   ├── mock.py        deterministic NLP engine (sentiment, NL search, BI)
│   ├── tool_router.py safe read-only DB tools
│   └── orchestrator.py multi-turn conversation + provider chain
├── services/          recommendation, feedback, search, comparison, cart,
│                      order, telemetry, OTP, Brevo email
└── api/               12 REST routers
```
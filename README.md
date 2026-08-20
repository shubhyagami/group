# OmniMart AI — Python Backend

AI-Powered E-Commerce & Recommendation Platform backend (FastAPI + SQLAlchemy + SQLite).

## Features

- **Hybrid Recommendation Engine** — `FinalScore = 0.35·S_pref + 0.25·S_beh + 0.20·S_cont + 0.10·S_rat + 0.10·S_pop` with explainable `whyRecommended` badges
- **Customer Feedback NLP** — deterministic sentiment/emotion/topic extraction (Positive/Negative/Mixed/Neutral; Delighted/Satisfied/Frustrated/Disappointed) with issue & positive-aspect extraction on every review
- **Search & Autocomplete** — filtered catalog (category, brand, price, rating, tags), tokenized relevance search, autocomplete suggestions + popular queries
- **Session-cookie auth** — BCrypt hashing, ROLE_USER/ROLE_ADMIN roles
- **Brevo transactional email** — dark HTML order-invoice templates, base64-key sanitization
- **Cart/Order lifecycle** — guest carts, stock reservation, payments (UPI / CREDIT_CARD / NET_BANKING / COD)
- **Wishlist** — per-user saved products
- **Seeder** — 8 categories, 12 brands, 104 products, ~955 reviews + sentiment, 64 orders, 560 interactions, demo accounts

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
| GET    | `/cart`                       | —      | Cart (guest cookie supported) |
| POST   | `/cart/add` · `/update` · `/remove` | —  | Cart management |
| POST   | `/checkout`                   | User   | Place order (stock reservation) |
| GET    | `/orders` · `/orders/{id}`    | User   | Order history / detail |
| GET    | `/wishlist` · `/wishlist/add` · `/wishlist/remove` | User | Wishlist |
| GET    | `/api/recommendations`        | —      | Hybrid recommendations |
| GET    | `/api/search/autocomplete`    | —      | Autocomplete + popular queries |

Full interactive spec: `GET /openapi.json` or Swagger UI at `/docs`. See
`docs/POSTMAN_TESTING.md` for a complete Postman testing guide (and the importable
collection in `postman/`).

## Deployment

- **Docker**: `docker build -t omnimart . && docker run -p 8080:8080 omnimart`
- **Render**: commit with `render.yaml`; set `BREVO_API_KEY` in the dashboard.

## Architecture

```
app/
├── main.py            app factory, middleware, router wiring
├── config.py          env settings (pydantic-settings)
├── database.py        engine + session
├── models.py          JPA-equivalent entities
├── schemas.py         Pydantic DTOs
├── security.py        bcrypt + session auth + role guards
├── seed.py            DataSeeder (idempotent CommandLineRunner equivalent)
├── services/          recommendation, feedback NLP, search, cart, order, Brevo email
└── api/               7 REST routers (auth, storefront, cart, orders, wishlist,
                       recommendations, search)
```
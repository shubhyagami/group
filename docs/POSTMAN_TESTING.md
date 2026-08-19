# OmniMart AI — API Testing with Postman

Complete guide for testing the OmniMart AI FastAPI backend using Postman. Covers setup,
authentication model, every endpoint, and ordered end-to-end test flows.

---

## 1. Prerequisites

- Backend running locally: `http://127.0.0.1:8080` (see project README for startup).
- [Postman](https://www.postman.com/downloads/) installed.
- Optional: import `postman/omnimart.postman_collection.json` (collection with all
  requests below pre-built). The collection uses the variables defined in §3.

---

## 2. Server & API overview

| Item | Value |
|---|---|
| Base URL (local) | `http://127.0.0.1:8080` |
| Interactive docs | `http://127.0.0.1:8080/docs` (Swagger UI), `/redoc` |
| Health check | `GET /health` → `{"status": "ok", ...}` |
| Interactive API spec | `http://127.0.0.1:8080/openapi.json` (importable into Postman via *Import → Link*) |

All endpoints accept and return JSON. Request bodies must set header
`Content-Type: application/json`.

### Demo accounts (seeded)

| Role | Email | Password |
|---|---|---|
| Admin | `admin@omnimart.com` | `admin123` |
| Customer | `user@omnimart.com` | `password123` |

### Sample data values used in this guide

- Category slugs: `smartphones`, `laptops`, `headphones`, `gaming`, `smart-home`,
  `cameras`, `accessories`, `monitors`.
- Brand slugs: `samsung`, `apple`, `sony`, `asus`, `lenovo`, `dell`, `hp`, `lg`, etc.
- Known product IDs: `1` = Samsung Galaxy S25 Ultra 5G, `5` = iPhone 16 Pro,
  `9` = Galaxy Buds (any `GET /products` returns a fresh list with ids).
- Known product slug: `samsung-galaxy-s25-ultra-5g`.

---

## 3. Postman setup (do this first)

### 3.1 Collection variables

Create a collection (or import the provided one) and add collection variables:

| Variable | Initial value | Scope |
|---|---|---|
| `baseUrl` | `http://127.0.0.1:8080` | Collection |
| `customerToken` | *(leave empty)* | Collection |
| `adminToken` | *(leave empty)* | Collection |

### 3.2 Cookies — how auth works here

This API uses **session cookies**, not Bearer tokens:

- `omnimart_session` — login/register session. Created by `POST /login` or
  `POST /register` via `Set-Cookie`. **Postman stores this cookie automatically**
  and sends it on subsequent requests. If your requests return 401/403, make sure
  the cookie is present (Postman → *Cookies* under the request / `omnimart_session`
  in the domain `127.0.0.1`).
- `cart_session` — anonymous cart identity, created by any `/cart*` request.

> Do NOT use an Authorization header. If you imported a stale OpenAPI spec that
> mentions `Authorization`, ignore it — the working auth mechanism is the cookie.

### 3.3 Suggested Postman folder layout

```
OmniMart AI
├── 0. Health & Meta
├── 1. Auth (register / login / logout / me)
├── 2. Storefront (categories, brands, products, reviews)
├── 3. Search & Compare (autocomplete, NL search, compare)
├── 4. Recommendations
├── 5. Cart & Checkout & Orders
├── 6. Profile & Wishlist
├── 7. Chat AI
├── 8. Telemetry
├── 9. OTP
├── 10. Admin (requires admin login)
└── 11. Error Cases
```

---

## 4. Endpoint reference

> Legend — Auth: `public` = no cookie needed · `user` = needs `omnimart_session` of a
> logged-in customer · `admin` = needs session of `admin@omnimart.com`.

### 4.0 Health & meta

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | public | Service health |
| GET | `/` | public | App info + demo accounts + endpoint list |

### 4.1 Auth

| Method | Path | Auth | Body / Params | Expected result |
|---|---|---|---|---|
| POST | `/register` | public | `{"email","password","full_name","phone?"}` | `201`-style UserDto JSON (status `200`); sets session cookie |
| POST | `/login` | public | `{"email","password"}` | UserDto JSON; sets `omnimart_session` |
| POST | `/logout` | user | — | `{"message": "Logged out"}`; clears cookie |
| GET | `/me` | user | — | Current user profile JSON |

**Register example body**

```json
{
  "email": "demo.user@example.com",
  "password": "secret123",
  "full_name": "Demo User",
  "phone": "9876543210"
}
```

**Errors:** `409` duplicate email · `401` bad credentials / inactive user ·
`422` validation (password < 6 chars, etc.).

### 4.2 Storefront

| Method | Path | Auth | Params | Description |
|---|---|---|---|---|
| GET | `/categories` | public | — | Active categories ordered by display order |
| GET | `/brands` | public | — | Active brands |
| GET | `/products` | public | `q`, `category`, `brand`, `min_price`, `max_price`, `min_rating`, `featured`, `sort` (`relevance\|price_asc\|price_desc\|rating\|newest`), `page`, `page_size` (≤60) | Paginated product cards + `total` + echoed `filters` |
| GET | `/products/{slug}` | public (optional user) | path: product slug | Full detail: specs, images, latest 5 reviews, brand, category |
| GET | `/products/id/{id}/related` | public | path: numeric id | 4 related product cards |
| POST | `/products/{id}/reviews` | user | `{"rating":1-5,"title","comment","verified_purchase"}` | Created review; product aggregates updated; sentiment analyzed |

**Products filter example**

```
GET /products?category=smartphones&min_price=10000&max_price=60000&min_rating=4.0&sort=rating&page=1&page_size=12
```

**Errors:** `404` unknown slug/id · `409` duplicate review by same user ·
`401` review without login.

### 4.3 Search & Compare

| Method | Path | Auth | Params / Body | Description |
|---|---|---|---|---|
| GET | `/api/search/autocomplete` | public | `?q=iph` | Product name suggestions + popular queries |
| POST | `/api/search/nl` | public | `{"query":"camera phone under 40k"}` | Natural-language search; returns parsed filters, `filters_hint`, matching cards |
| GET | `/api/compare/data` | public | `?ids=1,5,9` (2–5 ids) | Spec matrix, identical cells, AI verdict |

**NL search examples**

```json
{"query": "gaming laptops under 80000"}
{"query": "recommend a flagship phone under 60000"}
{"query": "sony headphones with anc"}
```

Response includes `parsed` (category / brand / min_price / max_price / min_rating /
tags), `filters_hint`, `products`, `total`.

**Errors:** `400` empty query · `400` <2 ids or invalid ids · `400` >5 ids.

### 4.4 Recommendations

| Method | Path | Auth | Params | Description |
|---|---|---|---|---|
| GET | `/api/recommendations` | public / user | `?limit=` (1–24, default 8) | Anonymous → popularity; logged-in → hybrid strategy with `why_recommended` badges on each card |

### 4.5 Cart & Checkout & Orders

All cart routes are anonymous-safe (create `cart_session` cookie automatically).

| Method | Path | Auth | Body / Params | Description |
|---|---|---|---|---|
| GET | `/cart` | public / user | — | Cart snapshot (items, subtotal, totals) |
| POST | `/cart/add` | public / user | `{"product_id":9,"quantity":1}` | Add item (max 99/line) |
| POST | `/cart/update` | public / user | `{"product_id":9,"quantity":2}` | Set quantity (0 = removes) |
| POST | `/cart/remove` | public / user | `{"product_id":9}` | Remove line item |
| POST | `/checkout` | **user** | `{"address_id":1,"payment_method":"UPI","email_receipt":true}` | Places order: reserves stock, creates payment, returns full OrderDto |
| GET | `/orders` | user | — | Order history (newest first) |
| GET | `/orders/{id}` | user | path: order id | Order detail (items, payment, address); only own orders |

**Checkout body**

```json
{
  "address_id": 1,
  "payment_method": "UPI",
  "email_receipt": false
}
```

`payment_method` allowed: `CREDIT_CARD`, `UPI`, `NET_BANKING`, `COD`.

**Flow note:** use the same Postman window (or same cookie jar) as `/cart/add` so
the cart cookie carries over. A customer must have ≥1 address (`POST /profile/address`)
before checkout — otherwise `400 Invalid shipping address`.

**Errors:** `400` invalid address / empty cart / out of stock · `401` not logged in.

### 4.6 Profile & Wishlist (all `user`)

| Method | Path | Body / Params | Description |
|---|---|---|---|
| GET | `/profile` | — | Profile + addresses + AI preferences |
| POST | `/profile/address` | `{"street_address","city","postal_code","phone","is_default":true}` | Add address; first/default address becomes default |
| PUT | `/profile/preferences` | `{"preferred_categories":{"Laptops":90},"preferred_brands":{"Apple":80},"max_budget":200000,"recommendations_enabled":true}` | Update AI preferences (partial update — omit keys you don't want to change) |
| GET | `/wishlist` | — | Wishlist products |
| POST | `/wishlist/add` | `?product_id=9` (query param) | Add to wishlist |
| POST | `/wishlist/remove` | `?product_id=9` | Remove from wishlist |

### 4.7 Chat AI

| Method | Path | Auth | Body | Description |
|---|---|---|---|---|
| POST | `/api/chat` | public / user | `{"message":"recommend a flagship phone under 60000"}` | Conversational AI with tool routing |

**Body fields:** `message` (required), `conversation_id` (optional — pass the id from
a previous response to continue a conversation), `current_product_id` (optional —
context for questions about the currently viewed product).

**Response fields:** `message` (reply), `conversation_id` (reuse for next turn),
`candidate_products` (grounded product cards the reply refers to),
`reasoning_summary`, `follow_up_suggestions`, `tool_used`, `active_provider`
(`nvidia` when a key responds, else `mock`).

**Multi-turn test sequence (one conversation):**

1. `{"message": "recommend a flagship phone under 60000"}` → save `conversation_id`.
2. `{"message": "compare the top two from above", "conversation_id": "<id>"}`
3. `{"message": "what do customers say about the battery of the first one?", "conversation_id": "<id>"}`

### 4.8 Telemetry

| Method | Path | Auth | Body | Description |
|---|---|---|---|---|
| POST | `/api/telemetry/interaction` | public / user | `{"interaction_type":"FILTER_APPLY","category_name":"Laptops","session_id":"sess-123"}` | Records behavior signal (powers recommendations + admin BI) |

`interaction_type` allowed: `PRODUCT_VIEW`, `SEARCH`, `ADD_TO_CART`,
`REMOVE_FROM_CART`, `ADD_TO_WISHLIST`, `PRODUCT_PURCHASE`, `PRODUCT_COMPARE`,
`FILTER_APPLY`.

### 4.9 OTP (email login)

> Email dispatch requires the Brevo account to authorize the server's IP (see README).
> Even when delivery fails, the OTP is generated in-memory and can be tested in
> DEBUG/dev mode by reading it from the server logs.

| Method | Path | Auth | Body | Description |
|---|---|---|---|---|
| POST | `/api/otp/send` | public | `{"email":"user@omnimart.com","name":"Rahul","purpose":"LOGIN"}` | Generates 6-digit OTP (5-min TTL, 5 attempts) and emails it |
| POST | `/api/otp/verify` | public | `{"email":"user@omnimart.com","otp":"123456"}` | Validates OTP; on success auto-logs the user in (sets session cookie) |

**Errors:** `400` wrong/expired/attempts-exceeded OTP.

### 4.10 Admin (all `admin`)

| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/api/admin/analytics-data` | — | Revenue, orders, AOV, sentiment distribution, top negative topics, churn signals, top products, low-stock list, recent orders |
| POST | `/api/admin/ask-ai` | `{"question":"What are customers complaining about?"}` | Deterministic BI answer grounded in live analytics |

**Ask-AI example questions**

```json
{"question": "What is our revenue and top product?"}
{"question": "What are customers complaining about?"}
{"question": "How many users do we have and what is the average order value?"}
```

**Security check:** calling these with the *customer* session must return `403`.

---

## 5. Recommended test flows (run in order)

### Flow A — Storefront & search (no login)

1. `GET /health` → expect `status: ok`.
2. `GET /categories` → 8 categories.
3. `GET /products?category=smartphones&min_rating=4.5&sort=rating&page_size=5`
   → `total > 0`, first product rating ≥ 4.5.
4. `GET /products/samsung-galaxy-s25-ultra-5g` → specs/images/reviews populated.
5. `GET /products/id/1/related` → 4 cards.
6. `POST /api/search/nl` `{"query":"gaming laptops under 80000"}` → products
   include `ASUS TUF Gaming F15`; `filters_hint` shows category/budget/tags.
7. `GET /api/search/autocomplete?q=iphon` → product suggestions + query history.
8. `GET /api/compare/data?ids=1,5,9` → `spec_matrix` rows, `identical_cells`,
   `ai_verdict` text.
9. `GET /api/recommendations?limit=4` → `strategy = popularity (anonymous)`.

### Flow B — Account lifecycle (customer)

1. `POST /register` with a fresh email → capture cookie automatically.
2. `GET /me` → same email.
3. `POST /profile/address` (street, city, `is_default: true`).
4. `PUT /profile/preferences` → set `preferred_categories: {"Laptops": 90}`, `max_budget: 200000`.
5. `GET /api/recommendations?limit=5` → `strategy = hybrid ...`, first card shows
   "Matches preferred category" badge.
6. `POST /wishlist/add?product_id=9` → list now contains the product.
7. `POST /api/telemetry/interaction` (`PRODUCT_VIEW`, `product_id: 1`).

### Flow C — Cart → checkout → order

1. `GET /cart` → empty cart, cookie `cart_session` created.
2. `POST /cart/add` ×2 products.
3. `POST /cart/update` (change a quantity).
4. `POST /checkout` with `address_id` from Flow B → order number `OM...`, payment
   `COMPLETED`, stock reserved.
5. `GET /orders` → the new order is first.
6. `GET /orders/{id}` → full detail.

### Flow D — AI chat (multi-turn)

1. `POST /api/chat` `{"message":"recommend a flagship phone under 60000"}` →
   `tool_used = searchProducts`, ≥1 `candidate_products`, `active_provider` either
   `nvidia` or `mock`. Save `conversation_id`.
2. Second turn with `conversation_id`: "compare the top two from above" →
   `tool_used = compareProducts`, reply contains a verdict.
3. Third turn with same id: "what do customers say about the battery of the first
   one?" → `tool_used = getProductFeedbackSummary`, reply references real review
   counts from `candidate_products`.

### Flow E — Reviews

1. `POST /products/20/reviews` as customer
   `{"rating":4,"title":"Great","comment":"Excellent product, smooth performance and great value for money"}`.
2. Verify `GET /products/{slug-of-20}` → `latest_reviews` shows the new review with
   your name, and `review_count` incremented.

### Flow F — Admin (login as `admin@omnimart.com` / `admin123`)

1. `GET /api/admin/analytics-data` → revenue ≈ `₹15,000,000+`, sentiment
   distribution (Positive ≫ Negative), `top_negative_topics` populated.
2. `POST /api/admin/ask-ai` with the three sample questions → answers quote live
   numbers.
3. **Negative check:** in a second Postman window, login as the customer and call
   `GET /api/admin/analytics-data` → must return `403`.

### Flow G — OTP

1. `POST /api/otp/send` (email `user@omnimart.com`, purpose `LOGIN`) → read the OTP
   from the server console (dev) or mailbox (prod).
2. `POST /api/otp/verify` with the OTP → `authenticated: true`; the session cookie
   is now set — `GET /me` works.
3. Wrong OTP → `400` with a descriptive message.

### Flow H — Error cases (contract checks)

| Request | Expected |
|---|---|
| `POST /login` wrong password | `401` |
| `POST /register` duplicate email | `409` |
| `GET /products/does-not-exist` | `404` |
| `POST /api/compare/data?ids=1` | `400` (need ≥2) |
| `POST /api/compare/data?ids=1,2,3,4,5,6` | `400` (max 5) |
| `POST /api/search/nl` empty body | `400` |
| `POST /products/1/reviews` logged out | `401` |
| `POST /checkout` logged out | `401` |
| `GET /api/admin/analytics-data` as customer | `403` |
| `POST /api/otp/verify` wrong OTP | `400` |
| `POST /register` short password | `422` |

---

## 6. Notes & gotchas

- **Cookie persistence:** Postman auto-manages `omnimart_session` and
  `cart_session` per domain. A *fresh login* in a new request window resets the
  session — subsequent authenticated calls must use that window/cookie jar.
- **Cart adoption:** a guest cart (`cart_session` cookie) is linked to your account
  at login/checkout. To see it, keep the same cookie jar between cart ops and login.
- **NVIDIA vs mock provider:** chat calls try NVIDIA first (3 keys, ~2.5s timeout
  each). If keys are exhausted/unreachable, the deterministic mock provider answers
  with the same grounded tool results. Either is a successful test.
- **OTP store is in-memory:** restarting the server invalidates outstanding OTPs.
- **Email:** `POST /api/otp/send` and `email_receipt: true` at checkout return
  success messages even if Brevo rejects delivery (IP authorization) — check
  `email_delivered` field and server logs.
- **Re-seeding:** to reset data, delete `omnimart.db` and restart the server
  (lifespan re-seeds automatically).
- **Rate/size limits:** `page_size ≤ 60`, `limit ≤ 24` (recommendations),
  chat message ≤ 4000 chars, cart line quantity ≤ 99.
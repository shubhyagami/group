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
├── 3. Search (autocomplete)
├── 4. Recommendations
├── 5. Cart & Checkout & Orders
├── 6. Wishlist
└── 7. Error Cases
```

---

## 4. Endpoint reference

> Legend — Auth: `public` = no cookie needed · `user` = needs `omnimart_session` of a
> logged-in customer.

### 4.0 Health & meta

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | public | Service health |
| GET | `/` | public | App info + demo accounts + endpoint list |

### 4.1 Auth

| Method | Path | Auth | Body / Params | Expected result |
|---|---|---|---|---|
| POST | `/register` | public | `{"email","password","full_name","phone?"}` | UserDto JSON; sets session cookie |
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
| GET | `/products` | public | `q`, `category`, `brand`, `min_price`, `max_price`, `min_rating`, `featured`, `sort` (`relevance\|price_asc\|price_desc\|rating\|newest\|popular`), `page`, `page_size` (≤60) | Paginated product cards + `total` + echoed `filters` |
| GET | `/products/{slug}` | public | path: product slug | Full detail: specs, images, latest 5 reviews, brand, category |
| GET | `/products/id/{id}/related` | public | path: numeric id | 4 related product cards |
| POST | `/products/{id}/reviews` | user | `{"rating":1-5,"title","comment","verified_purchase"}` | Created review; product aggregates updated; sentiment analyzed |

**Products filter example**

```
GET /products?category=smartphones&min_price=10000&max_price=60000&min_rating=4.0&sort=rating&page=1&page_size=12
```

**Errors:** `404` unknown slug/id · `409` duplicate review by same user ·
`401` review without login.

### 4.3 Search

| Method | Path | Auth | Params | Description |
|---|---|---|---|---|
| GET | `/api/search/autocomplete` | public | `?q=iph` | Product name suggestions + popular queries |

**Example**

```
GET /api/search/autocomplete?q=iphone
```

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
the cart cookie carries over. Customers have seeded addresses (e.g. `address_id=1`
for `user@omnimart.com`) — checkout fails with `400` if the address does not belong
to the logged-in user.

**Errors:** `400` invalid address / empty cart / out of stock · `401` not logged in.

### 4.6 Wishlist (all `user`)

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/wishlist` | — | Wishlist products |
| POST | `/wishlist/add` | `?product_id=9` (query param) | Add to wishlist |
| POST | `/wishlist/remove` | `?product_id=9` | Remove from wishlist |

---

## 5. Recommended test flows (run in order)

### Flow A — Storefront & search (no login)

1. `GET /health` → expect `status: ok`.
2. `GET /categories` → 8 categories.
3. `GET /products?category=smartphones&min_rating=4.5&sort=rating&page_size=5`
   → `total > 0`, first product rating ≥ 4.5.
4. `GET /products/samsung-galaxy-s25-ultra-5g` → specs/images/reviews populated.
5. `GET /products/id/1/related` → 4 cards.
6. `GET /api/search/autocomplete?q=iphon` → product suggestions + query history.
7. `GET /api/recommendations?limit=4` → `strategy = popularity (anonymous)`.

### Flow B — Account & wishlist (customer)

1. `POST /register` with a fresh email → capture cookie automatically.
2. `GET /me` → same email.
3. `POST /wishlist/add?product_id=9` → list now contains the product.
4. `POST /wishlist/remove?product_id=9` → list is empty again.

### Flow C — Cart → checkout → order

1. `GET /cart` → empty cart, cookie `cart_session` created.
2. `POST /cart/add` ×2 products.
3. `POST /cart/update` (change a quantity).
4. `POST /checkout` with `address_id=1` (customer account) → order number `OM...`,
   payment `COMPLETED`, stock reserved.
5. `GET /orders` → the new order is first.
6. `GET /orders/{id}` → full detail.

### Flow D — Reviews

1. `POST /products/20/reviews` as customer
   `{"rating":4,"title":"Great","comment":"Excellent product, smooth performance and great value for money"}`.
2. Verify `GET /products/{slug-of-20}` → `latest_reviews` shows the new review with
   your name, and `review_count` incremented.

### Flow E — Error cases (contract checks)

| Request | Expected |
|---|---|
| `POST /login` wrong password | `401` |
| `POST /register` duplicate email | `409` |
| `GET /products/does-not-exist` | `404` |
| `POST /products/1/reviews` logged out | `401` |
| `POST /checkout` logged out | `401` |
| `POST /checkout` with another user's address | `400` |
| `POST /register` short password | `422` |

---

## 6. Notes & gotchas

- **Cookie persistence:** Postman auto-manages `omnimart_session` and
  `cart_session` per domain. A *fresh login* in a new request window resets the
  session — subsequent authenticated calls must use that window/cookie jar.
- **Cart adoption:** a guest cart (`cart_session` cookie) is linked to your account
  at login/checkout. To see it, keep the same cookie jar between cart ops and login.
- **Email:** checkout with `email_receipt: true` returns success even if Brevo
  rejects delivery (IP authorization on the Brevo account) — check server logs.
- **Re-seeding:** to reset data, delete `omnimart.db` and restart the server
  (lifespan re-seeds automatically).
- **Rate/size limits:** `page_size ≤ 60`, `limit ≤ 24` (recommendations),
  cart line quantity ≤ 99.
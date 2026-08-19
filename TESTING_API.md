# 🧪 TESTING_API.md — How to Test Every API (with React Integration)

> A hands-on cookbook: start the server → copy-paste a test → see the result →
> copy-paste a React component that uses the same API.
> Every example uses **real values that exist in the seeded demo data**.

---

## 0. Before You Start

### Start the backend

```powershell
# 1. Build (if needed)
& "C:\Users\shubh\AppData\Local\Temp\opencode\tools\apache-maven-3.9.11\bin\mvn.cmd" package -DskipTests

# 2. Run
cmd /c "start /b java -jar ""D:\portfolio\group project\target\aistore-1.0.0.jar"" --server.port=8080"

# 3. Check it's alive (wait ~25s)
curl http://localhost:8080/health
# → {"success":true,"message":"OK","data":{"status":"UP","service":"omnimart-ai"},...}
```

### Demo accounts (seeded on every boot)

| Account | Email | Password | Role |
|---|---|---|---|
| Admin | `admin@omnimart.com` | `admin123` | ROLE_ADMIN |
| Shopper | `user@omnimart.com` | `password123` | ROLE_USER (Rahul Sharma) |

### Known-good test values

| What | Value |
|---|---|
| Product IDs for compare | `1`, `4`, `7` |
| Product slug (detail page) | `apple-iphone-15-pro-max-103` |
| NL search that returns 1 item | `camera phone under 40000` → Samsung Galaxy A55 5G |
| NL search that returns 3 items | `sony headphones with anc under 35000` → 3 Sony headphones |
| OTP code (dev mode) | returned as `devCode` in the `/api/otp/send` response |
| Payment methods | `UPI`, `CARD`, `COD` |
| Review | `rating` 1–5, `comment` required (≤ 2000 chars) |

### Response wrapper — every API answers in this shape

```json
{
  "success": true,
  "message": "OK",
  "data": { "...": "..." },
  "timestamp": "2026-08-19T12:00:00"
}
```
Always read `data`. Check `success` for errors.

---

## 1. The One Trick That Unlocks Everything: Sessions 🔑

Auth is **cookie-based** (no JWT). After `login` the server sets a `JSESSIONID`
cookie — every other call must send it back.

### In PowerShell (curl)

```powershell
# Login, keep the session cookie in a variable
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post `
  -ContentType "application/json" `
  -Body '{"email":"user@omnimart.com","password":"password123"}' `
  -WebSession $session

# Now this call is authenticated
Invoke-RestMethod -Uri "http://localhost:8080/cart" -WebSession $session
```

### In Postman
1. POST `http://localhost:8080/api/auth/login` with the JSON body
2. Postman automatically stores the `JSESSIONID` cookie
3. All subsequent requests just work ✓

### In the browser
Login via `/login` form page — cookie is set automatically.

---

## 2. Public APIs (no login needed)

### 2.1 Health & service info

| Test | Command |
|---|---|
| Health | `curl http://localhost:8080/health` |
| Info | `curl http://localhost:8080/` |

### 2.2 Product catalog

```powershell
# All products
curl "http://localhost:8080/products"

# Filter: category, brand, price range, rating, sort, pagination
curl "http://localhost:8080/products?category=Smartphones&minPrice=20000&maxPrice=50000&minRating=4&sort=price_asc&page=0&size=10"

# Search by keyword
curl "http://localhost:8080/products?q=sony"

# One product by slug
curl "http://localhost:8080/products/apple-iphone-15-pro-max-103"
# → data: { id, name, slug, price, rating, reviewCount, category, brand,
#           specifications: [...], reviews: [...], images: [...] }

# Similar products
curl "http://localhost:8080/products/1/similar"
```

### 2.3 AI chat

```powershell
curl -X POST http://localhost:8080/api/chat `
  -H "Content-Type: application/json" `
  -d '{"message":"recommend a gaming laptop under 80000"}'
# → data: { reply, products: [...], toolUsed: "searchProducts",
#           providerUsed: "nvidia|local|mock", conversationId, ... }

# Follow-up (reuse the SAME conversationId to keep context)
$conv = 'from-previous-response'
curl -X POST http://localhost:8080/api/chat `
  -H "Content-Type: application/json" `
  -d "{`"message`":`"compare the top two`",`"conversationId`":`"$conv`"}"

# Feedback question
curl -X POST http://localhost:8080/api/chat `
  -H "Content-Type: application/json" `
  -d '{"message":"what do customers say about iPhone 15 Pro Max?"}'
```

### 2.4 Natural-language search

```powershell
curl -X POST http://localhost:8080/api/search/nl `
  -H "Content-Type: application/json" `
  -d '{"query":"camera phone under 40000","limit":12}'
# → data: { query, products: [Samsung Galaxy A55 5G ...], filters: {...} }

# Autocomplete
curl "http://localhost:8080/api/search/autocomplete?q=iph"
# → data: [{value: "iphone 15 pro max", type: "product"}, ...]
```

### 2.5 Compare products

```powershell
curl "http://localhost:8080/api/compare/data?ids=1,4,7"
# → data: { products, specMatrix, keyDifferences,
#           priceComparison, bestPriceProductId, bestRatingProductId,
#           bestValueProductId, verdictSummary }
```

### 2.6 Recommendations (anonymous = popularity)

```powershell
curl "http://localhost:8080/api/recommendations?limit=8"
# → data: { strategy: "popularity", products: [{ ..., whyRecommended }] }
```

### 2.7 OTP (email verification)

```powershell
# Send (dev mode → the code comes back in the response)
curl -X POST http://localhost:8080/api/otp/send `
  -H "Content-Type: application/json" `
  -d '{"email":"test@example.com","name":"Test User","purpose":"REGISTRATION"}'
# → data: { expiresInSeconds: 300, devCode: "123456" }   ← devCode in dev mode

# Verify with the devCode
curl -X POST http://localhost:8080/api/otp/verify `
  -H "Content-Type: application/json" `
  -d '{"email":"test@example.com","otp":"123456"}'
# → data: { verified: true, attemptsRemaining: 5 }
```

### 2.8 Anonymous tracking (telemetry)

```powershell
curl -X POST http://localhost:8080/api/telemetry/interaction `
  -H "Content-Type: application/json" `
  -d '{"sessionId":"guest-abc-123","interactionType":"VIEW_PRODUCT","productId":1,"categoryName":"Smartphones","brandName":"Apple","durationSeconds":42}'
# → data: 12345 (the interaction id)
```

---

## 3. Logged-in APIs (use the session from §1)

### 3.1 Register → login → me

```powershell
# Register (password ≥ 8 chars; verify OTP first via §2.7)
curl -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"new@example.com","password":"secret123","fullName":"New User","phone":"9876543210"}'

# Login (demo)
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"user@omnimart.com","password":"password123"}'

# Who am I?
curl http://localhost:8080/api/auth/me
# → data: { id, email, fullName, roles: ["ROLE_USER"] }

# Logout
curl -X POST http://localhost:8080/api/auth/logout
```

### 3.2 Cart

```powershell
# Add (price is snapshotted at add-time)
$body = '{"productId":4,"quantity":1}'
Invoke-RestMethod -Uri "http://localhost:8080/cart/add" -Method Post `
  -ContentType "application/json" -Body $body -WebSession $session
# → data: { items: [{productId:4, name, unitPrice, quantity}], totalAmount, itemCount }

# View / count
Invoke-RestMethod -Uri "http://localhost:8080/cart" -WebSession $session
Invoke-RestMethod -Uri "http://localhost:8080/cart/count" -WebSession $session

# Update quantity (query params, note cartId)
Invoke-RestMethod -Uri "http://localhost:8080/cart/update?cartId=1&productId=4&quantity=2" `
  -Method Post -WebSession $session

# Remove
Invoke-RestMethod -Uri "http://localhost:8080/cart/remove?cartId=1&productId=4" `
  -Method Post -WebSession $session
```

### 3.3 Checkout & orders

```powershell
# Checkout (needs: an address id from /profile/addresses, payment method)
$body = '{"addressId":1,"paymentMethod":"UPI"}'
Invoke-RestMethod -Uri "http://localhost:8080/checkout" -Method Post `
  -ContentType "application/json" -Body $body -WebSession $session
# → data: { orderNumber: "OM...", finalAmount, status: "CONFIRMED", items: [...] }
#   Also: stock reserved, cart cleared, invoice email sent

# My orders
Invoke-RestMethod -Uri "http://localhost:8080/orders" -WebSession $session

# Order detail
Invoke-RestMethod -Uri "http://localhost:8080/orders/1" -WebSession $session

# Cancel (stock restored)
$body = '{"reason":"Changed my mind"}'
Invoke-RestMethod -Uri "http://localhost:8080/orders/1/cancel" -Method Post `
  -ContentType "application/json" -Body $body -WebSession $session
```

### 3.4 Profile & addresses & preferences

```powershell
# Add an address
$body = '{"fullName":"Rahul Sharma","streetAddress":"MG Road","city":"Bangalore","state":"Karnataka","postalCode":"560001","country":"India","phone":"9876543210","addressType":"HOME","isDefault":true}'
Invoke-RestMethod -Uri "http://localhost:8080/profile/addresses" -Method Post `
  -ContentType "application/json" -Body $body -WebSession $session
# → returns address with its id (you need this id for checkout!)

# AI preferences
$body = '{"preferredCategoriesJson":"[\"Laptops\",\"Gaming\"]","preferredBrandsJson":"[\"Dell\",\"HP\"]","minBudget":50000,"maxBudget":120000,"recommendationsEnabled":true}'
Invoke-RestMethod -Uri "http://localhost:8080/profile/preferences" -Method Put `
  -ContentType "application/json" -Body $body -WebSession $session

# View profile
Invoke-RestMethod -Uri "http://localhost:8080/profile" -WebSession $session
```

### 3.5 Write a review (AI analyzes it instantly)

```powershell
$body = '{"rating":5,"title":"Amazing camera!","comment":"Battery and camera are outstanding, highly recommended."}'
Invoke-RestMethod -Uri "http://localhost:8080/products/1/reviews" -Method Post `
  -ContentType "application/json" -Body $body -WebSession $session
# → data: { id, rating, title, comment, verifiedPurchase, sentiment: "Positive", ... }
# (a user can review a product only once → second attempt = 409/error)
```

### 3.6 Personalized recommendations (logged in)

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/recommendations?limit=8" -WebSession $session
# → data: { strategy: "hybrid", products: [{ ..., whyRecommended }] }
```

---

## 4. Admin APIs (login with `admin@omnimart.com / admin123`)

```powershell
$admin = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post `
  -ContentType "application/json" `
  -Body '{"email":"admin@omnimart.com","password":"admin123"}' `
  -WebSession $admin

# 17 KPIs: revenue, orders, top sellers, low stock, sentiment...
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/analytics-data" -WebSession $admin

# Ask the AI about the store
$body = '{"question":"What do customers complain about most?"}'
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/ask-ai" -Method Post `
  -ContentType "application/json" -Body $body -WebSession $admin
```

---

## 5. React Integration 🖥️

### 5.1 One API client to rule them all

`src/api/client.js`:

```js
const BASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

export async function api(path, { method = "GET", body, params } = {}) {
  const url = new URL(BASE + path);
  if (params) Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));

  const res = await fetch(url, {
    method,
    credentials: "include", // ← sends the JSESSIONID cookie (required for auth!)
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined,
  });

  const json = await res.json();
  if (!res.ok || json.success === false) throw new Error(json.message || "Request failed");
  return json.data; // ← everything is in `data`
}
```

> **CORS is already configured** for `localhost:*` and `*.onrender.com` with credentials.
> For Vite you can also add a proxy instead (`vite.config.js` → `server.proxy: { "/api": "http://localhost:8080" }`)
> and then just use `fetch("/api/...")` with same-origin cookies.

### 5.2 Product list with filters

```jsx
// src/components/ProductList.jsx
import { useEffect, useState } from "react";
import { api } from "../api/client";

export default function ProductList() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api("/products", { params: { category: "Smartphones", sort: "price_asc", size: 20 } })
      .then(setProducts)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Loading…</p>;
  return (
    <div className="grid">
      {products.map((p) => (
        <div key={p.id} className="card">
          <img src={p.primaryImageUrl} alt={p.name} />
          <h3>{p.name}</h3>
          <p>₹{p.price.toLocaleString("en-IN")} · ⭐ {p.rating} ({p.reviewCount})</p>
        </div>
      ))}
    </div>
  );
}
```

### 5.3 AI chat widget

```jsx
// src/components/ChatWidget.jsx
import { useState } from "react";
import { api } from "../api/client";

export default function ChatWidget() {
  const [conversationId, setConversationId] = useState(null); // reuse → follow-ups work!
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");

  async function send() {
    const text = input.trim();
    if (!text) return;
    setInput("");
    setMessages((m) => [...m, { sender: "user", content: text }]);

    const data = await api("/api/chat", {
      method: "POST",
      body: { message: text, conversationId }, // pass null on first message
    });
    setConversationId(data.conversationId);
    setMessages((m) => [...m, { sender: "ai", content: data.reply }]);
  }

  return (
    <div className="chat">
      {messages.map((m, i) => (
        <div key={i} className={m.sender}>{m.content}</div>
      ))}
      <input value={input} onChange={(e) => setInput(e.target.value)}
             onKeyDown={(e) => e.key === "Enter" && send()} placeholder="Ask about products…" />
      <button onClick={send}>Send</button>
    </div>
  );
}
```

### 5.4 Login + cart badge (session flow)

```jsx
// src/components/Header.jsx
import { useEffect, useState } from "react";
import { api } from "../api/client";

export default function Header() {
  const [user, setUser] = useState(null);
  const [cartCount, setCartCount] = useState(0);

  // On load: is there a session? how full is the cart?
  useEffect(() => {
    api("/api/auth/me").then((u) => {
      setUser(u);
      if (u) api("/cart/count").then((d) => setCartCount(d.count));
    });
  }, []);

  async function login(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await api("/api/auth/login", {
      method: "POST",
      body: { email: fd.get("email"), password: fd.get("password") },
    });
    window.location.reload(); // cookie now set → reload reads the session
  }

  return (
    <header>
      {user ? (
        <span>👋 {user.fullName} · 🛒 {cartCount}</span>
      ) : (
        <form onSubmit={login}>
          <input name="email" type="email" placeholder="email" required />
          <input name="password" type="password" placeholder="password" required />
          <button>Login</button>
        </form>
      )}
    </header>
  );
}
```

### 5.5 Add to cart → checkout

```jsx
// src/components/ProductPage.jsx — add to cart
import { api } from "../api/client";

export function AddToCartButton({ productId }) {
  const [busy, setBusy] = useState(false);
  return (
    <button disabled={busy} onClick={async () => {
      setBusy(true);
      try {
        await api("/cart/add", { method: "POST", body: { productId, quantity: 1 } });
        alert("Added to cart!");
      } catch (err) { alert(err.message); } finally { setBusy(false); }
    }}>
      Add to Cart
    </button>
  );
}

// src/components/Checkout.jsx — place order
export async function placeOrder(addressId, paymentMethod) {
  return api("/checkout", { method: "POST", body: { addressId, paymentMethod } });
}
// → returns { orderNumber, finalAmount, status } — show it as the order confirmation
```

### 5.6 Compare three products

```jsx
// src/components/CompareView.jsx
import { useEffect, useState } from "react";
import { api } from "../api/client";

export default function CompareView({ ids }) { // e.g. [1, 4, 7]
  const [data, setData] = useState(null);

  useEffect(() => {
    api("/api/compare/data", { params: { ids: ids.join(",") } }).then(setData);
  }, [ids]);

  if (!data) return <p>Comparing…</p>;
  return (
    <div>
      <table>
        <thead><tr>
          {data.products.map((p) => <th key={p.id}>{p.name}</th>)}
        </tr></thead>
        <tbody>
          {Object.entries(data.specMatrix).map(([spec, values]) => (
            <tr key={spec}><td>{spec}</td>
              {values.map((v, i) => <td key={i}>{v}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
      <p>{data.verdictSummary}</p>
    </div>
  );
}
```

### 5.7 Recommendations row

```jsx
// src/components/RecommendedRow.jsx
import { useEffect, useState } from "react";
import { api } from "../api/client";

export default function RecommendedRow() {
  const [items, setItems] = useState([]);
  useEffect(() => {
    api("/api/recommendations", { params: { limit: 8 } }).then((d) => setItems(d.products));
  }, []);
  return (
    <section>
      <h2>Recommended for you</h2>
      {items.map((p) => (
        <div key={p.id} className="card">
          <img src={p.primaryImageUrl} />
          <h4>{p.name}</h4>
          <small>{p.whyRecommended}</small> {/* ← the AI tells the user WHY */}
        </div>
      ))}
    </section>
  );
}
```

---

## 6. Cheat Sheet — "I want to test…"

| I want to… | Endpoint | Login needed? |
|---|---|---|
| See the store works | `GET /health` | — |
| Browse products | `GET /products?category=Smartphones` | — |
| Ask the AI | `POST /api/chat` | — |
| Search in plain English | `POST /api/search/nl` | — |
| Compare phones | `GET /api/compare/data?ids=1,4,7` | — |
| Get recommendations | `GET /api/recommendations?limit=8` | — (personalized if logged in) |
| Verify email with OTP | `POST /api/otp/send` → `POST /api/otp/verify` | — |
| Register / login / logout | `POST /api/auth/register·login·logout`, `GET /api/auth/me` | — / ✓ / ✓ |
| Add to cart | `POST /cart/add` | ✓ |
| Change quantity | `POST /cart/update?cartId=1&productId=4&quantity=2` | ✓ |
| Buy something | `POST /checkout` | ✓ |
| See my orders | `GET /orders` | ✓ |
| Write a review | `POST /products/1/reviews` | ✓ |
| See admin KPIs | `GET /api/admin/analytics-data` | ✓ (admin) |

**Full endpoint list & architecture:** [BACKEND_SCHEMA.md](BACKEND_SCHEMA.md) ·
**The 25 tables behind them:** [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)
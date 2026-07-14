# Bakeaura

**Hyperlocal AI-powered social commerce platform for home bakers.**

Customers discover nearby home bakers, browse baked goods, place instant or scheduled pre-orders, pay via Razorpay, and track delivery in real time. Sellers manage their shop, products, and orders. Influencers earn commission through referral codes. Admins govern the full platform.

**Primary demo feature:** AI Cake Design Assistant — a customer describes their dream cake in natural language, Gemini generates a written design brief and a visual image, which is then sent to a nearby baker as a custom order request.

> Built by **Sneha Kalra**, final-year B.Tech IT student — targeting Java Developer and full-stack roles.

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    Browser                      │
│        React 18.3.1 + Vite 5 (port 5173)        │
└──────────────────────┬──────────────────────────┘
                       │ HTTP / WebSocket
┌──────────────────────▼──────────────────────────┐
│     Spring Boot 3.5 + Java 21 (port 8080)       │
│  JWT auth · REST · STOMP/SockJS WebSocket       │
│  Bucket4j rate limiting · Resilience4j CBs      │
│  @Async email/SMS notifications                 │
└────────┬──────────────────┬──────────────────────┘
         │                  │
┌────────▼───────┐  ┌───────▼────────┐
│   PostgreSQL   │  │    Redis       │
│  20 JPA tables │  │  Cart · JWT    │
│                │  │  refresh tokens│
│                │  │  @Cacheable    │
└────────────────┘  └────────────────┘

External: Cloudinary · Razorpay · Google Gemini · Fast2SMS · Gmail SMTP
```

**Pattern: Modular monolith with clean microservice boundaries.** Each domain is fully encapsulated with its own controller, service, and repository. Services never call another package's repository directly — always through the service layer. Any module can be extracted into an independent microservice without restructuring the rest.

---

## Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5.0 |
| Security | Spring Security + JWT (jjwt 0.12.3), BCrypt |
| Database | PostgreSQL · Spring Data JPA (`ddl-auto: update`) |
| Cache / Sessions | Redis — `@Cacheable` (TTL 600s) · cart · refresh tokens |
| Payments | Razorpay 1.4.5 test mode · HMAC-SHA256 webhook |
| Media | Cloudinary SDK 1.38.0 (server-mediated upload) |
| AI | Spring AI BOM 1.1.4 + `spring-ai-starter-model-google-genai` — Gemini 2.5 Flash |
| Real-time | STOMP over SockJS WebSocket |
| Rate limiting | Bucket4j 8.10.1 (3 AI requests / 10 min per IP) |
| Fault tolerance | Resilience4j 2.2.0 circuit breakers on Razorpay, Cloudinary, Gemini |
| Email / SMS | JavaMailSender + Gmail SMTP · Fast2SMS — all `@Async` |
| Distance | Haversine formula in `MapService.java` — no Google Maps, no API key |
| Async pool | Core 4 / max 8 threads (`AsyncConfig.java`) |
| Monitoring | Spring Boot Actuator (`/actuator/health`) |
| Containerisation | Docker + Docker Compose |

### Frontend

| Layer | Technology |
|---|---|
| Framework | React 18.3.1 + Vite 5.4.19 |
| Routing | React Router v6 |
| State | Zustand 4.5.7 with `persist` (auth store) |
| HTTP | Axios 1.9.0 with JWT refresh interceptor (silent 401 recovery) |
| Forms | React Hook Form + Zod 3.25.28 |
| Charts | Recharts 3.9.1 (analytics dashboards) |
| WebSocket | `@stomp/stompjs` 7.1.1 + `sockjs-client` 1.6.1 |
| Styling | Vanilla CSS — Cream / Espresso / Mocha / Sienna / Gold palette |
| Fonts | Quicksand (body) · Playfair Display (headings) |
| Icons | lucide-react |
| Location | Browser Geolocation API + OpenStreetMap Nominatim (free, no key) |
| Payments | Razorpay JS SDK |

### Hosting targets (all free tier)

| Service | Platform |
|---|---|
| Frontend | Vercel |
| Backend | Railway or Render |
| Database | Railway PostgreSQL or Supabase |
| Redis | Upstash |
| Media | Cloudinary |

---

## Four Roles

All roles share a single `users` table — no separate tables per role.

| Role | How you get it | Key capabilities |
|---|---|---|
| **CUSTOMER** | Register with name + email + password | Browse, cart, Razorpay checkout, AI cake designer, order tracking, reviews, favorites |
| **SELLER** | Apply from CUSTOMER account (phone required) | Shop management, products, orders, custom order inbox, reels, analytics |
| **INFLUENCER** | Apply from CUSTOMER account (phone + social link + niche required) | Referral code, commission per referral, wallet, collaborations, analytics |
| **ADMIN** | Created directly via SQL | User management, role application review, category management, influencer payouts |

Email verification is required before placing orders or applying for roles. Browsing is free.

---

## Key Features

### AI Cake Design Assistant (primary demo)
1. Customer describes a dream cake — occasion, number of servings, budget, design ideas
2. `POST /api/ai/cake-design/preview` → Gemini 2.5 Flash returns a written design brief + base64 image (**nothing persisted yet**)
3. Customer reviews the preview; can regenerate
4. `POST /api/ai/cake-design/confirm` → image uploaded to Cloudinary → `custom_order_requests` row created → seller notified via WebSocket + DB notification
5. AI rate limited: 3 requests per 10 minutes per IP (Bucket4j)
6. Gemini circuit breaker wraps both calls (Resilience4j)

### Order lifecycle
`PENDING` → `CONFIRMED` → `PREPARING` → `OUT_FOR_DELIVERY` → `DELIVERED` / `CANCELLED`

- Stock reserved on order creation (`@Version` for optimistic locking), decremented only at payment capture
- Delivery address copied as a plain String at order creation — frozen permanently (historical orders are immutable)
- WebSocket push to customer and seller on every status change
- SMS sent at `CONFIRMED` and `OUT_FOR_DELIVERY`; email at `CONFIRMED` and `DELIVERED`

### Payment flow (Razorpay)
1. `POST /api/payments/create-order` — creates Razorpay order, returns `razorpay_order_id`
2. Customer pays in Razorpay JS widget
3. `POST /api/payments/verify` — client-side HMAC verification → triggers stock decrement + cart clear
4. `POST /api/payments/webhook` — server-side webhook for redundancy (idempotent — only `PENDING → CAPTURED` allowed)

Both `verifyPayment` and `handlePaymentCaptured` guard with `status == PENDING`. A `FAILED` or already-`CAPTURED` payment cannot be re-processed by either path.

### Reel feed (social layer)
Sellers and influencers upload short video reels. Feed is publicly accessible (no login needed). Engagement counters (view, like, save) use atomic `@Modifying @Query` at DB level. Feed ranking in `ContentService.getRankedFeed`:

```
score = (recency × 0.40) + (likes × 0.25) + (views × 0.20) + (sellerRating/5 × 0.15)
```

All components normalised to [0, 1] before weighting. Seller rating sourced via live `AVG()` query — not a stale stored column.

### Referral system
`OrderService` publishes `OrderCreatedEvent`. `ReferralOrderService` listens independently via Spring `@EventListener`. `OrderService` has zero knowledge of referrals — clean modular boundary.

### Wallet
Balance is never stored as a column. Always derived via:
```sql
SELECT SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)
FROM wallet_transactions WHERE influencer_id = ?
```
Eliminates double-entry accounting bugs.

### Notifications (4 layers)
1. Persisted to `notifications` table (inbox, unread count)
2. WebSocket push to `/topic/users/{userId}/notifications` (when online)
3. Email via Gmail SMTP (verification, order confirmed, delivered)
4. SMS via Fast2SMS (`CONFIRMED` and `OUT_FOR_DELIVERY` only)

All notification writes are `@Async`.

### WebSocket authentication
JWT is validated in `WebSocketAuthInterceptor` on STOMP `CONNECT` (not in Spring Security). On `SUBSCRIBE`, the interceptor checks topic ownership — a user cannot subscribe to `/topic/users/{otherId}/notifications`.

---

## Database (20 tables)

| Table | Purpose |
|---|---|
| `users` | All four roles in one table. Zero JPA relationship annotations — all FKs owned by child entities |
| `seller_profiles` | Shop info, delivery radius, ratings |
| `influencer_profiles` | Niche, social links, referral stats |
| `categories` | Product categories (admin-managed, `@Cacheable`) |
| `products` | Product listings with `@Version` for optimistic locking |
| `addresses` | Saved delivery addresses (single `addressLine` field) |
| `orders` | Order state machine with denormalized delivery address |
| `order_items` | Line items with `price_at_purchase` snapshot |
| `payments` | Razorpay payment records (idempotent state machine) |
| `reviews` | Seller-level reviews, only after `DELIVERED` |
| `reels` | Short video content with engagement counters |
| `favorites` | Product or seller favorites (one of two FKs non-null) |
| `notifications` | Persistent notification inbox |
| `role_applications` | SELLER / INFLUENCER applications (phone stored here until approval) |
| `referral_codes` | Unique codes per influencer |
| `referral_orders` | Commission earned per referral |
| `influencer_collaborations` | Seller–influencer collaboration requests |
| `wallet_transactions` | CREDIT / DEBIT entries (balance derived, never stored) |
| `custom_order_requests` | AI-generated + manual cake design requests |
| `payout_requests` | Influencer payout lifecycle (PENDING → APPROVED → PAID) |

Cart and refresh tokens are Redis-only — no JPA tables.

---

## Project Structure

```
Bakeaura/
├── backend/
│   └── src/main/java/com/bakeaura/
│       ├── address/          # Saved delivery addresses
│       ├── admin/            # Admin dashboard, user management, analytics
│       ├── ai/               # AI Cake Design Assistant (Gemini)
│       ├── auth/             # JWT auth, register, login, email verification
│       ├── cart/             # Redis-backed cart
│       ├── category/         # Product categories (@Cacheable)
│       ├── cloudinary/       # Media upload (images + videos)
│       ├── common/           # ApiResponse<T> wrapper
│       ├── config/           # SecurityConfig, WebSocket, CORS, RateLimit, Gemini
│       ├── content/          # Reel feed ranking algorithm
│       ├── customorder/      # Custom cake order requests
│       ├── enums/            # All enums (Role, OrderStatus, PaymentStatus, etc.)
│       ├── exception/        # GlobalExceptionHandler
│       ├── favorite/         # Product + seller favorites
│       ├── influencer/       # Influencer profiles, collaborations
│       ├── map/              # Haversine distance + ETA estimation
│       ├── notification/     # DB + WebSocket + Email + SMS notifications
│       ├── order/            # Order lifecycle, state machine, OrderCreatedEvent
│       ├── payment/          # Razorpay + webhook (idempotent)
│       ├── payout/           # Influencer payout requests (full lifecycle)
│       ├── product/          # Products (optimistic locking via @Version)
│       ├── reel/             # Short video reels + engagement counters
│       ├── referral/         # Referral codes + commission pipeline
│       ├── review/           # Seller reviews (post-delivery only)
│       ├── roleapplication/  # SELLER / INFLUENCER applications
│       ├── seller/           # Seller profiles, analytics
│       ├── user/             # User profile, email/password change
│       ├── wallet/           # Wallet transactions (balance derived via SQL)
│       └── websocket/        # STOMP order tracking
│
└── frontend/
    └── src/
        ├── api/              # 25 Axios API modules (one per domain)
        ├── components/       # Reusable UI components (Navbar, Modal, etc.)
        ├── pages/            # 33 page/component files
        ├── store/            # Zustand auth store (with persist)
        ├── utils/            # Formatting helpers
        └── router.jsx        # Full route tree with RequireAuth role guards
```

---

## Local Setup

### Prerequisites
- Java 21 · Maven
- Node.js 18+ · npm
- PostgreSQL running locally
- Redis running locally

External accounts needed:
- [Cloudinary](https://cloudinary.com) — free tier
- [Razorpay](https://razorpay.com) — test mode key pair + webhook secret
- [Google AI Studio](https://aistudio.google.com) — Gemini API key
- Gmail with an [App Password](https://myaccount.google.com/apppasswords)
- [Fast2SMS](https://www.fast2sms.com) — free developer tier (optional for local testing)

### Backend

```bash
cd backend
```

Create `backend/.env` (never commit — already in `.gitignore`):

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bakeaura
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=your-256-bit-secret-here

MAIL_USERNAME=your@gmail.com
MAIL_PASSWORD=your_gmail_app_password

RAZORPAY_KEY_ID=rzp_test_xxxx
RAZORPAY_KEY_SECRET=your_razorpay_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

GEMINI_API_KEY=your_gemini_key
FAST2SMS_API_KEY=your_fast2sms_key

APP_FRONTEND_URL=http://localhost:5173
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

```bash
./mvnw spring-boot:run
```

Backend starts on `http://localhost:8080`. Hibernate auto-creates all 20 tables on first run (`ddl-auto: update`).

Create the admin user directly in PostgreSQL:

```sql
INSERT INTO users (name, email, password, role, is_active, is_email_verified, created_at, updated_at)
VALUES ('Admin', 'admin@bakeaura.com',
        '$2a$10$<bcrypt_hash_of_your_password>',
        'ADMIN', true, true, NOW(), NOW());
```

Generate the BCrypt hash with any online BCrypt tool (e.g. bcrypt.online) or via `new BCryptPasswordEncoder().encode("yourpassword")` in a quick Spring test.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on `http://localhost:5173`.

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | — | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | — | DB username |
| `SPRING_DATASOURCE_PASSWORD` | — | DB password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | — | HS256 signing key (min 64 chars recommended) |
| `MAIL_USERNAME` | — | Gmail address for transactional email |
| `MAIL_PASSWORD` | — | Gmail App Password |
| `RAZORPAY_KEY_ID` | — | Razorpay test key ID |
| `RAZORPAY_KEY_SECRET` | — | Razorpay test key secret |
| `RAZORPAY_WEBHOOK_SECRET` | — | HMAC-SHA256 webhook signature secret |
| `CLOUDINARY_CLOUD_NAME` | — | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | — | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | — | Cloudinary API secret |
| `GEMINI_API_KEY` | — | Google Gemini API key (AI cake design) |
| `FAST2SMS_API_KEY` | — | Fast2SMS API key (Indian SMS) |
| `APP_FRONTEND_URL` | `http://localhost:5173` | Used in email verification links |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed CORS origins |
| `DELIVERY_MAX_RADIUS_KM` | `10` | Max delivery radius |
| `DELIVERY_AVERAGE_SPEED_KMPH` | `25` | Assumed delivery speed for ETA |
| `DELIVERY_ROAD_DISTANCE_FACTOR` | `1.3` | Haversine-to-road distance correction |

JWT: access token 15 min · refresh token 7 days (stored in Redis, keyed by user ID).

---

## Key API Endpoints

All authenticated endpoints require `Authorization: Bearer <access_token>`.

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register as CUSTOMER |
| POST | `/api/auth/login` | Public | Login, get JWT |
| POST | `/api/auth/refresh` | Public | Refresh access token |
| GET | `/api/products` | Public | Browse products |
| GET | `/api/sellers` | Public | Browse sellers |
| GET | `/api/reels/feed` | Public | Browse reel feed |
| GET | `/api/influencers` | Public | Browse influencers |
| POST | `/api/orders/from-cart` | CUSTOMER | Place order from cart |
| POST | `/api/payments/verify` | CUSTOMER | Verify Razorpay payment client-side |
| POST | `/api/payments/webhook` | Public (HMAC-verified) | Razorpay webhook |
| POST | `/api/ai/cake-design/preview` | CUSTOMER | Generate AI cake design (no persist) |
| POST | `/api/ai/cake-design/confirm` | CUSTOMER | Confirm → Cloudinary upload → custom order |
| GET | `/api/seller/orders` | SELLER | Incoming orders |
| PATCH | `/api/orders/{id}/status` | SELLER / ADMIN | Update order status |
| GET | `/api/seller/dashboard/analytics` | SELLER | Revenue, best seller, avg rating |
| GET | `/api/influencer/dashboard/analytics` | INFLUENCER | Earnings, referral stats |
| GET | `/api/admin/users` | ADMIN | Paginated user list |
| GET | `/api/admin/dashboard` | ADMIN | Platform-wide counts |
| POST | `/api/admin/role-applications/{id}/approve` | ADMIN | Approve SELLER / INFLUENCER application |

WebSocket endpoint: `ws://localhost:8080/ws`
- Notifications: `/topic/users/{userId}/notifications`
- Order tracking: `/topic/order/{orderId}`
- Reel processing: `/topic/reels/{sellerId}`

---

## Architecture Decisions (interview-ready)

| Decision | What and why |
|---|---|
| **User entity has zero JPA relationship annotations** | All FKs are owned by child entities. No `@OneToMany` on `User`. Prevents accidentally lazy-loading the entire platform through a single user lookup. |
| **JWT subject is numeric user ID** | Tokens stay valid even if the user changes their email. Email changes trigger refresh-token revocation — all existing sessions are forced to re-login. |
| **Wallet balance never stored** | Always derived via `SUM(CREDIT) - SUM(DEBIT)` over `wallet_transactions`. Eliminates balance drift bugs. |
| **Stock reserved on create, deducted on payment** | `@Version` on `Product` for optimistic locking at order creation. `PaymentService.reduceStock()` runs only after Razorpay confirms payment. Prevents inventory leak from abandoned payments. |
| **Delivery address denormalised on Order** | Address copied as a plain String at order creation and frozen. Historical orders are immutable even if the customer later changes their saved address. |
| **AI preview-then-confirm two-step** | `POST /preview` returns brief + base64 image without persisting anything. `POST /confirm` triggers Cloudinary upload + DB row + notification. Prevents junk rows from abandoned previews. |
| **Payment idempotency is PENDING-only** | Both client verify and webhook handler check `status == PENDING` before processing. A `FAILED` or already-`CAPTURED` payment cannot be re-processed by any path. |
| **WebSocket auth via ChannelInterceptor** | JWT validated on STOMP `CONNECT`. Topic subscriptions for `/topic/users/{id}/notifications` are ownership-checked — a user cannot subscribe to another user's stream. |
| **Referral pipeline is event-driven** | `OrderService` publishes `OrderCreatedEvent`. `ReferralOrderService` listens independently via `@EventListener`. `OrderService` has zero knowledge of referrals — clean modular monolith boundary. |
| **isActive checked on every authenticated request** | `JwtAuthFilter` does a live DB lookup per request. Deactivating a user takes effect immediately, not after the 15-min token window. At scale: replace with a Redis blocklist (`SET blocked:{userId} EX 900`). |
| **Phone staged on role application** | Phone is saved to `role_applications.phone` on submission, copied to `users.phone` only on admin approval. A rejected application leaves the user profile unchanged. |
| **Deactivation not hard-delete** | Admin "delete" sets `is_active = false`. Preserves order history and referential integrity. |
| **Collaboration re-request after rejection** | A new `PENDING` row is created; old `REJECTED` row is preserved as audit history. `findFirstByInfluencerIdAndSellerIdAndStatusOrderByCreatedAtDesc` ensures only the latest `PENDING` record is acted on. |
| **No Google Maps** | Haversine formula in pure Java (`MapService.java`) calculates distance and ETA. No external API, no key, no cost. |

---

## Known Limitations

| Item | Detail |
|---|---|
| Refund API | Cancelling a paid order marks payment as `REFUNDED` in DB and notifies the customer. Actual Razorpay refund API call is not wired — needs to be added before going live. |
| Admin pagination UI | Admin user list shows ≤50 users per page. No "Load more" button yet. |
| Reel comments / share | Buttons are present in the UI but not yet functional. |
| No Tailwind/shadcn | Frontend uses vanilla CSS. Planned enhancement. |
| No 404 catch-all route | Direct navigation to an unknown URL shows a blank page. |
| Integration test | `BakeauraBackendApplicationTests.contextLoads` requires live DB + Redis + all API keys. Fails in a clean dev environment. All 94 unit and slice tests pass. |

---

## GitHub

[https://github.com/sneha-860/Bakeaura](https://github.com/sneha-860/Bakeaura)

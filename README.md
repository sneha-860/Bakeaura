# Bakeaura

A full-stack home-bakery marketplace connecting customers with local home bakers (sellers), with an influencer discovery layer for Reels-based content and an AI-powered cake design assistant.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick Start — Docker Compose](#quick-start--docker-compose)
- [Local Development Setup](#local-development-setup)
- [Environment Variables](#environment-variables)
- [API Overview](#api-overview)
- [Key Design Decisions](#key-design-decisions)
- [Known Limitations](#known-limitations)

---

## Overview

Bakeaura is a monorepo with a Spring Boot backend and a React frontend. It supports four roles:

| Role | How acquired |
|---|---|
| **CUSTOMER** | Default on registration |
| **SELLER** | Apply from profile → Admin approves |
| **INFLUENCER** | Apply from profile → Admin approves |
| **ADMIN** | Set directly in database |

Customers browse products, add to cart, check out with Razorpay, and track orders in real time via WebSocket. Sellers manage products, incoming orders, and custom order requests. Influencers post short-form Reels, earn referral commissions, and request payouts. An AI assistant lets customers generate a cake design image from a text brief and turn it directly into a custom order request.

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    Browser                      │
│           React 18 + Vite (port 80)             │
└──────────────────────┬──────────────────────────┘
                       │ HTTP / WebSocket
┌──────────────────────▼──────────────────────────┐
│        Spring Boot 3.5 API (port 8080)          │
│  JWT auth · REST · STOMP/SockJS WebSocket       │
│  Bucket4j rate limiting · Resilience4j CBs      │
│  @Async email/SMS notifications                 │
└────────┬──────────────────┬──────────────────────┘
         │                  │
┌────────▼───────┐  ┌───────▼────────┐
│  PostgreSQL 15 │  │   Redis 7      │
│  (port 5433)   │  │  (port 6379)   │
│  20 JPA tables │  │  Cart · JWT    │
│                │  │  refresh tokens│
│                │  │  Cache TTL 10m │
└────────────────┘  └────────────────┘

External services: Cloudinary · Razorpay · Gemini AI · Fast2SMS · Gmail SMTP
```

---

## Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5.0 |
| Security | Spring Security + JWT (jjwt 0.12.3), BCrypt |
| Database | PostgreSQL 15, Spring Data JPA (ddl-auto: update) |
| Cache / Session | Redis 7 (Spring Cache TTL 10 min, cart, refresh tokens) |
| Payments | Razorpay 1.4.5 (HMAC-SHA256 webhook verification) |
| Media | Cloudinary SDK 1.38.0 (server-mediated upload) |
| AI | Spring AI 1.1.4 + Google Gemini 2.5 Flash (text & image generation) |
| Real-time | STOMP over SockJS WebSocket |
| Rate limiting | Bucket4j 8.10.1 |
| Fault tolerance | Resilience4j 2.2.0 circuit breakers (Razorpay, Cloudinary, Gemini) |
| Notifications | Fast2SMS (SMS) + Gmail SMTP (email) via `@Async` thread pool |
| Distance | Haversine math (`MapService`) — no external maps API |
| Build | Maven Wrapper (`./mvnw`) |
| Containerization | Docker (multi-stage) |

### Frontend

| Layer | Technology |
|---|---|
| Framework | React 18 + Vite 5 |
| Routing | React Router DOM 6 |
| State | Zustand 4 with `persist` (auth store) |
| HTTP | Axios with JWT refresh-token interceptor |
| Forms | react-hook-form + zod validation |
| Charts | Recharts 3 |
| WebSocket | `@stomp/stompjs` + sockjs-client |
| UI | Vanilla CSS — Cream/Espresso/Mocha/Sienna/Gold palette |
| Fonts | Quicksand (body) · Playfair Display (headings) |
| Icons | lucide-react |
| Toasts | react-hot-toast |

---

## Features

### Customer
- Register / email verification / login / refresh tokens / logout
- Browse products with category filtering and search
- View seller storefronts with ratings and reviews
- Add to cart (Redis-backed), checkout with Razorpay
- Real-time order status updates via WebSocket
- Submit and view custom order requests (AI-generated or manual)
- **AI Cake Design Assistant** — type a design brief → get a Gemini-generated preview image → confirm to create a custom order with the image uploaded to Cloudinary
- Manage saved addresses, favourites, notifications
- Leave seller reviews on delivered orders
- Apply to become a Seller or Influencer
- Enter referral codes at checkout to credit influencer wallets

### Seller
- Dashboard with shop open/closed toggle and shop profile editing
- Product management (create, update, delete with Cloudinary image upload)
- Incoming order management with status updates (triggers customer WebSocket push)
- Custom order request inbox — quote, accept, or reject
- Seller-initiated collaboration requests to influencers
- Analytics: revenue (all-time / monthly / weekly), orders by status, best-selling product, average rating

### Influencer
- Upload short-form Reels (video + Cloudinary)
- Public Reel feed with ranked algorithm: `0.40×recency + 0.25×likes + 0.20×views + 0.15×sellerRating`
- Referral code generation; commissions credited to wallet when customers check out with the code
- Collaboration request inbox — approve or reject seller requests
- Wallet: balance (derived via `SUM(CREDIT) - SUM(DEBIT)`), transaction history, payout requests
- Analytics: total earnings, wallet balance, referral order count, active collaborations

### Admin
- Dashboard: platform-wide stats
- User management: list all users, toggle active/inactive
- Role application review: approve or reject SELLER/INFLUENCER applications (auto-provisions the profile on approval)
- Payout management: approve or reject pending payout requests; mark approved payouts as paid

---

## Project Structure

```
Bakeaura/
├── backend/                        # Spring Boot application
│   ├── src/main/java/com/bakeaura/
│   │   ├── ai/                     # Cake design assistant (controller, service, DTOs)
│   │   ├── address/                # Address CRUD
│   │   ├── admin/                  # Admin dashboard + user management
│   │   ├── auth/                   # JWT auth, refresh tokens, email verification
│   │   ├── cart/                   # Redis cart
│   │   ├── category/               # Product categories (ADMIN-managed)
│   │   ├── common/                 # ApiResponse envelope
│   │   ├── config/                 # Redis, Cloudinary, Razorpay, CORS, WebSocket, Gemini config
│   │   ├── content/                # Ranked Reel feed
│   │   ├── customorder/            # Custom order requests
│   │   ├── enums/                  # Role, OrderStatus, PaymentStatus, etc.
│   │   ├── exception/              # GlobalExceptionHandler
│   │   ├── favorite/               # Favourites
│   │   ├── filter/                 # JwtAuthFilter, RateLimitFilter
│   │   ├── influencer/             # Influencer profile, collaborations, public directory
│   │   ├── map/                    # Haversine distance + ETA estimation
│   │   ├── notification/           # Persistent notifications + WebSocket push
│   │   ├── order/                  # Order lifecycle, OrderCreatedEvent
│   │   ├── payout/                 # Influencer payout requests + admin approval
│   │   ├── payment/                # Razorpay order creation, webhook, verification
│   │   ├── product/                # Product CRUD
│   │   ├── reel/                   # Reel upload + feed
│   │   ├── referral/               # Referral codes + ReferralOrderService (@EventListener)
│   │   ├── review/                 # Seller reviews
│   │   ├── roleapplication/        # SELLER/INFLUENCER applications
│   │   ├── seller/                 # Seller profile, analytics, public directory
│   │   ├── user/                   # User profile, password, email change
│   │   ├── wallet/                 # WalletTransaction, WalletService
│   │   └── websocket/              # STOMP order tracking
│   └── src/main/resources/
│       └── application.yml         # All config (env-var driven)
│
├── frontend/                       # React + Vite SPA
│   └── src/
│       ├── api/                    # 17 Axios modules (one per feature)
│       │   ├── axios.js            # Base instance + refresh-token interceptor
│       │   ├── auth.js / users.js / products.js / orders.js / payments.js
│       │   ├── cart.js / addresses.js / favourites.js / reviews.js
│       │   ├── sellers.js / influencers.js / content.js / reels.js
│       │   ├── customOrders.js / collaborations.js
│       │   ├── wallet.js / payouts.js / notifications.js
│       │   ├── cakeDesign.js / admin.js / roleApplications.js / categories.js
│       │   ├── enums.js            # Shared enum constants (Role, OrderType, etc.)
│       │   └── websocket.js        # createSocketClient() helper
│       ├── components/             # Reusable UI components (Navbar, Modal, etc.)
│       ├── pages/                  # Route-level pages grouped by domain
│       │   ├── AuthPages.jsx       # Login, Register, email verification
│       │   ├── ProductsPage.jsx / ProductDetailPage.jsx
│       │   ├── CartCheckoutPages.jsx
│       │   ├── OrdersPages.jsx
│       │   ├── DirectoryPages.jsx  # Sellers, seller storefront, influencers, influencer profile
│       │   ├── DashboardPages.jsx  # 7 role dashboards + admin pages
│       │   ├── UserPages.jsx       # Profile, addresses, favourites, custom orders, notifications
│       │   ├── ReelFeedPage.jsx / ReelUploadPage.jsx
│       │   ├── CakeDesignPage.jsx  # AI cake design assistant
│       │   └── seller/ + influencer/  # Analytics pages
│       ├── store/useAuthStore.js   # Zustand auth store with persist
│       └── router.jsx              # All routes with RequireAuth role guards
│
├── docker-compose.yml              # Orchestrates frontend, backend, postgres, redis
├── .env.example                    # Template for Docker Compose secrets
└── .env                            # Your local secrets (git-ignored)
```

---

## Prerequisites

- **Docker + Docker Compose** (recommended) — no local Java or Node needed
- **OR** for local dev:
  - Java 21+
  - Node.js 18+ / npm
  - PostgreSQL 15 running locally
  - Redis 7 running locally

External accounts required:
- [Cloudinary](https://cloudinary.com) — free tier is sufficient
- [Razorpay](https://razorpay.com) — test mode key pair + webhook secret
- [Google AI Studio](https://aistudio.google.com) — Gemini API key (for AI cake design)
- Gmail account with an [App Password](https://myaccount.google.com/apppasswords) (for email)
- [Fast2SMS](https://www.fast2sms.com) API key (for SMS; optional for local testing)

---

## Quick Start — Docker Compose

1. Copy the env template and fill in your secrets:

```bash
cp .env.example .env
```

Edit `.env`:

```env
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_strong_password
JWT_SECRET=a_long_random_string_at_least_64_chars
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
MAIL_USERNAME=youremail@gmail.com
MAIL_PASSWORD=your_gmail_app_password
APP_CORS_ALLOWED_ORIGINS=http://localhost
APP_FRONTEND_URL=http://localhost
```

Also add `GEMINI_API_KEY` to `backend/.env` (the AI feature reads it from there, gitignored):

```env
GEMINI_API_KEY=your_gemini_api_key
```

2. Build and start all services:

```bash
docker compose up --build
```

3. Access the app:

| Service | URL |
|---|---|
| Frontend | http://localhost |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5433 |
| Redis | localhost:6379 |

---

## Local Development Setup

### Backend

```bash
cd backend

# Copy env template and fill values
cp .env.example .env
# Edit .env with your local DB/Redis/API credentials

# Run (requires local Postgres on port 5433 and Redis on 6379)
./mvnw spring-boot:run
```

The backend starts on `http://localhost:8080`. Schema tables are created/updated automatically via Hibernate `ddl-auto: update`.

### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start dev server (proxied to backend on port 8080)
npm run dev
```

Frontend runs on `http://localhost:5173`. The `VITE_API_BASE_URL` in `frontend/.env` points to the backend.

---

## Environment Variables

### Root `.env` (for Docker Compose)

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | HS256 signing key (min. 64 characters) |
| `RAZORPAY_KEY_ID` | Razorpay publishable key |
| `RAZORPAY_KEY_SECRET` | Razorpay secret key |
| `RAZORPAY_WEBHOOK_SECRET` | Webhook signature secret |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |
| `MAIL_USERNAME` | Gmail address for sending email |
| `MAIL_PASSWORD` | Gmail App Password |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `APP_FRONTEND_URL` | Base URL for email verification links (default: `http://localhost`) |
| `DELIVERY_MAX_RADIUS_KM` | Max delivery radius (default: `10`) |
| `DELIVERY_AVERAGE_SPEED_KMPH` | Assumed delivery speed (default: `25`) |
| `DELIVERY_ROAD_DISTANCE_FACTOR` | Haversine-to-road correction factor (default: `1.3`) |

### `backend/.env` (local dev only, git-ignored)

| Variable | Description |
|---|---|
| `GEMINI_API_KEY` | Google Gemini API key for AI cake design |
| `FAST2SMS_API_KEY` | Fast2SMS key for SMS notifications |
| All above variables | Same set as root `.env`, for running backend directly |

### `frontend/.env`

| Variable | Description |
|---|---|
| `VITE_API_BASE_URL` | Backend base URL (default: `http://localhost:8080`) |

---

## API Overview

All authenticated endpoints require `Authorization: Bearer <access_token>`. Role guards are enforced with Spring Security `@PreAuthorize`.

| Domain | Base path | Auth |
|---|---|---|
| Auth | `/api/auth/**` | Public (login/register/refresh/verify) |
| Users | `/api/users/**` | Any authenticated user |
| Products | `/api/products/**` | Public (read), SELLER (write) |
| Categories | `/api/categories/**` | Public (read), ADMIN (write) |
| Cart | `/api/cart/**` | CUSTOMER |
| Orders | `/api/orders/**` | CUSTOMER / SELLER |
| Payments | `/api/payments/**` | CUSTOMER (create), Public (webhook) |
| Reviews | `/api/reviews/**` | CUSTOMER (write), Public (read) |
| Sellers | `/api/sellers/**` | Public (directory), SELLER (profile/analytics) |
| Influencers | `/api/influencers/**` | Public (directory) |
| Influencer profile | `/api/influencer/**` | INFLUENCER |
| Collaborations | `/api/collaborations/**` | SELLER / INFLUENCER |
| Reels | `/api/reels/**` | Public (feed), SELLER/INFLUENCER (upload) |
| Content feed | `/api/content/**` | Public |
| Custom orders | `/api/custom-orders/**` | CUSTOMER / SELLER |
| AI cake design | `/api/ai/cake-design/**` | CUSTOMER |
| Wallet | `/api/influencer/wallet/**` | INFLUENCER |
| Payouts | `/api/influencer/payout/**` (submit) / `/api/admin/payout/**` (approve) | INFLUENCER / ADMIN |
| Addresses | `/api/addresses/**` | Authenticated |
| Favourites | `/api/favourites/**` | Authenticated |
| Notifications | `/api/notifications/**` | Authenticated |
| Role applications | `/api/role-applications/**` | Authenticated (submit) / ADMIN (review) |
| Admin | `/api/admin/**` | ADMIN |
| Map / ETA | `/api/map/**` | Authenticated |
| WebSocket | `/ws` (STOMP endpoint) | JWT handshake |

Full request/response shapes are documented in `backend/BAKEAURA_API_REFERENCE.md`.

---

## Key Design Decisions

**JWT stored in memory, refresh token in Redis** — Access tokens (15 min) live only in the Zustand store (no localStorage); refresh tokens (7 days) are stored in Redis and rotated on each use. The JWT subject is the numeric user ID, not the email.

**Cart in Redis, not PostgreSQL** — Cart state is ephemeral and read-heavy. It is stored as a Redis hash keyed by `cart:{userId}` with no persistence to a JPA table.

**Wallet balance always derived** — `WalletService.getBalance()` runs `SELECT SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END)` rather than maintaining a stored balance column. This eliminates balance drift bugs at the cost of one DB aggregation per balance check.

**Referral pipeline is event-driven** — `OrderService` publishes `OrderCreatedEvent`; `ReferralOrderService` is an `@EventListener` that decouples referral commission from the order creation transaction.

**Stock is reserved at order creation, deducted at payment capture** — `ProductService.validateAndReserveStock` locks stock when the order row is created; `PaymentService.reduceStock` decrements it only after Razorpay confirms payment. This prevents overselling without requiring a distributed lock.

**Haversine only** — Distance and ETA between buyer and seller use pure Haversine math in `MapService`. There is no Google Maps or any third-party maps API.

**Feed ranking formula** — `ContentService.getRankedFeed` scores each Reel as `0.40 × recency + 0.25 × normalizedLikes + 0.20 × normalizedViews + 0.15 × sellerRating/5`. Recency uses linear decay; all components are normalized to [0, 1] before weighting.

**AI cake design flow** — `POST /api/ai/cake-design/preview` calls Gemini 2.5 Flash for a text design brief, then calls the `gemini-2.5-flash-image` model for a base64 image. The image is NOT persisted at this step. `POST /api/ai/cake-design/confirm` takes the base64 bytes from the client, uploads them to Cloudinary, then creates a `CustomOrderRequest` row and notifies the seller.

---

## Known Limitations

- **Test suite is partially stale** — 8 of 12 test files reference the old `email-as-JWT-subject` API surface and will not compile. Only `MapServiceTest`, `CategoryControllerTest`, `CategoryServiceTest`, and `BakeauraBackendApplicationTests` match the current codebase.
- **No follow/unfollow, no Stories** — These were originally planned but are not implemented. The Reel feed uses engagement-based ranking, not a social graph.
- **`SellerProfile.totalRatings` / `averageRating` columns are always zero** — `ReviewService.getSummary` uses a live `AVG()` query (correct); `SellerService.toDto` reads the stale columns (incorrect). Use the summary endpoint for accurate ratings.
- **`PayoutStatus.PAID` can only be reached via the admin "Mark as paid" action** — there is no automated payout-to-UPI step; admin marks manually after transferring outside the app.
- **AI rate limit** — The `RateLimitFilter` allows 3 AI requests per 10 minutes per IP on `POST /api/ai/**`.
